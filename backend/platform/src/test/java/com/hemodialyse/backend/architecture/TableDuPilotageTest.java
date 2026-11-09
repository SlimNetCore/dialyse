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
 * PD5 (ADR 0105, clôture) : garde nominative de ce qui RESTE dans {@code application/direction} de la plateforme (rendu PDF, comptes de
 * l'Organisation / IAM, services de lecture des autres contextes), sans SQL ; revue au plus tard le 2026-12-15.
 */
class TableDuPilotageTest {

    private static final Set<String> TABLES_DU_PILOTAGE = Set.of("direction_snapshot", "direction_alert_history");
    private static final LocalDate REVUE = LocalDate.of(2026, 12, 15);

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
    void la_liste_blanche_compte_deux_lecteurs_chacun_avec_un_butoir_apres_la_revue_du_2026_12_15() {
        assertThat(LECTEURS_CONNUS).hasSize(2);
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).startsWith("2026-");
            assertThat(lecteur.butoir()).as("butoir de " + chemin).isAfter(REVUE);
        });
    }

    // ---------------------------------------------------------------------------------------------------------------------------------
    // PD5 (ADR 0105) : clôture du Pilotage. Plus aucun fichier du Pilotage dans application/direction de la plateforme.
    // ---------------------------------------------------------------------------------------------------------------------------------

    private static final String APPLICATION_DIRECTION = "platform/src/main/java/com/hemodialyse/backend/application/direction/";

    /**
     * Ce qui RESTE dans {@code application/direction}, nominativement, avec son propriétaire cible et sa raison (revue au plus tard le
     * 2026-12-15, butoir des résiduels PD5-R1 à R6 : 2027-03-31). Dix de ces classes ne sont pas du Pilotage ; la onzième,
     * {@code DirectionIndicatorsQueryService}, compose des indicateurs du Pilotage et reste à migrer (PD5-R5, slice PD6 planifiée).
     */
    private record Reste(String categorie, String proprietaire) {
    }

    private static final Map<String, Reste> RESTE_DANS_APPLICATION_DIRECTION = new TreeMap<>(Map.ofEntries(
            Map.entry("DirectionReportPdfService.java", new Reste("rendu PDF", "plateforme (rendu seul : le contenu vient du Pilotage par son langage publié)")),
            Map.entry("SocieteLetterheadPort.java", new Reste("rendu PDF", "plateforme (en-tête du rapport)")),
            Map.entry("DirectionAccountService.java", new Reste("comptes", "Organisation / IAM")),
            Map.entry("SocieteAdminAccountService.java", new Reste("comptes", "Organisation / IAM")),
            Map.entry("PasswordPolicy.java", new Reste("comptes", "Organisation / IAM")),
            Map.entry("DirectionAbsencesQueryService.java", new Reste("lecture d'un autre contexte", "Absences de patients (absencespatients.v1)")),
            Map.entry("DirectionCapaciteQueryService.java", new Reste("lecture d'un autre contexte", "Planification (capacité) et GMAO")),
            Map.entry("DirectionGmaoQueryService.java", new Reste("lecture d'un autre contexte", "GMAO (gmao.v1)")),
            Map.entry("DirectionIndicatorsQueryService.java", new Reste("composition du Pilotage, non migrée (slice PD6 planifiée)",
                    "Pilotage direction (composition du tableau de bord, des alertes et de leur politique), avec lectures cliniques (DM9) et stock par langages publiés ; "
                            + "pas de filet dédié à ce jour (PD5-R5, ADR 0105)")),
            Map.entry("DirectionStockGroupesQueryService.java", new Reste("lecture d'un autre contexte", "Stock (stock.v1)")),
            Map.entry("AnonymityPolicy.java", new Reste("lecture d'un autre contexte", "plateforme : masquage des lectures ci-dessus (le Pilotage a son SeuilDAnonymat : égalité des deux seuils testée)"))));

    private static final Set<String> COMPTES = Set.of("DirectionAccountService.java", "SocieteAdminAccountService.java", "PasswordPolicy.java");
    private static final Set<String> RENDU_PDF = Set.of("DirectionReportPdfService.java", "SocieteLetterheadPort.java");
    private static final Pattern SQL_EN_MAJUSCULES = Pattern.compile("\\b(FROM|JOIN)\\s+[A-Za-z_]+\\b", Pattern.CASE_INSENSITIVE);

    /** Chemins relatifs à application/direction, récursifs : un sous-package échapperait sinon à la garde. */
    private static Set<String> fichiersDeApplicationDirection() throws IOException {
        Path racine = backend().resolve(APPLICATION_DIRECTION);
        try (Stream<Path> fichiers = Files.walk(racine)) {
            return fichiers.filter(Files::isRegularFile).map(p -> chemin(racine.relativize(p))).filter(n -> n.endsWith(".java"))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    @Test
    void application_direction_ne_contient_plus_que_le_rendu_pdf_les_comptes_et_les_lectures_des_autres_contextes() throws IOException {
        assertThat(fichiersDeApplicationDirection())
                .as("fichiers de application/direction : tout fichier du Pilotage vit dans backend/contexts/pilotage-direction (PD5, ADR 0105) ; "
                        + "tout nouveau fichier doit être nommé, avec son propriétaire, dans RESTE_DANS_APPLICATION_DIRECTION")
                .containsExactlyElementsOf(RESTE_DANS_APPLICATION_DIRECTION.keySet());
    }

    @Test
    void les_comptes_de_direction_et_d_administrateur_de_societe_restent_en_place_et_ne_sont_pas_du_pilotage() throws IOException {
        Set<String> presents = fichiersDeApplicationDirection();
        assertThat(presents).as("les comptes relèvent de l'Organisation / IAM : PD5 ne les retire ni ne les déplace").containsAll(COMPTES);
        for (String compte : COMPTES) {
            assertThat(RESTE_DANS_APPLICATION_DIRECTION.get(compte).proprietaire()).isEqualTo("Organisation / IAM");
            assertThat(lit(backend().resolve(APPLICATION_DIRECTION + compte), TABLES_DU_PILOTAGE)).as(compte + " ne touche pas les tables du Pilotage").isFalse();
        }
        for (Path source : sourcesDeProduction(backend())) {
            if (relatif(source).startsWith("contexts/pilotage-direction/")) {
                assertThat(source.getFileName().toString()).as("le module Pilotage ne reprend pas les comptes").isNotIn(COMPTES);
            }
        }
    }

    @Test
    void les_lectures_des_autres_contextes_et_le_rendu_pdf_n_ecrivent_aucun_sql() throws IOException {
        Set<String> avecSql = new TreeSet<>();
        for (String nom : RESTE_DANS_APPLICATION_DIRECTION.keySet()) {
            if (COMPTES.contains(nom)) continue;
            Matcher m = SQL_EN_MAJUSCULES.matcher(Files.readString(backend().resolve(APPLICATION_DIRECTION + nom)));
            if (m.find()) avecSql.add(nom + " : " + m.group());
        }
        assertThat(avecSql).as("ces classes passent par des langages publiés : aucun SQL cross-context (PD5)").isEmpty();
    }

    @Test
    void la_liste_du_reste_compte_onze_entrees_dont_deux_de_rendu_et_trois_de_comptes() {
        assertThat(RESTE_DANS_APPLICATION_DIRECTION).hasSize(11);
        assertThat(RESTE_DANS_APPLICATION_DIRECTION.keySet()).containsAll(RENDU_PDF).containsAll(COMPTES);
        RESTE_DANS_APPLICATION_DIRECTION.forEach((nom, reste) -> {
            assertThat(reste.categorie()).as("catégorie de " + nom).isNotBlank();
            assertThat(reste.proprietaire()).as("propriétaire de " + nom).isNotBlank();
        });
    }

    /**
     * Échéance EFFECTIVE (PD5, m3) : passé le 2026-12-15, ce test échoue tant que la revue des listes nominatives (propriétaires des
     * résiduels PD5-R1 à R8, questions KB-DIR-01 à 08) n'est pas faite ; la faire = confirmer ou corriger les décisions, puis repousser
     * {@link #REVUE} avec la nouvelle date dans l'ADR 0105. (Aucun autre garde du dépôt n'applique sa date : leurs « revue au plus tard »
     * sont du texte de raison.)
     */
    @Test
    void la_revue_du_2026_12_15_n_est_pas_depassee() {
        assertThat(REVUE).as("la date de revue est celle de l'ADR 0105").isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(LocalDate.now(java.time.ZoneOffset.UTC))
                .as("revue des listes nominatives du Pilotage (ADR 0105) échue : confirmer métiers/DPO puis repousser REVUE")
                .isBeforeOrEqualTo(REVUE);
    }

    @Test
    void les_deux_seuils_d_anonymat_de_la_plateforme_et_du_pilotage_sont_egaux() {
        assertThat(com.hemodialyse.backend.application.direction.AnonymityPolicy.THRESHOLD)
                .as("KB-DIR-01 : le DPO modifiera les deux copies ensemble (plateforme et SeuilDAnonymat du Pilotage)")
                .isEqualTo(com.hemodialyse.backend.contexts.pilotagedirection.domain.SeuilDAnonymat.SEUIL);
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
