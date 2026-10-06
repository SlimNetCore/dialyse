package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.port.PlacementPatientPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Placement des patients sur les colonnes {@code salle_id}, {@code position_id}, {@code generateur_id} et
 * {@code jour_*} de la table {@code patients}. Toutes les requêtes sont bornées au centre.
 */
@Component
public class PlacementPatientJdbcAdapter implements PlacementPatientPort {

    private final JdbcTemplate jdbc;

    public PlacementPatientJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Placement placementActuel(UUID centerId, UUID patientId) {
        return jdbc.query("SELECT salle_id, position_id, generateur_id, jour_dimanche, jour_lundi, jour_mardi, "
                + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi FROM patients "
                + "WHERE center_id = ? AND id = ?", (rs, i) -> {
            Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
            for (JourSemaine jour : JourSemaine.values()) {
                if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
            }
            return new Placement(rs.getObject("salle_id", UUID.class), rs.getObject("position_id", UUID.class),
                    rs.getObject("generateur_id", UUID.class), jours);
        }, centerId, patientId).stream().findFirst().orElse(Placement.vide());
    }

    @Override
    public void deplacer(UUID centerId, UUID patientId, UUID salleId, UUID creneauId, UUID generateurId) {
        jdbc.update("UPDATE patients SET salle_id = ?, position_id = ?, generateur_id = ? "
                + "WHERE center_id = ? AND id = ?", salleId, creneauId, generateurId, centerId, patientId);
    }

    @Override
    public void definirJours(UUID centerId, UUID patientId, Set<JourSemaine> jours) {
        StringBuilder sql = new StringBuilder("UPDATE patients SET ");
        Object[] args = new Object[JourSemaine.NB_JOURS + 2];
        int i = 0;
        for (JourSemaine jour : JourSemaine.values()) {
            sql.append(i == 0 ? "" : ", ").append("jour_").append(jour.name().toLowerCase(Locale.ROOT)).append(" = ?");
            args[i++] = jours.contains(jour);
        }
        sql.append(" WHERE center_id = ? AND id = ?");
        args[i++] = centerId;
        args[i] = patientId;
        jdbc.update(sql.toString(), args);
    }

    @Override
    public String nomPatient(UUID centerId, UUID patientId) {
        return jdbc.query("SELECT prenom, nom FROM patients WHERE center_id = ? AND id = ?",
                        (rs, i) -> (rs.getString(1) + " " + rs.getString(2)).trim(), centerId, patientId)
                .stream().findFirst().orElse("");
    }
}
