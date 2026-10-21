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
}

