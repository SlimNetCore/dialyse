package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.planification.application.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceLiberee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

/**
 * Mouvement {@code PLACE_LIBEREE} dans la table {@code mouvement_patient} (écriture seule, aucune mise à jour, aucune migration) :
 * automatique, daté du jour de la libération, état conservé (précédent et nouveau), place quittée figée (salle, créneau, générateur,
 * jours). Les mouvements d'admission et de changement d'état restent écrits par Patient et couverture.
 */
public class MouvementsDesPatientsJdbc implements MouvementsDesPatients {

    private final JdbcTemplate jdbc;

    public MouvementsDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static UUID valeur(com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId id) {
        return id == null ? null : id.valeur();
    }

    private static UUID valeur(com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId id) {
        return id == null ? null : id.valeur();
    }

    private static UUID valeur(com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId id) {
        return id == null ? null : id.valeur();
    }

    @Override
    public boolean dejaTrace(PlaceLiberee l) {
        return jdbc.query("SELECT type, etat_nouveau, salle_id, position_id, generateur_id, jours_dialyse FROM mouvement_patient "
                        + "WHERE center_id = ? AND patient_id = ? "
                        // à égalité d'horodatage (même milliseconde), la libération passe pour la dernière : jamais deux de suite
                        + "ORDER BY cree_le DESC, CASE WHEN type = 'PLACE_LIBEREE' THEN 0 ELSE 1 END, id LIMIT 1",
                (rs, i) -> "PLACE_LIBEREE".equals(rs.getString("type"))
                        && Objects.equals(l.etat(), rs.getString("etat_nouveau"))
                        && Objects.equals(valeur(l.placeQuittee().salle()), rs.getObject("salle_id", UUID.class))
                        && Objects.equals(valeur(l.placeQuittee().creneau()), rs.getObject("position_id", UUID.class))
                        && Objects.equals(valeur(l.placeQuittee().generateur()), rs.getObject("generateur_id", UUID.class))
                        && Objects.equals(l.placeQuittee().joursEnTexte(), rs.getString("jours_dialyse")),
                l.centre().valeur(), l.patient().valeur()).stream().findFirst().orElse(false);
    }

    @Override
    public void tracer(PlaceLiberee l) {
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_precedent, etat_nouveau, "
                        + "salle_id, position_id, generateur_id, jours_dialyse, automatique, cree_le) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), l.centre().valeur(), l.patient().valeur(), "PLACE_LIBEREE", Date.valueOf(l.dateDEffet()), l.etat(),
                l.etat(), valeur(l.placeQuittee().salle()), valeur(l.placeQuittee().creneau()), valeur(l.placeQuittee().generateur()),
                l.placeQuittee().joursEnTexte(), l.automatique(), Timestamp.from(l.survenueLe()));
    }
}
