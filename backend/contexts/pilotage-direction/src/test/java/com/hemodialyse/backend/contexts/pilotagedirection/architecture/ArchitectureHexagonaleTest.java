package com.hemodialyse.backend.contexts.pilotagedirection.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.hemodialyse.backend.contexts.pilotagedirection", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureHexagonaleTest {

    private static final String RACINE = "com.hemodialyse.backend.contexts.pilotagedirection";

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
    static final ArchRule l_application_est_sans_framework_de_persistance_web_ou_json = noClasses()
            .that().resideInAPackage(RACINE + ".application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..",
                    "tools.jackson..", "com.fasterxml.jackson..", "lombok..");

    @ArchTest
    static final ArchRule le_langage_publie_ne_depend_de_rien_du_contexte = noClasses()
            .that().resideInAPackage(RACINE + ".publishedlanguage..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    RACINE + ".domain..", RACINE + ".application..", RACINE + ".infrastructure..",
                    RACINE + ".interfaces..", RACINE + ".configuration..", "org.springframework..");

    @ArchTest
    static final ArchRule les_adapters_sortants_ne_dependent_pas_des_adapters_entrants = noClasses()
            .that().resideInAPackage(RACINE + ".infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(RACINE + ".interfaces..", RACINE + ".configuration..");

    @ArchTest
    static final ArchRule les_adapters_entrants_ne_dependent_pas_de_l_infrastructure = noClasses()
            .that().resideInAPackage(RACINE + ".interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(RACINE + ".infrastructure..", RACINE + ".configuration..");

    /** PD6 (ADR 0106) : ni le domaine ni l'application ne lisent un autre contexte ; seules les ACL de l'infrastructure le font. */
    @ArchTest
    static final ArchRule seule_l_infrastructure_lit_les_autres_contextes = noClasses()
            .that().resideInAnyPackage(RACINE + ".domain..", RACINE + ".application..", RACINE + ".interfaces..")
            .should().dependOnClassesThat(
                    resideInAPackage("com.hemodialyse.backend.contexts..")
                            .and(not(resideInAPackage(RACINE + "..")))
                            .and(not(resideInAPackage("com.hemodialyse.backend.contexts.sharedkernel.."))));

    @ArchTest
    static final ArchRule aucun_import_du_legacy = noClasses().that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hemodialyse.backend.domain..", "com.hemodialyse.backend.application..",
                    "com.hemodialyse.backend.infrastructure..", "com.hemodialyse.backend.shared..");

    /**
     * Les Maven {@code contexts-seance}, {@code contexts-facturation} et {@code contexts-dossier-medical} mettent sur le classpath, par
     * transitivité, d'autres contextes : le Pilotage direction ne lit d'un autre contexte QUE son langage publié (tout autre paquet, y
     * compris un paquet futur, est refusé).
     */
    @ArchTest
    static final ArchRule les_autres_contextes_seulement_par_leur_langage_publie = noClasses()
            .that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat(
                    resideInAPackage("com.hemodialyse.backend.contexts..")
                            .and(not(resideInAPackage(RACINE + "..")))
                            .and(not(resideInAnyPackage("com.hemodialyse.backend.contexts.sharedkernel..",
                                    "com.hemodialyse.backend.contexts..publishedlanguage.."))));
}
