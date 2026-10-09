package com.hemodialyse.backend.infrastructure.persistence.adapter.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.CompteRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan comptable enregistré d'un centre (table {@code comptes_comptables}). Chaque requête est bornée au
 * {@code center_id} ; SQL compatible H2 et PostgreSQL.
 */
@Component
public class PlanComptableJdbcAdapter implements CompteRepositoryPort {

    private static final RowMapper<CompteComptable> COMPTE = (rs, i) ->
            new CompteComptable(rs.getString("numero"), rs.getString("libelle"), rs.getBoolean("actif"));

    private final JdbcTemplate jdbc;

    public PlanComptableJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long count(UUID centerId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM comptes_comptables WHERE center_id = ?", Long.class,
                centerId);
        return total == null ? 0 : total;
    }

    @Override
    public PagedResult<CompteComptable> findPaged(UUID centerId, String recherche, boolean actifsSeulement, int page,
                                                  int size) {
        StringBuilder filtre = new StringBuilder(" FROM comptes_comptables WHERE center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centerId));
        if (actifsSeulement) {
            filtre.append(" AND actif = TRUE");
        }
        if (recherche != null && !recherche.isBlank()) {
            filtre.append(" AND (LOWER(numero) LIKE ? OR LOWER(libelle) LIKE ?)");
            String motif = "%" + recherche.trim().toLowerCase(Locale.ROOT) + "%";
            parametres.add(motif);
            parametres.add(motif);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filtre, Long.class, parametres.toArray());
        parametres.add(size);
        parametres.add((long) page * size);
        List<CompteComptable> items = jdbc.query("SELECT numero, libelle, actif" + filtre
                + " ORDER BY numero LIMIT ? OFFSET ?", COMPTE, parametres.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<CompteComptable> find(UUID centerId, String numero) {
        return jdbc.query("SELECT numero, libelle, actif FROM comptes_comptables WHERE center_id = ? AND numero = ?",
                COMPTE, centerId, numero).stream().findFirst();
    }

    @Override
    public void save(UUID centerId, CompteComptable compte) {
        int modifies = jdbc.update("UPDATE comptes_comptables SET libelle = ?, actif = ? WHERE center_id = ? "
                + "AND numero = ?", compte.libelle(), compte.actif(), centerId, compte.numero());
        if (modifies == 0) {
            jdbc.update("INSERT INTO comptes_comptables (id, center_id, numero, libelle, actif) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), centerId, compte.numero(), compte.libelle(), compte.actif());
        }
    }

    @Override
    public void delete(UUID centerId, String numero) {
        jdbc.update("DELETE FROM comptes_comptables WHERE center_id = ? AND numero = ?", centerId, numero);
    }
}
