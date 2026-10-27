package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.planification.application.SejoursDesPatients;
import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.SejourDuPatient;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacydossiermedical.RisquesInfectieuxJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers Patient et couverture ({@code patients}) : tous les patients du centre, leur place actuelle (colonnes de place, ADR 0052),
 * leur séjour (admission, état de sortie et date d'évènement traduits par {@link FinDOccupation}, sommeil) et leur nom « prénom nom »
 * comme la grille du legacy. Une requête, bornée au centre.
 */
public class SejoursDesPatientsJdbc implements SejoursDesPatients {

    private final JdbcTemplate jdbc;

    public SejoursDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PatientDeLaSemaine> duCentre(CentreId centre) {
        return lire(centre, RisquesInfectieuxJdbc.patientsARisque(jdbc, centre.valeur()));
    }

    /** Sans lecture du dossier médical : aucune sérologie n'est lue, tous les patients sont {@code SANS_RISQUE}. */
    @Override
    public List<PatientDeLaSemaine> duCentreSansRisque(CentreId centre) {
        return lire(centre, Set.of());
    }

    private List<PatientDeLaSemaine> lire(CentreId centre, Set<UUID> aRisque) {
        return jdbc.query("SELECT id, nom, prenom, salle_id, position_id, generateur_id, etat_patient, date_evenement_etat, "
                + "date_admission, COALESCE(en_sommeil, FALSE) AS en_sommeil, jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, "
                + "jour_jeudi, jour_vendredi, jour_samedi FROM patients WHERE center_id = ? ORDER BY prenom, nom, id", (rs, i) -> {
            Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
            for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
            }
            UUID id = rs.getObject("id", UUID.class);
            UUID salle = rs.getObject("salle_id", UUID.class);
            UUID creneau = rs.getObject("position_id", UUID.class);
            UUID generateur = rs.getObject("generateur_id", UUID.class);
            String etat = rs.getString("etat_patient");
            Date evenement = rs.getDate("date_evenement_etat");
            Date admission = rs.getDate("date_admission");
            LocalDate dernierJour = FinDOccupation.dernierJourOccupe(etat, evenement == null ? null : evenement.toLocalDate()).orElse(null);
            SejourDuPatient sejour = new SejourDuPatient(admission == null ? null : admission.toLocalDate(), dernierJour,
                    FinDOccupation.partLeDernierJour(etat), rs.getBoolean("en_sommeil"));
            String prenom = rs.getString("prenom") == null ? "" : rs.getString("prenom");
            String nom = rs.getString("nom") == null ? "" : rs.getString("nom");
            return new PatientDeLaSemaine(new PatientId(id), (prenom + " " + nom).trim(),
                    new Occupation(new PatientId(id), salle == null ? null : new SalleId(salle),
                            creneau == null ? null : new CreneauId(creneau), generateur == null ? null : new GenerateurId(generateur),
                            jours, CategorieDIsolement.deduite(aRisque.contains(id))), sejour);
        }, centre.valeur());
    }
}
