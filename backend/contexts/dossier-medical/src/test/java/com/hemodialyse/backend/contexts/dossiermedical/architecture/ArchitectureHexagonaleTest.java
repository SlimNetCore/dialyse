package com.hemodialyse.backend.contexts.dossiermedical.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.hemodialyse.backend.contexts.dossiermedical", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureHexagonaleTest {

    private static final String RACINE = "com.hemodialyse.backend.contexts.dossiermedical";

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

    @ArchTest
    static final ArchRule aucun_import_du_legacy = noClasses().that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hemodialyse.backend.domain..", "com.hemodialyse.backend.application..",
                    "com.hemodialyse.backend.infrastructure..", "com.hemodialyse.backend.shared..");

    @ArchTest
    static final ArchRule les_autres_contextes_seulement_par_leur_langage_publie = noClasses()
            .that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hemodialyse.backend.contexts.seance.domain..", "com.hemodialyse.backend.contexts.seance.application..",
                    "com.hemodialyse.backend.contexts.seance.infrastructure..", "com.hemodialyse.backend.contexts.seance.interfaces..",
                    "com.hemodialyse.backend.contexts.seance.configuration..",
                    "com.hemodialyse.backend.contexts.patientcouverture.domain..",
                    "com.hemodialyse.backend.contexts.patientcouverture.application..",
                    "com.hemodialyse.backend.contexts.patientcouverture.infrastructure..",
                    "com.hemodialyse.backend.contexts.patientcouverture.interfaces..",
                    "com.hemodialyse.backend.contexts.patientcouverture.configuration..",
                    "com.hemodialyse.backend.contexts.planification.domain..",
                    "com.hemodialyse.backend.contexts.planification.application..",
                    "com.hemodialyse.backend.contexts.planification.infrastructure..",
                    "com.hemodialyse.backend.contexts.planification.interfaces..",
                    "com.hemodialyse.backend.contexts.planification.configuration..",
                    "com.hemodialyse.backend.contexts.organisation.domain..",
                    "com.hemodialyse.backend.contexts.organisation.application..",
                    "com.hemodialyse.backend.contexts.organisation.infrastructure..",
                    "com.hemodialyse.backend.contexts.organisation.interfaces..",
                    "com.hemodialyse.backend.contexts.organisation.configuration..",
                    "com.hemodialyse.backend.contexts.stock.domain..", "com.hemodialyse.backend.contexts.stock.application..",
                    "com.hemodialyse.backend.contexts.stock.infrastructure..", "com.hemodialyse.backend.contexts.stock.interfaces..",
                    "com.hemodialyse.backend.contexts.stock.configuration..",
                    "com.hemodialyse.backend.contexts.notifications.domain..",
                    "com.hemodialyse.backend.contexts.notifications.application..",
                    "com.hemodialyse.backend.contexts.notifications.infrastructure..",
                    "com.hemodialyse.backend.contexts.notifications.interfaces..",
                    "com.hemodialyse.backend.contexts.notifications.configuration..");

    /**
     * Revue SE7d m4 : la dépendance Maven {@code contexts-seance} met sur le classpath, par transitivité, les Absences, la Facturation et la
     * Comptabilité. Le code de production du Dossier médical ne lit de la Séance que son langage publié (tout autre paquet, y compris un
     * paquet futur, est refusé) et ne dépend d'aucun paquet de ces trois contextes. Les tests sont exclus (le contrat
     * {@code HistoriqueDesConstantesJdbcContrat} joue le vrai adapter de la Séance).
     */
    @ArchTest
    static final ArchRule la_seance_seulement_par_son_langage_publie_ni_absences_ni_facturation_ni_comptabilite = noClasses()
            .that().resideInAPackage(RACINE + "..")
            .should().dependOnClassesThat(
                    resideInAPackage("com.hemodialyse.backend.contexts.seance..")
                            .and(not(resideInAPackage("com.hemodialyse.backend.contexts.seance.publishedlanguage..")))
                            .or(resideInAnyPackage(
                                    "com.hemodialyse.backend.contexts.absencespatients..",
                                    "com.hemodialyse.backend.contexts.facturation..")
                                    // S-DDL2 (ADR 0185) : la Comptabilité n'est lisible que par son langage publié (numérotation).
                                    .or(resideInAPackage("com.hemodialyse.backend.contexts.comptabilite..")
                                            .and(not(resideInAPackage("com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.."))))));
}
