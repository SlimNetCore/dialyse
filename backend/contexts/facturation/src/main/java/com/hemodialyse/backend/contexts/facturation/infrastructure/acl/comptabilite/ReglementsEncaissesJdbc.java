package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournit à la Comptabilité les règlements encaissés (lecture seule, aucune migration). Le montant est signé (négatif :
 * remboursement) ; le mode est celui de la migration V41, nul pour les règlements d'avant. Les règlements de reprise (saisis par la
 * reprise) des factures de reprise des soldes d'ouverture sont exclus (KB-FAC-67, décision du 2026-10-21) ; un encaissement saisi
 * ensuite dans la plateforme sur une telle facture est fourni comme tout règlement.
 */
public class ReglementsEncaissesJdbc implements ReglementsEncaisses {

    private final JdbcTemplate jdbc;

    public ReglementsEncaissesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ReglementEncaisse> encaissesEntre(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT fr.id, fr.facture_id, fr.montant, fr.date_reglement, fr.mode_reglement FROM facture_reglements fr "
                        + "WHERE fr.center_id = ? AND fr.date_reglement BETWEEN ? AND ? "
                        + "AND (COALESCE(fr.saisi_par, '') <> ? OR NOT EXISTS (SELECT 1 FROM factures f WHERE f.id = fr.facture_id AND f.numero_facture LIKE ?)) "
                        + "ORDER BY fr.date_reglement, fr.id",
                (rs, row) -> new ReglementEncaisse(rs.getObject("id", UUID.class), rs.getObject("facture_id", UUID.class),
                        rs.getBigDecimal("montant"), rs.getObject("date_reglement", LocalDate.class),
                        rs.getString("mode_reglement")),
                centre, du, au, RepriseDesSoldes.AUTEUR_DE_LA_REPRISE, RepriseDesSoldes.PREFIXE_DES_NUMEROS + "%");
    }
}
