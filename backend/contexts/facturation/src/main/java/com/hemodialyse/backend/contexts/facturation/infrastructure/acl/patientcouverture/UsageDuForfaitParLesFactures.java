package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Ce que les factures retiennent d'un forfait du catalogue (RF2, ADR 0124) : le nombre de lignes de facture ({@code facture_lignes})
 * qui le portent, sans regarder le centre (comme le contrôle d'avant). Additionné aux autres usages par le catalogue pour refuser la
 * suppression d'un forfait utilisé. Une table absente d'un schéma allégé est ignorée, comme avant.
 */
public class UsageDuForfaitParLesFactures implements UsageDuForfait {

    private final JdbcTemplate jdbc;

    public UsageDuForfaitParLesFactures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long nombreDeReferences(UUID centre, UUID forfait) {
        try {
            Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM facture_lignes WHERE forfait_id = ?", Long.class, forfait);
            return nombre == null ? 0 : nombre;
        } catch (BadSqlGrammarException schemaAllege) {
            return 0;
        }
    }
}
