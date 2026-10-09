package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.application.supervision.SanteBase.IndexInutilise;
import com.hemodialyse.backend.application.supervision.SanteBase.TableSante;
import com.hemodialyse.backend.application.supervision.port.SanteBasePort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Indicateurs de santé lus dans les vues statistiques de PostgreSQL. Les requêtes sont des fichiers SQL sans paramètre
 * ({@code db/supervision/*.sql}), rejoués tels quels par la CI sur un PostgreSQL réel : une faute de syntaxe ou une
 * colonne renommée par une nouvelle version majeure s'y voit avant la production.
 */
@Component
public class PgSanteBaseAdapter implements SanteBasePort {

    private final JdbcTemplate jdbc;

    public PgSanteBaseAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String sql(String fichier) {
        try {
            return StreamUtils.copyToString(new ClassPathResource("db/supervision/" + fichier).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Requête de supervision introuvable : " + fichier, e);
        }
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    @Override
    public boolean disponible() {
        String produit = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        return produit != null && produit.toLowerCase().contains("postgres");
    }

    @Override
    public Generale generale() {
        return jdbc.queryForObject(sql("sante-generale.sql"), (rs, i) -> new Generale(
                rs.getLong("taille"), rs.getDouble("cache_pct"), rs.getInt("connexions"), rs.getInt("connexions_max"),
                instant(rs.getTimestamp("stats_reset"))));
    }

    @Override
    public List<TableSante> plusGrossesTables() {
        return jdbc.query(sql("sante-tables.sql"), (rs, i) -> new TableSante(
                rs.getString("nom"), rs.getLong("taille"), rs.getLong("lignes"), rs.getLong("mortes"),
                rs.getLong("vivantes"), rs.getLong("scans_complets"), rs.getLong("lignes_lues_scans"),
                rs.getLong("scans_index"), instant(rs.getTimestamp("dernier_vacuum"))));
    }

    @Override
    public List<IndexInutilise> indexInutilises() {
        return jdbc.query(sql("sante-index-inutilises.sql"), (rs, i) -> new IndexInutilise(
                rs.getString("nom_table"), rs.getString("nom_index"), rs.getLong("taille")));
    }
}
