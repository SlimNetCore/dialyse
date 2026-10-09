package com.hemodialyse.backend.infrastructure.persistence.adapter.comptabilite;

import com.hemodialyse.backend.domain.comptabilite.port.ComptePayeurRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.PayeursPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Payeurs d'un centre (référentiel {@code centre_payeur}, en lecture) et compte client propre à chacun (table
 * {@code comptes_payeurs}). Chaque requête est bornée au {@code center_id}.
 */
@Component
public class ComptePayeurJdbcAdapter implements ComptePayeurRepositoryPort, PayeursPort {

    private static final RowMapper<Payeur> PAYEUR = (rs, i) ->
            new Payeur(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"));

    private final JdbcTemplate jdbc;

    public ComptePayeurJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─── Payeurs ─────────────────────────────────────────────────────────────

    @Override
    public PagedResult<Payeur> lister(UUID centerId, String recherche, int page, int size) {
        StringBuilder filtre = new StringBuilder(" FROM centre_payeur WHERE center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centerId));
        if (recherche != null && !recherche.isBlank()) {
            filtre.append(" AND (LOWER(code) LIKE ? OR LOWER(nom) LIKE ?)");
            String motif = "%" + recherche.trim().toLowerCase(Locale.ROOT) + "%";
            parametres.add(motif);
            parametres.add(motif);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filtre, Long.class, parametres.toArray());
        parametres.add(size);
        parametres.add((long) page * size);
        List<Payeur> items = jdbc.query("SELECT id, code, nom" + filtre + " ORDER BY nom, code LIMIT ? OFFSET ?",
                PAYEUR, parametres.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<Payeur> trouver(UUID centerId, UUID payeurId) {
        return jdbc.query("SELECT id, code, nom FROM centre_payeur WHERE center_id = ? AND id = ?", PAYEUR, centerId,
                payeurId).stream().findFirst();
    }

    // ─── Comptes des payeurs ─────────────────────────────────────────────────

    @Override
    public Optional<String> find(UUID centerId, UUID payeurId) {
        return jdbc.queryForList("SELECT compte FROM comptes_payeurs WHERE center_id = ? AND payeur_id = ?",
                String.class, centerId, payeurId).stream().findFirst();
    }

    @Override
    public Map<UUID, String> findAll(UUID centerId, Collection<UUID> payeurIds) {
        Map<UUID, String> comptes = new HashMap<>();
        if (payeurIds.isEmpty()) {
            return comptes;
        }
        List<Object> parametres = new ArrayList<>(List.of(centerId));
        parametres.addAll(payeurIds);
        String jokers = String.join(", ", payeurIds.stream().map(id -> "?").toList());
        jdbc.query("SELECT payeur_id, compte FROM comptes_payeurs WHERE center_id = ? AND payeur_id IN (" + jokers + ")",
                rs -> {
                    comptes.put(rs.getObject("payeur_id", UUID.class), rs.getString("compte"));
                }, parametres.toArray());
        return comptes;
    }

    @Override
    public void save(UUID centerId, UUID payeurId, String compte) {
        int modifies = jdbc.update("UPDATE comptes_payeurs SET compte = ? WHERE center_id = ? AND payeur_id = ?",
                compte, centerId, payeurId);
        if (modifies == 0) {
            jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, ?)", centerId,
                    payeurId, compte);
        }
    }

    @Override
    public void delete(UUID centerId, UUID payeurId) {
        jdbc.update("DELETE FROM comptes_payeurs WHERE center_id = ? AND payeur_id = ?", centerId, payeurId);
    }

    @Override
    public boolean existsByCompte(UUID centerId, String compte) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM comptes_payeurs WHERE center_id = ? AND compte = ?",
                Long.class, centerId, compte);
        return total != null && total > 0;
    }
}
