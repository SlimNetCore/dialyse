package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacydossiermedical.RisquesInfectieuxJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Places du centre dans les colonnes de place de {@code patients} ({@code salle_id}, {@code position_id}, {@code generateur_id},
 * {@code jour_*}) : aucune migration (ADR 0052). Une occupation exige une salle, un créneau et au moins un jour (RG-PLN-010) ; un
 * patient sorti libère sa place selon {@link FinDOccupation}, jugée au jour du centre ; un séjour pas encore commencé reste compté
 * (prudence du legacy). L'écriture ne touche que les colonnes de place. Toutes les requêtes sont bornées au centre.
 */
public class PlacesDuCentreJdbc implements PlacesDuCentreRepository {

    private static final String COLONNES = "id, salle_id, position_id, generateur_id, etat_patient, date_evenement_etat, "
            + "jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi";

    private final JdbcTemplate jdbc;

    public PlacesDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Ligne(Occupation occupation, LocalDate dernierJour) {
    }

    @Override
    public PlacesDuCentre charger(CentreId centre, RessourcesDuCentre ressources, LocalDate jugeLe) {
        Set<UUID> aRisque = RisquesInfectieuxJdbc.patientsARisque(jdbc, centre.valeur());
        List<Ligne> lignes = jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ? AND salle_id IS NOT NULL "
                + "AND position_id IS NOT NULL", (rs, i) -> lire(rs, aRisque), centre.valeur());
        List<Occupation> occupations = new ArrayList<>();
        for (Ligne ligne : lignes) {
            boolean libre = ligne.occupation().jours().isEmpty()
                    || (ligne.dernierJour() != null && jugeLe.isAfter(ligne.dernierJour()));
            if (!libre) occupations.add(ligne.occupation());
        }
        return PlacesDuCentre.de(centre, ressources, occupations);
    }

    @Override
    public Optional<Occupation> placementActuel(CentreId centre, PatientId patient) {
        Set<UUID> aRisque = RisquesInfectieuxJdbc.patientsARisque(jdbc, centre.valeur());
        return jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ? AND id = ?",
                (rs, i) -> lire(rs, aRisque).occupation(), centre.valeur(), patient.valeur()).stream().findFirst();
    }

    @Override
    public List<PatientId> patientsDuGenerateur(CentreId centre, GenerateurId generateur, LocalDate jugeLe) {
        List<PatientId> patients = new ArrayList<>();
        jdbc.query("SELECT id, etat_patient, date_evenement_etat FROM patients WHERE center_id = ? AND generateur_id = ? ORDER BY id",
                rs -> {
                    Date evenement = rs.getDate("date_evenement_etat");
                    LocalDate dernierJour = FinDOccupation.dernierJourOccupe(rs.getString("etat_patient"),
                            evenement == null ? null : evenement.toLocalDate()).orElse(null);
                    if (dernierJour == null || !jugeLe.isAfter(dernierJour)) {
                        patients.add(new PatientId(rs.getObject("id", UUID.class)));
                    }
                }, centre.valeur(), generateur.valeur());
        return patients;
    }

    @Override
    public void enregistrer(PlacesDuCentre places) {
        for (PlacesDuCentre.Changement changement : places.changements()) {
            Optional<Place> place = changement.place();
            StringBuilder sql = new StringBuilder("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ?");
            List<Object> args = new ArrayList<>();
            args.add(place.map(p -> p.salle().valeur()).orElse(null));
            args.add(place.map(p -> p.creneau().valeur()).orElse(null));
            args.add(place.map(p -> p.generateur().valeur()).orElse(null));
            for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                sql.append(", jour_").append(jour.name().toLowerCase(Locale.ROOT)).append(" = ?");
                args.add(place.map(p -> p.jours().contains(jour)).orElse(false));
            }
            sql.append(" WHERE center_id = ? AND id = ?");
            args.add(places.centre().valeur());
            args.add(changement.patient().valeur());
            jdbc.update(sql.toString(), args.toArray());
        }
    }

    private static Ligne lire(java.sql.ResultSet rs, Set<UUID> aRisque) throws java.sql.SQLException {
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
        }
        UUID patient = rs.getObject("id", UUID.class);
        UUID salle = rs.getObject("salle_id", UUID.class);
        UUID creneau = rs.getObject("position_id", UUID.class);
        UUID generateur = rs.getObject("generateur_id", UUID.class);
        Date evenement = rs.getDate("date_evenement_etat");
        LocalDate dernierJour = FinDOccupation.dernierJourOccupe(rs.getString("etat_patient"),
                evenement == null ? null : evenement.toLocalDate()).orElse(null);
        Occupation occupation = new Occupation(new PatientId(patient), salle == null ? null : new SalleId(salle),
                creneau == null ? null : new CreneauId(creneau), generateur == null ? null : new GenerateurId(generateur), jours,
                CategorieDIsolement.deduite(aRisque.contains(patient)));
        return new Ligne(occupation, dernierJour);
    }
}
