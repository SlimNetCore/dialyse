package com.hemodialyse.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-DDL3 (ADR 0186) : le DDL H2 est réparti en scripts par contexte propriétaire (liste {@code spring.sql.init.schema-locations} de
 * {@code application.yml}) ; le résultat, rejoué sur une base H2 VIERGE (sans Hibernate) dans l'ordre de cette liste, doit rester
 * STRICTEMENT identique à celui de l'ancien {@code schema.sql} unique : colonnes (rang, type, largeur, précision, nullité, défaut,
 * identité), contraintes (clé primaire, unicités, clés étrangères, vérifications) et index, relevés dans INFORMATION_SCHEMA et comparés
 * à {@code db/schema-h2-reference.txt}, relevé fait AVANT la scission. Les noms engendrés par H2 (CONSTRAINT_xx, PRIMARY_KEY_xx) sont
 * normalisés ; l'ordre des lignes ne compte pas.
 *
 * <p>Régénérer la référence (à ne faire que pour un changement voulu du schéma H2) :
 * {@code -Dschema.reference.ecrire=true} sur ce test.
 */
class SchemaH2DesScriptsParContexteTest {

    private static final Pattern NOM_ENGENDRE = Pattern.compile(
            "(CONSTRAINT|PRIMARY_KEY|CONSTRAINT_INDEX)_[0-9A-F]+(_INDEX_[0-9A-F]+)?");
    private static final String REFERENCE = "db/schema-h2-reference.txt";

    private static List<String> scriptsDansLOrdre() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties proprietes = yaml.getObject();
        String liste = proprietes.getProperty("spring.sql.init.schema-locations");
        return Arrays.stream(liste.split(",")).map(String::trim).toList();
    }

    /** Suffixe {@code _INDEX_<hex>} que H2 ajoute à l'index d'une contrainte : il dépend de l'ordre de création, pas du schéma. */
    private static final Pattern SUFFIXE_D_INDEX = Pattern.compile("_INDEX_[0-9A-F]+$");

    private static String normaliser(String nom) {
        if (nom == null) {
            return "";
        }
        if (NOM_ENGENDRE.matcher(nom).matches()) {
            return "(auto)";
        }
        return SUFFIXE_D_INDEX.matcher(nom).replaceFirst("_INDEX");
    }

    private static List<String> releve() throws Exception {
        PathMatchingResourcePatternResolver resolveur = new PathMatchingResourcePatternResolver();
        try (Connection base = DriverManager.getConnection(
                "jdbc:h2:mem:schema_par_contexte_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            for (String emplacement : scriptsDansLOrdre()) {
                Resource[] ressources = resolveur.getResources(emplacement);
                assertThat(ressources).as("script de schéma introuvable : " + emplacement).hasSize(1);
                assertThat(ressources[0].exists()).as(emplacement).isTrue();
                ScriptUtils.executeSqlScript(base, ressources[0]);
            }
            List<String> lignes = new ArrayList<>();
            colonnes(base, lignes);
            contraintes(base, lignes);
            verifications(base, lignes);
            index(base, lignes);
            Collections.sort(lignes);
            return lignes;
        }
    }

    private static void colonnes(Connection base, List<String> lignes) throws SQLException {
        try (var st = base.prepareStatement("SELECT LOWER(TABLE_NAME), LOWER(COLUMN_NAME), ORDINAL_POSITION, DATA_TYPE, "
                + "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, DATETIME_PRECISION, IS_NULLABLE, COLUMN_DEFAULT, "
                + "IS_IDENTITY, IDENTITY_GENERATION FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC'");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lignes.add("COLONNE|" + rs.getString(1) + "|" + String.format("%03d", rs.getInt(3)) + "|" + rs.getString(2) + "|"
                        + String.join("|", rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12)));
            }
        }
    }

    private static void contraintes(Connection base, List<String> lignes) throws SQLException {
        try (var st = base.prepareStatement("SELECT LOWER(c.TABLE_NAME), c.CONSTRAINT_TYPE, c.CONSTRAINT_NAME, "
                + "(SELECT LISTAGG(LOWER(k.COLUMN_NAME) || ':' || COALESCE(CAST(k.POSITION_IN_UNIQUE_CONSTRAINT AS VARCHAR), ''), ',') "
                + "WITHIN GROUP (ORDER BY k.ORDINAL_POSITION) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k "
                + "WHERE k.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = c.CONSTRAINT_NAME) "
                + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS c WHERE c.TABLE_SCHEMA = 'PUBLIC' AND c.CONSTRAINT_TYPE <> 'CHECK'");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lignes.add("CONTRAINTE|" + rs.getString(1) + "|" + rs.getString(2) + "|" + normaliser(rs.getString(3)) + "|"
                        + rs.getString(4));
            }
        }
    }

    private static void verifications(Connection base, List<String> lignes) throws SQLException {
        try (var st = base.prepareStatement("SELECT LOWER(c.TABLE_NAME), c.CONSTRAINT_NAME, k.CHECK_CLAUSE "
                + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS c JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS k "
                + "ON k.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = c.CONSTRAINT_NAME "
                + "WHERE c.TABLE_SCHEMA = 'PUBLIC' AND c.CONSTRAINT_TYPE = 'CHECK'");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lignes.add("VERIFICATION|" + rs.getString(1) + "|" + normaliser(rs.getString(2)) + "|"
                        + rs.getString(3).replaceAll("\\s+", " "));
            }
        }
    }

    private static void index(Connection base, List<String> lignes) throws SQLException {
        try (var st = base.prepareStatement("SELECT LOWER(i.TABLE_NAME), i.INDEX_NAME, i.INDEX_TYPE_NAME, "
                + "(SELECT LISTAGG(LOWER(x.COLUMN_NAME) || ' ' || COALESCE(x.ORDERING_SPECIFICATION, ''), ',') "
                + "WITHIN GROUP (ORDER BY x.ORDINAL_POSITION) FROM INFORMATION_SCHEMA.INDEX_COLUMNS x "
                + "WHERE x.INDEX_SCHEMA = i.INDEX_SCHEMA AND x.INDEX_NAME = i.INDEX_NAME) "
                + "FROM INFORMATION_SCHEMA.INDEXES i WHERE i.TABLE_SCHEMA = 'PUBLIC'");
             ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lignes.add("INDEX|" + rs.getString(1) + "|" + normaliser(rs.getString(2)) + "|" + rs.getString(3) + "|"
                        + rs.getString(4));
            }
        }
    }

    @Test
    void le_schema_h2_repartit_par_contexte_est_identique_au_releve_de_l_ancien_script_unique() throws Exception {
        List<String> releve = releve();
        if (Boolean.getBoolean("schema.reference.ecrire")) {
            Files.write(Path.of("src/test/resources/" + REFERENCE), releve, StandardCharsets.UTF_8);
        }
        List<String> attendu;
        try (var flux = new ClassPathResource(REFERENCE).getInputStream()) {
            attendu = new String(flux.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        List<String> enPlus = new ArrayList<>(releve);
        enPlus.removeAll(attendu);
        List<String> enMoins = new ArrayList<>(attendu);
        enMoins.removeAll(releve);
        assertThat(enPlus).as("lignes en plus par rapport à la référence").isEmpty();
        assertThat(enMoins).as("lignes manquantes par rapport à la référence").isEmpty();
        assertThat(releve).hasSameSizeAs(attendu);
    }
}
