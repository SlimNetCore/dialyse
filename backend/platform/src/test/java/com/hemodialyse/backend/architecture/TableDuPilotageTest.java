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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD1 et PD4 (ADR 0102), PD2 (ADR 0103) : garde des tables que POSSÈDE le Pilotage direction, {@code direction_snapshot} (instantanés
 * mensuels figés, PD2, passée dans le contexte au lot B) et {@code direction_alert_history} (historique des alertes, PD3). Remplace
 * {@code LectureSeuleDuPilotageTest}, qui ne protégeait que les quatre tables du module parallèle gelé (retiré avec le lot A).
 * <p>
 * Liste blanche NOMINATIVE des lecteurs et écrivains actuels : les deux propriétaires, adaptateurs JDBC du contexte
 * ({@code direction_snapshot} depuis le lot B, {@code direction_alert_history} depuis le lot C, PD3, ADR 0104) ; chaque entrée porte sa
 * date et son butoir. Une entrée dont le fichier ne lit plus la table est « morte » : elle est à retirer. Les tables du tableau de bord ne sont PAS ici : le module ne possède pas de table, il lit celles des autres
 * contextes par leurs langages publiés (ou, pour {@code patients} et {@code centers}, par les entrées nommées de leurs gardes).
 * <p>
 * PD5 (ADR 0105, clôture) puis PL10b2 + PL10c (ADR 0152) : règle PERMANENTE (sans date de revue) sur {@code application/direction} de la
 * plateforme, qui ne garde que les comptes de l'Organisation / IAM.
 */
class TableDuPilotageTest {

    private static final Set<String> TABLES_DU_PILOTAGE = Set.of("direction_snapshot", "direction_alert_history");

    private record Lecteur(String nature, LocalDate butoir) {
    }

    /**
     * PROPRIÉTAIRE de {@code direction_snapshot} depuis le lot B (PD2, ADR 0103) : l'adaptateur JDBC du contexte, seul lecteur et seul
     * écrivain des instantanés mensuels (aucun butoir : c'est le chez-soi de la table).
     */
    private static final String PROPRIETAIRE_DES_INSTANTANES =
            "contexts/pilotage-direction/src/main/java/com/hemodialyse/backend/contexts/pilotagedirection/infrastructure/persistence/"
                    + "InstantanesMensuelsJdbc.java";

    /**
     * PROPRIÉTAIRE de {@code direction_alert_history} depuis le lot C (PD3, ADR 0104) : l'adaptateur JDBC du contexte, seul lecteur et seul
     * écrivain de l'historique des alertes (aucun butoir : c'est le chez-soi de la table).
     */
    private static final String PROPRIETAIRE_DE_L_HISTORIQUE =
            "contexts/pilotage-direction/src/main/java/com/hemodialyse/backend/contexts/pilotagedirection/infrastructure/persistence/"
                    + "HistoriqueDesAlertesJdbc.java";

    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>(Map.of(
            PROPRIETAIRE_DES_INSTANTANES,
            new Lecteur("2026-11-09 : propriétaire de direction_snapshot (PD2, ADR 0103) : liste, relit et fige les instantanés mensuels ; "
                    + "SQL repris mot pour mot du service retiré", LocalDate.of(2099, 12, 31)),
            PROPRIETAIRE_DE_L_HISTORIQUE,
            new Lecteur("2026-11-09 : propriétaire de direction_alert_history (PD3, ADR 0104) : ouvre, résout et relit l'historique des "
                    + "alertes tant que la direction est connectée ; SQL repris mot pour mot du service retiré", LocalDate.of(2099, 12, 31))));

    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path p) {
        return chemin(backend().relativize(p));
    }

    static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new java.util.ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java)) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java")).filter(p -> !chemin(p).contains("/entity/")).forEach(trouves::add);
                }
            }
        }
        return trouves;
    }

    static boolean lit(Path source, Set<String> tables) {
        try {
            Matcher m = LECTURE_SQL.matcher(Files.readString(source));
            while (m.find()) {
                if (tables.contains(m.group(2).toLowerCase())) return true;
            }
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_nouveau_lecteur_sql_des_tables_du_pilotage_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (lit(source, TABLES_DU_PILOTAGE) && !LECTEURS_CONNUS.containsKey(relatif(source))) nouveaux.add(relatif(source));
        }
        assertThat(nouveaux)
                .as("nouveaux lecteurs SQL de direction_snapshot / direction_alert_history : passer par le Pilotage direction ou les inscrire "
                        + "(nommés, datés, butoir)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() {
        Set<String> morts = new TreeSet<>();
        for (String chemin : LECTEURS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !lit(source, TABLES_DU_PILOTAGE)) morts.add(chemin);
        }
        assertThat(morts).as("entrées qui ne lisent plus les tables du Pilotage : les retirer (avancement de PD2 / PD3)").isEmpty();
    }

    @Test
    void direction_snapshot_n_a_plus_que_son_proprietaire_dans_le_contexte_et_aucun_lecteur_de_la_plateforme() throws IOException {
        Set<String> lecteurs = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (lit(source, Set.of("direction_snapshot"))) lecteurs.add(relatif(source));
        }
        assertThat(lecteurs).as("PD2 est passée dans le contexte : seul l'adaptateur JDBC du Pilotage lit/écrit direction_snapshot")
                .containsExactly(PROPRIETAIRE_DES_INSTANTANES);
    }

    @Test
    void direction_alert_history_n_a_plus_que_son_proprietaire_dans_le_contexte_et_aucun_lecteur_de_la_plateforme() throws IOException {
        Set<String> lecteurs = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (lit(source, Set.of("direction_alert_history"))) lecteurs.add(relatif(source));
        }
        assertThat(lecteurs).as("PD3 est passée dans le contexte : seul l'adaptateur JDBC du Pilotage lit/écrit direction_alert_history")
                .containsExactly(PROPRIETAIRE_DE_L_HISTORIQUE);
    }

    @Test
    void la_liste_blanche_compte_deux_proprietaires_dates_et_sans_echeance() {
        assertThat(LECTEURS_CONNUS).hasSize(2);
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).startsWith("2026-");
            assertThat(lecteur.butoir()).as("butoir de " + chemin + " : un propriétaire n'a pas d'échéance").isEqualTo(LocalDate.of(2099, 12, 31));
        });
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // PD5 (ADR 0105) : clôture du Pilotage. Plus aucun fichier du Pilotage dans application/direction de la plateforme.
    // ---------------------------------------------------------------------------------------------------------------------------------

    private static final String APPLICATION_DIRECTION = "platform/src/main/java/com/hemodialyse/backend/application/direction/";

    /**
     * PL10b2 + PL10c (ADR 0152) : les quatre services de lecture (GMAO, Stock, Planification, Absences) et l'anonymat de la plateforme ont
     * quitté {@code application/direction} ; il ne reste que les comptes de l'Organisation / IAM (leur retrait est suivi par
     * {@code TableDeLOrganisationTest}, qui porte sa propre échéance). Cette garde est PERMANENTE : plus aucune date de revue.
     */
    private static final Set<String> COMPTES = Set.of("DirectionAccountService.java", "SocieteAdminAccountService.java", "PasswordPolicy.java");

    /** Chemins relatifs à application/direction, récursifs : un sous-package échapperait sinon à la garde. */
    private static Set<String> fichiersDeApplicationDirection() throws IOException {
        Path racine = backend().resolve(APPLICATION_DIRECTION);
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(Files::isRegularFile).map(p -> chemin(racine.relativize(p))).filter(n -> n.endsWith(".java"))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    @Test
    void application_direction_ne_contient_plus_que_les_comptes() throws IOException {
        assertThat(fichiersDeApplicationDirection())
                .as("fichiers de application/direction : tout fichier du Pilotage vit dans backend/contexts/pilotage-direction (PD5, ADR 0105 ; "
                        + "PL10b2 + PL10c, ADR 0152) ; il ne reste que les comptes de l'Organisation / IAM")
                .containsExactlyInAnyOrderElementsOf(COMPTES);
    }

    @Test
    void les_comptes_de_direction_et_d_administrateur_de_societe_restent_en_place_et_ne_sont_pas_du_pilotage() throws IOException {
        Set<String> presents = fichiersDeApplicationDirection();
        assertThat(presents).as("les comptes relèvent de l'Organisation / IAM : PD5 ne les retire ni ne les déplace").containsAll(COMPTES);
        for (String compte : COMPTES) {
            assertThat(lit(backend().resolve(APPLICATION_DIRECTION + compte), TABLES_DU_PILOTAGE)).as(compte + " ne touche pas les tables du Pilotage").isFalse();
        }
        for (Path source : sourcesDeProduction(backend())) {
            if (relatif(source).startsWith("contexts/pilotage-direction/")) {
                assertThat(source.getFileName().toString()).as("le module Pilotage ne reprend pas les comptes").isNotIn(COMPTES);
            }
        }
    }

    @Test
    void le_seuil_d_anonymat_est_unique_et_le_pilotage_n_a_plus_de_copie_dans_la_plateforme() throws IOException {
        // PL10b2 + PL10c (ADR 0152, KB-PL10b-5) : une seule politique, SeuilDAnonymat ; le DPO la modifie en un seul endroit.
        // Valeur à confirmer métiers/DPO : question ouverte KB-DIR-01, portée par le Pilotage (plus de copie à tenir égale).
        assertThat(com.hemodialyse.backend.contexts.pilotagedirection.domain.SeuilDAnonymat.SEUIL).isEqualTo(5);
        try (Stream<Path> sources = Files.walk(backend().resolve("platform/src/main/java"))) {
            assertThat(sources.map(p -> p.getFileName().toString())).as("AnonymityPolicy ne revient pas dans la plateforme")
                    .doesNotContain("AnonymityPolicy.java");
        }
    }

    @Test
    void la_garde_detecte_une_lecture_directe_d_une_table_du_pilotage() throws IOException {
        Path bac = Files.createTempDirectory("garde-pilotage");
        Path fautif = bac.resolve("Lecture.java");
        Files.writeString(fautif, "class Lecture { String s = \"SELECT payload FROM direction_snapshot WHERE societe_id = ?\"; }");
        Path ecriture = bac.resolve("Ecriture.java");
        Files.writeString(ecriture, "class Ecriture { String s = \"INSERT INTO direction_alert_history (id) VALUES (?)\"; }");
        Path propre = bac.resolve("Propre.java");
        Files.writeString(propre, "class Propre { String s = \"SELECT id FROM factures\"; }");

        assertThat(lit(fautif, TABLES_DU_PILOTAGE)).isTrue();
        assertThat(lit(ecriture, TABLES_DU_PILOTAGE)).isTrue();
        assertThat(lit(propre, TABLES_DU_PILOTAGE)).isFalse();
    }

    @Test
    void la_garde_balaie_la_plateforme_et_les_contextes() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(TableDuPilotageTest::relatif).toList();

        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/pilotage-direction/src/main/java/"));
    }
}
