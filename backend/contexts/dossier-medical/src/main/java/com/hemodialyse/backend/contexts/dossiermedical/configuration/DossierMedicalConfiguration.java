package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.AnnulerUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesDemandesDExamenDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.DemanderUnExamen;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerLePrelevement;
import com.hemodialyse.backend.contexts.dossiermedical.application.RendreLeResultatDisponible;
import com.hemodialyse.backend.contexts.dossiermedical.application.ValiderUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DemandesDExamenJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.DemandesDExamenControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.AnnulerUneOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesConstantesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesOrdonnancesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.GenerateurDIdentifiants;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.MarquerUneOrdonnanceImprimee;
import com.hemodialyse.backend.contexts.dossiermedical.application.NumerotationDesOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.RedigerUneOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SignerUneOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.patientcouverture.PatientsDuCentreDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.identifiants.GenerateurDIdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.HistoriqueDesConstantesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.NumerotationDesOrdonnancesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.OrdonnancesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.ConstantesControleur;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.OrdonnancesControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLaPrescriptionEnVigueur;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesPrescriptionsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.PrescrireUnTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.stock.CatalogueDesArticlesDuStock;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.PrescriptionsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.PrescriptionsControleur;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SignalementDesPrescriptions;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.dossiermedical.application.CentresActifs;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLObservance;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeSuiviDeLAnemie;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAdministrationsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.ControlerLObservance;
import com.hemodialyse.backend.contexts.dossiermedical.application.GererLesAlertesDObservance;
import com.hemodialyse.backend.contexts.dossiermedical.application.SaisirUneAdministration;
import com.hemodialyse.backend.contexts.dossiermedical.application.SortiesDeStockDesAdministrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Alertes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.legacyorganisation.CentresActifsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.stock.SortiesDeStockDuStock;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AdministrationsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AlertesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.BilansBiologiquesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.AnemieControleur;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.jobs.ControleDeNuitDeLObservance;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Composition du contexte Dossier médical (refonte sur place, ADR 0024, 0080, 0081) : mêmes tables, mêmes URL. DM1 : constantes ;
 * DM2 : ordonnances. Seul endroit qui connaît toutes les couches.
 */
@Configuration(proxyBeanMethods = false)
public class DossierMedicalConfiguration {

    /** Fuseau du centre : la table des centres n'en a pas, tous suivent Africa/Algiers (comme les Absences). */
    private static final ZoneId FUSEAU_DES_CENTRES = ZoneId.of("Africa/Algiers");

    @Bean
    SessionCourante sessionCouranteDossierMedical() {
        return new SessionLegacy();
    }

    @Bean
    @Primary
    HorlogeDuCentre horlogeDuDossierMedical() {
        return new HorlogeDuCentre(Clock.systemUTC(), FUSEAU_DES_CENTRES);
    }

    @Bean
    UniteDeTravail uniteDeTravailDuDossierMedical(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager), jdbc, Duration.ofSeconds(5));
    }

    @Bean
    GenerateurDIdentifiants generateurDIdentifiantsDuDossierMedical() {
        return new GenerateurDIdentifiantsAleatoires();
    }

    @Bean
    PatientsDuCentre patientsDuCentreDuDossierMedical(FichesDesPatients fiches) {
        return new PatientsDuCentreDeLaFiche(fiches);
    }

    @Bean
    HistoriqueDesConstantes historiqueDesConstantes(JdbcTemplate jdbc) {
        return new HistoriqueDesConstantesJdbc(jdbc);
    }

    @Bean
    ConsulterLesConstantesDuPatient consulterLesConstantesDuPatient(SessionCourante session, HistoriqueDesConstantes historique) {
        return new ConsulterLesConstantesDuPatient(session, historique);
    }

    @Bean
    ConstantesControleur constantesControleur(ConsulterLesConstantesDuPatient consulter) {
        return new ConstantesControleur(consulter);
    }

    // ------------------------------------------------------------------ DM2 : ordonnances

    @Bean
    Ordonnances ordonnancesDuDossierMedical(JdbcTemplate jdbc) {
        return new OrdonnancesJdbc(jdbc);
    }

    @Bean
    NumerotationDesOrdonnances numerotationDesOrdonnances(JdbcTemplate jdbc) {
        return new NumerotationDesOrdonnancesJdbc(jdbc);
    }

    @Bean
    ConsulterLesOrdonnancesDuPatient consulterLesOrdonnancesDuPatient(SessionCourante session, Ordonnances ordonnances) {
        return new ConsulterLesOrdonnancesDuPatient(session, ordonnances);
    }

    @Bean
    RedigerUneOrdonnance redigerUneOrdonnance(SessionCourante session, PatientsDuCentre patients, Ordonnances ordonnances,
                                              UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        return new RedigerUneOrdonnance(session, patients, ordonnances, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    SignerUneOrdonnance signerUneOrdonnance(SessionCourante session, Ordonnances ordonnances, NumerotationDesOrdonnances numerotation,
                                            UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        return new SignerUneOrdonnance(session, ordonnances, numerotation, uniteDeTravail, horloge);
    }

    @Bean
    MarquerUneOrdonnanceImprimee marquerUneOrdonnanceImprimee(SessionCourante session, Ordonnances ordonnances,
                                                              UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        return new MarquerUneOrdonnanceImprimee(session, ordonnances, uniteDeTravail, horloge);
    }

    @Bean
    AnnulerUneOrdonnance annulerUneOrdonnance(SessionCourante session, Ordonnances ordonnances, UniteDeTravail uniteDeTravail,
                                              HorlogeDuCentre horloge) {
        return new AnnulerUneOrdonnance(session, ordonnances, uniteDeTravail, horloge);
    }

    // ------------------------------------------------------------------ DM2 : prescriptions

    @Bean
    Prescriptions prescriptionsDuDossierMedical(JdbcTemplate jdbc) {
        return new PrescriptionsJdbc(jdbc);
    }

    @Bean
    CatalogueDesArticles catalogueDesArticlesDuDossierMedical(CatalogueDArticles stock) {
        return new CatalogueDesArticlesDuStock(stock);
    }

    @Bean
    ConsulterLesPrescriptionsDuPatient consulterLesPrescriptionsDuPatient(SessionCourante session, Prescriptions prescriptions,
                                                                           CatalogueDesArticles catalogue) {
        return new ConsulterLesPrescriptionsDuPatient(session, prescriptions, catalogue);
    }

    @Bean
    ConsulterLaPrescriptionEnVigueur consulterLaPrescriptionEnVigueur(SessionCourante session, Prescriptions prescriptions,
                                                                      CatalogueDesArticles catalogue, HorlogeDuCentre horloge) {
        return new ConsulterLaPrescriptionEnVigueur(session, prescriptions, catalogue, horloge);
    }

    @Bean
    PrescrireUnTraitement prescrireUnTraitement(SessionCourante session, PatientsDuCentre patients, Prescriptions prescriptions,
                                                CatalogueDesArticles catalogue, UniteDeTravail uniteDeTravail,
                                                GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge,
                                                SignalementDesPrescriptions signalement) {
        return new PrescrireUnTraitement(session, patients, prescriptions, catalogue, uniteDeTravail, identifiants, horloge, signalement);
    }

    @Bean
    SupprimerUnePrescription supprimerUnePrescription(SessionCourante session, Prescriptions prescriptions,
                                                      UniteDeTravail uniteDeTravail, SignalementDesPrescriptions signalement) {
        return new SupprimerUnePrescription(session, prescriptions, uniteDeTravail, signalement);
    }

    @Bean
    PrescriptionsControleur prescriptionsControleur(ConsulterLesPrescriptionsDuPatient consulter, ConsulterLaPrescriptionEnVigueur enVigueur,
                                                    PrescrireUnTraitement prescrire, SupprimerUnePrescription supprimer) {
        return new PrescriptionsControleur(consulter, enVigueur, prescrire, supprimer);
    }

    // ------------------------------------------------------------------ DM2 : anémie, observance, planificateur

    /**
     * Horloge de l'observance : le jour UTC, qui est celui de la JVM du serveur (forcée en UTC, y compris par les tests) et donc celui
     * du legacy (KB-MED-15). L'injection permet de passer au jour du centre (Africa/Algiers) en remplaçant ce seul bean, quand les
     * métiers l'auront décidé.
     */
    @Bean
    HorlogeDuCentre horlogeDeLObservance() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    Administrations administrationsDuDossierMedical(JdbcTemplate jdbc) {
        return new AdministrationsJdbc(jdbc);
    }

    @Bean
    Alertes alertesDuDossierMedical(JdbcTemplate jdbc) {
        return new AlertesJdbc(jdbc);
    }

    @Bean
    BilansBiologiques bilansBiologiquesDuDossierMedical(JdbcTemplate jdbc) {
        return new BilansBiologiquesJdbc(jdbc);
    }

    @Bean
    CentresActifs centresActifsDuDossierMedical(JdbcTemplate jdbc) {
        return new CentresActifsJdbc(jdbc);
    }

    @Bean
    SortiesDeStockDesAdministrations sortiesDeStockDesAdministrations(ConsommationsDeSeance consommations) {
        return new SortiesDeStockDuStock(consommations);
    }

    @Bean
    ConsulterLeSuiviDeLAnemie consulterLeSuiviDeLAnemie(SessionCourante session, BilansBiologiques bilans, Prescriptions prescriptions,
                                                         Administrations administrations, CatalogueDesArticles catalogue) {
        return new ConsulterLeSuiviDeLAnemie(session, bilans, prescriptions, administrations, catalogue);
    }

    @Bean
    ConsulterLObservance consulterLObservance(SessionCourante session, Prescriptions prescriptions, Administrations administrations,
                                              @Qualifier("horlogeDeLObservance") HorlogeDuCentre horloge) {
        return new ConsulterLObservance(session, prescriptions, administrations, horloge);
    }

    @Bean
    GererLesAlertesDObservance gererLesAlertesDObservance(SessionCourante session, Alertes alertes, UniteDeTravail uniteDeTravail,
                                                          HorlogeDuCentre horloge) {
        return new GererLesAlertesDObservance(session, alertes, uniteDeTravail, horloge);
    }

    @Bean
    ConsulterLesAdministrationsDuPatient consulterLesAdministrationsDuPatient(SessionCourante session, Administrations administrations) {
        return new ConsulterLesAdministrationsDuPatient(session, administrations);
    }

    @Bean
    SaisirUneAdministration saisirUneAdministration(SessionCourante session, PatientsDuCentre patients, Administrations administrations,
                                                    CatalogueDesArticles catalogue, SortiesDeStockDesAdministrations sortiesDeStock,
                                                    UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge,
                                                    NotificationsDeLAnemie notifications) {
        return new SaisirUneAdministration(session, patients, administrations, catalogue, sortiesDeStock, uniteDeTravail, identifiants, horloge,
                notifications);
    }

    @Bean
    ControlerLObservance controlerLObservance(Prescriptions prescriptions, Administrations administrations, Alertes alertes, CentresActifs centres,
                                              UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                              @Qualifier("horlogeDeLObservance") HorlogeDuCentre horloge, NotificationsDeLAnemie notifications) {
        return new ControlerLObservance(prescriptions, administrations, alertes, centres, uniteDeTravail, identifiants, horloge, notifications);
    }

    @Bean
    ControleDeNuitDeLObservance controleDeNuitDeLObservance(ControlerLObservance controler) {
        return new ControleDeNuitDeLObservance(controler);
    }

    @Bean
    AnemieControleur anemieControleur(ConsulterLeSuiviDeLAnemie suivi, ConsulterLObservance observance, GererLesAlertesDObservance alertes,
                                      ConsulterLesAdministrationsDuPatient administrations, SaisirUneAdministration saisir) {
        return new AnemieControleur(suivi, observance, alertes, administrations, saisir);
    }

    // ------------------------------------------------------------------ DM3 : demandes d'examen

    /**
     * Horloge des demandes d'examen : le jour UTC, qui est celui de la JVM du serveur (forcée en UTC, y compris par les tests) et donc
     * celui du legacy (KB-MED-34), pour la date par défaut ET pour la limite « pas dans le futur ». Passer au jour du centre
     * (Africa/Algiers) en remplaçant ce seul bean, quand les métiers l'auront décidé : le test « demain est refusé » deviendrait alors
     * instable entre 23:00 et 24:00 UTC (le jour d'Alger a déjà changé).
     */
    @Bean
    HorlogeDuCentre horlogeDesDemandesDExamen() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    DemandesDExamen demandesDExamenDuDossierMedical(JdbcTemplate jdbc) {
        return new DemandesDExamenJdbc(jdbc);
    }

    @Bean
    ConsulterLesDemandesDExamenDuPatient consulterLesDemandesDExamenDuPatient(SessionCourante session, DemandesDExamen demandes) {
        return new ConsulterLesDemandesDExamenDuPatient(session, demandes);
    }

    @Bean
    DemanderUnExamen demanderUnExamen(SessionCourante session, PatientsDuCentre patients, DemandesDExamen demandes,
                                      UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                      @Qualifier("horlogeDesDemandesDExamen") HorlogeDuCentre horloge) {
        return new DemanderUnExamen(session, patients, demandes, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    EnregistrerLePrelevement enregistrerLePrelevement(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail,
                                                      HorlogeDuCentre horloge) {
        return new EnregistrerLePrelevement(session, demandes, uniteDeTravail, horloge);
    }

    @Bean
    RendreLeResultatDisponible rendreLeResultatDisponible(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail,
                                                          HorlogeDuCentre horloge) {
        return new RendreLeResultatDisponible(session, demandes, uniteDeTravail, horloge);
    }

    @Bean
    ValiderUneDemandeDExamen validerUneDemandeDExamen(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail,
                                                      HorlogeDuCentre horloge) {
        return new ValiderUneDemandeDExamen(session, demandes, uniteDeTravail, horloge);
    }

    @Bean
    AnnulerUneDemandeDExamen annulerUneDemandeDExamen(SessionCourante session, DemandesDExamen demandes, UniteDeTravail uniteDeTravail,
                                                      HorlogeDuCentre horloge) {
        return new AnnulerUneDemandeDExamen(session, demandes, uniteDeTravail, horloge);
    }

    @Bean
    DemandesDExamenControleur demandesDExamenControleur(ConsulterLesDemandesDExamenDuPatient consulter, DemanderUnExamen demander,
                                                        EnregistrerLePrelevement prelever, RendreLeResultatDisponible rendreDisponible,
                                                        ValiderUneDemandeDExamen valider, AnnulerUneDemandeDExamen annuler) {
        return new DemandesDExamenControleur(consulter, demander, prelever, rendreDisponible, valider, annuler);
    }

    @Bean
    OrdonnancesControleur ordonnancesControleur(ConsulterLesOrdonnancesDuPatient consulter, RedigerUneOrdonnance rediger,
                                                SignerUneOrdonnance signer, MarquerUneOrdonnanceImprimee marquerImprimee,
                                                AnnulerUneOrdonnance annuler) {
        return new OrdonnancesControleur(consulter, rediger, signer, marquerImprimee, annuler);
    }
}
