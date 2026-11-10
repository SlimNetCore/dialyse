package com.hemodialyse.backend.architecture;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revue globale 2026-11-10, constat major : les descripteurs {@link ReferentialKind} des référentiels déjà migrés chez Patient et
 * couverture (RF1-R1 TRANSPORTEUR, RF2-R1 FORFAIT, RF3-R1 CAISSE / AGENCE / CENTRE_PAYEUR) restent dans l'administration générique
 * (lecteur de fichiers et modèles d'import communs). Leur butoir « fin de RF3 » était périmé : il est désormais DATÉ (2027-03-31, à
 * confirmer par les métiers) et VERROUILLÉ ici. La liste est nominative, son effectif asserté ; un nouveau descripteur migré sans être
 * listé, ou un descripteur retiré sans retirer l'entrée, fait échouer le test ; après le butoir, le test échoue (reconduction motivée
 * ou retrait des descripteurs).
 */
class DescripteursResiduelsDeLAdministrationGeneriqueTest {

    private static final String BUTOIR = " Butoir : revue des ADR 0122, 0124, 0126 et 0128 au 2027-03-31 (sinon reconduction motivée).";

    /** Descripteurs résiduels, avec leur résiduel et leur raison datée. */
    private static final Map<ReferentialKind, String> RESIDUELS = new TreeMap<>();

    static {
        String commun = "2026-11-10 (RF4) : descripteur (champs, alias d'en-têtes, modèle d'import, libellé) commun au lecteur de fichiers "
                + "de l'administration générique, retiré avec elle.";
        RESIDUELS.put(ReferentialKind.TRANSPORTEUR, "RF1-R1 " + commun + BUTOIR);
        RESIDUELS.put(ReferentialKind.FORFAIT, "RF2-R1 " + commun + BUTOIR);
        RESIDUELS.put(ReferentialKind.CAISSE, "RF3-R1 " + commun + BUTOIR);
        RESIDUELS.put(ReferentialKind.AGENCE, "RF3-R1 " + commun + BUTOIR);
        RESIDUELS.put(ReferentialKind.CENTRE_PAYEUR, "RF3-R1 " + commun + BUTOIR);
    }

    /** Les référentiels qui ne sont pas encore migrés : jamais des résiduels. */
    private static final Set<ReferentialKind> ENCORE_GENERIQUES = Set.of(
            ReferentialKind.CRENEAU, ReferentialKind.SALLE, ReferentialKind.MEDECIN);

    /** Les ADR portant le résiduel et la ligne de chaque résiduel (début de ligne de tableau). */
    private static final Map<String, List<String>> ADR_ET_LIGNES = Map.of(
            "0122-rf1-transporteurs-et-categories-de-transport-chez-patient-couverture.md", List.of("| RF1-R1 |"),
            "0124-rf2-catalogue-de-forfaits-chez-patient-couverture.md", List.of("| RF2-R1 |"),
            "0126-rf3a-chaine-des-payeurs-chez-patient-couverture.md", List.of("| RF3-R1 |"),
            "0128-rf3c-cloture-des-payeurs-comptabilite-facturation-et-lectures-communes.md", List.of("| RF3-R1 |"));

    @Test
    void la_liste_des_descripteurs_residuels_est_nominative_et_son_effectif_asserte() {
        assertThat(RESIDUELS.keySet()).containsExactlyInAnyOrder(ReferentialKind.TRANSPORTEUR, ReferentialKind.FORFAIT,
                ReferentialKind.CAISSE, ReferentialKind.AGENCE, ReferentialKind.CENTRE_PAYEUR);
        assertThat(RESIDUELS).hasSize(5);
    }

    @Test
    void tout_descripteur_de_l_administration_generique_est_soit_residuel_nomme_soit_encore_generique() {
        Set<ReferentialKind> tous = Arrays.stream(ReferentialKind.values()).collect(Collectors.toCollection(TreeSet::new));
        Set<ReferentialKind> classes = new TreeSet<>(RESIDUELS.keySet());
        classes.addAll(ENCORE_GENERIQUES);
        assertThat(tous).as("un nouveau descripteur doit être classé : résiduel daté ou référentiel encore générique")
                .isEqualTo(classes);
    }

    @Test
    void chaque_residuel_porte_un_butoir_lisible_et_aucun_n_est_depasse() {
        Map<String, String> raisons = RESIDUELS.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue));
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(raisons, LocalDate.of(2026, 11, 10))).isEmpty();
        assertThat(ButoirsDesGardesDeTables.butoir(BUTOIR)).contains(LocalDate.of(2027, 3, 31));
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(raisons, LocalDate.now()))
                .as("butoir (2027-03-31) dépassé : retirer les descripteurs avec l'administration générique, ou reconduire avec "
                        + "une raison motivée et une nouvelle date (ADR 0122, 0124, 0126, 0128)").isEmpty();
        assertThat(ButoirsDesGardesDeTables.butoirsAbsentsOuDepasses(raisons, LocalDate.of(2027, 4, 1))).hasSize(5);
    }

    @Test
    void les_adr_datent_chaque_residuel_au_2027_03_31() throws IOException {
        Path adr = Path.of("..", "..", ".ddd", "architecture", "adr").toAbsolutePath().normalize();
        Set<String> sansDate = new TreeSet<>();
        for (Map.Entry<String, List<String>> e : ADR_ET_LIGNES.entrySet()) {
            Path fichier = adr.resolve(e.getKey());
            assertThat(Files.isRegularFile(fichier)).as(fichier.toString()).isTrue();
            List<String> lignes = Files.readAllLines(fichier);
            for (String debut : e.getValue()) {
                String ligne;
                try (Stream<String> s = lignes.stream()) {
                    ligne = s.filter(l -> l.startsWith(debut)).findFirst().orElse("");
                }
                if (!ligne.contains("2027-03-31")) sansDate.add(e.getKey() + " " + debut);
            }
        }
        assertThat(sansDate).as("résiduels dont la ligne d'ADR ne porte pas le butoir daté 2027-03-31").isEmpty();
    }
}
