package com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacyseances;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * ACL vers les séances (contexte hors Stock, encore legacy) : une séance est facturée quand son statut est {@code FACTUREE}.
 * Remplace {@code SeanceBillingStatusJdbcAdapter} pour le contexte Stock ; sera remplacée par le langage publié de la
 * Facturation quand celui-ci exposera ce statut.
 */
public class StatutDesSeancesLegacy implements StatutDesSeances {

    private final JdbcTemplate jdbc;

    public StatutDesSeancesLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean estFacturee(CentreId centre, UUID seanceId) {
        return jdbc.queryForList("SELECT statut FROM seances WHERE id = ? AND center_id = ?", String.class, seanceId, centre.valeur())
                .stream().anyMatch("FACTUREE"::equalsIgnoreCase);
    }
}

