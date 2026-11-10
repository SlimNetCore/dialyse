package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que les prises en charge (table {@code prise_en_charge}) retiennent d'un forfait (RF2) : le forfait demandé et le forfait
 * effectif comptent chacun, mais seules les prises en charge DU CENTRE du forfait comptent (KB-RF2-02 corrigé, ADR 0125). Une table
 * absente d'un schéma allégé est ignorée, comme avant.
 */
public class UsageDuForfaitParLesPec implements UsageDuForfait {

    private final JdbcTemplate jdbc;

    public UsageDuForfaitParLesPec(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID centre, UUID forfait) {
        long total = 0;
        for (String colonne : new String[]{"forfait_demande_id", "forfait_effectif_id"}) {
            try {
                Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM prise_en_charge WHERE " + colonne + " = ? AND center_id = ?",
                        Long.class, forfait, centre);
                total += nombre == null ? 0 : nombre;
            } catch (DataAccessException schemaAllege) {
                // table absente d'un schéma allégé : le contrôle est ignoré, comme avant
            }
        }
        return total;
    }
}
