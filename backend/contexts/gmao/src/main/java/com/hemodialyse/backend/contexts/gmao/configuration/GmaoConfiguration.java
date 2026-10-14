package com.hemodialyse.backend.contexts.gmao.configuration;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.application.equipement.CapaciteDesSalles;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsignerObservation;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConstaterEtat;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsulterEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.CorrigerIdentification;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ListerParc;
import com.hemodialyse.backend.contexts.gmao.application.equipement.MettreHorsService;
import com.hemodialyse.backend.contexts.gmao.application.equipement.Reformer;
import com.hemodialyse.backend.contexts.gmao.application.equipement.RemettreEnService;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.ConsulterIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.CorrigerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.DesactiverIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.EnregistrerIntervenant;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.ListerIntervenants;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AcheverTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterLigneDeCout;
import com.hemodialyse.backend.contexts.gmao.application.intervention.Annuler;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.AjouterTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.DemarrerTache;
import com.hemodialyse.backend.contexts.gmao.application.intervention.IntervenantsDuCentre;
import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ListerInterventions;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PlanifierIntervention;
import com.hemodialyse.backend.contexts.gmao.application.intervention.Rectifier;
import com.hemodialyse.backend.contexts.gmao.application.intervention.RetirerLigneDeCout;
import com.hemodialyse.backend.contexts.gmao.application.intervention.TerminerIntervention;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.JoindrePieceJointe;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.ListerPiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.RetirerPieceJointe;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.TelechargerPieceJointe;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ConsulterPlan;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EnregistrerExecution;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.EtablirPlan;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.LecturePlans;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansActifs;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansDEquipement;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansDuCentre;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ListerPlansEnRetard;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.ReprendrePlan;
import com.hemodialyse.backend.contexts.gmao.application.planmaintenance.SuspendrePlan;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.UniciteDuCodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceRepository;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.EtatEquipementPort;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.gmao.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.gmao.infrastructure.evenements.JournalDesEvenements;
import com.hemodialyse.backend.contexts.gmao.infrastructure.evenements.PublicateurSpring;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementStatutHistoriqueSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantsDuCentreJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionsHotesJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LecturePiecesJointesJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureEquipementsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureIntervenantsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureInterventionsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LigneDeCoutSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LecturePlansJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.RectificationSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.TacheSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.systeme.HorlogeSysteme;
import com.hemodialyse.backend.contexts.gmao.infrastructure.systeme.IdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.gmao.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.gmao.application.equipement.ConsulterLaFiche;
import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.application.equipement.VerifierLaDisponibilite;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterLaChronologie;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ConsulterLesIndicateurs;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImpressionDuBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImprimerLeBon;
import com.hemodialyse.backend.contexts.gmao.application.intervention.NomsDesUtilisateurs;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.ComptageDesGenerateurs;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.ConsulterLesStatistiques;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.SynthetiserLeCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.AnalyseDeDecision;
import com.hemodialyse.backend.contexts.gmao.infrastructure.acl.legacyidentite.NomsLegacy;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.HistoriqueDesStatutsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.GenerateursDuCentreJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureDesComptagesJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureDesCoutsJpa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composition root du contexte GMAO : seul endroit qui connaît toutes les couches. Les use cases sont des objets Java
 * purs ; leurs ports sont câblés ici sur les adapters. {@code @EnableAsync} : les consommateurs
 * {@code @ApplicationModuleListener} s'exécutent après commit, dans leur propre transaction (le legacy n'utilise aucun
 * {@code @Async}, l'activation n'a pas d'autre effet).
 */
@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:gmao-defaults.properties")
@EnableAsync
public class GmaoConfiguration {

    // ------------------------------------------------------------------ socle

    @Bean
    SessionCourante gmaoSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    Horloge gmaoHorloge() {
        return new HorlogeSysteme();
    }

    @Bean
    GenerateurIdentifiants gmaoGenerateurIdentifiants() {
        return new IdentifiantsAleatoires();
    }

    @Bean
    UniteDeTravail gmaoUniteDeTravail(PlatformTransactionManager transactionManager) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager));
    }

    @Bean
    PublicateurDEvenements gmaoPublicateurDEvenements(ApplicationEventPublisher publisher) {
        return new PublicateurSpring(publisher);
    }

    @Bean
    JournalDesEvenements gmaoJournalDesEvenements() {
        return new JournalDesEvenements();
    }

    // ------------------------------------------------------------------ équipements (parc et cycle de vie)

    /** Le repository est aussi le port des codes du centre (R7). {@code CapaciteDesSalles} est fourni par l'ACL du legacy. */
    @Bean
    EquipementRepositoryJpa gmaoEquipementRepository(EquipementSpringData equipements,
                                                     EquipementStatutHistoriqueSpringData historique) {
        return new EquipementRepositoryJpa(equipements, historique);
    }

    @Bean
    LectureEquipements gmaoLectureEquipements(EquipementSpringData donnees) {
        return new LectureEquipementsJpa(donnees);
    }

    @Bean
    UniciteDuCodeEquipement gmaoUniciteDuCodeEquipement(EquipementRepositoryJpa equipements) {
        return new UniciteDuCodeEquipement(equipements);
    }

    @Bean
    EnregistrerEquipement gmaoEnregistrerEquipement(EquipementRepository equipements, UniciteDuCodeEquipement unicite,
                                                    CapaciteDesSalles salles, PublicateurDEvenements publicateur,
                                                    Horloge horloge, GenerateurIdentifiants identifiants,
                                                    UniteDeTravail uniteDeTravail) {
        return new EnregistrerEquipement(equipements, unicite, salles, publicateur, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    CorrigerIdentification gmaoCorrigerIdentification(EquipementRepository equipements, CapaciteDesSalles salles,
                                                      PublicateurDEvenements publicateur, Horloge horloge,
                                                      UniteDeTravail uniteDeTravail) {
        return new CorrigerIdentification(equipements, salles, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    MettreHorsService gmaoMettreHorsService(EquipementRepository equipements, PublicateurDEvenements publicateur,
                                            Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new MettreHorsService(equipements, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    RemettreEnService gmaoRemettreEnService(EquipementRepository equipements, PublicateurDEvenements publicateur,
                                            Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new RemettreEnService(equipements, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    Reformer gmaoReformer(EquipementRepository equipements, PublicateurDEvenements publicateur, Horloge horloge,
                          UniteDeTravail uniteDeTravail) {
        return new Reformer(equipements, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ConsignerObservation gmaoConsignerObservation(EquipementRepository equipements, Horloge horloge,
                                                  UniteDeTravail uniteDeTravail) {
        return new ConsignerObservation(equipements, horloge, uniteDeTravail);
    }

    /** Langage publié {@code EtatEquipementPort} : la clôture d'intervention (legacy) y délègue. */
    @Bean
    ConstaterEtat gmaoConstaterEtat(EquipementRepository equipements, PublicateurDEvenements publicateur, Horloge horloge,
                                    UniteDeTravail uniteDeTravail) {
        return new ConstaterEtat(equipements, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ConsulterEquipement gmaoConsulterEquipement(LectureEquipements lecture) {
        return new ConsulterEquipement(lecture);
    }

    @Bean
    ListerParc gmaoListerParc(LectureEquipements lecture) {
        return new ListerParc(lecture);
    }

    // ------------------------------------------------------------------ plans de maintenance

    @Bean
    PlanMaintenanceRepository gmaoPlanMaintenanceRepository(PlanMaintenanceSpringData donnees) {
        return new PlanMaintenanceRepositoryJpa(donnees);
    }

    @Bean
    LecturePlans gmaoLecturePlans(PlanMaintenanceSpringData donnees) {
        return new LecturePlansJpa(donnees);
    }

    @Bean
    EtablirPlan gmaoEtablirPlan(PlanMaintenanceRepository plans, PublicateurDEvenements publicateur, Horloge horloge,
                                GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail, EquipementExiste equipements) {
        return new EtablirPlan(plans, publicateur, horloge, identifiants, uniteDeTravail, equipements);
    }

    @Bean
    EnregistrerExecution gmaoEnregistrerExecution(PlanMaintenanceRepository plans, PublicateurDEvenements publicateur,
                                                  Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new EnregistrerExecution(plans, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    SuspendrePlan gmaoSuspendrePlan(PlanMaintenanceRepository plans, PublicateurDEvenements publicateur,
                                    Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new SuspendrePlan(plans, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ReprendrePlan gmaoReprendrePlan(PlanMaintenanceRepository plans, PublicateurDEvenements publicateur,
                                    Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new ReprendrePlan(plans, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ConsulterPlan gmaoConsulterPlan(LecturePlans lecture) {
        return new ConsulterPlan(lecture);
    }

    @Bean
    ListerPlansDuCentre gmaoListerPlansDuCentre(LecturePlans lecture) {
        return new ListerPlansDuCentre(lecture);
    }

    @Bean
    ListerPlansActifs gmaoListerPlansActifs(LecturePlans lecture) {
        return new ListerPlansActifs(lecture);
    }

    @Bean
    ListerPlansEnRetard gmaoListerPlansEnRetard(LecturePlans lecture, Horloge horloge) {
        return new ListerPlansEnRetard(lecture, horloge);
    }

    @Bean
    ListerPlansDEquipement gmaoListerPlansDEquipement(LecturePlans lecture) {
        return new ListerPlansDEquipement(lecture);
    }

    // ------------------------------------------------------------------ intervenants

    @Bean
    IntervenantRepository gmaoIntervenantRepository(IntervenantSpringData donnees) {
        return new IntervenantRepositoryJpa(donnees);
    }

    @Bean
    LectureIntervenants gmaoLectureIntervenants(IntervenantSpringData donnees) {
        return new LectureIntervenantsJpa(donnees);
    }

    @Bean
    EnregistrerIntervenant gmaoEnregistrerIntervenant(IntervenantRepository intervenants, PublicateurDEvenements publicateur,
                                                      Horloge horloge, GenerateurIdentifiants identifiants,
                                                      UniteDeTravail uniteDeTravail) {
        return new EnregistrerIntervenant(intervenants, publicateur, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    CorrigerIntervenant gmaoCorrigerIntervenant(IntervenantRepository intervenants, PublicateurDEvenements publicateur,
                                                Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new CorrigerIntervenant(intervenants, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    DesactiverIntervenant gmaoDesactiverIntervenant(IntervenantRepository intervenants, PublicateurDEvenements publicateur,
                                                    Horloge horloge, UniteDeTravail uniteDeTravail) {
        return new DesactiverIntervenant(intervenants, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ListerIntervenants gmaoListerIntervenants(LectureIntervenants lecture) {
        return new ListerIntervenants(lecture);
    }

    @Bean
    ConsulterIntervenant gmaoConsulterIntervenant(LectureIntervenants lecture) {
        return new ConsulterIntervenant(lecture);
    }

    // ------------------------------------------------------------------ interventions

    /** Le repository est aussi le chargeur du read model : même mapping pour l'écriture et les listes. */
    @Bean
    InterventionRepositoryJpa gmaoInterventionRepository(InterventionSpringData interventions, LigneDeCoutSpringData lignes,
                                                         RectificationSpringData rectifications, TacheSpringData taches) {
        return new InterventionRepositoryJpa(interventions, lignes, rectifications, taches);
    }

    @Bean
    LectureInterventions gmaoLectureInterventions(InterventionSpringData interventions, InterventionRepositoryJpa repository) {
        return new LectureInterventionsJpa(interventions, repository);
    }

    @Bean
    IntervenantsDuCentre gmaoIntervenantsDuCentre(IntervenantSpringData intervenants) {
        return new IntervenantsDuCentreJpa(intervenants);
    }

    @Bean
    PlanifierIntervention gmaoPlanifierIntervention(InterventionRepository interventions, EquipementRepository equipements,
                                                    PublicateurDEvenements publicateur, Horloge horloge,
                                                    GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        return new PlanifierIntervention(interventions, equipements, publicateur, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    DemarrerIntervention gmaoDemarrerIntervention(InterventionRepository interventions, EtatEquipementPort etatEquipement,
                                                  PublicateurDEvenements publicateur, Horloge horloge,
                                                  UniteDeTravail uniteDeTravail) {
        return new DemarrerIntervention(interventions, etatEquipement, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    TerminerIntervention gmaoTerminerIntervention(InterventionRepository interventions, IntervenantsDuCentre intervenants,
                                                  EtatEquipementPort etatEquipement, PublicateurDEvenements publicateur,
                                                  Horloge horloge, GenerateurIdentifiants identifiants,
                                                  UniteDeTravail uniteDeTravail) {
        return new TerminerIntervention(interventions, intervenants, etatEquipement, publicateur, horloge, identifiants,
                uniteDeTravail);
    }

    @Bean
    AjouterLigneDeCout gmaoAjouterLigneDeCout(InterventionRepository interventions, IntervenantsDuCentre intervenants,
                                              Horloge horloge, GenerateurIdentifiants identifiants,
                                              UniteDeTravail uniteDeTravail) {
        return new AjouterLigneDeCout(interventions, intervenants, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    AjouterTache gmaoAjouterTache(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                                  GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        return new AjouterTache(interventions, publicateur, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    DemarrerTache gmaoDemarrerTache(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                                    UniteDeTravail uniteDeTravail) {
        return new DemarrerTache(interventions, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    AcheverTache gmaoAcheverTache(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                                  UniteDeTravail uniteDeTravail) {
        return new AcheverTache(interventions, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    RetirerLigneDeCout gmaoRetirerLigneDeCout(InterventionRepository interventions, Horloge horloge,
                                              UniteDeTravail uniteDeTravail) {
        return new RetirerLigneDeCout(interventions, horloge, uniteDeTravail);
    }

    @Bean
    Rectifier gmaoRectifier(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                            GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        return new Rectifier(interventions, publicateur, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    Annuler gmaoAnnuler(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                        UniteDeTravail uniteDeTravail) {
        return new Annuler(interventions, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ConsulterIntervention gmaoConsulterIntervention(InterventionRepository interventions, Horloge horloge) {
        return new ConsulterIntervention(interventions, horloge);
    }

    @Bean
    ListerInterventions gmaoListerInterventions(LectureInterventions lecture, EquipementRepository equipements,
                                                Horloge horloge) {
        return new ListerInterventions(lecture, equipements, horloge);
    }

    // ------------------------------------------------------------------ consultation et pilotage (lectures)

    @Bean
    HistoriqueDesStatuts gmaoHistoriqueDesStatuts(EquipementStatutHistoriqueSpringData historique) {
        return new HistoriqueDesStatutsJpa(historique);
    }

    @Bean
    LectureDesComptages gmaoLectureDesComptages(EquipementSpringData equipements, InterventionSpringData interventions,
                                                PlanMaintenanceSpringData plans) {
        return new LectureDesComptagesJpa(equipements, interventions, plans);
    }

    @Bean
    LectureDesCouts gmaoLectureDesCouts(LigneDeCoutSpringData lignes) {
        return new LectureDesCoutsJpa(lignes);
    }

    /** L'ACL du legacy fournit les noms d'utilisateurs ; la plateforme fournit l'impression du bon ({@code ImpressionDuBon}). */
    @Bean
    NomsDesUtilisateurs gmaoNomsDesUtilisateurs(JdbcTemplate jdbc) {
        return new NomsLegacy(jdbc);
    }

    /** Le seuil de réforme est un paramètre de l'application, 0,60 par défaut (RG-GMA-062). */
    @Bean
    AnalyseDeDecision gmaoAnalyseDeDecision(@Value("${hemodialyse.gmao.reforme-seuil-ratio:0.60}") BigDecimal seuil) {
        return new AnalyseDeDecision(seuil);
    }

    @Bean
    ConsulterLesIndicateurs gmaoConsulterLesIndicateurs(LectureInterventions interventions, LectureEquipements equipements,
                                                        HistoriqueDesStatuts historique, Horloge horloge) {
        return new ConsulterLesIndicateurs(interventions, equipements, historique, horloge);
    }

    @Bean
    ConsulterLaChronologie gmaoConsulterLaChronologie(LectureInterventions interventions, NomsDesUtilisateurs noms) {
        return new ConsulterLaChronologie(interventions, noms);
    }

    @Bean
    ImprimerLeBon gmaoImprimerLeBon(LectureInterventions interventions, LectureEquipements equipements,
                                    LectureIntervenants intervenants, ConsulterLaChronologie chronologie,
                                    ImpressionDuBon impression, Horloge horloge) {
        return new ImprimerLeBon(interventions, equipements, intervenants, chronologie, impression, horloge);
    }

    @Bean
    ConsulterLaFiche gmaoConsulterLaFiche(LectureEquipements equipements, HistoriqueDesStatuts historique,
                                          LectureDesComptages comptages, LectureDesCouts couts,
                                          LectureInterventions interventions, LecturePlans plans, AnalyseDeDecision analyse,
                                          Horloge horloge) {
        return new ConsulterLaFiche(equipements, historique, comptages, couts, interventions, plans, analyse, horloge);
    }

    @Bean
    VerifierLaDisponibilite gmaoVerifierLaDisponibilite(LectureEquipements equipements) {
        return new VerifierLaDisponibilite(equipements);
    }

    @Bean
    ConsulterLesStatistiques gmaoConsulterLesStatistiques(LectureDesComptages comptages, Horloge horloge) {
        return new ConsulterLesStatistiques(comptages, horloge);
    }

    /** Langage publié {@code SyntheseGmaoDuCentre} : le pilotage de la direction y lit la GMAO de chaque centre. */
    @Bean
    SynthetiserLeCentre gmaoSynthetiserLeCentre(LectureEquipements equipements, HistoriqueDesStatuts historique,
                                                LectureDesComptages comptages, LectureDesCouts couts,
                                                AnalyseDeDecision analyse) {
        return new SynthetiserLeCentre(equipements, historique, comptages, couts, analyse);
    }

    /** Langage publié {@code GenerateursInstalles} : la capacité théorique de la direction compte les générateurs de chaque centre. */
    @Bean
    ComptageDesGenerateurs gmaoComptageDesGenerateurs(LectureDesComptages comptages) {
        return new ComptageDesGenerateurs(comptages);
    }

    /** Langage publié {@code GenerateursDuCentre} : la reprise de données retrouve le générateur d'un patient. */
    @Bean
    GenerateursDuCentre gmaoGenerateursDuCentre(EquipementSpringData equipements) {
        return new GenerateursDuCentreJpa(equipements);
    }

    // ------------------------------------------------------------------ pièces jointes

    @Bean
    PieceJointeRepository gmaoPieceJointeRepository(PieceJointeSpringData donnees) {
        return new PieceJointeRepositoryJpa(donnees);
    }

    @Bean
    LecturePiecesJointes gmaoLecturePiecesJointes(PieceJointeSpringData donnees) {
        return new LecturePiecesJointesJpa(donnees);
    }

    @Bean
    InterventionsHotes gmaoInterventionsHotes(InterventionSpringData interventions) {
        return new InterventionsHotesJpa(interventions);
    }

    @Bean
    JoindrePieceJointe gmaoJoindrePieceJointe(InterventionsHotes hotes, PieceJointeRepository pieces,
                                              PublicateurDEvenements publicateur, Horloge horloge,
                                              GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        return new JoindrePieceJointe(hotes, pieces, publicateur, horloge, identifiants, uniteDeTravail);
    }

    @Bean
    RetirerPieceJointe gmaoRetirerPieceJointe(InterventionsHotes hotes, PieceJointeRepository pieces,
                                              PublicateurDEvenements publicateur, Horloge horloge,
                                              UniteDeTravail uniteDeTravail) {
        return new RetirerPieceJointe(hotes, pieces, publicateur, horloge, uniteDeTravail);
    }

    @Bean
    ListerPiecesJointes gmaoListerPiecesJointes(InterventionsHotes hotes, LecturePiecesJointes lecture) {
        return new ListerPiecesJointes(hotes, lecture);
    }

    @Bean
    TelechargerPieceJointe gmaoTelechargerPieceJointe(InterventionsHotes hotes, PieceJointeRepository pieces) {
        return new TelechargerPieceJointe(hotes, pieces);
    }
}
