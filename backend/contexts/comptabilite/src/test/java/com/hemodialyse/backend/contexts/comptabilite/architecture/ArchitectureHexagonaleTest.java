package com.hemodialyse.backend.contexts.comptabilite.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Règles d'architecture du contexte Comptabilité (ADR 0015), équivalentes à {@code .ddd/arch-rules.json} / {@code check-arch.ts} :
 * dépendances vers l'intérieur, domaine et application sans framework, aucun import du legacy hors ACL.
 */
@AnalyzeClasses(packages = "com.hemodialyse.backend.contexts.comptabilite", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureHexagonaleTest {

    private static final String RACINE = "com.hemodialyse.backend.contexts.comptabilite";

    @ArchTest
    static final ArchRule le_domaine_ne_depend_que_de_lui_meme = noClasses().that().resideInAPackage(RACINE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RACINE + ".application..", RACINE + ".infrastructure..", RACINE + ".interfaces..",
                    RACINE + ".configuration..", RACINE + ".publishedlanguage..");

    @ArchTest
    static final ArchRule le_domaine_est_sans_framework = noClasses().that().resideInAPackage(RACINE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "javax..", "org.hibernate..", "tools.jackson..",
                    "com.fasterxml.jackson..", "lombok..", "org.slf4j..");

    /**
     * Le domaine est déterministe : identifiants et instants lui sont fournis (revue 2026-10-19, ADR 0028).
     */
    @ArchTest
    static final ArchRule le_domaine_ne_tire_ni_identifiant_ni_heure = noClasses().that().resideInAPackage(RACINE + ".domain..")
            .should().callMethod(java.util.UUID.class, "randomUUID")
            .orShould().callMethod(java.time.LocalDate.class, "now")
            .orShould().callMethod(java.time.LocalDateTime.class, "now")
            .orShould().callMethod(java.time.OffsetDateTime.class, "now")
            .orShould().callMethod(java.time.Instant.class, "now");

    @ArchTest
    static final ArchRule l_application_ne_depend_ni_des_adapters_ni_de_la_configuration = noClasses()
            .that().resideInAPackage(RACINE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RACINE + ".infrastructure..", RACINE + ".interfaces..", RACINE + ".configuration..");

    @ArchTest
    static final ArchRule l_application_est_sans_framework_de_persistance_web_ou_json = noClasses()
            .that().resideInAPackage(RACINE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..",
                    "tools.jackson..", "com.fasterxml.jackson..", "lombok..");

    @ArchTest
    static final ArchRule le_langage_publie_est_sans_framework_de_persistance = noClasses()
            .that().resideInAPackage(RACINE + ".publishedlanguage..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "lombok..",
                    RACINE + ".domain..", RACINE + ".infrastructure..", RACINE + ".interfaces..", RACINE + ".configuration..");

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
}
