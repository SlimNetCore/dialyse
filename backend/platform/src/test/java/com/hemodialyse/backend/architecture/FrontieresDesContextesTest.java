package com.hemodialyse.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Frontière des contextes refaits sur place, vue depuis le legacy ({@code platform}, {@code bc-*}), que
 * {@code check-arch.ts} n'analyse pas : le legacy n'utilise un contexte que par son langage publié (revue
 * {@code .ddd/reviews/2026-10-19-comptabilite.md}, constat major « archi »).
 */
@AnalyzeClasses(packages = "com.hemodialyse.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class FrontieresDesContextesTest {

    private static final String COMPTABILITE = "com.hemodialyse.backend.contexts.comptabilite";

    @ArchTest
    static final ArchRule le_legacy_n_utilise_la_comptabilite_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(COMPTABILITE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    COMPTABILITE + ".domain..", COMPTABILITE + ".application..", COMPTABILITE + ".infrastructure..",
                    COMPTABILITE + ".interfaces..", COMPTABILITE + ".configuration..")
            .because("la Comptabilité ne s'utilise que par son langage publié (ADR 0025)");

    private static final String FACTURATION = "com.hemodialyse.backend.contexts.facturation";

    /**
     * Slice F6 : le module Maven {@code bc-facturation} ne contient plus de classe ; aucune classe du dépôt ne doit
     * réintroduire le paquet legacy {@code com.hemodialyse.backend.domain.facturation} (ports, agrégat TypeTVA, services).
     */
    @ArchTest
    static final ArchRule plus_aucune_classe_ne_depend_du_paquet_legacy_de_facturation = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("com.hemodialyse.backend.domain.facturation..")
            .allowEmptyShould(true)
            .because("bc-facturation est vide depuis F5 et retiré par F6 : la Facturation vit dans contexts/facturation");

    /**
     * Slice F6 : le legacy n'utilise la Facturation que par son langage publié, sans aucune exemption (la dette des trois
     * classes {@code AbsencesDesPatientsLegacy}, {@code FacturationSyntheseMensuelleReportService} et
     * {@code DocumentRestController} est soldée).
     */
    @ArchTest
    static final ArchRule le_legacy_n_utilise_la_facturation_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(FACTURATION + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    FACTURATION + ".domain..", FACTURATION + ".application..", FACTURATION + ".infrastructure..",
                    FACTURATION + ".interfaces..", FACTURATION + ".configuration..")
            .because("la Facturation ne s'utilise que par son langage publié (ADR 0030..0034)");

    private static final String ABSENCES = "com.hemodialyse.backend.contexts.absencespatients";

    /**
     * Slice A1 : le contexte Absences de patients n'a pas encore de langage publié ; le legacy ne l'utilise donc d'aucune
     * façon (le module est câblé par sa configuration, scannée par Spring). Le langage publié (lecture du pilotage,
     * déclaration de l'exclusion) arrive avec les slices A2 et A4.
     */
    @ArchTest
    static final ArchRule le_legacy_n_utilise_pas_le_contexte_des_absences_de_patients = noClasses()
            .that().resideOutsideOfPackage(ABSENCES + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ABSENCES + ".domain..", ABSENCES + ".application..", ABSENCES + ".infrastructure..",
                    ABSENCES + ".interfaces..", ABSENCES + ".configuration..")
            .because("les Absences de patients ne s'utiliseront que par leur langage publié (A2, A4)");
}

