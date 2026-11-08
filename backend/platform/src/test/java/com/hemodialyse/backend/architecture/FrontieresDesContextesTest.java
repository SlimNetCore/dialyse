package com.hemodialyse.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    private static final String PORTS_LEGACY_GREFFE = "com.hemodialyse.backend.domain.medical.greffe.port.";
    private static final java.util.Set<String> PORTS_RESIDUELS_DU_BILAN_PRE_GREFFE = java.util.Set.of(
            PORTS_LEGACY_GREFFE + "BilanPreGreffeRepositoryPort", PORTS_LEGACY_GREFFE + "EtapeBilanPreGreffeRepositoryPort",
            PORTS_LEGACY_GREFFE + "DonneurVivantRepositoryPort");
    private static final java.util.Set<String> ECRITURES_RESIDUELLES = java.util.Set.of("save", "deleteById", "findById");

    /**
     * Slice DM7 (ADR 0086) : les écritures résiduelles des ports legacy du bilan pré-greffe ({@code save}, {@code deleteById},
     * {@code findById}) n'ont plus d'appelant ; le module {@code contexts/dossier-medical} est seul propriétaire de l'écriture. Aucun
     * nouvel appelant ne doit apparaître avant leur retrait (revue au plus tard le 2026-12-15) ; seules les lectures
     * ({@code findByPatientId}) restent permises pour KDIGO, le PDF et les statistiques.
     */
    @ArchTest
    static final ArchRule plus_aucun_appelant_des_ecritures_legacy_du_bilan_pre_greffe = noClasses()
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "cible une écriture résiduelle des ports legacy du bilan pré-greffe") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    return PORTS_RESIDUELS_DU_BILAN_PRE_GREFFE.contains(appel.getTargetOwner().getName())
                            && ECRITURES_RESIDUELLES.contains(appel.getName());
                }
            })
            .because("l'écriture du bilan pré-greffe vit dans contexts/dossier-medical (ADR 0086) ; ces méthodes seront retirées");

    /**
     * Slice DM7 (ADR 0086) : la matrice de transition legacy n'est plus utilisée que par l'agrégat legacy lui-même (écriture
     * morte) ; aucune autre classe ne doit la réutiliser, la machine à états vit dans {@code StatutDuBilanPreGreffe}.
     */
    @ArchTest
    static final ArchRule la_transition_legacy_du_bilan_n_a_pas_d_autre_utilisateur = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.domain.medical.greffe.specification.TransitionStatutBilanGreffeSpecification")
            .should().onlyHaveDependentClassesThat().haveFullyQualifiedName(
                    "com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe")
            .allowEmptyShould(true)
            .because("la machine à états du bilan vit dans contexts/dossier-medical (ADR 0086)");

    private static final java.util.Set<String> PORTS_RESIDUELS_DU_DOSSIER_MEDICAL = java.util.Set.of(
            "com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort",
            "com.hemodialyse.backend.domain.medical.serologie.port.SerologieRepositoryPort",
            "com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort");
    private static final java.util.Set<String> METHODES_RESIDUELLES_DU_DOSSIER_MEDICAL = java.util.Set.of(
            "save", "deleteById", "findById", "findPagedByPatientId");

    /**
     * Slice DM8 (ADR 0087) : les trois ports legacy qui restent lus par KDIGO et le PDF de greffe (antécédents, sérologies, résultats
     * d'analyse) n'ont plus d'appelant d'écriture, de lecture unitaire ni de lecture paginée, que ce soit par le port ou par le type de ses
     * adapters ; seule {@code findByPatientId} reste permise. Aucun nouvel appelant avant leur retrait (revue au plus tard le 2026-12-15).
     */
    @ArchTest
    static final ArchRule plus_aucun_appelant_des_ecritures_legacy_du_dossier_medical = noClasses()
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "cible une méthode résiduelle des ports legacy du dossier médical") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    // Le port ET ses adapters : un appel par le type de l'adapter ou d'une sous-classe est aussi refusé.
                    return PORTS_RESIDUELS_DU_DOSSIER_MEDICAL.stream().anyMatch(port -> appel.getTargetOwner().isAssignableTo(port))
                            && METHODES_RESIDUELLES_DU_DOSSIER_MEDICAL.contains(appel.getName());
                }
            })
            .because("l'écriture et la lecture du dossier médical vivent dans contexts/dossier-medical (ADR 0087) ; ces méthodes seront retirées");

    private static final java.util.Set<String> METHODES_RESIDUELLES_DE_LA_CREATION_DE_SEANCE = java.util.Set.of(
            "create", "createFromQr", "scanAndValidate");
    private static final java.util.Set<String> CLASSES_PORTEUSES_DE_LA_CREATION_DE_SEANCE = java.util.Set.of(
            "com.hemodialyse.backend.application.seance.SeanceApplicationService",
            "com.hemodialyse.backend.domain.seance.service.SeanceDomainService");

    /**
     * Slice SE1 (ADR 0088) : la création, le scan et la consultation des séances vivent dans {@code contexts/seance}. Les méthodes
     * résiduelles {@code create}, {@code createFromQr} et {@code scanAndValidate} de {@code SeanceUseCase} n'ont plus d'autre appelant
     * que leur propre chaîne de délégation (service applicatif et service de domaine) ; aucun nouvel appelant avant leur retrait
     * (revue au plus tard le 2026-12-15).
     */
    @ArchTest
    static final ArchRule plus_aucun_appelant_des_ecritures_legacy_de_la_creation_de_seance = noClasses()
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "cible une méthode résiduelle de SeanceUseCase depuis hors de sa chaîne de délégation") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    return appel.getTargetOwner().isAssignableTo("com.hemodialyse.backend.domain.seance.port.SeanceUseCase")
                            && METHODES_RESIDUELLES_DE_LA_CREATION_DE_SEANCE.contains(appel.getName())
                            && !CLASSES_PORTEUSES_DE_LA_CREATION_DE_SEANCE.contains(appel.getOriginOwner().getName());
                }
            })
            .because("la création, le scan et la consultation des séances vivent dans contexts/seance (ADR 0088) ; ces méthodes seront retirées");

    private static final java.util.Set<String> METHODES_RESIDUELLES_DE_LA_VALIDATION_DE_SEANCE = java.util.Set.of(
            "validerParInfirmier", "signerParMedecin", "deverrouillerPourRegularisation", "memoriserPlace");
    private static final java.util.Set<String> CLASSES_PORTEUSES_DE_LA_VALIDATION_DE_SEANCE = java.util.Set.of(
            "com.hemodialyse.backend.domain.seance.model.Seance",
            "com.hemodialyse.backend.domain.seance.service.SeanceDomainService");

    /**
     * Slice SE2 (ADR 0089) : la validation, la signature et le déverrouillage des séances vivent dans {@code contexts/seance}
     * ({@code ValiderUneSeance}, {@code SignerUneSeance}, {@code DeverrouillerUneSeance}). {@code SeanceUseCase.validate},
     * {@code signByMedecin} et {@code unlockForRegularisation} sont retirés ; les quatre méthodes de l'entité legacy {@code Seance} qui
     * restent n'ont plus d'appelant de production hors d'elle-même et du scan résiduel de SE1 ({@code SeanceDomainService}) ; aucun
     * nouvel appelant avant leur retrait (revue au plus tard le 2026-12-15).
     */
    @ArchTest
    static final ArchRule plus_aucun_appelant_des_ecritures_legacy_de_la_validation_de_seance = noClasses()
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "cible une méthode résiduelle de l'entité Seance depuis hors de sa chaîne résiduelle") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    return appel.getTargetOwner().isAssignableTo("com.hemodialyse.backend.domain.seance.model.Seance")
                            && METHODES_RESIDUELLES_DE_LA_VALIDATION_DE_SEANCE.contains(appel.getName())
                            && !CLASSES_PORTEUSES_DE_LA_VALIDATION_DE_SEANCE.contains(appel.getOriginOwner().getName());
                }
            })
            .because("la validation, la signature et le déverrouillage des séances vivent dans contexts/seance (ADR 0089) ; ces méthodes seront retirées");

    /**
     * Slice SE3 (ADR 0090) : la déclaration, la modification et le retrait des consommables d'une séance, ainsi que les raccourcis,
     * vivent dans {@code contexts/seance}. Les handlers, les méthodes de {@code SeanceUseCase}/{@code SeanceDomainService} et la
     * famille {@code SeanceRaccourci*} sont supprimés (le compilateur interdit tout retour). Il ne reste aucun résidu legacy : le
     * legacy ne déclare ni ne fixe plus une consommation de séance (seul {@code contexts/seance} le fait, par l'ACL du Stock) ; la
     * lecture ({@code sortiesParDate}, {@code historique}) et l'annulation à la suppression restent permises.
     */
    @ArchTest
    static final ArchRule seul_contexts_seance_declare_ou_fixe_une_consommation_de_seance = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .and().resideOutsideOfPackage("com.hemodialyse.backend.contexts.stock..")
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "déclare ou fixe une consommation de séance du Stock") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    return appel.getTargetOwner().isAssignableTo("com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance")
                            && java.util.Set.of("ajouter", "fixer").contains(appel.getName());
                }
            })
            .because("la déclaration des consommables d'une séance vit dans contexts/seance (ADR 0090)");

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
     * Slice P9 (ADR 0069) : le paquet legacy {@code domain.infirmier} est retiré ; l'Infirmier vit dans contexts/planification.
     */
    @ArchTest
    static final ArchRule plus_aucune_classe_ne_depend_du_paquet_legacy_des_infirmiers = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("com.hemodialyse.backend.domain.infirmier..")
            .allowEmptyShould(true)
            .because("domain.infirmier est retiré depuis P9 : la présence et le roulement vivent dans contexts/planification");

    /**
     * Slice P9 : il ne reste du paquet legacy {@code domain.planning} (module bc-seance) que quatre classes de dette, consommées par
     * une liste fermée de sources (jalons dans {@code roadmap.md}, « Dette de P9 ») : {@code JourSemaine} (fiche patient),
     * {@code PlanningParametres} et son port (paramétrage lu par la fiche patient), {@code CapaciteTheoriqueCalculator} (tableau de
     * bord de la Direction). Toute nouvelle dépendance échoue.
     */
    @ArchTest
    static final ArchRule le_paquet_legacy_du_planning_n_a_que_les_consommateurs_de_la_dette_de_p9 = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.domain.planning..")
            .and().haveNameNotMatching(
                    ".*\\.(PlacementDeLaFiche|PlanningParametresDuContexte|PatientRestController|DirectionCapaciteQueryService)(\\$.*)?")
            .should().dependOnClassesThat().resideInAnyPackage("com.hemodialyse.backend.domain.planning..")
            .allowEmptyShould(true)
            .because("domain.planning est en voie de retrait (P9) : liste fermée des consommateurs restants");

    /**
     * Contexte Seance (ADR 0062, 0088) : le legacy ne l'utilise que par son langage publie. Les ports fournis par la plateforme
     * sont loges dans son langage publie (ADR 0088 point 9) : AUCUNE exception n'est ouverte ici.
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
