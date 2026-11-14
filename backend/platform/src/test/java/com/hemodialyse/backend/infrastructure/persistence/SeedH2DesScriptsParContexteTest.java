package com.hemodialyse.backend.infrastructure.persistence;

import com.hemodialyse.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEED2 (ADR 0188) : le jeu d'amorçage H2 est réparti en scripts par contexte propriétaire (liste
 * {@code spring.sql.init.data-locations} de {@code application.yml}) ; le contenu de TOUTES les tables après amorçage doit rester
 * STRICTEMENT identique à celui de l'ancien {@code db/seed.sql} unique.
 *
 * <p>Référence : {@code src/test/resources/db/seed-reference-avant-decoupe.sql}, copie octet pour octet de l'ancien script unique, prise
 * AVANT la découpe (une empreinte figée dans un fichier ne tiendrait pas : le jeu dépend de {@code CURRENT_DATE}). Deux contextes Spring
 * complets (Hibernate puis scripts) sont démarrés dans la même minute, sur deux bases H2 distinctes : l'un avec la configuration de
 * production ({@code application.yml}), l'autre avec la seule référence ({@code SEED_DATA_LOCATIONS}). Empreinte par table : comptage,
 * somme de contrôle SHA-256 des lignes triées, et colonnes (les tables créées par le script lui-même sont donc comparées aussi).
 *
 * <p>Valeurs normalisées (non déterministes par construction, jamais des données de démonstration) : instants à moins de 30 minutes de
 * « maintenant » ({@code CURRENT_TIMESTAMP} du script), {@code password_hash} (posé par SeedPasswordInitializer, sel aléatoire),
 * identifiants tirés par {@code RANDOM_UUID()} (voir {@link #COLONNES_ALEATOIRES}).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:seed_par_contexte_nouveau;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.url=jdbc:h2:mem:seed_par_contexte_nouveau;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class SeedH2DesScriptsParContexteTest {

    private static final String BASE_REFERENCE = "jdbc:h2:mem:seed_par_contexte_reference;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    /** Colonnes dont la valeur n'est pas déterministe, {@code table.colonne} (ou {@code *.colonne}). */
    private static final Set<String> COLONNES_ALEATOIRES = Set.of(
            "*.password_hash",
            // INSERT ... SELECT RANDOM_UUID() du script (attestations et prises en charge générées pour les patients de démonstration)
            "attestation_droit.id", "prise_en_charge.id",
            // provisionnés au démarrage (ModeleDocumentProvisioner : identifiants tirés au hasard ; DevLicenseBootstrapInitializer : clé et dates
            // éphémères) : ce ne sont pas des données du script, seul le nombre de lignes est comparé
            "modele_document.id", "license.*");

    @Autowired
    private DataSource nouvelleBase;

    private static boolean proche(Instant instant, Instant maintenant) {
        return Duration.between(instant, maintenant).abs().compareTo(Duration.ofMinutes(30)) < 0;
    }

    private static String valeur(String table, String colonne, Object v) {
        if (COLONNES_ALEATOIRES.contains("*." + colonne) || COLONNES_ALEATOIRES.contains(table + "." + colonne)
                || COLONNES_ALEATOIRES.contains(table + ".*")) {
            return "*";
        }
        if (v == null) {
            return "~";
        }
        if (v instanceof byte[] octets) {
            return HexFormat.of().formatHex(octets);
        }
        if (v instanceof Timestamp t && proche(t.toInstant(), Instant.now())) {
            return "<maintenant>";
        }
        if (v instanceof OffsetDateTime o && proche(o.toInstant(), Instant.now())) {
            return "<maintenant>";
        }
        if (v instanceof java.time.LocalDateTime l && (proche(l.toInstant(ZoneOffset.UTC), Instant.now())
                || proche(l.atZone(java.time.ZoneId.systemDefault()).toInstant(), Instant.now()))) {
            return "<maintenant>";
        }
        return String.valueOf(v);
    }

    /** table -> lignes triées ; les colonnes ({@code _colonnes}) et la liste des tables sont des pseudo-tables comparées comme les autres. */
    static Map<String, List<String>> releve(Connection c) throws SQLException {
        Map<String, List<String>> releve = new TreeMap<>();
        List<String> tables = new ArrayList<>();
        try (var st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' "
                     + "AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        List<String> colonnes = new ArrayList<>();
        try (var st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT LOWER(TABLE_NAME), LOWER(COLUMN_NAME), ORDINAL_POSITION, DATA_TYPE, "
                     + "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE "
                     + "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            while (rs.next()) {
                colonnes.add(rs.getString(1) + "|" + rs.getInt(3) + "|" + rs.getString(2) + "|" + rs.getString(4) + "|" + rs.getString(5)
                        + "|" + rs.getString(6) + "|" + rs.getString(7) + "|" + rs.getString(8));
            }
        }
        Collections.sort(colonnes);
        releve.put("_colonnes", colonnes);
        for (String table : tables) {
            List<String> lignes = new ArrayList<>();
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM \"" + table + "\"")) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    StringBuilder ligne = new StringBuilder();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        ligne.append(valeur(table.toLowerCase(), md.getColumnName(i).toLowerCase(), rs.getObject(i))).append('|');
                    }
                    lignes.add(ligne.toString());
                }
            }
            Collections.sort(lignes);
            releve.put(table.toLowerCase(), lignes);
        }
        return releve;
    }

    private static String abreger(String ligne) {
        return ligne.length() > 160 ? ligne.substring(0, 160) + "…" : ligne;
    }

    private static String sommeDeControle(List<String> lignes) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        for (String ligne : lignes) {
            sha.update(ligne.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '\n');
        }
        return HexFormat.of().formatHex(sha.digest()).substring(0, 16);
    }

    @Test
    void le_jeu_d_amorcage_reparti_par_contexte_est_identique_a_celui_de_l_ancien_script_unique() throws Exception {
        Map<String, List<String>> nouveau;
        try (Connection c = nouvelleBase.getConnection()) {
            nouveau = releve(c);
        }
        Map<String, List<String>> reference;
        try (ConfigurableApplicationContext ref = new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE).profiles("test")
                .run("--spring.datasource.url=" + BASE_REFERENCE, "--jakarta.persistence.jdbc.url=" + BASE_REFERENCE,
                        "--SEED_DATA_LOCATIONS=classpath:db/seed-reference-avant-decoupe.sql,classpath:db/h2/comptabilite-seed.sql");
             Connection c = ref.getBean(DataSource.class).getConnection()) {
            reference = releve(c);
        }
        Set<String> tablesVides = new TreeSet<>();
        Set<String> ecarts = new TreeSet<>();
        Set<String> toutes = new TreeSet<>(reference.keySet());
        toutes.addAll(nouveau.keySet());
        int garnies = 0;
        for (String table : toutes) {
            List<String> attendu = reference.getOrDefault(table, List.of("<table absente>"));
            List<String> obtenu = nouveau.getOrDefault(table, List.of("<table absente>"));
            if (!table.startsWith("_") && attendu.isEmpty()) {
                tablesVides.add(table);
            }
            if (!table.startsWith("_") && !attendu.isEmpty()) {
                garnies++;
            }
            if (!attendu.equals(obtenu)) {
                List<String> enPlus = new ArrayList<>(obtenu);
                enPlus.removeAll(attendu);
                List<String> enMoins = new ArrayList<>(attendu);
                enMoins.removeAll(obtenu);
                ecarts.add(table + " : référence " + attendu.size() + " lignes (" + sommeDeControle(attendu) + "), obtenu " + obtenu.size()
                        + " (" + sommeDeControle(obtenu) + ") ; en plus " + enPlus.stream().limit(2).map(l -> abreger(l)).toList()
                        + " ; en moins " + enMoins.stream().limit(2).map(l -> abreger(l)).toList());
            }
        }
        assertThat(garnies).as("le jeu d'amorçage garnit des tables (sinon la comparaison ne prouve rien)").isGreaterThan(30);
        assertThat(ecarts).as("tables dont le contenu diffère du relevé de l'ancien script unique").isEmpty();
    }
}
