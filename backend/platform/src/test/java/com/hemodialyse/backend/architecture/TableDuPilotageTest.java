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
 * Liste blanche NOMINATIVE des lecteurs et écrivains actuels : le propriétaire de {@code direction_snapshot} (adaptateur du contexte) et le
 * lecteur de PD3 resté en plateforme (PD3 viendra ensuite) ; chaque entrée porte sa date et son butoir. Une entrée dont le fichier ne lit
 * plus la table est « morte » : elle est à retirer (avancement de PD3). Les tables du tableau de bord ne sont PAS ici : le module ne possède pas de table, il lit celles des autres
 * contextes par leurs langages publiés (ou, pour {@code patients} et {@code centers}, par les entrées nommées de leurs gardes).
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

    private static final Map<String, Lecteur> LECTEURS_CONNUS = new TreeMap<>(Map.of(
            PROPRIETAIRE_DES_INSTANTANES,
            new Lecteur("2026-11-09 : propriétaire de direction_snapshot (PD2, ADR 0103) : liste, relit et fige les instantanés mensuels ; "
                    + "SQL repris mot pour mot du service retiré", LocalDate.of(2099, 12, 31)),
            "platform/src/main/java/com/hemodialyse/backend/application/direction/DirectionAlertHistoryService.java",
            new Lecteur("2026-11-09 : écrit et lit l'historique des alertes (direction_alert_history) tant que la direction est connectée "
                    + "(PD3, inchangé par les lots A et B) ; échéance : reprise de PD3", LocalDate.of(2027, 3, 31))));

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
    void la_liste_blanche_compte_deux_lecteurs_chacun_avec_un_butoir_apres_la_revue_du_2026_12_15() {
        assertThat(LECTEURS_CONNUS).hasSize(2);
        LECTEURS_CONNUS.forEach((chemin, lecteur) -> {
            assertThat(lecteur.nature()).as("nature de " + chemin).startsWith("2026-");
            assertThat(lecteur.butoir()).as("butoir de " + chemin).isAfter(REVUE);
        });
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
