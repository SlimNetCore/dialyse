package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Liste blanche des ÉCRIVAINS de {@code seances.facture_id} (ADR 0097, décision 7 ; revue SE7c2, constat m4), sur le modèle de
 * {@link TableDesSeancesTest} : même scan des sources de production (Java et ressources SQL hors Flyway, entités JPA exceptées), mais le
 * contexte Séance compris. ARGENT : aucune clé étrangère ni contrainte ne protège la colonne (KB-SEA-74). Un écrivain est :
 * <ul>
 *   <li>un {@code UPDATE seances … SET …} dont la clause SET affecte {@code facture_id} ;</li>
 *   <li>un {@code INSERT INTO seances (…)} dont la liste de colonnes contient {@code facture_id} ;</li>
 *   <li>un appel {@code setFactureId(…)} hors entité, ou un code qui enregistre une {@code SeanceJpaEntity} par JPA ({@code save},
 *   {@code saveAll}, {@code saveAndFlush}, {@code persist}, {@code merge}) : la colonne est mappée et réécrite avec la ligne entière.</li>
 * </ul>
 * Toute entrée est nommée, porte sa nature et un butoir ; la liste est revue au plus tard le {@link #REVUE}. Un nouvel écrivain fait échouer
 * le test, une entrée qui n'écrit plus la colonne aussi (entrée morte).
 */
class EcrivainsDeLaFactureDesSeancesTest {

    /** Revue de toute la liste au plus tard à cette date ; chaque entrée porte en plus son butoir. */
    private static final LocalDate REVUE = LocalDate.of(2026, 12, 15);

    private static final LocalDate RETRAIT_DU_PILOTAGE_LEGACY = LocalDate.of(2027, 6, 30);
    private static final LocalDate SANS_ECHEANCE_DE_RETRAIT = LocalDate.of(2099, 12, 31);

    private record Ecrivain(String nature, LocalDate butoir) {
    }

    /** Écrivains connus de {@code seances.facture_id} (chemin depuis {@code backend/}) : 2026-11-08, revue SE7c2. */
    private static final Map<String, Ecrivain> ECRIVAINS_CONNUS = new TreeMap<>();

    private static void ecrivain(String chemin, String nature, LocalDate butoir) {
        ECRIVAINS_CONNUS.put(chemin, new Ecrivain(nature, butoir));
    }

    static {
        // Séance : le SEUL écrivain voulu, appelé par la Facturation via LiaisonDesSeancesAUneFacturePubliee (ADR 0097, lot c2b).
        ecrivain("contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/LiaisonDesSeancesJdbc.java",
                "rattachement des séances à leur facture à l'émission (SET statut = 'FACTUREE', facture_id = ?), ARGENT", SANS_ECHEANCE_DE_RETRAIT);
        // Hors Séance : écrivains legacy révélés par la garde (2026-11-08), décrits dans la fiche de slice SE7c2, non masqués. SE7f (ADR 0100) :
        // SeanceRepositoryAdapter (réécriture JPA de la ligne entière, KB-SEA-49) est retiré avec bc-seance ; KB-SEA-49 est clos.
        ecrivain("platform/src/main/resources/db/seed.sql",
                "jeu d'amorçage de développement : rattache les séances FACTUREE aux factures AUTO- du mois (jamais sur une base partagée)",
                RETRAIT_DU_PILOTAGE_LEGACY);
    }

    private static final Pattern MISE_A_JOUR = Pattern.compile(
            "\\bUPDATE\\s+(?:public\\.)?seances\\b[\\s\"+]*(?:(?!SET\\b)[A-Za-z_][A-Za-z0-9_]*[\\s\"+]+)?SET\\b((?:(?!\\bWHERE\\b|;).){0,600})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern AFFECTATION_DE_LA_FACTURE = Pattern.compile("(?<![.\\w])facture_id\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERTION = Pattern.compile("\\bINSERT\\s+INTO\\s+(?:public\\.)?seances\\s*\\(([^)]{0,800})\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COLONNE_FACTURE = Pattern.compile("\\bfacture_id\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENREGISTREMENT_JPA = Pattern.compile("\\.(save|saveAll|saveAndFlush|persist|merge)\\s*\\(");
    private static final Pattern MUTATEUR = Pattern.compile("\\.setFactureId\\s*\\(");

    /** Ressources SQL de production hors Flyway (états Jasper, requêtes en fichier) : lues comme les sources Java. */
    private static final Set<String> EXTENSIONS_DE_RESSOURCES = Set.of(".sql", ".jrxml", ".xml");

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path p) {
        return chemin(backend().relativize(p));
    }

    /** Tous les sources de production de {@code racine}, contexte Séance COMPRIS (les entités JPA et les migrations Flyway exceptées). */
    private static List<Path> sourcesDeProduction(Path racine) throws IOException {
        List<Path> trouves = new ArrayList<>();
        try (Stream<Path> modules = Stream.concat(Files.list(racine), Files.list(racine.resolve("contexts")))) {
            for (Path module : modules.toList()) {
                Path java = module.resolve("src/main/java");
                if (!Files.isDirectory(java)) continue;
                try (Stream<Path> fichiers = Files.walk(java)) {
                    fichiers.filter(p -> p.toString().endsWith(".java")).filter(p -> !chemin(p).contains("/entity/")).forEach(trouves::add);
                }
                Path ressources = module.resolve("src/main/resources");
                if (Files.isDirectory(ressources)) {
                    try (Stream<Path> fichiers = Files.walk(ressources)) {
                        fichiers.filter(Files::isRegularFile).filter(p -> !chemin(p).contains("/db/migration/"))
                                .filter(p -> EXTENSIONS_DE_RESSOURCES.stream().anyMatch(e -> p.toString().endsWith(e)))
                                .forEach(trouves::add);
                    }
                }
            }
        }
        return trouves;
    }

    static boolean ecritLaFactureDesSeances(Path source) {
        try {
            String texte = Files.readString(source);
            Matcher maj = MISE_A_JOUR.matcher(texte);
            while (maj.find()) {
                if (AFFECTATION_DE_LA_FACTURE.matcher(maj.group(1)).find()) return true;
            }
            Matcher ins = INSERTION.matcher(texte);
            while (ins.find()) {
                if (COLONNE_FACTURE.matcher(ins.group(1)).find()) return true;
            }
            if (MUTATEUR.matcher(texte).find()) return true;
            return texte.contains("SeanceJpaEntity") && ENREGISTREMENT_JPA.matcher(texte).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aucun_nouvel_ecrivain_de_la_facture_des_seances_hors_de_la_liste_blanche() throws IOException {
        Set<String> nouveaux = new TreeSet<>();
        for (Path source : sourcesDeProduction(backend())) {
            if (ecritLaFactureDesSeances(source) && !ECRIVAINS_CONNUS.containsKey(relatif(source))) nouveaux.add(relatif(source));
        }
        assertThat(nouveaux)
                .as("nouveaux écrivains de seances.facture_id : passer par LiaisonDesSeancesAUneFacturePubliee (seance.v1) ou les inscrire "
                        + "(nommés, datés, butoir) et les décrire dans la fiche de SE7c2")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche_des_ecrivains() {
        Set<String> morts = new TreeSet<>();
        for (String chemin : ECRIVAINS_CONNUS.keySet()) {
            Path source = backend().resolve(chemin);
            if (!Files.isRegularFile(source) || !ecritLaFactureDesSeances(source)) morts.add(chemin);
        }
        assertThat(morts).as("entrées qui n'écrivent plus seances.facture_id : les retirer").isEmpty();
    }

    @Test
    void chaque_ecrivain_a_un_butoir_propre_et_la_liste_est_a_revoir_au_plus_tard_le_2026_12_15() {
        assertThat(ECRIVAINS_CONNUS).isNotEmpty();
        ECRIVAINS_CONNUS.forEach((chemin, ecrivain) -> {
            assertThat(ecrivain.nature()).as("nature de " + chemin).isNotBlank();
            assertThat(ecrivain.butoir()).as("butoir de " + chemin).isAfter(REVUE);
        });
    }

    @Test
    void la_liste_blanche_compte_2_ecrivains_dont_1_de_la_seance_et_aucun_de_la_facturation() {
        assertThat(ECRIVAINS_CONNUS)
                .as("2 écrivains de seances.facture_id : LiaisonDesSeancesJdbc (Séance) et db/seed.sql (amorçage) ; SeanceRepositoryAdapter (JPA "
                        + "legacy, KB-SEA-49) est retiré par SE7f ; toute entrée ajoutée ou retirée est décidée, datée et reportée dans la fiche de SE7c2")
                .hasSize(2);
        assertThat(ECRIVAINS_CONNUS.keySet().stream().filter(c -> c.startsWith("contexts/seance/")).count()).as("dont 1 de la Séance")
                .isEqualTo(1);
        assertThat(ECRIVAINS_CONNUS.keySet()).as("aucun de la Facturation (ADR 0097)").noneMatch(c -> c.startsWith("contexts/facturation/"));
    }

    @Test
    void la_garde_scanne_le_contexte_seance_la_plateforme_et_les_ressources_sql() throws IOException {
        List<String> scannes = sourcesDeProduction(backend()).stream().map(EcrivainsDeLaFactureDesSeancesTest::relatif).toList();
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/seance/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("platform/src/main/java/"));
        assertThat(scannes).anyMatch(s -> s.startsWith("contexts/facturation/src/main/java/"));
        assertThat(scannes).as("ressources SQL de production hors Flyway").anyMatch(s -> s.contains("/src/main/resources/"));
        assertThat(scannes).noneMatch(s -> s.contains("/db/migration/"));
    }

    @Test
    void la_garde_distingue_l_ecriture_de_la_facture_des_autres_ecritures_et_des_lectures() throws IOException {
        Path bac = Files.createTempDirectory("garde-facture-des-seances");
        Path liaison = fichier(bac, "Liaison.java", "String sql = \"UPDATE seances \" + \"SET statut = 'FACTUREE', facture_id = ? WHERE id = ?\";");
        Path blocDeTexte = fichier(bac, "BlocDeTexte.java", "String sql = \"\"\"\n    UPDATE seances\n    SET facture_id = ?\n    WHERE id = ?\n\"\"\";");
        Path alias = fichier(bac, "seed.sql", "UPDATE seances s\nSET facture_id = (SELECT f.id FROM factures f WHERE f.center_id = s.center_id)\n"
                + "WHERE s.statut = 'FACTUREE';");
        Path insertion = fichier(bac, "Insertion.java", "String sql = \"INSERT INTO seances (id, center_id, facture_id) VALUES (?, ?, ?)\";");
        Path jpa = fichier(bac, "AdapterJpa.java", "SeanceJpaEntity e = toJpa(s); jpa.save(e);");
        Path mutateur = fichier(bac, "Mutateur.java", "e.setFactureId(facture);");
        Path gardeSeulement = fichier(bac, "Exclusion.java",
                "String sql = \"UPDATE seances SET statut = 'ABSENT' WHERE id = ? AND facture_id IS NULL\";");
        Path egaliteDansLeWhere = fichier(bac, "Where.java", "String sql = \"UPDATE seances SET statut = ? WHERE facture_id = ?\";");
        Path autreTable = fichier(bac, "Lignes.java", "String sql = \"UPDATE facture_lignes SET facture_id = ? WHERE id = ?\";");
        Path lecture = fichier(bac, "Lecture.java", "String sql = \"SELECT facture_id FROM seances WHERE id = ?\";");
        Path insertionSansFacture = fichier(bac, "Creation.java", "String sql = \"INSERT INTO seances (id, center_id, statut) VALUES (?, ?, ?)\";");

        assertThat(ecritLaFactureDesSeances(liaison)).as("UPDATE … SET facture_id concaténé").isTrue();
        assertThat(ecritLaFactureDesSeances(blocDeTexte)).as("bloc de texte").isTrue();
        assertThat(ecritLaFactureDesSeances(alias)).as("alias et sous-requête").isTrue();
        assertThat(ecritLaFactureDesSeances(insertion)).as("INSERT avec facture_id").isTrue();
        assertThat(ecritLaFactureDesSeances(jpa)).as("enregistrement JPA de la ligne entière").isTrue();
        assertThat(ecritLaFactureDesSeances(mutateur)).as("setFactureId").isTrue();
        assertThat(ecritLaFactureDesSeances(gardeSeulement)).as("facture_id IS NULL en garde").isFalse();
        assertThat(ecritLaFactureDesSeances(egaliteDansLeWhere)).as("facture_id = ? dans le WHERE").isFalse();
        assertThat(ecritLaFactureDesSeances(autreTable)).as("facture_id d'une autre table").isFalse();
        assertThat(ecritLaFactureDesSeances(lecture)).as("lecture").isFalse();
        assertThat(ecritLaFactureDesSeances(insertionSansFacture)).as("INSERT sans facture_id").isFalse();
    }

    private static Path fichier(Path bac, String nom, String contenu) throws IOException {
        return Files.writeString(bac.resolve(nom), contenu);
    }
}
