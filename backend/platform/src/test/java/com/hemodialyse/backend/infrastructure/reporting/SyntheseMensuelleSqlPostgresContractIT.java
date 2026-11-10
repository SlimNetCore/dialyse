package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat PostgreSQL 16 RÉEL des deux requêtes de la synthèse mensuelle qui lisent {@code factures} et {@code facture_lignes}
 * depuis {@code platform} (ADR 0035, amendement 2026-10-21) : le gabarit dynamique ({@link FacturationSyntheseMensuelleReportService})
 * et le gabarit statique ({@code reports/synthese_mensuelle_facturation.jrxml}). Les mêmes données sont rejouées sur H2 et sur
 * PostgreSQL : part de TTC par ligne ({@code line_ht × total_ttc / total_ht}), division par zéro, arrondi, types NUMERIC.
 * Base JETABLE uniquement : {@code -Dfacturation.pg16.url=jdbc:postgresql://…} (schéma Flyway V1..Vn déjà appliqué).
 */
@EnabledIfSystemProperty(named = "facturation.pg16.url", matches = ".+")
class SyntheseMensuelleSqlPostgresContractIT {

    private static final UUID CENTRE = UUID.fromString("99996700-0000-0000-0000-0000000000f5");
    private static final UUID CAISSE_CNAS = UUID.fromString("99996700-0000-0000-0000-0000000000c1");
    private static final UUID CAISSE_MUTUELLE = UUID.fromString("99996700-0000-0000-0000-0000000000c2");
    private static final UUID AGENCE_CNAS = UUID.fromString("99996700-0000-0000-0000-0000000000a1");
    private static final UUID AGENCE_MUTUELLE = UUID.fromString("99996700-0000-0000-0000-0000000000a2");
    private static final Pattern PARAMETRE = Pattern.compile("\\$P\\{(CENTER_ID|PERIOD_START|PERIOD_END)}");

    @Test
    void les_deux_requetes_donnent_les_memes_montants_sur_h2_et_sur_postgresql_16() throws Exception {
        List<String> requetes = List.of(requeteDuGabaritDynamique(), requeteDuGabaritStatique());
        try (Connection h2 = DriverManager.getConnection("jdbc:h2:mem:synthese_sql;DB_CLOSE_DELAY=-1");
             Connection pg = DriverManager.getConnection(System.getProperty("facturation.pg16.url"),
                     System.getProperty("facturation.pg16.user", "facturation_test"),
                     System.getProperty("facturation.pg16.password", ""))) {
            creerLeSchemaH2(h2);
            preparer(h2);
            preparer(pg);
            try {
                for (String requete : requetes) {
                    List<Map<String, BigDecimal>> surH2 = lire(h2, requete);
                    List<Map<String, BigDecimal>> surPg = lire(pg, requete);
                    assertThat(surPg).hasSameSizeAs(surH2).isNotEmpty();
                    for (int i = 0; i < surH2.size(); i++) {
                        for (var colonne : surH2.get(i).entrySet()) {
                            BigDecimal attendu = colonne.getValue();
                            BigDecimal obtenu = surPg.get(i).get(colonne.getKey());
                            // Ce que le gabarit imprime : deux décimales
                            assertThat(obtenu.setScale(2, RoundingMode.HALF_UP))
                                    .as("ligne %d colonne %s", i, colonne.getKey())
                                    .isEqualByComparingTo(attendu.setScale(2, RoundingMode.HALF_UP));
                        }
                    }
                }
            } finally {
                nettoyer(pg);
            }
        }
    }

    @Test
    void la_division_par_zero_d_un_total_ht_nul_est_protegee_a_l_identique_sur_les_deux_bases() throws Exception {
        try (Connection h2 = DriverManager.getConnection("jdbc:h2:mem:synthese_sql_zero;DB_CLOSE_DELAY=-1");
             Connection pg = DriverManager.getConnection(System.getProperty("facturation.pg16.url"),
                     System.getProperty("facturation.pg16.user", "facturation_test"),
                     System.getProperty("facturation.pg16.password", ""))) {
            creerLeSchemaH2(h2);
            // Sans la garde « CASE WHEN total_ht = 0 », les deux bases échouent : la garde des requêtes est nécessaire.
            for (Connection base : List.of(h2, pg)) {
                assertThatThrownBy(() -> {
                    try (var s = base.createStatement()) {
                        s.executeQuery("SELECT CAST(10.00 AS NUMERIC(14,2)) * CAST(12.00 AS NUMERIC(14,2)) / CAST(0.00 AS NUMERIC(14,2))");
                    }
                }).isInstanceOf(SQLException.class);
            }
            for (String requete : List.of(requeteDuGabaritDynamique(), requeteDuGabaritStatique())) {
                for (Connection base : List.of(h2, pg)) {
                    try {
                        preparerLaFactureADeTotalNul(base);
                        List<Map<String, BigDecimal>> lignes = lire(base, requete);
                        assertThat(lignes).hasSize(1);
                        assertThat(lignes.get(0).get("TOTAL_HT")).isEqualByComparingTo("50.00");
                        assertThat(lignes.get(0).get("TOTAL_TTC")).isEqualByComparingTo("0");
                    } finally {
                        nettoyer(base);
                    }
                }
            }
        }
    }

    @Test
    void les_types_numeric_de_la_part_de_ttc_sont_lus_comme_decimaux_sur_les_deux_bases() throws Exception {
        try (Connection h2 = DriverManager.getConnection("jdbc:h2:mem:synthese_sql_types;DB_CLOSE_DELAY=-1");
             Connection pg = DriverManager.getConnection(System.getProperty("facturation.pg16.url"),
                     System.getProperty("facturation.pg16.user", "facturation_test"),
                     System.getProperty("facturation.pg16.password", ""))) {
            creerLeSchemaH2(h2);
            for (Connection base : List.of(h2, pg)) {
                try {
                    preparer(base);
                    try (PreparedStatement ps = base.prepareStatement(avecDesPoints(requeteDuGabaritStatique()))) {
                        lier(ps);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertThat(rs.getMetaData().getColumnTypeName(3).toUpperCase())
                                    .as("CNAS_TTC").isIn("NUMERIC", "DECIMAL");
                        }
                    }
                } finally {
                    nettoyer(base);
                }
            }
        }
    }

    // ------------------------------------------------------------------ requêtes sous test

    private static String requeteDuGabaritStatique() throws Exception {
        try (var flux = SyntheseMensuelleSqlPostgresContractIT.class.getResourceAsStream("/reports/synthese_mensuelle_facturation.jrxml")) {
            return requeteDuGabarit(new String(flux.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String requeteDuGabaritDynamique() {
        String[] capture = new String[1];
        JasperReportService capteur = new JasperReportService(null) {
            @Override
            public byte[] generateReport(String jrxmlPath, Map<String, Object> params, String format) throws java.io.IOException {
                capture[0] = Files.readString(Path.of(jrxmlPath));
                return new byte[0];
            }
        };
        SyntheseMensuelle synthese = new SyntheseMensuelle(CENTRE, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                List.of(new SyntheseMensuelle.Caisse("CNAS", "CNAS"), new SyntheseMensuelle.Caisse("MUT", "Mutuelle")),
                List.of(), List.of(), 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        // RF3b (ADR 0127) : les codes de caisse viennent de l'annuaire des payeurs ; ici les deux agences du jeu d'essai (mêmes codes que les tables).
        new FacturationSyntheseMensuelleReportService(capteur, centre -> Map.of(AGENCE_CNAS, "CNAS", AGENCE_MUTUELLE, "mutuelle")).imprimer(synthese, FormatDImpression.PDF);
        return requeteDuGabarit(capture[0]);
    }

    private static String requeteDuGabarit(String jrxml) {
        Matcher m = Pattern.compile("<queryString>\\s*<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL).matcher(jrxml);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    /** Comme Jasper : chaque {@code $P{…}} devient un paramètre lié, dans l'ordre d'apparition. */
    private static String avecDesPoints(String requete) {
        return PARAMETRE.matcher(requete).replaceAll("?");
    }

    private static void lier(PreparedStatement ps) throws SQLException {
        // Ordre des paramètres dans les deux requêtes : centre, début, fin
        ps.setString(1, CENTRE.toString());
        ps.setString(2, "2026-02-01");
        ps.setString(3, "2026-02-28");
    }

    private static List<Map<String, BigDecimal>> lire(Connection base, String requete) throws SQLException {
        try (PreparedStatement ps = base.prepareStatement(avecDesPoints(requete))) {
            lier(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, BigDecimal>> lignes = new ArrayList<>();
                int colonnes = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, BigDecimal> ligne = new java.util.LinkedHashMap<>();
                    ligne.put("FORFAIT", BigDecimal.valueOf(rs.getString(1).hashCode()));
                    for (int c = 2; c <= colonnes; c++) {
                        ligne.put(rs.getMetaData().getColumnLabel(c).toUpperCase(), rs.getBigDecimal(c));
                    }
                    lignes.add(ligne);
                }
                return lignes;
            }
        }
    }

    // ------------------------------------------------------------------ données

    private static void creerLeSchemaH2(Connection h2) throws SQLException {
        try (var s = h2.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(255), nom VARCHAR(255), type_caisse VARCHAR(255))");
            s.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, caisse_id UUID, center_id UUID NOT NULL, code VARCHAR(255), nom VARCHAR(255))");
            s.execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                    + "numero_facture VARCHAR(120) NOT NULL, agence_id_snapshot UUID, period_start DATE NOT NULL, period_end DATE NOT NULL, "
                    + "date_facturation DATE NOT NULL, tva_rate DECIMAL(5,2) NOT NULL, total_ht DECIMAL(14,2) NOT NULL, "
                    + "total_tva DECIMAL(14,2) NOT NULL, total_ttc DECIMAL(14,2) NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS facture_lignes (id UUID PRIMARY KEY, facture_id UUID NOT NULL, center_id UUID NOT NULL, "
                    + "forfait_label VARCHAR(255) NOT NULL, unit_price_ht DECIMAL(14,2) NOT NULL, seance_count INTEGER NOT NULL, line_ht DECIMAL(14,2) NOT NULL)");
        }
    }

    private static void preparer(Connection base) throws SQLException {
        nettoyer(base);
        base.setAutoCommit(true);
        executer(base, "INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", CAISSE_CNAS, CENTRE, "CNAS", "CNAS");
        executer(base, "INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", CAISSE_MUTUELLE, CENTRE, "mutuelle", "Mutuelle");
        executer(base, "INSERT INTO agence (id, caisse_id, center_id, code, nom) VALUES (?, ?, ?, ?, ?)", AGENCE_CNAS, CAISSE_CNAS, CENTRE, "A1", "A1");
        executer(base, "INSERT INTO agence (id, caisse_id, center_id, code, nom) VALUES (?, ?, ?, ?, ?)", AGENCE_MUTUELLE, CAISSE_MUTUELLE, CENTRE, "A2", "A2");
        // Totaux enregistrés différents de la somme des lignes (1000.35 pour 1000.00) : part de TTC non entière
        facture(base, "F-1", "2026-02-01", AGENCE_CNAS, "1000.00", "190.00", "1190.00", "Forfait HD", "900.45", "Forfait Transport", "99.90");
        facture(base, "F-2", "2026-02-15", AGENCE_MUTUELLE, "7.00", "1.33", "8.33", "Forfait HD", "3.33", "Forfait HD", "3.67");
        // Division à résultat périodique : 1.00 × 3.57 / 3.00 et 3 lignes de 1.00
        facture(base, "F-3", "2026-02-28", null, "3.00", "0.57", "3.57", "Forfait HD", "1.00", "Forfait HD", "1.00");
        // Total HT nul avec une ligne positive : la garde doit donner 0 (pas d'erreur)
        facture(base, "F-4", "2026-02-10", AGENCE_CNAS, "0.00", "0.00", "0.00", "Forfait Gratuit", "50.00", "Forfait Gratuit", "0.00");
        // Hors période : ignorée
        facture(base, "F-5", "2026-03-01", AGENCE_CNAS, "100.00", "19.00", "119.00", "Forfait HD", "100.00", "Forfait HD", "0.00");
    }

    private static void preparerLaFactureADeTotalNul(Connection base) throws SQLException {
        nettoyer(base);
        base.setAutoCommit(true);
        facture(base, "F-0", "2026-02-10", null, "0.00", "0.00", "0.00", "Forfait Gratuit", "50.00", "Forfait Gratuit", "0.00");
    }

    private static void facture(Connection base, String numero, String date, UUID agence, String ht, String tva, String ttc,
                                String libelle1, String ligne1, String libelle2, String ligne2) throws SQLException {
        UUID id = UUID.nameUUIDFromBytes(("synthese-sql:" + numero).getBytes());
        executer(base, "INSERT INTO factures (id, center_id, patient_id, numero_facture, agence_id_snapshot, period_start, period_end, "
                        + "date_facturation, tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, CENTRE, UUID.nameUUIDFromBytes("patient".getBytes()), numero, agence, java.sql.Date.valueOf(date),
                java.sql.Date.valueOf(date), java.sql.Date.valueOf(date), new BigDecimal("19.00"), new BigDecimal(ht),
                new BigDecimal(tva), new BigDecimal(ttc));
        String[][] lignes = {{libelle1, ligne1}, {libelle2, ligne2}};
        for (int i = 0; i < lignes.length; i++) {
            executer(base, "INSERT INTO facture_lignes (id, facture_id, center_id, forfait_label, unit_price_ht, seance_count, line_ht) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.nameUUIDFromBytes((numero + ":l" + i).getBytes()), id, CENTRE, lignes[i][0], new BigDecimal(lignes[i][1]), 1,
                    new BigDecimal(lignes[i][1]));
        }
    }

    private static void nettoyer(Connection base) throws SQLException {
        for (String table : new String[]{"facture_lignes", "factures", "agence", "caisse_assurance"}) {
            executer(base, "DELETE FROM " + table + " WHERE center_id = ?", CENTRE);
        }
    }

    private static void executer(Connection base, String sql, Object... valeurs) throws SQLException {
        try (PreparedStatement ps = base.prepareStatement(sql)) {
            for (int i = 0; i < valeurs.length; i++) {
                ps.setObject(i + 1, valeurs[i]);
            }
            ps.executeUpdate();
        }
    }
}
