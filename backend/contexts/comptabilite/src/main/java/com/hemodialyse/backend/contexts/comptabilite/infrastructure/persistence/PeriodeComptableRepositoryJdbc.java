package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC de periodes_comptables, limité au centre demandé.
 */
public final class PeriodeComptableRepositoryJdbc implements PeriodeComptableRepository {
    private final JdbcTemplate jdbc;

    public PeriodeComptableRepositoryJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(Object value) {
        if (value instanceof OffsetDateTime date) return date.toInstant();
        if (value instanceof Timestamp date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        return null;
    }

    @Override
    public Optional<PeriodeComptable> duMois(CentreId centre, YearMonth mois) {
        List<PeriodeComptable> periodes = jdbc.query("SELECT cloturee, cloturee_at, cloturee_by FROM periodes_comptables "
                        + "WHERE center_id = ? AND annee = ? AND mois = ? ORDER BY id LIMIT 1",
                (rs, row) -> PeriodeComptable.reconstituer(centre, mois, rs.getBoolean("cloturee"),
                        instant(rs.getObject("cloturee_at")), rs.getString("cloturee_by")),
                centre.valeur(), mois.getYear(), mois.getMonthValue());
        return periodes.stream().findFirst();
    }

    @Override
    public void enregistrer(PeriodeComptable periode) {
        List<UUID> existante = jdbc.query("SELECT id FROM periodes_comptables WHERE center_id = ? AND annee = ? AND mois = ? "
                        + "ORDER BY id LIMIT 1", (rs, row) -> rs.getObject("id", UUID.class),
                periode.centre().valeur(), periode.mois().getYear(), periode.mois().getMonthValue());
        if (existante.isEmpty()) {
            jdbc.update("INSERT INTO periodes_comptables (id, center_id, annee, mois, cloturee, cloturee_at, cloturee_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), periode.centre().valeur(),
                    periode.mois().getYear(), periode.mois().getMonthValue(), periode.estCloturee(),
                    periode.clotureeLe() == null ? null : OffsetDateTime.ofInstant(periode.clotureeLe(), java.time.ZoneOffset.UTC),
                    periode.clotureePar());
        } else {
            jdbc.update("UPDATE periodes_comptables SET cloturee = ?, cloturee_at = ?, cloturee_by = ? "
                            + "WHERE id = ? AND center_id = ?", periode.estCloturee(),
                    periode.clotureeLe() == null ? null : OffsetDateTime.ofInstant(periode.clotureeLe(), java.time.ZoneOffset.UTC),
                    periode.clotureePar(), existante.getFirst(), periode.centre().valeur());
        }
    }
}
