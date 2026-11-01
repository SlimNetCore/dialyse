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
     * Fuseau unique du serveur : UTC ({@code BackendApplication}, Hibernate, Jackson). Aucune horloge ni fuseau « par défaut du
     * système » dans le code de production : {@code Clock.systemUTC()} et {@code ZoneOffset.UTC} sont explicites.
     */
    @ArchTest
    static final ArchRule aucune_horloge_ni_fuseau_du_systeme = noClasses()
            .should().callMethod(java.time.Clock.class, "systemDefaultZone")
            .orShould().callMethod(java.time.ZoneId.class, "systemDefault")
            .because("tous les horodatages et les jours métier du serveur sont en UTC");

    private static final String STOCK = "com.hemodialyse.backend.contexts.stock";

    /**
     * Slice S8 (ADR 0046) : les modules {@code bc-stock} et {@code bc-article} sont retirés ; aucune classe du dépôt (hors du
     * contexte, qui n'en a pas besoin) ne doit réintroduire les paquets legacy {@code domain.stock} ni {@code domain.article}.
     * Cette règle remplace le verrou « la Séance n'appelle plus {@code BonSortieUseCase} » (classe supprimée, ADR 0045).
     */
    @ArchTest
    static final ArchRule plus_aucune_classe_ne_depend_des_paquets_legacy_du_stock_ni_des_articles = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hemodialyse.backend.domain.stock..", "com.hemodialyse.backend.domain.article..")
            .allowEmptyShould(true)
            .because("bc-stock et bc-article sont retirés depuis S8 : le Stock vit dans contexts/stock");

    /**
     * Slice S8 : la Séance ne connaît du Stock que son langage publié ({@code ConsommationsDeSeance}, {@code CatalogueDArticles}).
     */
    @ArchTest
    static final ArchRule la_seance_n_utilise_le_stock_que_par_son_langage_publie = noClasses()
            .that().resideInAnyPackage("com.hemodialyse.backend.domain.seance..", "com.hemodialyse.backend.application.seance..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    STOCK + ".domain..", STOCK + ".application..", STOCK + ".infrastructure..", STOCK + ".interfaces..",
                    STOCK + ".configuration..")
            .because("la Séance passe par les ports publiés du Stock (ADR 0045, 0046)");


    /**
     * Slices A1 à A4 : le legacy n'utilise le contexte Absences de patients que par son langage publié
     * ({@code AbsencesDuPilotage} pour le pilotage, A4 ; {@code SignalementDesSaisies} et {@code NotificationsDuCentre}, fournis par
     * la plateforme), jamais par ses couches internes ; le module est câblé par sa configuration, scannée par Spring. Aucun
     * exemption : le legacy n'a plus de code d'absence de patient (A5 soldé, voir {@code TableDesAbsencesDePatientsTest}).
     */
    @ArchTest
    static final ArchRule le_legacy_n_utilise_pas_le_contexte_des_absences_de_patients = noClasses()
            .that().resideOutsideOfPackage(ABSENCES + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ABSENCES + ".domain..", ABSENCES + ".application..", ABSENCES + ".infrastructure..",
                    ABSENCES + ".interfaces..", ABSENCES + ".configuration..")
            .because("les Absences de patients ne s'utiliseront que par leur langage publié (A2, A4)");

    private static final String PLANIFICATION = "com.hemodialyse.backend.contexts.planification";

    private static final String SEANCE = "com.hemodialyse.backend.contexts.seance";

    /**
     * Contexte Planification (ADR 0049 à 0054) : le legacy (fiche patient, sérologie, Séance, Absences, Direction,
     * administration des utilisateurs) ne l'utilise que par son langage publié ({@code PlacementDesPatients},
     * {@code LiberationDesPlaces}, {@code ReaffectationEnIsolement}, {@code PlacesDesPatients}, {@code CapaciteDesCentres},
     * {@code FichesDesInfirmiers}). Règle vide tant que le module est un squelette ; la règle « pas de SQL sur les tables de la
     * Planification hors du contexte » s'ajoute à P9.
     */
    @ArchTest
    static final ArchRule le_legacy_n_utilise_la_planification_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(PLANIFICATION + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    PLANIFICATION + ".domain..", PLANIFICATION + ".application..", PLANIFICATION + ".infrastructure..",
                    PLANIFICATION + ".interfaces..", PLANIFICATION + ".configuration..")
            .allowEmptyShould(true)
            .because("la Planification ne s'utilise que par son langage publié (ADR 0053)");

    /**
     * Contexte Seance (ADR 0062) : le legacy ne l'utilise que par son langage publie. Regle vide tant que le module
     * est un squelette.
     */
    @ArchTest
    static final ArchRule le_legacy_n_utilise_la_seance_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(SEANCE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    SEANCE + ".domain..", SEANCE + ".application..", SEANCE + ".infrastructure..",
                    SEANCE + ".interfaces..", SEANCE + ".configuration..")
            .allowEmptyShould(true)
            .because("la Seance ne s'utilise que par son langage publie (ADR 0062)");

    private static final String ORGANISATION = "com.hemodialyse.backend.contexts.organisation";

    @ArchTest
    static final ArchRule le_legacy_n_utilise_l_organisation_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(ORGANISATION + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ORGANISATION + ".domain..", ORGANISATION + ".application..", ORGANISATION + ".infrastructure..",
                    ORGANISATION + ".interfaces..", ORGANISATION + ".configuration..")
            .allowEmptyShould(true)
            .because("l'Organisation ne s'utilise que par son langage publie (ADR 0063)");

    private static final String PATIENT_COUVERTURE = "com.hemodialyse.backend.contexts.patientcouverture";

    @ArchTest
    static final ArchRule le_legacy_n_utilise_le_patient_couverture_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(PATIENT_COUVERTURE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    PATIENT_COUVERTURE + ".domain..", PATIENT_COUVERTURE + ".application..", PATIENT_COUVERTURE + ".infrastructure..",
                    PATIENT_COUVERTURE + ".interfaces..", PATIENT_COUVERTURE + ".configuration..")
            .allowEmptyShould(true)
            .because("le Patient & couverture ne s'utilise que par son langage publie (ADR 0004, 0064)");

    private static final String DOSSIER_MEDICAL = "com.hemodialyse.backend.contexts.dossiermedical";

    @ArchTest
    static final ArchRule le_legacy_n_utilise_le_dossier_medical_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(DOSSIER_MEDICAL + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    DOSSIER_MEDICAL + ".domain..", DOSSIER_MEDICAL + ".application..", DOSSIER_MEDICAL + ".infrastructure..",
                    DOSSIER_MEDICAL + ".interfaces..", DOSSIER_MEDICAL + ".configuration..")
            .allowEmptyShould(true)
            .because("le Dossier medical ne s'utilise que par son langage publie (ADR 0007, 0065)");

    private static final String PILOTAGE_DIRECTION = "com.hemodialyse.backend.contexts.pilotagedirection";

    @ArchTest
    static final ArchRule le_legacy_n_utilise_le_pilotage_direction_que_par_son_langage_publie = noClasses()
            .that().resideOutsideOfPackage(PILOTAGE_DIRECTION + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    PILOTAGE_DIRECTION + ".domain..", PILOTAGE_DIRECTION + ".application..", PILOTAGE_DIRECTION + ".infrastructure..",
                    PILOTAGE_DIRECTION + ".interfaces..", PILOTAGE_DIRECTION + ".configuration..")
            .allowEmptyShould(true)
            .because("le Pilotage direction ne s'utilise que par son langage publie (ADR 0009, 0066)");
}
