package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Identité imprimée sur les tables {@code centers} et {@code societes}. SQL repris à l'identique de {@code DocumentIdentityProvider}
 * (plateforme, O4 lot E, ADR 0111) : mêmes colonnes, même jointure externe (un centre sans société rend une société vide), aucun filtre
 * d'activité, textes rognés et jamais nuls, logo nul s'il est vide.
 */
public class IdentiteImprimeeJdbc implements IdentiteImprimee {

    private final JdbcTemplate jdbc;

    public IdentiteImprimeeJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Map<String, Object> lower(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>();
        row.forEach((k, v) -> out.put(k.toLowerCase(), v));
        return out;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : Objects.toString(v).trim();
    }

    private static IdentiteDeLaSociete societe(Map<String, Object> r) {
        byte[] logo = r.get("s_logo") instanceof byte[] bytes && bytes.length > 0 ? bytes : null;
        return new IdentiteDeLaSociete(str(r, "s_nom"), str(r, "s_adresse"), str(r, "s_ville"), str(r, "s_wilaya"), str(r, "s_tel"),
                str(r, "s_email"), str(r, "s_web"), str(r, "s_nif"), str(r, "s_nis"), str(r, "s_rc"), str(r, "s_pied"), logo);
    }

    @Override
    public Optional<IdentiteDuCentre> duCentre(UUID centre) {
        if (centre == null) return Optional.empty();
        var rows = jdbc.queryForList(
                "SELECT c.name AS c_nom, c.adresse AS c_adresse, c.ville AS c_ville, c.wilaya AS c_wilaya, "
                        + "c.telephone AS c_tel, c.email AS c_email, c.site_web AS c_web, "
                        + "s.raison_sociale AS s_nom, s.adresse AS s_adresse, s.ville AS s_ville, s.wilaya AS s_wilaya, "
                        + "s.telephone AS s_tel, s.email AS s_email, s.site_web AS s_web, s.nif AS s_nif, "
                        + "s.nis AS s_nis, s.rc AS s_rc, s.pied_page AS s_pied, s.logo AS s_logo "
                        + "FROM centers c LEFT JOIN societes s ON s.id = c.societe_id WHERE c.id = ?", centre);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> r = lower(rows.get(0));
        return Optional.of(new IdentiteDuCentre(str(r, "c_nom"), str(r, "c_adresse"), str(r, "c_ville"), str(r, "c_wilaya"),
                str(r, "c_tel"), str(r, "c_email"), str(r, "c_web"), societe(r)));
    }

    @Override
    public Optional<IdentiteDeLaSociete> deLaSociete(UUID societe) {
        if (societe == null) return Optional.empty();
        var rows = jdbc.queryForList(
                "SELECT s.raison_sociale AS s_nom, s.adresse AS s_adresse, s.ville AS s_ville, s.wilaya AS s_wilaya, "
                        + "s.telephone AS s_tel, s.email AS s_email, s.site_web AS s_web, s.nif AS s_nif, "
                        + "s.nis AS s_nis, s.rc AS s_rc, s.pied_page AS s_pied, s.logo AS s_logo "
                        + "FROM societes s WHERE s.id = ?", societe);
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(societe(lower(rows.get(0))));
    }
}
