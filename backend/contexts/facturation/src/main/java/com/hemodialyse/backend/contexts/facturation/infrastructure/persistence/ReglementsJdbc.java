package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.FactureARegler;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.ModeDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les règlements : table {@code facture_reglements} (id, facture, centre, montant signé, date, auteur dans {@code saisi_par},
 * code, et depuis la migration V41 mode, référence et clé d'idempotence, nullables). La facture est verrouillée
 * ({@code SELECT ... FOR UPDATE}, H2 et PostgreSQL) avant tout contrôle : deux règlements simultanés de la même facture s'exécutent
 * l'un après l'autre. L'unicité de la clé d'idempotence par (centre, facture) est aussi garantie par un index unique
 * (les valeurs nulles ne s'excluent pas).
 */
public class ReglementsJdbc implements Reglements {

    private static final String COLONNES = "id, facture_id, center_id, montant, date_reglement, saisi_par, code_reglement, "
            + "mode_reglement, reference_paiement, cle_idempotence";

    private static final RowMapper<Reglement> LIGNE = (rs, i) -> new Reglement(rs.getObject("id", UUID.class),
            rs.getObject("facture_id", UUID.class), new CentreId(rs.getObject("center_id", UUID.class)),
            rs.getBigDecimal("montant"), rs.getObject("date_reglement", LocalDate.class), rs.getString("saisi_par"),
            rs.getString("code_reglement"), mode(rs.getString("mode_reglement")), rs.getString("reference_paiement"),
            rs.getString("cle_idempotence"));

    private final JdbcTemplate jdbc;

    public ReglementsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static ModeDeReglement mode(String valeur) {
        return valeur == null ? null : ModeDeReglement.depuis(valeur);
    }

    @Override
    public Optional<FactureARegler> factureARegler(CentreId centre, UUID facture) {
        List<BigDecimal> totaux = jdbc.query("SELECT total_ttc FROM factures WHERE id = ? AND center_id = ? FOR UPDATE",
                (rs, i) -> rs.getBigDecimal("total_ttc"), facture, centre.valeur());
        if (totaux.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal regle = jdbc.queryForObject("SELECT COALESCE(SUM(montant), 0) FROM facture_reglements "
                + "WHERE facture_id = ? AND center_id = ?", BigDecimal.class, facture, centre.valeur());
        return Optional.of(new FactureARegler(facture, totaux.getFirst(), regle));
    }

    @Override
    public Optional<Reglement> parCleDIdempotence(CentreId centre, UUID facture, String cle) {
        return jdbc.query("SELECT " + COLONNES + " FROM facture_reglements WHERE center_id = ? AND facture_id = ? "
                + "AND cle_idempotence = ?", LIGNE, centre.valeur(), facture, cle).stream().findFirst();
    }

    @Override
    public void enregistrer(Reglement reglement) {
        jdbc.update("INSERT INTO facture_reglements (" + COLONNES + ", created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                reglement.id(), reglement.facture(), reglement.centre().valeur(), reglement.montant(),
                Date.valueOf(reglement.date()), reglement.auteur(), reglement.code(),
                reglement.mode() == null ? null : reglement.mode().name(), reglement.reference(), reglement.cleDIdempotence());
    }

    @Override
    public List<Reglement> historique(CentreId centre, UUID facture) {
        return jdbc.query("SELECT " + COLONNES + " FROM facture_reglements WHERE facture_id = ? AND center_id = ? "
                + "ORDER BY date_reglement DESC, created_at DESC", LIGNE, facture, centre.valeur());
    }
}
