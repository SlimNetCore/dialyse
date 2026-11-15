package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde de NON-RÉGRESSION de la table {@code gmao_equipements} (propriété du contexte GMAO, revue SE1 du 2026-11-07) : les lecteurs SQL
 * hors du contexte GMAO sont une LISTE BLANCHE NOMMÉE, avec la raison et la condition de sortie (un événement, jamais une date) de chaque jalon. Un nouveau lecteur hors
 * liste fait échouer le test ; une entrée morte aussi (la liste doit être réduite). Modèle : {@link TableDuPatientCouvertureTest}.
 */
class TableDesEquipementsDeLaGmaoTest {

    private static final Pattern TABLE = Pattern.compile("\\bgmao_equipements\\b", Pattern.CASE_INSENSITIVE);

    // GenerateursDesPatientsJdbc (Séance) : entrée SOLDÉE par LEC-PLN-GMAO (2026-11-14, ADR 0179) ; le générateur du patient est lu par
    // FichesDesGenerateurs (gmao.v1, fabricant compris). Verrou : voir le test dédié ci-dessous.
    // GenerateursDuCentreJdbc et LectureDesMouvementsJdbc (Patient et couverture) : entrées SOLDÉES par LEC-PAT-GMAO (2026-11-14, ADR 0181) ;
    // les deux lisent DescriptionsDesEquipements (gmao.v1, lecture par lot bornée au centre, supprimés compris). Aucun report. La liste
    // blanche est VIDE : tout lecteur de gmao_equipements hors du contexte GMAO fait échouer le test.
    private static final Map<String, String> JALONS = Map.of();

    private static List<String> lecteurs(Path backend) throws IOException {
        try (Stream<Path> fichiers = Files.walk(backend)) {
            return fichiers.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> chemin(p).contains("/src/main/java/"))
                    .filter(p -> !chemin(p).contains("/target/") && !chemin(p).contains("/contexts/gmao/"))
                    .filter(TableDesEquipementsDeLaGmaoTest::mentionneLaTable)
                    .map(p -> chemin(backend.relativize(p))).sorted().toList();
        }
    }

    private static boolean mentionneLaTable(Path source) {
        try {
            return TABLE.matcher(Files.readString(source, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    @Test
    void aucun_nouveau_lecteur_de_gmao_equipements_hors_du_contexte_gmao() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Set<String> nouveaux = new TreeSet<>(lecteurs(backend));
        nouveaux.removeAll(JALONS.keySet());
        assertThat(nouveaux).as("lecteurs de gmao_equipements hors du contexte GMAO et hors jalon nommé (avec nature et condition de sortie)").isEmpty();
    }

    @Test
    void chaque_jalon_est_un_lecteur_reel() throws IOException {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        Set<String> mortes = new TreeSet<>(JALONS.keySet());
        mortes.removeAll(lecteurs(backend));
        assertThat(mortes).as("jalons dont le fichier ne lit plus gmao_equipements : les retirer").isEmpty();
    }

    /** LEC-PLN-GMAO (ADR 0179) : la Séance ne lit plus la table des équipements ; ni jalon, ni lecteur. */
    @Test
    void la_seance_ne_lit_plus_les_equipements_de_la_gmao() throws IOException {
        String adapter = "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/legacygmao/GenerateursDesPatientsJdbc.java";
        assertThat(JALONS).doesNotContainKey(adapter);
        assertThat(lecteurs(Path.of("..").toAbsolutePath().normalize())).noneMatch(c -> c.startsWith("contexts/seance/"));
    }

    /** LEC-PAT-GMAO (ADR 0181) : plus aucun lecteur hors GMAO ; la liste blanche est vide et les deux anciens lecteurs n'y figurent plus. */
    @Test
    void patient_et_couverture_ne_lit_plus_les_equipements_de_la_gmao() throws IOException {
        assertThat(JALONS).isEmpty();
        assertThat(lecteurs(Path.of("..").toAbsolutePath().normalize())).isEmpty();
    }

    @Test
    void chaque_jalon_porte_une_nature_et_une_condition_de_sortie() {
        assertThat(JALONS.values()).allSatisfy(raison -> {
            assertThat(raison).isNotBlank();
            assertThat(raison).contains("condition de sortie :");
        });
    }
}
