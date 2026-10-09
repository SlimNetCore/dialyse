package com.hemodialyse.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La migration Flyway {@code V33__plan_comptable_payeurs_modeles_piece.sql} (production, PostgreSQL) et le schéma de
 * développement (H2 : {@code db/schema.sql} et entités JPA) doivent décrire les mêmes tables et les mêmes colonnes :
 * sinon le développement local ne reflète plus la production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PlanComptableMigrationTest {

    private static final String MIGRATION = "db/migration/V33__plan_comptable_payeurs_modeles_piece.sql";
    private static final Pattern TABLE = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\((.*?)\\)\\s*;",
            Pattern.DOTALL);
    private static final List<String> MOTS_SQL = List.of("UUID", "NOT", "NULL", "PRIMARY", "KEY", "VARCHAR",
            "BOOLEAN", "INTEGER", "CONSTRAINT", "UNIQUE");

    @Autowired
    private JdbcTemplate jdbc;

    private static String script() throws Exception {
        return new String(new ClassPathResource(MIGRATION).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Colonnes déclarées dans un CREATE TABLE : ses identifiants, hors mots du SQL et noms de contraintes. Lecture
     * indépendante de la mise en page du script (un outil peut le réaligner).
     */
    private static List<String> colonnesDeclarees(String corps) {
        List<String> colonnes = new ArrayList<>();
        Matcher mots = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(corps);
        while (mots.find()) {
            String mot = mots.group().toUpperCase(Locale.ROOT);
            if (!MOTS_SQL.contains(mot) && !mot.startsWith("UQ_") && !mot.startsWith("PK_") && !colonnes.contains(mot)) {
                colonnes.add(mot);
            }
        }
        return colonnes.stream().sorted().toList();
    }

    private List<String> colonnesH2(String table) {
        return jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = ?",
                String.class, table.toUpperCase(Locale.ROOT)).stream().map(c -> c.toUpperCase(Locale.ROOT)).sorted().toList();
    }

    @Test
    void every_table_of_the_migration_exists_in_the_development_schema_with_the_same_columns() throws Exception {
        Matcher tables = TABLE.matcher(script());
        int nombre = 0;
        while (tables.find()) {
            nombre++;
            assertEquals(colonnesDeclarees(tables.group(2)), colonnesH2(tables.group(1)), tables.group(1));
        }
        assertEquals(4, nombre, "comptes_comptables, comptes_payeurs, modeles_piece, modeles_piece_lignes");
    }

    @Test
    void the_entry_column_and_the_indexes_of_the_migration_exist_in_the_development_schema() {
        assertTrue(colonnesH2("ecritures_comptables").contains("MODELE_ID"));
        List<String> index = jdbc.queryForList("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES", String.class)
                .stream().map(i -> i.toUpperCase(Locale.ROOT)).toList();
        assertTrue(index.contains("IDX_LIGNES_ECRITURE_COMPTE"));
        assertTrue(index.contains("IDX_MODELES_PIECE_LIGNES_COMPTE"));
    }

    @Test
    void the_legacy_client_account_columns_are_no_longer_required() {
        // les comptes clients par type de payeur ont laissé place au compte porté par chaque payeur : le schéma de
        // développement ne les crée plus, et la migration les rend facultatifs en production
        List<String> colonnes = colonnesH2("mapping_comptable");
        assertTrue(colonnes.contains("COMPTE_CLIENT_PATIENT"));
        assertTrue(colonnes.contains("COMPTE_CLIENT_AUTRE"));
        assertTrue(colonnes.stream().noneMatch(c -> c.equals("COMPTE_CLIENT_CNAS") || c.equals("COMPTE_CLIENT_CASNOS")
                || c.equals("COMPTE_CLIENT_MUTUELLE")));
    }
}
