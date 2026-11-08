package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.AjouterUneDecisionDeRcp;
import com.hemodialyse.backend.contexts.dossiermedical.application.AjouterUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.ChangerLeStatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesDonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesEtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.GenererLesEtapesStandard;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourLeBilanImmunologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourLesNotesDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.MettreAJourUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnDonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneEtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.BilansPreGreffeJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DonneursVivantsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.EtapesDeBilanJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.BilanPreGreffeControleur;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.DonneursVivantsControleur;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.EtapesDeBilanControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.AnnulerUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAnalysesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesObservationsBiologiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.DemandesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.ModifierUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AnalysesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DemandesDuPatientJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.ObservationsBiologiquesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.AnalysesControleur;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.ObservationsBiologiquesControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAbordsVasculairesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.ModifierUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AbordsVasculairesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.AbordsVasculairesControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAllergiesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.DeclarerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AllergiesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.AllergiesControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAntecedentsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.ResoudreUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AntecedentsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.AntecedentsControleur;
import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesSerologiesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.application.IsolementDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification.IsolementDesPatientsParLaPlanification;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.SerologiesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.SerologiesControleur;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;
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

    /** Langage publié DM7 : la garde de patient des routes KDIGO et export PDF encore servies par la plateforme. */
    @Bean
    com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.GardeDePatient gardeDePatientDuDossierMedical(PatientsDuCentre patients) {
        return new com.hemodialyse.backend.contexts.dossiermedical.application.GardeDePatientDuCentre(patients);
    }

    @Bean
    HistoriqueDesConstantes historiqueDesConstantes(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees seances) {
        // SE7d (ADR 0098) : la Séance publie les constantes de ses séances ; plus aucun SQL sur seances ici
        return new com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance.HistoriqueDesConstantesPublie(seances);
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

    // ------------------------------------------------------------------ DM4 : sérologies

    /**
     * Horloge des sérologies : le jour UTC, qui est celui de la JVM du serveur (forcée en UTC) et donc celui du legacy (KB-MED-45), pour
     * la date par défaut ET pour la limite « pas dans le futur ». Passer au jour du centre en remplaçant ce seul bean quand les métiers
     * l'auront décidé (même réserve que les demandes d'examen).
     */
    @Bean
    HorlogeDuCentre horlogeDesSerologies() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    Serologies serologiesDuDossierMedical(JdbcTemplate jdbc) {
        return new SerologiesJdbc(jdbc);
    }

    @Bean
    IsolementDesPatients isolementDesPatientsDuDossierMedical(FichesDesPatients fiches, VerrouDesPlacesDuCentre verrou,
                                                              ReaffectationEnIsolement reaffectation) {
        return new IsolementDesPatientsParLaPlanification(fiches, verrou, reaffectation);
    }

    @Bean
    ConsulterLesSerologiesDuPatient consulterLesSerologiesDuPatient(SessionCourante session, Serologies serologies) {
        return new ConsulterLesSerologiesDuPatient(session, serologies);
    }

    @Bean
    EnregistrerUneSerologie enregistrerUneSerologie(SessionCourante session, Serologies serologies, IsolementDesPatients isolement,
                                                    UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                    @Qualifier("horlogeDesSerologies") HorlogeDuCentre horloge) {
        return new EnregistrerUneSerologie(session, serologies, isolement, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    CorrigerUneSerologie corrigerUneSerologie(SessionCourante session, Serologies serologies, IsolementDesPatients isolement,
                                              UniteDeTravail uniteDeTravail, @Qualifier("horlogeDesSerologies") HorlogeDuCentre horloge) {
        return new CorrigerUneSerologie(session, serologies, isolement, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUneSerologie supprimerUneSerologie(SessionCourante session, Serologies serologies, UniteDeTravail uniteDeTravail) {
        return new SupprimerUneSerologie(session, serologies, uniteDeTravail);
    }

    @Bean
    SerologiesControleur serologiesControleur(ConsulterLesSerologiesDuPatient consulter, EnregistrerUneSerologie enregistrer,
                                              CorrigerUneSerologie corriger, SupprimerUneSerologie supprimer) {
        return new SerologiesControleur(consulter, enregistrer, corriger, supprimer);
    }

    // ------------------------------------------------------------------ DM5 : antécédents, allergies, abords vasculaires
    /**
     * Horloge des fiches cliniques (antécédents, allergies, abords) : le jour UTC, qui est celui de la JVM du serveur (forcée en UTC) et
     * donc celui du legacy (date par défaut d'une allergie et limite « pas dans le futur »). Passer au jour du centre en remplaçant ce
     * seul bean quand les métiers l'auront décidé (même réserve que les demandes d'examen et les sérologies).
     */
    @Bean
    HorlogeDuCentre horlogeDesFichesCliniques() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    Antecedents antecedentsDuDossierMedical(JdbcTemplate jdbc) {
        return new AntecedentsJdbc(jdbc);
    }

    @Bean
    ConsulterLesAntecedentsDuPatient consulterLesAntecedentsDuPatient(SessionCourante session, Antecedents antecedents) {
        return new ConsulterLesAntecedentsDuPatient(session, antecedents);
    }

    @Bean
    EnregistrerUnAntecedent enregistrerUnAntecedent(SessionCourante session, PatientsDuCentre patients, Antecedents antecedents,
                                                    UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                    @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new EnregistrerUnAntecedent(session, patients, antecedents, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    CorrigerUnAntecedent corrigerUnAntecedent(SessionCourante session, Antecedents antecedents, UniteDeTravail uniteDeTravail,
                                              @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new CorrigerUnAntecedent(session, antecedents, uniteDeTravail, horloge);
    }

    @Bean
    ResoudreUnAntecedent resoudreUnAntecedent(SessionCourante session, Antecedents antecedents, UniteDeTravail uniteDeTravail,
                                              @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new ResoudreUnAntecedent(session, antecedents, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUnAntecedent supprimerUnAntecedent(SessionCourante session, Antecedents antecedents, UniteDeTravail uniteDeTravail) {
        return new SupprimerUnAntecedent(session, antecedents, uniteDeTravail);
    }

    @Bean
    AntecedentsControleur antecedentsControleur(ConsulterLesAntecedentsDuPatient consulter, EnregistrerUnAntecedent enregistrer,
                                                CorrigerUnAntecedent corriger, ResoudreUnAntecedent resoudre,
                                                SupprimerUnAntecedent supprimer) {
        return new AntecedentsControleur(consulter, enregistrer, corriger, resoudre, supprimer);
    }

    @Bean
    Allergies allergiesDuDossierMedical(JdbcTemplate jdbc) {
        return new AllergiesJdbc(jdbc);
    }

    @Bean
    ConsulterLesAllergiesDuPatient consulterLesAllergiesDuPatient(SessionCourante session, Allergies allergies) {
        return new ConsulterLesAllergiesDuPatient(session, allergies);
    }

    @Bean
    DeclarerUneAllergie declarerUneAllergie(SessionCourante session, PatientsDuCentre patients, Allergies allergies,
                                            UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                            @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new DeclarerUneAllergie(session, patients, allergies, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    CorrigerUneAllergie corrigerUneAllergie(SessionCourante session, Allergies allergies, UniteDeTravail uniteDeTravail,
                                            @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new CorrigerUneAllergie(session, allergies, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUneAllergie supprimerUneAllergie(SessionCourante session, Allergies allergies, UniteDeTravail uniteDeTravail) {
        return new SupprimerUneAllergie(session, allergies, uniteDeTravail);
    }

    @Bean
    AllergiesControleur allergiesControleur(ConsulterLesAllergiesDuPatient consulter, DeclarerUneAllergie declarer, CorrigerUneAllergie corriger,
                                            SupprimerUneAllergie supprimer) {
        return new AllergiesControleur(consulter, declarer, corriger, supprimer);
    }

    @Bean
    AbordsVasculaires abordsVasculairesDuDossierMedical(JdbcTemplate jdbc) {
        return new AbordsVasculairesJdbc(jdbc);
    }

    @Bean
    ConsulterLesAbordsVasculairesDuPatient consulterLesAbordsVasculairesDuPatient(SessionCourante session, AbordsVasculaires abords) {
        return new ConsulterLesAbordsVasculairesDuPatient(session, abords);
    }

    @Bean
    EnregistrerUnAbordVasculaire enregistrerUnAbordVasculaire(SessionCourante session, PatientsDuCentre patients, AbordsVasculaires abords,
                                                              UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                              @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new EnregistrerUnAbordVasculaire(session, patients, abords, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    ModifierUnAbordVasculaire modifierUnAbordVasculaire(SessionCourante session, AbordsVasculaires abords, UniteDeTravail uniteDeTravail,
                                                        @Qualifier("horlogeDesFichesCliniques") HorlogeDuCentre horloge) {
        return new ModifierUnAbordVasculaire(session, abords, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUnAbordVasculaire supprimerUnAbordVasculaire(SessionCourante session, AbordsVasculaires abords, UniteDeTravail uniteDeTravail) {
        return new SupprimerUnAbordVasculaire(session, abords, uniteDeTravail);
    }

    @Bean
    AbordsVasculairesControleur abordsVasculairesControleur(ConsulterLesAbordsVasculairesDuPatient consulter,
                                                            EnregistrerUnAbordVasculaire enregistrer, ModifierUnAbordVasculaire modifier,
                                                            SupprimerUnAbordVasculaire supprimer) {
        return new AbordsVasculairesControleur(consulter, enregistrer, modifier, supprimer);
    }

    // ------------------------------------------------------------------ DM6 : observations biologiques et analyses

    /**
     * Horloge de la biologie : le jour UTC, qui est celui de la JVM du serveur (forcée en UTC) et donc celui du legacy (KB-MED-80), pour la
     * date par défaut d'une analyse ET pour la limite « pas dans le futur » d'une observation. Passer au jour du centre en remplaçant ce
     * seul bean quand les métiers l'auront décidé (même réserve que les demandes d'examen, les sérologies et les fiches cliniques).
     */
    @Bean
    HorlogeDuCentre horlogeDeLaBiologie() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    DemandesDuPatient demandesDuPatientDuDossierMedical(JdbcTemplate jdbc) {
        return new DemandesDuPatientJdbc(jdbc);
    }

    @Bean
    ObservationsBiologiques observationsBiologiquesDuDossierMedical(JdbcTemplate jdbc) {
        return new ObservationsBiologiquesJdbc(jdbc);
    }

    @Bean
    ConsulterLesObservationsBiologiquesDuPatient consulterLesObservationsBiologiquesDuPatient(SessionCourante session,
                                                                                              ObservationsBiologiques observations,
                                                                                              DemandesDuPatient demandes) {
        return new ConsulterLesObservationsBiologiquesDuPatient(session, observations, demandes);
    }

    @Bean
    EnregistrerUneObservationBiologique enregistrerUneObservationBiologique(SessionCourante session, PatientsDuCentre patients,
                                                                            ObservationsBiologiques observations, UniteDeTravail uniteDeTravail,
                                                                            GenerateurDIdentifiants identifiants,
                                                                            @Qualifier("horlogeDeLaBiologie") HorlogeDuCentre horloge) {
        return new EnregistrerUneObservationBiologique(session, patients, observations, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    CorrigerUneObservationBiologique corrigerUneObservationBiologique(SessionCourante session, ObservationsBiologiques observations,
                                                                       UniteDeTravail uniteDeTravail,
                                                                       @Qualifier("horlogeDeLaBiologie") HorlogeDuCentre horloge) {
        return new CorrigerUneObservationBiologique(session, observations, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUneObservationBiologique supprimerUneObservationBiologique(SessionCourante session, ObservationsBiologiques observations,
                                                                        UniteDeTravail uniteDeTravail) {
        return new SupprimerUneObservationBiologique(session, observations, uniteDeTravail);
    }

    @Bean
    ObservationsBiologiquesControleur observationsBiologiquesControleur(ConsulterLesObservationsBiologiquesDuPatient consulter,
                                                                        EnregistrerUneObservationBiologique enregistrer,
                                                                        CorrigerUneObservationBiologique corriger,
                                                                        SupprimerUneObservationBiologique supprimer) {
        return new ObservationsBiologiquesControleur(consulter, enregistrer, corriger, supprimer);
    }

    @Bean
    Analyses analysesDuDossierMedical(JdbcTemplate jdbc) {
        return new AnalysesJdbc(jdbc);
    }

    @Bean
    ConsulterLesAnalysesDuPatient consulterLesAnalysesDuPatient(SessionCourante session, Analyses analyses) {
        return new ConsulterLesAnalysesDuPatient(session, analyses);
    }

    @Bean
    EnregistrerUneAnalyse enregistrerUneAnalyse(SessionCourante session, PatientsDuCentre patients, Analyses analyses,
                                                UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                @Qualifier("horlogeDeLaBiologie") HorlogeDuCentre horloge) {
        return new EnregistrerUneAnalyse(session, patients, analyses, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    ModifierUneAnalyse modifierUneAnalyse(SessionCourante session, Analyses analyses, UniteDeTravail uniteDeTravail,
                                          @Qualifier("horlogeDeLaBiologie") HorlogeDuCentre horloge) {
        return new ModifierUneAnalyse(session, analyses, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUneAnalyse supprimerUneAnalyse(SessionCourante session, Analyses analyses, UniteDeTravail uniteDeTravail) {
        return new SupprimerUneAnalyse(session, analyses, uniteDeTravail);
    }

    @Bean
    AnalysesControleur analysesControleur(ConsulterLesAnalysesDuPatient consulter, EnregistrerUneAnalyse enregistrer, ModifierUneAnalyse modifier,
                                          SupprimerUneAnalyse supprimer) {
        return new AnalysesControleur(consulter, enregistrer, modifier, supprimer);
    }

    // ------------------------------------------------------------------ DM7 : bilan pré-greffe, étapes, donneurs vivants

    /**
     * Horloge du bilan pré-greffe : le jour UTC, qui est celui de la JVM du serveur (forcée en UTC) et donc celui du legacy (KB-MED-92),
     * pour les dates de début, d'inscription et de greffe ET pour la limite « pas dans le futur » d'une décision de RCP. Passer au jour du
     * centre en remplaçant ce seul bean quand les métiers l'auront décidé.
     */
    @Bean
    HorlogeDuCentre horlogeDuBilanPreGreffe() {
        return new HorlogeDuCentre(Clock.systemUTC(), ZoneOffset.UTC);
    }

    @Bean
    BilansPreGreffe bilansPreGreffeDuDossierMedical(JdbcTemplate jdbc) {
        return new BilansPreGreffeJdbc(jdbc);
    }

    @Bean
    EtapesDeBilan etapesDeBilanDuDossierMedical(JdbcTemplate jdbc) {
        return new EtapesDeBilanJdbc(jdbc);
    }

    @Bean
    DonneursVivants donneursVivantsDuDossierMedical(JdbcTemplate jdbc) {
        return new DonneursVivantsJdbc(jdbc);
    }

    @Bean
    ConsulterLeBilanPreGreffe consulterLeBilanPreGreffe(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                                        UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                        @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new ConsulterLeBilanPreGreffe(session, patients, bilans, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    ChangerLeStatutDuBilanPreGreffe changerLeStatutDuBilanPreGreffe(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                                                    UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                                    @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new ChangerLeStatutDuBilanPreGreffe(session, patients, bilans, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    MettreAJourLeBilanImmunologique mettreAJourLeBilanImmunologique(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                                                    UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                                    @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new MettreAJourLeBilanImmunologique(session, patients, bilans, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    MettreAJourLesNotesDuBilan mettreAJourLesNotesDuBilan(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                                          UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                          @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new MettreAJourLesNotesDuBilan(session, patients, bilans, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    AjouterUneDecisionDeRcp ajouterUneDecisionDeRcp(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                                    UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                    @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new AjouterUneDecisionDeRcp(session, patients, bilans, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    BilanPreGreffeControleur bilanPreGreffeControleur(ConsulterLeBilanPreGreffe consulter, ChangerLeStatutDuBilanPreGreffe changerLeStatut,
                                                      MettreAJourLeBilanImmunologique immunologique, MettreAJourLesNotesDuBilan notes,
                                                      AjouterUneDecisionDeRcp decision) {
        return new BilanPreGreffeControleur(consulter, changerLeStatut, immunologique, notes, decision);
    }

    @Bean
    ConsulterLesEtapesDeBilan consulterLesEtapesDeBilan(SessionCourante session, EtapesDeBilan etapes) {
        return new ConsulterLesEtapesDeBilan(session, etapes);
    }

    @Bean
    AjouterUneEtapeDeBilan ajouterUneEtapeDeBilan(SessionCourante session, PatientsDuCentre patients, EtapesDeBilan etapes,
                                                  UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                  @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new AjouterUneEtapeDeBilan(session, patients, etapes, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    GenererLesEtapesStandard genererLesEtapesStandard(SessionCourante session, PatientsDuCentre patients, EtapesDeBilan etapes,
                                                      UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                      @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new GenererLesEtapesStandard(session, patients, etapes, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    MettreAJourUneEtapeDeBilan mettreAJourUneEtapeDeBilan(SessionCourante session, EtapesDeBilan etapes, UniteDeTravail uniteDeTravail,
                                                          @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new MettreAJourUneEtapeDeBilan(session, etapes, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUneEtapeDeBilan supprimerUneEtapeDeBilan(SessionCourante session, EtapesDeBilan etapes, UniteDeTravail uniteDeTravail) {
        return new SupprimerUneEtapeDeBilan(session, etapes, uniteDeTravail);
    }

    @Bean
    EtapesDeBilanControleur etapesDeBilanControleur(ConsulterLesEtapesDeBilan consulter, AjouterUneEtapeDeBilan ajouter,
                                                    GenererLesEtapesStandard generer, MettreAJourUneEtapeDeBilan mettreAJour,
                                                    SupprimerUneEtapeDeBilan supprimer) {
        return new EtapesDeBilanControleur(consulter, ajouter, generer, mettreAJour, supprimer);
    }

    @Bean
    ConsulterLesDonneursVivants consulterLesDonneursVivants(SessionCourante session, DonneursVivants donneurs) {
        return new ConsulterLesDonneursVivants(session, donneurs);
    }

    @Bean
    EnregistrerUnDonneurVivant enregistrerUnDonneurVivant(SessionCourante session, PatientsDuCentre patients, DonneursVivants donneurs,
                                                          UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                                          @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new EnregistrerUnDonneurVivant(session, patients, donneurs, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    MettreAJourUnDonneurVivant mettreAJourUnDonneurVivant(SessionCourante session, DonneursVivants donneurs, UniteDeTravail uniteDeTravail,
                                                          @Qualifier("horlogeDuBilanPreGreffe") HorlogeDuCentre horloge) {
        return new MettreAJourUnDonneurVivant(session, donneurs, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUnDonneurVivant supprimerUnDonneurVivant(SessionCourante session, DonneursVivants donneurs, UniteDeTravail uniteDeTravail) {
        return new SupprimerUnDonneurVivant(session, donneurs, uniteDeTravail);
    }

    @Bean
    DonneursVivantsControleur donneursVivantsControleur(ConsulterLesDonneursVivants consulter, EnregistrerUnDonneurVivant enregistrer,
                                                        MettreAJourUnDonneurVivant mettreAJour, SupprimerUnDonneurVivant supprimer) {
        return new DonneursVivantsControleur(consulter, enregistrer, mettreAJour, supprimer);
    }

    @Bean
    OrdonnancesControleur ordonnancesControleur(ConsulterLesOrdonnancesDuPatient consulter, RedigerUneOrdonnance rediger,
                                                SignerUneOrdonnance signer, MarquerUneOrdonnanceImprimee marquerImprimee,
                                                AnnulerUneOrdonnance annuler) {
        return new OrdonnancesControleur(consulter, rediger, signer, marquerImprimee, annuler);
    }
}
