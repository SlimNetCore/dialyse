package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationDeSeance;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationsImputees;
import com.hemodialyse.backend.contexts.stock.domain.consommations.Imputation;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tables {@code consommations_de_seance} et {@code consommations_de_seance_imputations} (migration V45, slice S6b), dans la
 * transaction de l'appelant (la pièce est verrouillée par le cas d'usage).
 */
public class ConsommationsImputeesJdbc implements ConsommationsImputees {
    private final JdbcTemplate jdbc;

    public ConsommationsImputeesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConsommationDeSeance> parSeance(CentreId centre, UUID seance) {
        return jdbc.query("SELECT patient_id, sortie_id FROM consommations_de_seance WHERE seance_id = ? AND center_id = ?",
                (rs, i) -> new UUID[]{rs.getObject("patient_id", UUID.class), rs.getObject("sortie_id", UUID.class)}, seance,
                centre.valeur()).stream().findFirst().map(entete -> {
            List<Imputation> imputations = jdbc.query("SELECT id, article_id, lot_id, quantite, alerte FROM "
                            + "consommations_de_seance_imputations WHERE seance_id = ?",
                    (rs, i) -> new Imputation(rs.getObject("id", UUID.class), rs.getObject("article_id", UUID.class),
                            rs.getObject("lot_id", UUID.class), rs.getBigDecimal("quantite"), rs.getString("alerte")), seance);
            return ConsommationDeSeance.reconstituer(seance, centre, entete[0], entete[1], imputations);
        });
    }

    @Override
    public void enregistrer(ConsommationDeSeance c) {
        int modifiees = jdbc.update("UPDATE consommations_de_seance SET patient_id = ?, sortie_id = ?, mise_a_jour = ? WHERE seance_id = ? "
                + "AND center_id = ?", c.patientId(), c.sortieId(), OffsetDateTime.now(), c.seanceId(), c.centre().valeur());
        if (modifiees == 0) {
            jdbc.update("INSERT INTO consommations_de_seance (seance_id, center_id, patient_id, sortie_id, mise_a_jour) "
                    + "VALUES (?, ?, ?, ?, ?)", c.seanceId(), c.centre().valeur(), c.patientId(), c.sortieId(), OffsetDateTime.now());
        }
        jdbc.update("DELETE FROM consommations_de_seance_imputations WHERE seance_id = ?", c.seanceId());
        jdbc.batchUpdate("INSERT INTO consommations_de_seance_imputations (id, seance_id, article_id, lot_id, quantite, alerte) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                c.imputations().stream().map(i -> new Object[]{i.id(), c.seanceId(), i.articleId(), i.lotId(), i.quantite(),
                        i.alerte()}).toList());
    }

    @Override
    public void supprimer(CentreId centre, UUID seance) {
        jdbc.update("DELETE FROM consommations_de_seance_imputations WHERE seance_id IN (SELECT seance_id FROM consommations_de_seance "
                + "WHERE seance_id = ? AND center_id = ?)", seance, centre.valeur());
        jdbc.update("DELETE FROM consommations_de_seance WHERE seance_id = ? AND center_id = ?", seance, centre.valeur());
    }

    @Override
    public Map<UUID, BigDecimal> totauxParLotSaufSeance(CentreId centre, UUID sortie, UUID seance) {
        Map<UUID, BigDecimal> totaux = new LinkedHashMap<>();
        jdbc.query("SELECT i.lot_id, SUM(i.quantite) AS total FROM consommations_de_seance_imputations i JOIN consommations_de_seance c "
                + "ON c.seance_id = i.seance_id WHERE c.sortie_id = ? AND c.center_id = ? AND c.seance_id <> ? AND i.lot_id IS NOT NULL "
                + "GROUP BY i.lot_id", rs -> {
            totaux.put(rs.getObject("lot_id", UUID.class), rs.getBigDecimal("total"));
        }, sortie, centre.valeur(), seance);
        return totaux;
    }
}