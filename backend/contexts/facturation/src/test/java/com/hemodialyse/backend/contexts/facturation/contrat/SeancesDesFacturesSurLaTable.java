package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Double de TEST du langage fourni {@link SeancesDesFacturesFournies} : il lit la table des séances que le contrat de pilotage sème lui-même
 * (le module Facturation ne peut pas dépendre de la Séance, ADR 0097). Il n'existe que pour prouver les requêtes de la Facturation (factures,
 * caisses, tris) ; le vrai adapter de la Séance et sa sémantique sont prouvés par le contrat de la Séance (mémoire, H2, PG16) et par les goldens
 * de pilotage de la plateforme, câblés en production.
 */
final class SeancesDesFacturesSurLaTable implements SeancesDesFacturesFournies {

    private final JdbcTemplate jdbc;

    SeancesDesFacturesSurLaTable(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures) {
        Map<UUID, Long> nombres = new HashMap<>();
        for (UUID facture : factures) {
            Long n = jdbc.queryForObject("SELECT COUNT(1) FROM seances WHERE center_id = ? AND facture_id = ?", Long.class, centre, facture);
            if (n != null && n > 0) {
                nombres.put(facture, n);
            }
        }
        return nombres;
    }

    @Override
    public Map<UUID, Long> nombreParFactureAuStatutFacturee(UUID centre, Collection<UUID> factures) {
        Map<UUID, Long> nombres = new HashMap<>();
        for (UUID facture : factures) {
            Long n = jdbc.queryForObject("SELECT COUNT(1) FROM seances WHERE center_id = ? AND facture_id = ? AND statut = 'FACTUREE'",
                    Long.class, centre, facture);
            if (n != null && n > 0) {
                nombres.put(facture, n);
            }
        }
        return nombres;
    }

    @Override
    public long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin) {
        Long n = jdbc.queryForObject("SELECT COUNT(1) FROM seances WHERE center_id = ? AND statut = 'FACTUREE' AND date_seance BETWEEN ? AND ?",
                Long.class, centre, Date.valueOf(debut), Date.valueOf(fin));
        return n == null ? 0L : n;
    }
}
