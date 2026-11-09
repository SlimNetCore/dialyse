package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.TraitementDeLAnemiePublie;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrations de l'anémie par centre sur la table réelle {@code administrations_anemie} (DM9, ADR 0101). Requête reprise MOT POUR MOT
 * de {@code platform/.../application/direction/DirectionBreakdownQueryService.java:217-224} (retirée de la plateforme) ; bornes incluses.
 */
public class TraitementDeLAnemieJdbc implements TraitementDeLAnemiePublie {

    private final JdbcTemplate jdbc;

    public TraitementDeLAnemieJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, AdministrationsDuCentre> parCentre(List<UUID> centres, LocalDate du, LocalDate au) {
        Map<UUID, AdministrationsDuCentre> comptes = new HashMap<>();
        if (centres.isEmpty()) return comptes;
        String in = String.join(",", Collections.nCopies(centres.size(), "?"));
        List<Object> args = new ArrayList<>(centres);
        args.addAll(List.of(Date.valueOf(du), Date.valueOf(au)));
        jdbc.query("SELECT center_id, "
                        + "COUNT(DISTINCT CASE WHEN administree = TRUE AND type_traitement = 'EPO' THEN patient_id END) AS pat_epo, "
                        + "COUNT(DISTINCT CASE WHEN administree = TRUE AND type_traitement = 'FER_INJECTABLE' THEN patient_id END) AS pat_fer, "
                        + "SUM(CASE WHEN administree = TRUE AND type_traitement = 'EPO' THEN 1 ELSE 0 END) AS adm_epo, "
                        + "SUM(CASE WHEN administree = TRUE AND type_traitement = 'FER_INJECTABLE' THEN 1 ELSE 0 END) AS adm_fer, "
                        + "SUM(CASE WHEN administree = FALSE THEN 1 ELSE 0 END) AS non_adm "
                        + "FROM administrations_anemie WHERE center_id IN (" + in + ") "
                        + "AND date_administration BETWEEN ? AND ? GROUP BY center_id",
                rs -> {
                    comptes.put(rs.getObject("center_id", UUID.class), new AdministrationsDuCentre(rs.getLong("pat_epo"), rs.getLong("pat_fer"),
                            rs.getLong("adm_epo"), rs.getLong("adm_fer"), rs.getLong("non_adm")));
                }, args.toArray());
        return comptes;
    }
}
