package com.hemodialyse.backend.contexts.planification.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Règles d'architecture du contexte Planification (ADR 0015, 0049 à 0054), équivalentes à {@code .ddd/arch-rules.json} /
 * {@code check-arch.ts} : dépendances vers l'intérieur, domaine et application sans framework, Timefold confiné à
 * {@code infrastructure.optimisation}, aucun import du legacy, autres contextes par leur seul langage publié.
 */
@AnalyzeClasses(packages = "com.hemodialyse.backend.contexts.planification", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureHexagonaleTest {

    private static final String RACINE = "com.hemodialyse.backend.contexts.planification";

    @ArchTest
    static final ArchRule le_domaine_ne_depend_que_de_lui_meme = noClasses().that().resideInAPackage(RACINE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RACINE + ".application..", RACINE + ".infrastructure..", RACINE + ".interfaces..",
                    RACINE + ".configuration..", RACINE + ".publishedlanguage..");

    @ArchTest
    static final ArchRule le_domaine_est_sans_framework = noClasses().that().resideInAPackage(RACINE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax..", "org.hibernate..", "tools.jackson..",
                    "com.fasterxml.jackson..", "lombok..", "org.slf4j..", "ai.timefold..");

    /**
     * Le domaine est déterministe : identifiants, instants et « aujourd'hui » (fuseau du centre, ADR 0054) lui sont fournis.
     */
    @ArchTest
    static final ArchRule le_domaine_ne_tire_ni_identifiant_ni_heure = noClasses().that().resideInAPackage(RACINE + ".domain..")
            .should().callMethod(java.util.UUID.class, "randomUUID")
            .orShould().callMethod(java.time.LocalDate.class, "now")
            .orShould().callMethod(java.time.LocalDateTime.class, "now")
            .orShould().callMethod(java.time.OffsetDateTime.class, "now")
            .orShould().callMethod(java.time.ZonedDateTime.class, "now")
            .orShould().callMethod(java.time.Instant.class, "now");

    @ArchTest
    static final ArchRule l_application_ne_depend_ni_des_adapters_ni_de_la_configuration = noClasses()
            .that().resideInAPackage(RACINE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RACINE + ".infrastructure..", RACINE + ".interfaces..", RACINE + ".configuration..");

    @ArchTest
    static final ArchRule l_application_est_sans_framework_de_persistance_web_json_ou_solveur = noClasses()
            .that().resideInAPackage(RACINE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..",
                    "tools.jackson..", "com.fasterxml.jackson..", "lombok..", "ai.timefold..");

    /** ADR 0050 : Timefold est une bibliothèque d'infrastructure, derrière le port {@code Optimiseur}. */
    @ArchTest
    static final ArchRule timefold_seulement_dans_l_infrastructure_d_optimisation = noClasses()
            .that().resideOutsideOfPackage(RACINE + ".infrastructure.optimisation..")
            .should().dependOnClassesThat().resideInAnyPackage("ai.timefold..");

    @ArchTest
    static final ArchRule le_langage_publie_ne_depend_de_rien_du_contexte = noClasses()
            .that().resideInAPackage(RACINE + ".publishedlanguage..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RACINE + ".domain..", RACINE + ".application..", RACINE + ".infrastructure..",
                    RACINE + ".interfaces..", RACINE + ".configuration..", "org.springframework..", "ai.timefold..");

    @ArchTest
    static final ArchRule les_adapters_sortants_ne_dependent_pas_des_adapters_entrants = noClasses()
            .that().resideInAPackage(RACINE + ".infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(RACINE + ".interfaces..", RACINE + ".configuration..");

    @ArchTest
    static final ArchRule les_adapters_entrants_ne_dependent_pas_de_l_infrastructure = noClasses()
            .that().resideInAPackage(RACINE + ".interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(RACINE + ".infrastructure..", RACINE + ".configuration..");

    @ArchTest
    static final ArchRule aucun_import_du_legacy = noClasses().that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hemodialyse.backend.domain..", "com.hemodialyse.backend.application..",
                    "com.hemodialyse.backend.infrastructure..", "com.hemodialyse.backend.shared..");

    @ArchTest
    static final ArchRule les_autres_contextes_seulement_par_leur_langage_publie = noClasses()
            .that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hemodialyse.backend.contexts.gmao.domain..",
                    "com.hemodialyse.backend.contexts.gmao.application..",
                    "com.hemodialyse.backend.contexts.gmao.infrastructure..",
                    "com.hemodialyse.backend.contexts.gmao.interfaces..",
                    "com.hemodialyse.backend.contexts.gmao.configuration..",
                    "com.hemodialyse.backend.contexts.comptabilite.domain..",
                    "com.hemodialyse.backend.contexts.comptabilite.application..",
                    "com.hemodialyse.backend.contexts.comptabilite.infrastructure..",
                    "com.hemodialyse.backend.contexts.comptabilite.interfaces..",
                    "com.hemodialyse.backend.contexts.comptabilite.configuration..",
                    "com.hemodialyse.backend.contexts.facturation.domain..",
                    "com.hemodialyse.backend.contexts.facturation.application..",
                    "com.hemodialyse.backend.contexts.facturation.infrastructure..",
                    "com.hemodialyse.backend.contexts.facturation.interfaces..",
                    "com.hemodialyse.backend.contexts.facturation.configuration..",
                    "com.hemodialyse.backend.contexts.absencespatients.domain..",
                    "com.hemodialyse.backend.contexts.absencespatients.application..",
                    "com.hemodialyse.backend.contexts.absencespatients.infrastructure..",
                    "com.hemodialyse.backend.contexts.absencespatients.interfaces..",
                    "com.hemodialyse.backend.contexts.absencespatients.configuration..",
                    "com.hemodialyse.backend.contexts.stock.domain..",
                    "com.hemodialyse.backend.contexts.stock.application..",
                    "com.hemodialyse.backend.contexts.stock.infrastructure..",
                    "com.hemodialyse.backend.contexts.stock.interfaces..",
                    "com.hemodialyse.backend.contexts.stock.configuration..");
}
