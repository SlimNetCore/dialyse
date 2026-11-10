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

    /**
     * DM9 (ADR 0101) : le module Maven {@code bc-medical} est retiré ; aucune classe du dépôt ne doit réintroduire ses paquets legacy
     * {@code domain.medical} (agrégats, ports, politiques KDIGO, objets-valeurs). Remplace les verrous de DM7 (ADR 0086) et DM8 (ADR 0087)
     * sur les méthodes résiduelles des ports legacy du bilan pré-greffe, des antécédents et des sérologies (classes disparues : le compilateur
     * interdit tout retour). Le Dossier médical vit dans {@code contexts/dossier-medical}.
     */
    @ArchTest
    static final ArchRule plus_aucune_classe_ne_depend_des_paquets_legacy_du_dossier_medical = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.hemodialyse.backend.domain.medical..")
            .allowEmptyShould(true)
            .because("bc-medical est retiré depuis DM9 (ADR 0101) : le Dossier médical vit dans contexts/dossier-medical");

    /** DM9 (ADR 0101) : le contrôleur legacy de l'évaluation KDIGO ne doit pas réapparaître (une seule route, celle du module). */
    @ArchTest
    static final ArchRule le_controleur_legacy_de_l_evaluation_kdigo_a_disparu = noClasses()
            .should().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.web.rest.KdigoGreffeRestController")
            .orShould().haveFullyQualifiedName("com.hemodialyse.backend.application.query.KdigoGreffeQueryService")
            .because("GET /api/v1/patients/{id}/greffe/kdigo est servi par contexts/dossier-medical (EvaluationKdigoControleur, DM9, ADR 0101)");

    /**
     * DM9 (ADR 0101) : la plateforme lit le dossier de greffe, les statistiques, les indicateurs cliniques et l'anémie par les langages
     * publiés du Dossier médical ; aucune de ces classes ne construit plus de requête SQL ({@code JdbcTemplate}).
     */
    @ArchTest
    static final ArchRule les_lecteurs_medicaux_de_la_plateforme_passent_par_les_langages_publies_du_dossier_medical = noClasses()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.application.query.GreffePdfService")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.application.query.PatientStatsQueryService")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .because("le calcul médical vit dans contexts/dossier-medical (DossierDeGreffePublie, StatistiquesMedicalesPubliees, "
                    + "IndicateursCliniquesPublies, ADR 0101) ; la plateforme ne garde que la présentation");

    /**
     * SE7f (ADR 0100) : le module Maven {@code bc-seance} est retiré ; aucune classe du dépôt ne doit réintroduire ses paquets legacy
     * {@code domain.seance} (agrégat, ports, services) et {@code domain.planning} (paramétrage, capacité théorique), ni le service
     * applicatif {@code application.seance} de la plateforme. Remplace les verrous de SE1, SE2, SE5 et SE6 sur les méthodes résiduelles de
     * {@code SeanceUseCase}, {@code Seance} et {@code SeanceRepositoryPort} (classes disparues : le compilateur interdit tout retour).
     */
    @ArchTest
    static final ArchRule plus_aucune_classe_ne_depend_des_paquets_legacy_de_la_seance_ni_du_planning = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("com.hemodialyse.backend.domain.seance..",
                    "com.hemodialyse.backend.domain.planning..", "com.hemodialyse.backend.application.seance..")
            .allowEmptyShould(true)
            .because("bc-seance est retiré depuis SE7f (ADR 0100) : la Séance vit dans contexts/seance, la capacité théorique dans contexts/planification");

    /** SE7f (ADR 0100) : le contrôleur legacy du volet paramédical ne doit pas réapparaître (une seule route, celle du module Séance). */
    @ArchTest
    static final ArchRule le_controleur_legacy_du_volet_paramedical_a_disparu = noClasses()
            .should().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.web.rest.VoletParamedicalRestController")
            .because("PUT /api/v1/seances/{seanceId}/volet-paramedical est servi par contexts/seance (VoletParamedicalControleur, SE7f, ADR 0100)");

    /** SE7f (ADR 0100) : une seule entrée pour la saisie du volet paramédical, le contrôleur du module. */
    @ArchTest
    static final ArchRule seul_contexts_seance_renseigne_le_volet_paramedical = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletParamedical")
            .because("la saisie du volet paramédical vit dans contexts/seance (ADR 0100)");

    /**
     * SE7f (ADR 0100) puis DM9 (ADR 0101) : l'évaluation KDIGO du candidat (désormais dans le module) lit la dernière créatinine du patient
     * par le port publié {@code DerniereCreatininePubliee} (KB-SEA-96 reproduit), plus par un port de {@code bc-seance}.
     */
    @ArchTest
    static final ArchRule kdigo_lit_la_derniere_creatinine_par_le_langage_publie_du_dossier_medical = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.contexts.dossiermedical.application.EvaluerLeCandidatALaGreffe")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee")
            .because("la dernière créatinine vient de dossier-medical.v1 (ADR 0100, SE7f ; ADR 0101, DM9), plus de bc-seance");

    /** SE7f (ADR 0100) : la Direction calcule la capacité théorique par le langage publié de la Planification. */
    @ArchTest
    static final ArchRule la_direction_calcule_la_capacite_theorique_par_le_langage_publie_de_la_planification = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.application.direction.DirectionCapaciteQueryService")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteTheoriqueDesCentres")
            .because("la méthode de la capacité théorique appartient à la Planification (ADR 0100, SE7f), plus à bc-seance");

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

    /**
     * SE4 (ADR 0091) : le rappel quotidien des séances à régulariser vit dans {@code contexts/seance} (cas d'usage, planificateur de
     * 07:00 unique). Le service et le scheduler legacy sont supprimés (le compilateur interdit tout retour) ; seul le fournisseur du
     * langage publié {@code RappelsFournis} (plateforme, ACL) appelle encore {@code NotificationService.notifySeancesARegulariser}, et
     * aucune classe hors du contexte ne dépend du cas d'usage : un second déclencheur provoquerait un double rappel.
     */
    @ArchTest
    static final ArchRule seul_le_fournisseur_du_langage_publie_emet_le_rappel_de_regularisation = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.infrastructure.acl.seance..")
            .and().doNotHaveFullyQualifiedName("com.hemodialyse.backend.application.notification.NotificationService")
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "émet le rappel de régularisation (SEANCES_A_REGULARISER)") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    return appel.getTargetOwner().isAssignableTo("com.hemodialyse.backend.application.notification.NotificationService")
                            && appel.getName().equals("notifySeancesARegulariser");
                }
            })
            .because("le rappel vit dans contexts/seance ; l'identifiant déterministe évite le doublon (KB-SEA-35)");

    @ArchTest
    static final ArchRule seul_contexts_seance_declenche_le_rappel_de_regularisation = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.RappelerLesSeancesARegulariser")
            .because("un seul planificateur actif : celui du module (cron 07:00), pas de double déclenchement (ADR 0091)");

    /**
     * SE5 (ADR 0092) : l'imposition d'un forfait à une séance vit dans {@code contexts/seance} (cas d'usage {@code ImposerUnForfait}, route
     * PUT {@code /api/v1/seances/{id}/forfait}). Le handler, {@code SeanceUseCase.updateForfait}, {@code Seance.modifierForfait} et le
     * catalogue legacy sont supprimés (le compilateur interdit tout retour) ; aucune classe hors du contexte ne dépend du cas d'usage.
     * Les écrivants restants de {@code forfait_override_*} sont la Facturation ({@code poserLeForfait}, KB-SEA-47, hors périmètre) et
     * l'adaptateur de persistance legacy de la séance (mapping de la ligne ; {@code updateDate}, qui la réécrivait, est retiré avec SE6).
     */
    @ArchTest
    static final ArchRule seul_contexts_seance_impose_un_forfait_a_une_seance = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ImposerUnForfait")
            .because("une seule entrée pour la surcharge du forfait d'une séance : le contrôleur du module (ADR 0092)");

    /**
     * SE6 (ADR 0093) : le changement de jour et la suppression d'une séance vivent dans {@code contexts/seance} (PUT et DELETE
     * {@code /api/v1/seances/{id}}). Les handlers, {@code SeanceUseCase.updateDate}, {@code Seance.verifierSuppressible},
     * {@code SuppressionSeanceService}, son journal et {@code SeanceRepositoryPort.delete} sont supprimés (le compilateur interdit tout
     * retour) ; aucune classe hors du contexte ne dépend de ces cas d'usage.
     */
    @ArchTest
    static final ArchRule seul_contexts_seance_change_le_jour_et_supprime_une_seance = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ChangerLeJourDUneSeance")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.SupprimerUneSeance")
            .because("une seule entrée pour le changement de jour et pour la suppression : le contrôleur du module (ADR 0093)");

    /**
     * SE7a (ADR 0094) : le journal du jour, le calendrier du centre (consultation, ajout, retrait), le volet médical et les séances récentes
     * vivent dans {@code contexts/seance}. Les handlers, {@code VoletMedicalUseCase}, {@code VoletMedicalDomainService},
     * {@code SeanceUseCase.recentByPatient} et {@code SeanceRepositoryPort.findRecentByPatient} sont supprimés (le compilateur interdit
     * tout retour) ; aucune classe hors du contexte ne dépend de ces cas d'usage, sans exception.
     */
    @ArchTest
    static final ArchRule seul_contexts_seance_sert_les_routes_residuelles = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ConsulterLeJournalDuJour")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ConsulterLeCalendrier")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.AjouterUnJourAuCalendrier")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.RetirerUnJourDuCalendrier")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletMedical")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ConsulterLesSeancesRecentes")
            .because("une seule entrée pour le journal, le calendrier, le volet médical et les séances récentes : le contrôleur du module (ADR 0094)");

    /**
     * SE7b (ADR 0095) : le tableau de bord mensuel des séances (compteurs, détail nominatif, exports) n'a qu'une entrée, le contrôleur du
     * module. Le détail nominatif est une donnée de santé ouverte à la secrétaire (KB-SEA-66) : aucune autre classe hors du contexte ne
     * doit le relire, sans exception.
     */
    @ArchTest
    static final ArchRule seul_contexts_seance_sert_le_tableau_de_bord_mensuel = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.seance..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ConsulterLeTableauDeBord")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ConsulterLeDetailDuTableauDeBord")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.application.ExporterLeTableauDeBord")
            .because("une seule entrée pour le tableau de bord, son détail nominatif et ses exports : le contrôleur du module (ADR 0095)");

    /** SE7b : le contrôleur historique de la Séance ne porte plus aucune route ; il ne doit pas réapparaître. */
    @ArchTest
    static final ArchRule le_controleur_historique_de_la_seance_a_disparu = noClasses()
            .should().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.web.rest.SeanceRestController")
            .because("SeanceRestController est supprimé (SE7b, ADR 0095) : ses routes sont dans contexts/seance (le PUT du volet paramédical l'a rejoint en SE7f)");

    /**
     * SE6 (revue, KB-SEA-50) : les surcharges courtes de {@code Seance.restituer} (sans {@code FactureId}) posent la facture à nul ; un
     * adaptateur de production qui les emploierait rendrait la garde de suppression inopérante EN SILENCE. Le code de production
     * (tests exclus) n'appelle donc que la restitution complète, à onze paramètres, où la facture est un argument explicite.
     */
    @ArchTest
    static final ArchRule le_code_de_production_restitue_une_seance_avec_sa_facture = noClasses()
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "restitue une séance sans passer sa facture (surcharge courte de Seance.restituer)") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    return appel.getTargetOwner().getFullName().equals("com.hemodialyse.backend.contexts.seance.domain.Seance")
                            && appel.getName().equals("restituer")
                            && appel.getTarget().getRawParameterTypes().size() < 11;
                }
            })
            .because("une surcharge sans FactureId ferait d'une séance facturée une séance libre : la garde KB-SEA-50 s'ouvrirait en silence");

    /**
     * Revue SE3 : les deux autres écritures de la même consommation ont chacune un seul appelant légitime. {@code enregistrerALaValidation}
     * appartient à {@code contexts/seance} (validation, SE2) ; {@code administrer} (EPO, fer) appartient à {@code contexts/dossier-medical}.
     * Aucune autre classe, legacy comprise, ne les appelle ; le Stock les implémente.
     */
    @ArchTest
    static final ArchRule les_autres_ecritures_de_consommation_de_seance_ont_un_seul_appelant_legitime = noClasses()
            .that().resideOutsideOfPackage("com.hemodialyse.backend.contexts.stock..")
            .should().callMethodWhere(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaMethodCall>(
                    "appelle administrer ou enregistrerALaValidation du Stock depuis un contexte non propriétaire") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaMethodCall appel) {
                    if (!appel.getTargetOwner().isAssignableTo("com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance")) {
                        return false;
                    }
                    String origine = appel.getOriginOwner().getPackageName();
                    return switch (appel.getName()) {
                        case "enregistrerALaValidation" -> !origine.startsWith("com.hemodialyse.backend.contexts.seance");
                        case "administrer" -> !origine.startsWith("com.hemodialyse.backend.contexts.dossiermedical");
                        default -> false;
                    };
                }
            })
            .because("enregistrerALaValidation appartient à contexts/seance et administrer à contexts/dossier-medical (revue SE3)");

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
     * Slice S8 (puis SE7f, ADR 0100 : {@code domain.seance} et {@code application.seance} du legacy sont retirés) : la Séance ne connaît
     * du Stock que son langage publié ({@code ConsommationsDeSeance}, {@code CatalogueDArticles}).
     */
    @ArchTest
    static final ArchRule la_seance_n_utilise_le_stock_que_par_son_langage_publie = noClasses()
            .that().resideInAPackage("com.hemodialyse.backend.contexts.seance..")
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

    /**
     * SE7c1 (ADR 0096) : le Stock lit la facturation et la place d'une séance par le langage publié de la Séance, dans l'ACL de la
     * plateforme. Le module Seance dépend du module Stock : le Stock ne dépend d'AUCUNE classe de la Séance, sans exception.
     */
    @ArchTest
    static final ArchRule le_stock_ne_depend_pas_de_la_seance = noClasses()
            .that().resideInAPackage(STOCK + "..")
            .should().dependOnClassesThat().resideInAPackage(SEANCE + "..")
            .allowEmptyShould(true)
            .because("le Stock ne lit plus la table seances : l'ACL de la plateforme appelle seance.v1 (ADR 0096)");

    /**
     * SE7c2 (ADR 0097, ARGENT) : la Facturation lit et écrit les séances par le langage publié de la Séance, dans l'ACL de la plateforme
     * (cycle Maven Séance - Absences-patients - Facturation). Elle ne dépend d'AUCUNE classe de la Séance, sans exception, et ne contient plus
     * de SQL sur la table des séances ({@code TableDesSeancesTest}).
     */
    @ArchTest
    static final ArchRule la_facturation_ne_depend_pas_de_la_seance = noClasses()
            .that().resideInAPackage(FACTURATION + "..")
            .should().dependOnClassesThat().resideInAPackage(SEANCE + "..")
            .allowEmptyShould(true)
            .because("la Facturation ne lit plus la table seances : l'ACL de la plateforme appelle seance.v1 (ADR 0097)");

    /**
     * SE7d (ADR 0098) : les Absences de patients lisent les séances réalisées et le lien d'exclusion par le langage publié de la Séance, dans
     * l'ACL de la plateforme (cycle Maven : la Séance dépend des Absences). Elles ne dépendent d'AUCUNE classe de la Séance, sans exception.
     */
    @ArchTest
    static final ArchRule les_absences_de_patients_ne_dependent_pas_de_la_seance = noClasses()
            .that().resideInAPackage(ABSENCES + "..")
            .should().dependOnClassesThat().resideInAPackage(SEANCE + "..")
            .allowEmptyShould(true)
            .because("les Absences ne lisent plus la table seances : l'ACL de la plateforme appelle seance.v1 (ADR 0098)");

    /**
     * SE7d (ADR 0098) : la Planification lit les séances réalisées avec leur place par le langage publié de la Séance, dans l'ACL de la
     * plateforme (cycle Maven : la Séance dépend de Patient-couverture et des Absences, qui dépendent de la Planification). Elle ne dépend
     * d'AUCUNE classe de la Séance, sans exception.
     */
    @ArchTest
    static final ArchRule la_planification_ne_depend_pas_de_la_seance = noClasses()
            .that().resideInAPackage(PLANIFICATION + "..")
            .should().dependOnClassesThat().resideInAPackage(SEANCE + "..")
            .allowEmptyShould(true)
            .because("la Planification ne lit plus la table seances : l'ACL de la plateforme appelle seance.v1 (ADR 0098)");

    /**
     * SE7d (ADR 0098) : le Dossier médical dépend directement du module Séance (aucun cycle) mais n'en connaît que le langage publié
     * ({@code ConstantesDesSeancesPubliees}), jamais le domaine, l'application, l'infrastructure, les interfaces ni la configuration.
     */
    @ArchTest
    static final ArchRule le_dossier_medical_ne_connait_de_la_seance_que_son_langage_publie = noClasses()
            .that().resideInAPackage("com.hemodialyse.backend.contexts.dossiermedical..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    SEANCE + ".domain..", SEANCE + ".application..", SEANCE + ".infrastructure..", SEANCE + ".interfaces..",
                    SEANCE + ".configuration..")
            .allowEmptyShould(true)
            .because("le Dossier médical lit les constantes par seance.v1 (ADR 0098), sans SQL sur la table seances");

    /**
     * SE7e (ADR 0099) : les derniers lecteurs Java de la plateforme (pilotage de la Direction, statistiques du patient, cahier de dialyse,
     * reprise de données, référentiel) lisent les séances par le langage publié de la Séance. Chacun dépend de SON port publié (la
     * plateforme dépend directement de {@code contexts-seance} : aucun cycle Maven) ; {@code TableDesSeancesTest} vérifie qu'ils n'ont
     * plus de SQL ni de nom de table calculé sur {@code seances}.
     */
    private static final String PORTS_SE7E = SEANCE + ".publishedlanguage.";

    @ArchTest
    static final ArchRule le_pilotage_de_la_direction_lit_les_seances_realisees_par_le_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.application.direction.DirectionAbsencesQueryService")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.seance.SeancesRealiseesParLaSeance")
            .should().dependOnClassesThat().haveFullyQualifiedName(PORTS_SE7E + "SeancesRealiseesDesCentresPubliees")
            .because("le pilotage compte les séances réalisées par seance.v1 (ADR 0099, SE7e) ; depuis PD1 (ADR 0102) le tableau de bord le fait dans le module");

    /**
     * PD1 et PD4 (ADR 0102) : le Pilotage direction ne lit d'un autre contexte que son langage publié (la règle équivalente du module
     * est dans son {@code ArchitectureHexagonaleTest}) ; la plateforme ne lit du Pilotage que {@code publishedlanguage} (règle existante
     * {@code le_legacy_n_utilise_le_pilotage_direction_que_par_son_langage_publie}).
     */
    @ArchTest
    static final ArchRule le_tableau_de_bord_et_le_rapport_sont_servis_par_le_pilotage_direction_via_son_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.web.rest.DirectionDashboardRestController")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.application.direction.DirectionReportPdfService")
            .should().dependOnClassesThat().resideInAPackage("com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage..")
            .because("le contrôleur et le rendu PDF consomment le Pilotage direction par son langage publié (PD1, PD4, ADR 0102)");

    /**
     * PD3 (ADR 0104) : les alertes et le temps réel de la direction sont repris par le Pilotage direction. Les signaleurs de la plateforme
     * (notifications, stock, inventaire, suivi des abonnements STOMP) et les indicateurs (qui appellent la politique d'alertes) ne le
     * joignent que par son langage publié ; le diffuseur STOMP est l'adaptateur de sortie du port fourni.
     */
    @ArchTest
    static final ArchRule les_alertes_et_le_temps_reel_de_la_direction_passent_par_le_langage_publie_du_pilotage = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.application.notification.NotificationService")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.websocket.WebSocketStockEventPublisher")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.websocket.WebSocketInventaireEventPublisher")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.websocket.DirectionSubscriptionTracker")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.websocket.WebSocketDirectionPublisher")
            .should().dependOnClassesThat().resideInAPackage("com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage..")
            .because("la politique d'alertes, l'historique et le temps réel vivent dans le Pilotage direction (PD3, ADR 0104)");

    /**
     * PD6 (ADR 0106) : la composition des indicateurs de la direction (cliniques du Dossier médical, stock du Stock, alertes) vit dans le
     * Pilotage direction ; son ancien service de la plateforme et les trois traducteurs qui le fournissaient au module ne doivent pas
     * réapparaître, et le contrôleur ne joint le Pilotage que par son langage publié {@code IndicateursDeLaDirection}.
     */
    @ArchTest
    static final ArchRule la_composition_des_indicateurs_de_la_direction_a_quitte_la_plateforme = noClasses()
            .should().haveFullyQualifiedName("com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService")
            .orShould().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.acl.pilotagedirection.IndicateursAFigerParLaPlateforme")
            .orShould().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.acl.pilotagedirection.IndicateursDuRapportParLaPlateforme")
            .orShould().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.acl.pilotagedirection.IndicateursDuTempsReelParLaPlateforme")
            .because("les indicateurs de la direction sont composés par contexts/pilotage-direction (ConsulterLesIndicateursDeLaDirection, PD6, ADR 0106)");

    @ArchTest
    static final ArchRule le_controleur_des_indicateurs_les_lit_par_le_langage_publie_du_pilotage = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.web.rest.DirectionDashboardRestController")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection")
            .because("GET /api/v1/direction/indicators est servi par IndicateursDeLaDirection (PD6, ADR 0106)");

    @ArchTest
    static final ArchRule les_statistiques_du_patient_lisent_les_seances_par_le_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.application.query.PatientStatsQueryService")
            .should().dependOnClassesThat().haveFullyQualifiedName(PORTS_SE7E + "StatistiquesDesSeancesPubliees")
            .because("les statistiques du patient lisent les séances par seance.v1 (ADR 0099, SE7e)");

    @ArchTest
    static final ArchRule le_cahier_compte_ses_seances_par_le_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.reporting.CahierDialyseReportService")
            .should().dependOnClassesThat().haveFullyQualifiedName(PORTS_SE7E + "SeancesDuCahierPubliees")
            .because("le refus CAHIER_VIDE compte les séances par seance.v1 (ADR 0099, SE7e)");

    @ArchTest
    static final ArchRule le_catalogue_de_forfaits_compte_les_forfaits_imposes_par_le_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.UsageDuForfaitParLesSeances")
            .should().dependOnClassesThat().haveFullyQualifiedName(PORTS_SE7E + "ForfaitsImposesAuxSeancesPublies")
            .because("l'usage d'un forfait imposé à une séance est compté par seance.v1 (ADR 0099, SE7e), additionné par le catalogue de "
                    + "forfaits de Patient et couverture (RF2, ADR 0124 : ce n'est plus l'administration générique qui le compte)");

    /**
     * RF3b (ADR 0127) : les lecteurs « annuaire » de la chaîne des payeurs (séance, pilotage de la direction, impressions) la lisent par le
     * langage publié {@code PayeursDuCentre}, jamais par les tables {@code caisse_assurance}, {@code agence} et {@code centre_payeur}
     * (garde de tables : {@code TableDesPayeursTest}).
     */
    @ArchTest
    static final ArchRule les_lecteurs_annuaire_des_payeurs_passent_par_le_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.ChaineDesPayeursParLAnnuaire")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.patientcouverture.CaissesParLAnnuaireDesPayeurs")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.acl.patientcouverture.CaissesDesAgencesParLAnnuaire")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre")
            .because("la caisse d'un centre payeur est lue dans l'annuaire des payeurs de Patient et couverture (RF3b, ADR 0127)");

    /**
     * SE7e (ADR 0099) : {@code SeancesDeLaReprisePubliees} est le SEUL port, hors de la Séance, qui ÉCRIT et SUPPRIME des séances. Il est
     * réservé aux deux adapters de la reprise de données (import et annulation d'un lot) ; aucune autre classe ne peut l'utiliser.
     */
    @ArchTest
    static final ArchRule seule_la_reprise_de_donnees_ecrit_des_seances_par_le_langage_publie = noClasses()
            .that().resideOutsideOfPackage(SEANCE + "..")
            .and().haveNameNotMatching(".*\\.(HistoricalRecordJdbcAdapter|MigrationRollbackJdbcAdapter)(\\$.*)?")
            .should().dependOnClassesThat().haveFullyQualifiedName(PORTS_SE7E + "SeancesDeLaReprisePubliees")
            .because("seule la reprise de données écrit et supprime des séances hors de la Séance (ADR 0099, SE7e)");

    @ArchTest
    static final ArchRule la_reprise_de_donnees_ecrit_les_seances_par_le_langage_publie = classes()
            .that().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.persistence.adapter.migration.HistoricalRecordJdbcAdapter")
            .or().haveFullyQualifiedName("com.hemodialyse.backend.infrastructure.persistence.adapter.migration.MigrationRollbackJdbcAdapter")
            .should().dependOnClassesThat().haveFullyQualifiedName(PORTS_SE7E + "SeancesDeLaReprisePubliees")
            .because("la reprise lit, écrit et supprime les séances par seance.v1 (ADR 0099, SE7e)");

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
