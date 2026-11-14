package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les dix-sept tables du Dossier médical appartiennent au contexte {@code contexts/dossier-medical} (ADR 0101, DM9 ; {@code resultats_analyses}
 * est gardée à part par {@link TableDesAnalysesTest}). Garde RÉÉCRITE et ACTIVE depuis DM9 (elle listait le schéma parallèle du module gelé,
 * ADR 0072) sur le modèle de {@link TableDesAnalysesTest} : elle scanne TOUT le code de production (plateforme, anciens modules, autres
 * contextes, ressources SQL / JRXML / XML, entités JPA comprises : un {@code @Table(name = "…")} est un lecteur), sauf le contexte Dossier
 * médical. Les lecteurs restants sont une liste blanche NOMMÉE, DATÉE, avec une revue au plus tard le {@link #REVUE} et un BUTOIR PROPRE à
 * chaque entrée ; un lecteur hors liste ou une entrée morte fait échouer le test.
 */
class TableDuDossierMedicalTest {

    private static final LocalDate REVUE = LocalDate.of(2026, 12, 15);
    private static final LocalDate LECTURES_PUBLIEES = LocalDate.of(2027, 3, 31);
    private static final LocalDate RETRAIT_DU_PILOTAGE_LEGACY = LocalDate.of(2027, 6, 30);

    static final Set<String> TABLES = Set.of("abords_vasculaires", "administrations_anemie", "alertes_observance", "allergies_patient",
            "antecedents_medicaux", "bilans_pre_greffe", "decisions_rcp", "demandes_examen", "donneurs_vivants", "dossier_medical_patient",
            "etapes_bilan_pre_greffe", "lignes_demande_examen", "lignes_ordonnance", "observations_biologiques", "ordonnances",
            "prescriptions_medicales", "serologies_patient");

    private record Lecteur(String nature, LocalDate butoir) {
    }

    /** Lecteurs connus hors du contexte Dossier médical (chemin depuis {@code backend/}) : 2026-11-09, DM9 (ADR 0101). */
    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>();

    private static void lecteur(String chemin, String nature, LocalDate butoir) {
        LECTEURS_CONNUS.put(chemin, new Lecteur(nature, butoir));
    }

    static {
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        // HistoricalRecordJdbcAdapter : entrée SOLDÉE par RP3b (2026-11-13, ADR 0172) ; l'import écrit le dossier de base, les antécédents, les
        // sérologies et les abords par RepriseDeLHistoriqueMedical (dossier-medical.v1). Butoir 2027-06-30 supprimé, aucun report.
        // MigrationRollbackJdbcAdapter : entrée SOLDÉE par RP2 (2026-11-13, ADR 0170) ; l'annulation d'un lot lit et supprime ces tables par
        // DonneesMedicalesDeLaReprise (dossier-medical.v1). Butoir supprimé, aucun report.
        String ressources = "platform/src/main/resources/";
        lecteur(ressources + "db/schema.sql", "schéma H2 des tests et du poste de développement (CREATE TABLE IF NOT EXISTS ; DM9 : remplace "
                + "les 17 entités JPA retirées) ; aucune lecture ni écriture de données", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/seed.sql", "jeu d'amorçage (écrit des données médicales ; rejoué HORS profil prod seulement, voir TableDesSeancesTest)",
                RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/demo/renadial-seed.sql", "jeu de démonstration (écrit des données médicales)", RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur(ressources + "db/demo/renadial-purge.sql", "purge de la démonstration (SUPPRIME des données médicales ; jamais sur une base partagée)",
                RETRAIT_DU_PILOTAGE_LEGACY);
        lecteur("contexts/documents/src/main/resources/reports/cahier_dialyse.jrxml", "état imprimé du cahier de dialyse (SE7e, résidu E-R3) : lit dossier_medical_patient "
                + "(hépatites) et prescriptions_medicales (cibles de séance) en SQL Jasper ; échéance : lecture publiée du Dossier médical", LECTURES_PUBLIEES);
        lecteur("contexts/documents/src/main/resources/reports/ordonnance.jrxml", "état imprimé d'une ordonnance (RG-MED-055) : lit ordonnances et lignes_ordonnance en SQL "
                + "Jasper ; échéance : impression par le module ou lecture publiée", LECTURES_PUBLIEES);
        // StatistiquesDesSeancesJdbc (Séance) : entrée SOLDÉE par LEC-PLN-GMAO (c) (2026-11-14, ADR 0180) ; le poids sec cible est lu par
        // PoidsSecsPrescritsPublies (dossier-medical.v1) via le port PoidsSecDesPrescriptions. Butoir 2027-03-31 supprimé, aucun report.
    }

    private static final String ALTERNATIVES = TABLES.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:public\\.)?([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LISTE_DE_TABLES = Pattern.compile(
            "\\bFROM\\s+[^;()\"]{0,160}?,\\s*(?:public\\.)?(?:" + ALTERNATIVES + ")\\b", Pattern.CASE_INSENSITIVE);
    /** Entité JPA déclarée sur une des tables (KB-SEA-98 : une garde qui ne voit pas les accès JPA ne garde rien). */
    private static final Pattern ENTITE_JPA = Pattern.compile(
            "@Table\\s*\\(\\s*name\\s*=\\s*\"(?:public\\.)?(?:" + ALTERNATIVES + ")\"", Pattern.CASE_INSENSITIVE);

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    static boolean mentionneUneTable(Path source) {
        try {
            String texte = Files.readString(source);
            Matcher m = LECTURE_SQL.matcher(texte);
            while (m.find()) {
                if (TABLES.contains(m.group(2).toLowerCase())) return true;
            }
            if (LISTE_DE_TABLES.matcher(texte).find()) return true;
            if (ENTITE_JPA.matcher(texte).find()) return true;
            return NomsDeTableCalcules.contientLeLitteral(texte, TABLES) && NomsDeTableCalcules.construitUneRequete(texte);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> lecteurs(List<Path> sources) {
        Set<String> lecteurs = new TreeSet<>();
        for (Path source : sources) {
            if (mentionneUneTable(source)) lecteurs.add(backend().relativize(source).toString().replace('\\', '/'));
        }
        NomsDeTableCalcules.lecteursIndirects(sources, TABLES).forEach(p -> lecteurs.add(backend().relativize(p).toString().replace('\\', '/')));
        return lecteurs;
    }

    @Test
    void aucun_nouveau_lecteur_sql_des_tables_du_dossier_medical_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>(lecteurs(TableDesAnalysesTest.sourcesDeProduction(backend())));
        nouveaux.removeAll(LECTEURS_CONNUS.keySet());
        assertThat(nouveaux)
                .as("nouveaux lecteurs des tables du Dossier médical (SQL, nom de table calculé ou entité JPA) : passer par dossier-medical.v1 ou les inscrire (nommés, datés, butoir)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Set<String> lecteurs = lecteurs(TableDesAnalysesTest.sourcesDeProduction(backend()));
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            if (!Files.isRegularFile(backend().resolve(chemin)) || !lecteurs.contains(chemin)) morts.add(chemin);
        }
        assertThat(morts).as("entrées qui ne lisent plus les tables du Dossier médical : les retirer").isEmpty();
    }

    @Test
    void la_plateforme_et_la_planification_n_ont_plus_ni_entite_jpa_ni_lecteur_sql_du_dossier_medical() throws IOException {
        String plateforme = "platform/src/main/java/com/hemodialyse/backend/";
        List<String> disparus = List.of(
                plateforme + "infrastructure/persistence/entity/SerologieJpaEntity.java",
                plateforme + "infrastructure/persistence/entity/DossierMedicalPatientJpaEntity.java",
                plateforme + "infrastructure/persistence/adapter/SerologieRepositoryAdapter.java",
                plateforme + "infrastructure/persistence/adapter/BilanPreGreffeRepositoryAdapter.java",
                plateforme + "infrastructure/acl/seance/SaisiesDeSeanceFourniesLegacy.java",
                "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacydossiermedical/RisquesInfectieuxJdbc.java",
                "bc-medical/pom.xml");
        assertThat(disparus).as("retirés par DM9 (ADR 0101)").noneMatch(c -> Files.isRegularFile(backend().resolve(c)));
        Set<String> lecteurs = lecteurs(TableDesAnalysesTest.sourcesDeProduction(backend()));
        assertThat(lecteurs).as("les lecteurs de la plateforme et de la Planification passent par dossier-medical.v1").doesNotContain(
                plateforme + "application/direction/DirectionIndicatorsQueryService.java",
                plateforme + "application/query/PatientStatsQueryService.java",
                "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/ContraintesDesPatientsJdbc.java");
    }

    @Test
    void chaque_entree_a_un_butoir_propre_et_la_liste_est_a_revoir_au_plus_tard_le_2026_12_15() {
        assertThat(LECTEURS_CONNUS).hasSize(6);
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).isNotBlank();
            assertThat(lecteur.butoir()).as("butoir de " + chemin).isAfter(REVUE);
        });
        // Plus aucun lecteur Java dans un autre contexte (la Séance est soldée, ADR 0180) ; seuls restent les deux états Jasper.
        // DOC2 (ADR 0162) : les deux états Jasper (cahier, ordonnance) ont changé d'emplacement (module Documents) sans changer de nature
        assertThat(LECTEURS_CONNUS.keySet().stream().filter(c -> c.startsWith("contexts/")).toList())
                .containsExactly("contexts/documents/src/main/resources/reports/cahier_dialyse.jrxml",
                        "contexts/documents/src/main/resources/reports/ordonnance.jrxml");
    }

    /** LEC-PLN-GMAO (c), ADR 0180 : la Séance ne lit plus les prescriptions ; ni entrée, ni lecteur. */
    @Test
    void la_seance_ne_lit_plus_les_prescriptions_medicales() throws IOException {
        String adapter = "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/StatistiquesDesSeancesJdbc.java";
        assertThat(LECTEURS_CONNUS).doesNotContainKey(adapter);
        assertThat(lecteurs(TableDesAnalysesTest.sourcesDeProduction(backend()))).noneMatch(c -> c.startsWith("contexts/seance/"));
    }

    /**
     * RP2 (2026-11-13, ADR 0170) : l'annulation d'un lot de reprise passe par {@code DonneesMedicalesDeLaReprise} ; son entrée et son butoir
     * 2027-06-30 sont SOLDÉS (supprimés, jamais reportés). Verrou : elle ne figure plus dans la liste blanche ET ne lit plus ces tables.
     */
    @Test
    void l_annulation_d_un_lot_de_reprise_ne_touche_plus_les_tables_du_dossier_medical() throws IOException {
        String adapter = "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java";
        assertThat(LECTEURS_CONNUS).doesNotContainKey(adapter);
        assertThat(lecteurs(TableDesAnalysesTest.sourcesDeProduction(backend()))).doesNotContain(adapter);
    }

    /**
     * RP3b (2026-11-13, ADR 0172) : l'import de l'historique d'un lot de reprise passe par {@code RepriseDeLHistoriqueMedical} ; son entrée et
     * son butoir 2027-06-30 sont SOLDÉS (supprimés, jamais reportés). Verrou : elle ne figure plus dans la liste blanche ET ne lit ni
     * n'écrit plus ces tables (SQL ou nom de table calculé).
     */
    @Test
    void l_import_de_l_historique_d_un_lot_de_reprise_n_ecrit_plus_les_tables_du_dossier_medical() throws IOException {
        String adapter = "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java";
        assertThat(LECTEURS_CONNUS).doesNotContainKey(adapter);
        assertThat(lecteurs(TableDesAnalysesTest.sourcesDeProduction(backend()))).doesNotContain(adapter);
    }

    @Test
    void la_garde_detecte_une_entite_jpa_une_lecture_directe_et_une_creation_de_table() throws IOException {
        Path bac = Files.createTempDirectory("garde-dossier-medical-dm9");
        Path entite = bac.resolve("Entite.java");
        Files.writeString(entite, "@Entity\n@Table(name = \"serologies_patient\", indexes = @Index(name = \"i\", columnList = \"patient_id\"))\nclass Entite { }");
        Path lecture = bac.resolve("Lecture.java");
        Files.writeString(lecture, "class Lecture { String s = \"SELECT patient_id FROM abords_vasculaires WHERE center_id = ?\"; }");
        Path creation = bac.resolve("schema.sql");
        Files.writeString(creation, "CREATE TABLE IF NOT EXISTS dossier_medical_patient (id UUID PRIMARY KEY);");
        Path liste = bac.resolve("Liste.java");
        Files.writeString(liste, "class Liste { String s = \"SELECT 1 FROM patients p, prescriptions_medicales m WHERE m.patient_id = p.id\"; }");
        Path propre = bac.resolve("Propre.java");
        Files.writeString(propre, "class Propre { String s = \"SELECT id FROM seances\"; String t = \"SELECT id FROM serologies\"; }");

        assertThat(mentionneUneTable(entite)).as("entité JPA").isTrue();
        assertThat(mentionneUneTable(lecture)).as("lecture SQL").isTrue();
        assertThat(mentionneUneTable(creation)).as("CREATE TABLE IF NOT EXISTS").isTrue();
        assertThat(mentionneUneTable(liste)).as("liste de tables").isTrue();
        assertThat(mentionneUneTable(propre)).as("autres tables (dont l'ancien schéma parallèle du module gelé)").isFalse();
    }

    /** La liste des tables couvre bien ce que le contexte lit lui-même en SQL (elle ne peut pas dériver des tables réellement possédées). */
    @Test
    void la_liste_des_tables_couvre_les_tables_ecrites_par_le_contexte_dossier_medical() throws IOException {
        Path contexte = backend().resolve("contexts/dossier-medical/src/main/java");
        Set<String> ecrites = new TreeSet<>();
        try (var sources = Files.walk(contexte)) {
            for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = Pattern.compile("\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(Files.readString(source));
                while (m.find()) ecrites.add(m.group(2).toLowerCase());
            }
        }
        ecrites.remove("resultats_analyses"); // gardée par TableDesAnalysesTest
        // S-DDL2 (2026-11-14, ADR 0185) : l'exception « app_settings » est SOLDÉE ; les numéros d'ordonnance viennent de la Comptabilité.
        assertThat(TABLES).as("toute table écrite en SQL par le contexte dossier-medical doit être gardée ici").containsAll(ecrites);
    }
}
