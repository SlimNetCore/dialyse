package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.PlacesTenues;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceLiberee;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceQuittee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Places tenues sur les colonnes de place de {@code patients} (aucune migration, ADR 0052). Le dernier jour de place vient de
 * {@link FinDOccupation} (règle du legacy, asymétrie conservée : décédé et greffé la veille de la date, transféré et guéri le jour de
 * la date, sortie sans date libérée tout de suite, permanent et état inconnu jamais ; le sommeil n'y change rien). L'écriture ne
 * touche que les colonnes de place. Toutes les requêtes sont bornées au centre.
 */
public class PlacesTenuesJdbc implements PlacesTenues {

    private static final String COLONNES = "id, etat_patient, date_evenement_etat, salle_id, position_id, generateur_id, "
            + "jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi";
    private static final String AVEC_PLACE = " AND (salle_id IS NOT NULL OR position_id IS NOT NULL OR generateur_id IS NOT NULL)";

    private final JdbcTemplate jdbc;

    public PlacesTenuesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static PlaceTenue lire(CentreId centre, ResultSet rs) throws SQLException {
        Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
        }
        UUID salle = rs.getObject("salle_id", UUID.class);
        UUID creneau = rs.getObject("position_id", UUID.class);
        UUID generateur = rs.getObject("generateur_id", UUID.class);
        Date evenement = rs.getDate("date_evenement_etat");
        String etat = rs.getString("etat_patient");
        LocalDate dernierJour = FinDOccupation.dernierJourOccupe(etat, evenement == null ? null : evenement.toLocalDate()).orElse(null);
        return new PlaceTenue(centre, new PatientId(rs.getObject("id", UUID.class)), etat, dernierJour,
                new PlaceQuittee(salle == null ? null : new SalleId(salle), creneau == null ? null : new CreneauId(creneau),
                        generateur == null ? null : new GenerateurId(generateur), jours));
    }

    @Override
    public List<PlaceTenue> duCentre(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ?" + AVEC_PLACE + " ORDER BY id",
                (rs, i) -> lire(centre, rs), centre.valeur());
    }

    @Override
    public Optional<PlaceTenue> de(CentreId centre, PatientId patient) {
        return une(centre, patient, "");
    }

    @Override
    public Optional<PlaceTenue> verrouillee(CentreId centre, PatientId patient) {
        return une(centre, patient, " FOR UPDATE");
    }

    private Optional<PlaceTenue> une(CentreId centre, PatientId patient, String verrou) {
        return jdbc.query("SELECT " + COLONNES + " FROM patients WHERE center_id = ? AND id = ?" + AVEC_PLACE + verrou,
                (rs, i) -> lire(centre, rs), centre.valeur(), patient.valeur()).stream().findFirst();
    }

    @Override
    public void liberer(PlaceLiberee liberee) {
        StringBuilder sql = new StringBuilder("UPDATE patients SET salle_id = NULL, position_id = NULL, generateur_id = NULL");
        for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
            sql.append(", jour_").append(jour.name().toLowerCase(Locale.ROOT)).append(" = FALSE");
        }
        sql.append(" WHERE center_id = ? AND id = ?");
        jdbc.update(sql.toString(), liberee.centre().valeur(), liberee.patient().valeur());
    }
}
