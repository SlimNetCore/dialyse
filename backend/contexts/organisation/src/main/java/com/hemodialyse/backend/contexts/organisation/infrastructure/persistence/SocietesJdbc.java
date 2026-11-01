package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SocietesJdbc implements SocieteRepository {
    private static final RowMapper<Societe> MAPPER = (rs, i) -> new Societe(
            new SocieteId(rs.getObject("id", UUID.class)),
            rs.getString("code"),
            rs.getString("raison_sociale"),
            rs.getBoolean("actif"));
    private final JdbcTemplate jdbc;

    public SocietesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Societe> trouver(SocieteId id) {
        List<Societe> r = jdbc.query("SELECT id, code, raison_sociale, actif FROM societes WHERE id = ?", MAPPER, id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Societe s) {
        int n = jdbc.update("UPDATE societes SET code=?, raison_sociale=?, actif=? WHERE id=?", s.code(), s.raisonSociale(), s.actif(), s.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)", s.id().valeur(), s.code(), s.raisonSociale(), s.actif());
        }
    }

    @Override
    public List<Societe> lister() {
        return jdbc.query("SELECT id, code, raison_sociale, actif FROM societes ORDER BY code", MAPPER);
    }

    @Override
    public PagedResult<Societe> listerParPage(String recherche, int page, int size) {
        String filtre = (recherche == null || recherche.isBlank()) ? "%" : "%" + recherche.trim() + "%";
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM societes WHERE code ILIKE ? OR raison_sociale ILIKE ?",
                Long.class, filtre, filtre);
        List<Societe> items = jdbc.query(
                "SELECT id, code, raison_sociale, actif FROM societes "
                        + "WHERE code ILIKE ? OR raison_sociale ILIKE ? "
                        + "ORDER BY code LIMIT ? OFFSET ?",
                MAPPER, filtre, filtre, size, page * size);
        return PagedResult.of(items, total, page, size);
    }

    @Override
    public Optional<LogoDeSociete> lireLogo(SocieteId id) {
        List<LogoDeSociete> r = jdbc.query(
                "SELECT logo, logo_content_type FROM societes WHERE id = ? AND logo IS NOT NULL",
                (rs, i) -> new LogoDeSociete(rs.getBytes("logo"), rs.getString("logo_content_type")),
                id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void enregistrerLogo(SocieteId id, LogoDeSociete logo) {
        jdbc.update("UPDATE societes SET logo = ?, logo_content_type = ? WHERE id = ?",
                logo.contenu(), logo.contentType(), id.valeur());
    }

    @Override
    public void supprimerLogo(SocieteId id) {
        jdbc.update("UPDATE societes SET logo = NULL, logo_content_type = NULL WHERE id = ?",
                id.valeur());
    }

    @Override
    public Set<SocieteId> idsAvecLogo(Collection<SocieteId> ids) {
        if (ids.isEmpty()) return Set.of();
        List<UUID> valeurs = ids.stream().map(SocieteId::valeur).toList();
        String places = String.join(",", valeurs.stream().map(v -> "?").toList());
        List<SocieteId> trouves = jdbc.query(
                "SELECT id FROM societes WHERE id IN (" + places + ") AND logo IS NOT NULL",
                (rs, i) -> new SocieteId(rs.getObject("id", UUID.class)),
                valeurs.toArray());
        return new HashSet<>(trouves);
    }
}
