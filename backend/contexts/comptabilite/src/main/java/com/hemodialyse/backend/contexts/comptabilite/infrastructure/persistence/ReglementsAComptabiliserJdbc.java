package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementsAComptabiliser;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lecture de la table {@code facture_reglements} du Règlement (aucune écriture, aucune migration). Le mode est toujours
 * la banque : la table ne le porte pas (B13).
 */
public final class ReglementsAComptabiliserJdbc implements ReglementsAComptabiliser {

    private final JdbcTemplate jdbc;

    public ReglementsAComptabiliserJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID identifiantDuReglement(CentreId centre, UUID facture, BigDecimal montant, LocalDate date, String auteur) {
        return jdbc.query("SELECT fr.id FROM facture_reglements fr WHERE fr.facture_id = ? AND fr.center_id = ? "
                                + "AND fr.date_reglement = ? AND fr.montant = ? AND COALESCE(fr.saisi_par, '') = COALESCE(?, '') "
                                + "ORDER BY fr.created_at DESC LIMIT 1",
                        (rs, row) -> rs.getObject("id", UUID.class), facture, centre.valeur(), date, montant, auteur)
                .stream().findFirst()
                .orElseGet(() -> UUID.nameUUIDFromBytes((centre.valeur() + "|" + facture + "|" + date + "|" + montant
                        + "|" + auteur).getBytes()));
    }

    @Override
    public List<ReglementAComptabiliser> encaissesEntre(CentreId centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT fr.id, fr.facture_id, fr.montant, fr.date_reglement FROM facture_reglements fr "
                        + "WHERE fr.center_id = ? AND fr.date_reglement BETWEEN ? AND ? ORDER BY fr.date_reglement, fr.id",
                (rs, row) -> new ReglementAComptabiliser(rs.getObject("id", UUID.class),
                        rs.getObject("facture_id", UUID.class), rs.getBigDecimal("montant"),
                        rs.getObject("date_reglement", LocalDate.class), "BANQUE", "Règlement facture", null),
                centre.valeur(), du, au);
    }
}

