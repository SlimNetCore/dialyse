package com.hemodialyse.backend.contexts.seance.configuration;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.seance.application.AbsencesDuCentre;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeDetailDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ExporterLeTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.ExportsDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.JoursBloques;
import com.hemodialyse.backend.contexts.seance.application.PatientsDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.absencespatients.AbsencesDuCentreParLePilotage;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.PatientsDuJourParLaFiche;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.PatientsDuTableauDeBordParLaFiche;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.PlanningDesPatientsParLaFiche;
import com.hemodialyse.backend.contexts.seance.infrastructure.export.ExportsDuTableauDeBordCsvPdfXlsx;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.JoursBloquesDuCalendrier;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesRealiseesJdbc;
import com.hemodialyse.backend.contexts.seance.application.AjouterUnJourAuCalendrier;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeCalendrier;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLeJournalDuJour;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLesSeancesRecentes;
import com.hemodialyse.backend.contexts.seance.application.PatientsDuJour;
import com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletMedical;
import com.hemodialyse.backend.contexts.seance.application.RetirerUnJourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.application.SortiesDuJour;
import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.SortiesDuJourDuStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.CalendrierDuCentreJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsMedicauxJdbc;
import com.hemodialyse.backend.contexts.seance.application.AnnulationDesConsommations;
import com.hemodialyse.backend.contexts.seance.application.ChangerLeJourDUneSeance;
import com.hemodialyse.backend.contexts.seance.application.JournalDesSuppressions;
import com.hemodialyse.backend.contexts.seance.application.SaisiesDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.SupprimerUneSeance;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.SaisiesParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsAnnuleesParLeStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.JournalDesSuppressionsJdbc;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SaisiesDeSeanceFournies;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.RenseignerLeVoletParamedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.FichesDesGenerateurs;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.DeplacementsDuJourDesPatients;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.gmao.GenerateursDesPatientsParLaGmao;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation.JourDuCentrePublie;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.planification.PlaceDuPatientParLaPlanification;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.ForfaitsDeSeanceJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.HistoriqueDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsDeLaSeanceJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsParamedicauxJdbc;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;
import com.hemodialyse.backend.contexts.seance.application.ConsommationsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.ConsulterUneSeance;
import com.hemodialyse.backend.contexts.seance.application.CreerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.DiffusionsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.EligibiliteALaFacturation;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.GenerateurDIdentifiants;
import com.hemodialyse.backend.contexts.seance.application.GenerateursDesPatients;
import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.seance.application.ListerLesSeances;
import com.hemodialyse.backend.contexts.seance.application.PatientsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.application.ResoudreLePatientScanne;
import com.hemodialyse.backend.contexts.seance.application.ScannerUnPatient;
import com.hemodialyse.backend.contexts.seance.application.SessionCourante;
import com.hemodialyse.backend.contexts.seance.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.seance.application.VoletsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.ImposerUnForfait;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PatientsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications.DiffusionsDeSeanceParLeCentreDeNotifications;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications.RappelsDeRegularisationParLeCentreDeNotifications;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications.SignalementDesAdministrationsSaisiesParLeCentreDeNotifications;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SignalementDesAdministrationsSaisies;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LecturesPublieesDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.FacturationDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LiaisonDesSeancesJdbc;
import org.springframework.beans.factory.annotation.Qualifier;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.EligibiliteParLaCouverture;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsDeLaSeanceDuStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.identifiants.GenerateurDIdentifiantsAleatoires;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.seance.application.ConsommationsALaValidation;
import com.hemodialyse.backend.contexts.seance.application.DeverrouillerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.application.SignerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsALaValidationDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.AjouterUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.ConsommablesDeLaSeance;
import com.hemodialyse.backend.contexts.seance.application.ConsulterLesRaccourcis;
import com.hemodialyse.backend.contexts.seance.application.ModifierLaQuantiteDUnConsommable;
import com.hemodialyse.backend.contexts.seance.application.RemplacerLesRaccourcis;
import com.hemodialyse.backend.contexts.seance.application.RetirerUnConsommable;
import com.hemodialyse.backend.contexts.seance.domain.ArticlesDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ArticlesDuCentreDuStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommablesDeLaSeanceDuStock;
import com.hemodialyse.backend.contexts.seance.infrastructure.cache.RaccourcisDesCentresEnCache;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.RaccourcisDesCentresJdbc;
import com.hemodialyse.backend.contexts.seance.application.CentresARappeler;
import com.hemodialyse.backend.contexts.seance.application.RappelerLesSeancesARegulariser;
import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.application.VerrousDesRappels;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation.CentresARappelerPublies;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VerrousDesRappelsJdbc;
import com.hemodialyse.backend.contexts.seance.interfaces.jobs.RappelDesRegularisationsPlanifie;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Câblage du contexte Séance (ADR 0088). SESSION A : seuls les adapters qui ne dépendent d'aucun port de la plateforme sont câblés
 * (dépôt JDBC des séances, unité de travail, identifiants, horloge du centre, session, ACL de la couverture et du Stock). Les cas d'usage
 * (créer, scanner, consulter, lister) ne sont PAS exposés : ils attendent les adapters de la plateforme (patients, planning, diffusions,
 * validation, générateurs, volets, forfaits, historique) et le contrôleur, SESSION B. Aucun trafic ne passe par ce module. Les noms de
 * beans portent le suffixe du contexte : les modules voisins ont les mêmes noms de ports.
 */
@Configuration(proxyBeanMethods = false)
public class SeanceConfiguration {

    private static final Duration DELAI_DE_VERROU = Duration.ofSeconds(5);

    @Bean
    Seances seancesDuContexteSeance(JdbcTemplate jdbc) {
        return new SeancesJdbc(jdbc);
    }

    /** Lectures publiées de la Séance ({@code seance.v1}, ADR 0096) : facturation et place d'une séance, pour le Stock (SE7c1). */
    @Bean
    LecturesPublieesDesSeancesJdbc lecturesPublieesDesSeances(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation libelles) {
        return new LecturesPublieesDesSeancesJdbc(jdbc, libelles);
    }

    /**
     * Lectures et écritures publiées de la Séance pour la Facturation (SE7c2, ADR 0097, ARGENT) : séances à facturer, exclusion, forfait imposé
     * et comptages (un seul bean pour les trois ports) ; liaison séance -> facture (le seul écrivain de {@code facture_id} hors de la Séance).
     * Aucune transaction propre : elles participent à celle de l'émission (même {@code JdbcTemplate}).
     */
    @Bean
    FacturationDesSeancesJdbc facturationDesSeances(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.seance.domain.ChaineDesPayeursDuCentre payeurs,
            com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre fiches) {
        return new FacturationDesSeancesJdbc(jdbc, payeurs, fiches);
    }

    /** LEC-PAT (ii), ARGENT (ADR 0177) : la fiche des patients (facturation, centre payeur, identité) vient du langage publié de Patient et couverture. */
    @Bean
    com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre fichesDesPatientsDuCentreDeLaSeance(LotsDePatients lots) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.FichesDesPatientsDuCentreParLaFiche(lots);
    }

    @Bean
    com.hemodialyse.backend.contexts.seance.application.ForfaitDeLaPriseEnCharge forfaitDeLaPriseEnChargeDeLaSeance(CouvertureDesPatients couverture) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.ForfaitDeLaPriseEnChargeParLaCouverture(couverture);
    }

    @Bean
    LiaisonDesSeancesJdbc liaisonDesSeancesAUneFacture(JdbcTemplate jdbc) {
        return new LiaisonDesSeancesJdbc(jdbc);
    }

    /**
     * Lectures publiées de la Séance pour les Absences de patients et la Planification (SE7d, ADR 0098) : séances réalisées, lien
     * d'exclusion, séances réalisées avec leur place (un seul bean pour les trois ports). Lecture seule, sans transaction propre.
     */
    @Bean
    com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LecturesDesSeancesRealiseesJdbc lecturesDesSeancesRealiseesPubliees(
            JdbcTemplate jdbc, com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre fiches) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LecturesDesSeancesRealiseesJdbc(jdbc, fiches);
    }

    /** Fournit à la Planification les séances réalisées avec leur place (SE7d, ADR 0098 ; hébergé par la Séance, ADR 0190). */
    @Bean
    com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies planificationSeancesRealiseesAvecLeurPlaceFournies(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees seances) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.planification.SeancesRealiseesAvecLeurPlaceParLaSeance(seances);
    }

    /** Lecture publiée des constantes des séances pour le Dossier médical (SE7d, ADR 0098). Lecture seule. */
    @Bean
    com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees constantesDesSeancesPubliees(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.ConstantesDesSeancesJdbc(jdbc);
    }

    /**
     * Lectures publiées de la Séance pour la plateforme (SE7e, ADR 0099) : pilotage de la Direction, statistiques du patient, cahier de
     * dialyse, usages d'un forfait. Lecture seule, sans transaction propre.
     */
    @Bean
    com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees seancesRealiseesDesCentresPubliees(
            JdbcTemplate jdbc, com.hemodialyse.backend.contexts.seance.domain.ChaineDesPayeursDuCentre payeurs,
            com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre fiches) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesRealiseesDesCentresJdbc(jdbc, payeurs, fiches);
    }

    /** RF3b (ADR 0127) : la chaîne des payeurs est lue dans l'annuaire de Patient et couverture, plus par jointure SQL. */
    @Bean
    com.hemodialyse.backend.contexts.seance.domain.ChaineDesPayeursDuCentre chaineDesPayeursDeLaSeance(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.ChaineDesPayeursParLAnnuaire(annuaire);
    }

    @Bean
    com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees statistiquesDesSeancesPubliees(
            JdbcTemplate jdbc, com.hemodialyse.backend.contexts.seance.publishedlanguage.PoidsSecDesPrescriptions poidsSecs) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.StatistiquesDesSeancesJdbc(jdbc, poidsSecs);
    }

    /**
     * Les deux comptages (cahier de dialyse, forfait imposé) sont servis par le même adapter {@code SeancesDuCahierEtForfaitsImposesJdbc},
     * exposé au conteneur sous ses deux PORTS seulement (revue SE7e n3) : chaque bean est une référence de méthode qui n'implémente QUE son
     * port (l'instance de l'adapter, qui implémente les deux, n'est pas un bean : Spring apparie par le type réel de l'objet, deux beans de
     * la classe concrète rendraient chaque port ambigu).
     */
    @Bean
    com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees seancesDuCahierPubliees(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesDuCahierEtForfaitsImposesJdbc(jdbc)::nombreDeSeancesAImprimer;
    }

    @Bean
    com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies forfaitsImposesAuxSeancesPublies(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesDuCahierEtForfaitsImposesJdbc(jdbc)::nombreDeSeancesQuiImposentLeForfait;
    }

    /**
     * Séances reprises (SE7e, ADR 0099) : le seul port qui écrit et supprime des séances hors des cas d'usage de la Séance, réservé à la
     * reprise de données (ArchUnit). Participe à la transaction de l'import ou de l'annulation du lot.
     */
    @Bean
    com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees seancesDeLaReprisePubliees(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.persistence.SeancesDeLaRepriseJdbc(jdbc);
    }

    @Bean
    SessionCourante sessionCouranteDeLaSeance() {
        return new SessionLegacy();
    }

    /** Le jour du centre est calculé par la Séance (SE7f, ADR 0100) : fuseau lu dans organisation.v1 (O4 lot A, ADR 0107), défaut de la plateforme. */
    @Bean
    HorlogeDuCentre horlogeDeLaSeance(AnnuaireDesCentres annuaire, @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        return new HorlogeDuCentre(Clock.systemUTC(), new JourDuCentrePublie(annuaire, Clock.systemUTC(), ZoneId.of(fuseauParDefaut)));
    }

    @Bean
    UniteDeTravail uniteDeTravailDeLaSeance(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager), jdbc, DELAI_DE_VERROU);
    }

    @Bean
    GenerateurDIdentifiants generateurDIdentifiantsDeLaSeance() {
        return new GenerateurDIdentifiantsAleatoires();
    }

    @Bean
    EligibiliteALaFacturation eligibiliteALaFacturationDeLaSeance(CouvertureDesPatients couverture) {
        return new EligibiliteParLaCouverture(couverture);
    }

    @Bean
    ConsommationsDeLaSeance consommationsDeLaSeanceDuStock(ConsommationsDeSeance stock) {
        return new ConsommationsDeLaSeanceDuStock(stock);
    }

    // ---- Lectures tenues par la Séance depuis SE7f (ADR 0100 : requêtes des anciens fournisseurs de la plateforme, reprises mot pour mot)

    /** LEC-PAT (i), ADR 0176 : jours de dialyse, sommeil, état et dates lus par la programmation publiée de Patient et couverture. */
    @Bean
    PlanningDesPatients planningDesPatientsDeLaSeance(JdbcTemplate jdbc, ProgrammationDesPatients programmation) {
        return new PlanningDesPatientsParLaFiche(jdbc, programmation);
    }

    @Bean
    GenerateursDesPatients generateursDesPatientsDeLaSeance(FichesDesGenerateurs fiches) {
        // LEC-PLN-GMAO (ADR 0179) : fiche du générateur (fabricant compris) lue par le langage publié gmao.v1
        return new GenerateursDesPatientsParLaGmao(fiches);
    }

    @Bean
    VoletsParamedicaux voletsParamedicauxDeLaSeance(JdbcTemplate jdbc) {
        return new VoletsParamedicauxJdbc(jdbc);
    }

    @Bean
    VoletsDeLaSeance voletsDeLaSeance(@Qualifier("voletsParamedicauxDeLaSeance") VoletsParamedicaux paramedicaux,
                                      @Qualifier("voletsMedicauxDeLaSeance") VoletsMedicaux medicaux) {
        return new VoletsDeLaSeanceJdbc(paramedicaux, medicaux);
    }

    @Bean
    ForfaitsDeSeance forfaitsDeLaSeance(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.seance.application.ForfaitDeLaPriseEnCharge priseEnCharge) {
        return new ForfaitsDeSeanceJdbc(jdbc, priseEnCharge);
    }

    @Bean
    HistoriqueDesSeances historiqueDesSeances(JdbcTemplate jdbc, LotsDePatients lots) {
        // LEC-PAT (iii), ADR 0178 : la fiche des patients (texte, tri, collation) vient du langage publié de Patient et couverture
        return new HistoriqueDesSeancesJdbc(jdbc,
                new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.PatientsDeLHistoriqueParLaFiche(lots));
    }

    /** LEC-PLN-GMAO (ADR 0179) : place habituelle et déplacement temporaire du jour lus par les langages publiés de la Planification. */
    @Bean
    PlaceDuPatient placeDuPatientDeLaSeance(DeplacementsDuJourDesPatients deplacements, PlacesDesPatients places) {
        return new PlaceDuPatientParLaPlanification(deplacements, places);
    }

    // ---- ACL du legacy : traduisent les langages publiés encore fournis par la plateforme (ADR 0088, 0100 : fiches, diffusions, rappels)

    @Bean
    PatientsParLeLegacy patientsDeLaSeance(FichesFournies fiches) {
        return new PatientsParLeLegacy(fiches);
    }

    // ---- Notifications (PL13b, ADR 0154) : catalogue des messages dans la Séance, émission par notifications.v1

    @Bean
    DiffusionsDeSeance diffusionsDeLaSeance(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        return new DiffusionsDeSeanceParLeCentreDeNotifications(notifier, diffusion);
    }

    /** Fournit au Dossier médical le signal de la saisie {@code ANEMIE} (seance.v1). */
    @Bean
    SignalementDesAdministrationsSaisies signalementDesAdministrationsSaisiesParLaSeance(
            NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion, FichesDesPatients patients,
            @Qualifier("sessionCouranteDeLaSeance") SessionCourante session) {
        return new SignalementDesAdministrationsSaisiesParLeCentreDeNotifications(notifier, diffusion, patients, session);
    }

    // ---- SE7f : saisie du volet paramédical (ADR 0100)

    @Bean
    RenseignerLeVoletParamedical renseignerLeVoletParamedical(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                              @Qualifier("seancesDuContexteSeance") Seances seances,
                                                              @Qualifier("voletsParamedicauxDeLaSeance") VoletsParamedicaux volets,
                                                              @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                                              @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                                              @Qualifier("generateurDIdentifiantsDeLaSeance") GenerateurDIdentifiants identifiants,
                                                              @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                                              @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new RenseignerLeVoletParamedical(session, seances, volets, patients, uniteDeTravail, identifiants, horloge, diffusions);
    }

    @Bean
    ConsommationsALaValidation consommationsALaValidationDeLaSeance(CatalogueDArticles catalogue, ConsommationsDeSeance stock) {
        return new ConsommationsALaValidationDuStock(catalogue, stock);
    }

    // ---- SE3 : consommables déclarés et raccourcis (ADR 0090)

    @Bean
    ConsommablesDeLaSeance consommablesDeLaSeanceDuStock(ConsommationsDeSeance stock) {
        return new ConsommablesDeLaSeanceDuStock(stock);
    }

    @Bean
    ArticlesDuCentre articlesDuCentreDeLaSeance(CatalogueDArticles catalogue) {
        return new ArticlesDuCentreDuStock(catalogue);
    }

    /**
     * Raccourcis : adapter JDBC, derrière le cache par centre {@code seance.raccourcis} (KB-SEA-32, reproduit). La purge est posée APRÈS la
     * validation de la transaction.
     */
    @Bean
    RaccourcisDesCentres raccourcisDesCentresDeLaSeance(JdbcTemplate jdbc, CacheManager caches) {
        Cache cache = caches.getCache(RaccourcisDesCentresEnCache.NOM_DU_CACHE);
        if (cache == null) throw new IllegalStateException("Cache " + RaccourcisDesCentresEnCache.NOM_DU_CACHE + " non déclaré");
        return new RaccourcisDesCentresEnCache(new RaccourcisDesCentresJdbc(jdbc), cache);
    }

    @Bean
    AjouterUnConsommable ajouterUnConsommable(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                              @Qualifier("seancesDuContexteSeance") Seances seances,
                                              @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                              @Qualifier("consommablesDeLaSeanceDuStock") ConsommablesDeLaSeance consommables,
                                              @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                              @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new AjouterUnConsommable(session, seances, patients, consommables, uniteDeTravail, diffusions);
    }

    @Bean
    ModifierLaQuantiteDUnConsommable modifierLaQuantiteDUnConsommable(
            @Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
            @Qualifier("seancesDuContexteSeance") Seances seances,
            @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
            @Qualifier("consommablesDeLaSeanceDuStock") ConsommablesDeLaSeance consommables,
            @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
            @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new ModifierLaQuantiteDUnConsommable(session, seances, patients, consommables, uniteDeTravail, diffusions);
    }

    @Bean
    RetirerUnConsommable retirerUnConsommable(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                              @Qualifier("seancesDuContexteSeance") Seances seances,
                                              @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                              @Qualifier("consommablesDeLaSeanceDuStock") ConsommablesDeLaSeance consommables,
                                              @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                              @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new RetirerUnConsommable(session, seances, patients, consommables, uniteDeTravail, diffusions);
    }

    @Bean
    ConsulterLesRaccourcis consulterLesRaccourcis(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                  @Qualifier("raccourcisDesCentresDeLaSeance") RaccourcisDesCentres raccourcis) {
        return new ConsulterLesRaccourcis(session, raccourcis);
    }

    @Bean
    RemplacerLesRaccourcis remplacerLesRaccourcis(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                  @Qualifier("articlesDuCentreDeLaSeance") ArticlesDuCentre articles,
                                                  @Qualifier("raccourcisDesCentresDeLaSeance") RaccourcisDesCentres raccourcis,
                                                  @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail) {
        return new RemplacerLesRaccourcis(session, articles, raccourcis, uniteDeTravail);
    }

    // ---- SE4 : rappel quotidien des séances à régulariser (ADR 0091)

    @Bean
    CentresARappeler centresARappelerDeLaSeance(AnnuaireDesCentres annuaire) {
        return new CentresARappelerPublies(annuaire);
    }

    @Bean
    RappelsDeRegularisation rappelsDeRegularisationDeLaSeance(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        return new RappelsDeRegularisationParLeCentreDeNotifications(notifier, diffusion);
    }

    @Bean
    VerrousDesRappels verrousDesRappelsDeLaSeance(JdbcTemplate jdbc) {
        return new VerrousDesRappelsJdbc(jdbc);
    }

    @Bean
    RappelerLesSeancesARegulariser rappelerLesSeancesARegulariser(
            @Qualifier("centresARappelerDeLaSeance") CentresARappeler centres,
            @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
            @Qualifier("historiqueDesSeances") HistoriqueDesSeances historique,
            @Qualifier("rappelsDeRegularisationDeLaSeance") RappelsDeRegularisation rappels,
            @Qualifier("verrousDesRappelsDeLaSeance") VerrousDesRappels verrous,
            @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail) {
        return new RappelerLesSeancesARegulariser(centres, horloge, historique, rappels, verrous, uniteDeTravail);
    }

    /** L'unique planificateur du rappel (07:00), déclaré ici comme celui du Stock : le legacy n'en a plus (lot B). */
    @Bean
    RappelDesRegularisationsPlanifie rappelDesRegularisationsPlanifie(RappelerLesSeancesARegulariser rappeler) {
        return new RappelDesRegularisationsPlanifie(rappeler);
    }

    // ---- SE5 : imposition d'un forfait à une séance (ADR 0092)

    @Bean
    ForfaitsDuCentre forfaitsDuCentreDeLaSeance(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre catalogue) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.ForfaitsDuCentreParLeCatalogue(catalogue);
    }

    /** RF2 (ADR 0124) : les séances qui imposent un forfait comptent dans son usage ; le catalogue additionne les usages. */
    @Bean
    com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait usageDuForfaitParLesSeances(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies imposes) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture.UsageDuForfaitParLesSeances(imposes);
    }

    @Bean
    ImposerUnForfait imposerUnForfait(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                      @Qualifier("seancesDuContexteSeance") Seances seances,
                                      @Qualifier("forfaitsDuCentreDeLaSeance") ForfaitsDuCentre referentiel,
                                      @Qualifier("forfaitsDeLaSeance") ForfaitsDeSeance forfaits,
                                      @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                      @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge) {
        return new ImposerUnForfait(session, seances, referentiel, forfaits, uniteDeTravail, horloge);
    }

    // ---- SE6 : changement de jour et suppression d'une séance (ADR 0093)

    @Bean
    AnnulationDesConsommations annulationDesConsommationsDeLaSeance(ConsommationsDeSeance stock) {
        return new ConsommationsAnnuleesParLeStock(stock);
    }

    @Bean
    SaisiesDeLaSeance saisiesDeLaSeance(SaisiesDeSeanceFournies administrations,
                                        @Qualifier("voletsParamedicauxDeLaSeance") VoletsParamedicaux voletsParamedicaux,
                                        @Qualifier("voletsMedicauxDeLaSeance") VoletsMedicaux voletsMedicaux) {
        return new SaisiesParLeLegacy(administrations, voletsParamedicaux, voletsMedicaux);
    }

    @Bean
    JournalDesSuppressions journalDesSuppressionsDeLaSeance(JdbcTemplate jdbc) {
        return new JournalDesSuppressionsJdbc(jdbc);
    }

    @Bean
    ChangerLeJourDUneSeance changerLeJourDUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                    @Qualifier("seancesDuContexteSeance") Seances seances,
                                                    @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                                    @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge) {
        return new ChangerLeJourDUneSeance(session, seances, uniteDeTravail, horloge);
    }

    @Bean
    SupprimerUneSeance supprimerUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                          @Qualifier("seancesDuContexteSeance") Seances seances,
                                          @Qualifier("annulationDesConsommationsDeLaSeance") AnnulationDesConsommations consommations,
                                          @Qualifier("saisiesDeLaSeance") SaisiesDeLaSeance saisies,
                                          @Qualifier("journalDesSuppressionsDeLaSeance") JournalDesSuppressions journal,
                                          @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                          @Qualifier("generateurDIdentifiantsDeLaSeance") GenerateurDIdentifiants identifiants,
                                          @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                          @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new SupprimerUneSeance(session, seances, consommations, saisies, journal, uniteDeTravail, identifiants, horloge, diffusions);
    }

    // ---- SE7a : journal du jour, calendrier du centre, volet médical, séances récentes (ADR 0094)

    @Bean
    CalendrierDuCentre calendrierDuCentreDeLaSeance(JdbcTemplate jdbc) {
        return new CalendrierDuCentreJdbc(jdbc);
    }

    @Bean
    VoletsMedicaux voletsMedicauxDeLaSeance(JdbcTemplate jdbc) {
        return new VoletsMedicauxJdbc(jdbc);
    }

    @Bean
    PatientsDuJour patientsDuJourDeLaSeance(JdbcTemplate jdbc, LotsDePatients lots) {
        return new PatientsDuJourParLaFiche(jdbc, lots);
    }

    @Bean
    SortiesDuJour sortiesDuJourDeLaSeance(ConsommationsDeSeance stock) {
        return new SortiesDuJourDuStock(stock);
    }

    @Bean
    ConsulterLeJournalDuJour consulterLeJournalDuJour(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                      @Qualifier("patientsDuJourDeLaSeance") PatientsDuJour patients,
                                                      @Qualifier("sortiesDuJourDeLaSeance") SortiesDuJour sorties) {
        return new ConsulterLeJournalDuJour(session, patients, sorties);
    }

    @Bean
    ConsulterLeCalendrier consulterLeCalendrier(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                @Qualifier("calendrierDuCentreDeLaSeance") CalendrierDuCentre calendrier) {
        return new ConsulterLeCalendrier(session, calendrier);
    }

    @Bean
    AjouterUnJourAuCalendrier ajouterUnJourAuCalendrier(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                        @Qualifier("calendrierDuCentreDeLaSeance") CalendrierDuCentre calendrier,
                                                        @Qualifier("generateurDIdentifiantsDeLaSeance") GenerateurDIdentifiants identifiants) {
        return new AjouterUnJourAuCalendrier(session, calendrier, identifiants);
    }

    @Bean
    RetirerUnJourDuCalendrier retirerUnJourDuCalendrier(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                        @Qualifier("calendrierDuCentreDeLaSeance") CalendrierDuCentre calendrier) {
        return new RetirerUnJourDuCalendrier(session, calendrier);
    }

    @Bean
    RenseignerLeVoletMedical renseignerLeVoletMedical(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                      @Qualifier("seancesDuContexteSeance") Seances seances,
                                                      @Qualifier("voletsMedicauxDeLaSeance") VoletsMedicaux volets,
                                                      @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                                      @Qualifier("generateurDIdentifiantsDeLaSeance") GenerateurDIdentifiants identifiants,
                                                      @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge) {
        return new RenseignerLeVoletMedical(session, seances, volets, uniteDeTravail, identifiants, horloge);
    }

    @Bean
    ConsulterLesSeancesRecentes consulterLesSeancesRecentes(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                            @Qualifier("seancesDuContexteSeance") Seances seances,
                                                            @Qualifier("voletsDeLaSeance") VoletsDeLaSeance volets,
                                                            @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge) {
        return new ConsulterLesSeancesRecentes(session, seances, volets, horloge);
    }

    // ---- SE7b : tableau de bord mensuel des séances, détails et exports (ADR 0095)

    @Bean
    SeancesRealisees seancesRealiseesDeLaSeance(JdbcTemplate jdbc) {
        return new SeancesRealiseesJdbc(jdbc);
    }

    @Bean
    PatientsDuTableauDeBord patientsDuTableauDeBordDeLaSeance(JdbcTemplate jdbc, ProgrammationDesPatients programmation,
                                                              LotsDePatients lots) {
        return new PatientsDuTableauDeBordParLaFiche(jdbc, programmation, lots);
    }

    @Bean
    AbsencesDuCentre absencesDuCentreDeLaSeance(AbsencesDuPilotage pilotage) {
        return new AbsencesDuCentreParLePilotage(pilotage);
    }

    @Bean
    JoursBloques joursBloquesDeLaSeance(@Qualifier("calendrierDuCentreDeLaSeance") CalendrierDuCentre calendrier) {
        return new JoursBloquesDuCalendrier(calendrier);
    }

    @Bean
    ExportsDuTableauDeBord exportsDuTableauDeBordDeLaSeance() {
        return new ExportsDuTableauDeBordCsvPdfXlsx();
    }

    @Bean
    ConsulterLeTableauDeBord consulterLeTableauDeBord(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                      @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                                      @Qualifier("seancesRealiseesDeLaSeance") SeancesRealisees seances,
                                                      @Qualifier("patientsDuTableauDeBordDeLaSeance") PatientsDuTableauDeBord patients,
                                                      @Qualifier("absencesDuCentreDeLaSeance") AbsencesDuCentre absences,
                                                      @Qualifier("joursBloquesDeLaSeance") JoursBloques joursBloques) {
        return new ConsulterLeTableauDeBord(session, horloge, seances, patients, absences, joursBloques);
    }

    @Bean
    ConsulterLeDetailDuTableauDeBord consulterLeDetailDuTableauDeBord(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                                      @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                                                      @Qualifier("seancesRealiseesDeLaSeance") SeancesRealisees seances,
                                                                      @Qualifier("patientsDuTableauDeBordDeLaSeance") PatientsDuTableauDeBord patients,
                                                                      @Qualifier("absencesDuCentreDeLaSeance") AbsencesDuCentre absences) {
        return new ConsulterLeDetailDuTableauDeBord(session, horloge, seances, patients, absences);
    }

    @Bean
    ExporterLeTableauDeBord exporterLeTableauDeBord(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                    ConsulterLeTableauDeBord tableau,
                                                    @Qualifier("exportsDuTableauDeBordDeLaSeance") ExportsDuTableauDeBord exports) {
        return new ExporterLeTableauDeBord(session, tableau, exports);
    }

    // ---- Cas d'usage (appelés par le contrôleur de l'interface)

    @Bean
    ValiderUneSeance validerUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                      @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                      @Qualifier("seancesDuContexteSeance") Seances seances,
                                      @Qualifier("placeDuPatientDeLaSeance") PlaceDuPatient places,
                                      @Qualifier("consommationsALaValidationDeLaSeance") ConsommationsALaValidation consommations,
                                      @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                      @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                      @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new ValiderUneSeance(session, patients, seances, places, consommations, uniteDeTravail, horloge, diffusions);
    }

    @Bean
    SignerUneSeance signerUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                    @Qualifier("seancesDuContexteSeance") Seances seances,
                                    @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                    @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge) {
        return new SignerUneSeance(session, seances, uniteDeTravail, horloge);
    }

    @Bean
    DeverrouillerUneSeance deverrouillerUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                                  @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                                  @Qualifier("seancesDuContexteSeance") Seances seances,
                                                  @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                                  @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                                  @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new DeverrouillerUneSeance(session, patients, seances, uniteDeTravail, horloge, diffusions);
    }

    @Bean
    CreerUneSeance creerUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                  @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                  @Qualifier("generateursDesPatientsDeLaSeance") GenerateursDesPatients generateurs,
                                  @Qualifier("eligibiliteALaFacturationDeLaSeance") EligibiliteALaFacturation eligibilite,
                                  @Qualifier("seancesDuContexteSeance") Seances seances,
                                  @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                  @Qualifier("generateurDIdentifiantsDeLaSeance") GenerateurDIdentifiants identifiants,
                                  @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                  @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new CreerUneSeance(session, patients, generateurs, eligibilite, seances, uniteDeTravail, identifiants, horloge,
                diffusions);
    }

    @Bean
    ScannerUnPatient scannerUnPatient(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                      @Qualifier("patientsDeLaSeance") PatientsParLeLegacy patients,
                                      @Qualifier("generateursDesPatientsDeLaSeance") GenerateursDesPatients generateurs,
                                      @Qualifier("planningDesPatientsDeLaSeance") PlanningDesPatients planning,
                                      @Qualifier("eligibiliteALaFacturationDeLaSeance") EligibiliteALaFacturation eligibilite,
                                      @Qualifier("seancesDuContexteSeance") Seances seances,
                                      @Qualifier("placeDuPatientDeLaSeance") PlaceDuPatient places,
                                      @Qualifier("uniteDeTravailDeLaSeance") UniteDeTravail uniteDeTravail,
                                      @Qualifier("generateurDIdentifiantsDeLaSeance") GenerateurDIdentifiants identifiants,
                                      @Qualifier("horlogeDeLaSeance") HorlogeDuCentre horloge,
                                      @Qualifier("diffusionsDeLaSeance") DiffusionsDeSeance diffusions) {
        return new ScannerUnPatient(session, new ResoudreLePatientScanne(patients), patients, generateurs, planning, eligibilite,
                seances, places, uniteDeTravail, identifiants, horloge, diffusions);
    }

    @Bean
    ConsulterUneSeance consulterUneSeance(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                          @Qualifier("seancesDuContexteSeance") Seances seances,
                                          @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
                                          @Qualifier("generateursDesPatientsDeLaSeance") GenerateursDesPatients generateurs,
                                          @Qualifier("voletsDeLaSeance") VoletsDeLaSeance volets,
                                          @Qualifier("consommationsDeLaSeanceDuStock") ConsommationsDeLaSeance consommations,
                                          @Qualifier("forfaitsDeLaSeance") ForfaitsDeSeance forfaits) {
        return new ConsulterUneSeance(session, seances, patients, generateurs, volets, consommations, forfaits);
    }

    @Bean
    ListerLesSeances listerLesSeances(@Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
                                      @Qualifier("historiqueDesSeances") HistoriqueDesSeances historique,
                                      @Qualifier("forfaitsDeLaSeance") ForfaitsDeSeance forfaits) {
        return new ListerLesSeances(session, historique, forfaits);
    }

    /** Cahier de dialyse (PL5, ADR 0144) : séances comptées par le langage publié de la Séance, rendu confié à Documents (documents.v1). */
    @Bean
    com.hemodialyse.backend.contexts.seance.application.SeancesDuCahier seancesDuCahierDeLaSeance(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees publiees) {
        return (centre, patient, periode) -> publiees.nombreDeSeancesAImprimer(centre.valeur(), patient.valeur(), periode.du(), periode.au());
    }

    @Bean
    com.hemodialyse.backend.contexts.seance.application.RenduDuCahier renduDuCahierDeLaSeance(
            com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument documents) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.documents.CahierRenduParDocuments(documents);
    }

    @Bean
    com.hemodialyse.backend.contexts.seance.application.ImprimerLeCahierDeDialyse imprimerLeCahierDeDialyse(
            @Qualifier("sessionCouranteDeLaSeance") SessionCourante session,
            @Qualifier("patientsDeLaSeance") PatientsDeLaSeance patients,
            @Qualifier("seancesDuCahierDeLaSeance") com.hemodialyse.backend.contexts.seance.application.SeancesDuCahier seances,
            @Qualifier("renduDuCahierDeLaSeance") com.hemodialyse.backend.contexts.seance.application.RenduDuCahier rendu) {
        return new com.hemodialyse.backend.contexts.seance.application.ImprimerLeCahierDeDialyse(session, patients, seances, rendu, Clock.systemUTC());
    }

    /** PL11-2 (ADR 0158) : ce que les séances retiennent d'un créneau de l'Organisation (suppression ou désactivation). */
    @Bean
    com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDuCreneau usageDuCreneauParLesSeances(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation.UsageDuCreneauParLesSeances(jdbc);
    }

    /** PL11-4 (ADR 0160) : ce que les séances retiennent d'une salle de l'Organisation (suppression ou désactivation). */
    @Bean
    com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle usageDeLaSalleParLesSeances(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.organisation.UsageDeLaSalleParLesSeances(jdbc);
    }

    /** Fournit au Stock le statut facturé d'une séance (SE7c1, ADR 0096 ; hébergé par la Séance, ADR 0191). */
    @Bean
    com.hemodialyse.backend.contexts.stock.publishedlanguage.StatutDesSeancesFourni stockStatutDesSeancesFourni(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee seances) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.StatutDesSeancesParLaSeance(seances);
    }

    /** Fournit au Stock le périmètre d'une séance (SE7c1, ADR 0096 ; hébergé par la Séance, ADR 0191). */
    @Bean
    com.hemodialyse.backend.contexts.stock.publishedlanguage.PerimetreDesSeancesFourni stockPerimetreDesSeancesFourni(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees places) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.PerimetreDesSeancesParLaSeance(places);
    }

    /** Fournit à la Facturation la liaison séance -> facture (SE7c2, ADR 0097 ; hébergé par la Séance, ADR 0191). */
    @Bean
    com.hemodialyse.backend.contexts.facturation.publishedlanguage.LiaisonDesSeancesFournie facturationLiaisonDesSeancesFournie(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.LiaisonDesSeancesAUneFacturePubliee liaison) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.facturation.LiaisonDesSeancesParLaSeance(liaison);
    }

    /** Fournit à la Facturation l'exclusion, la réinclusion et le forfait imposé des séances (SE7c2, ADR 0097 ; ADR 0191). */
    @Bean
    com.hemodialyse.backend.contexts.facturation.publishedlanguage.ExclusionsDeSeancesFournies facturationExclusionsDeSeancesFournies(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies seances) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.facturation.SeancesDeFacturationParLaSeance(seances);
    }

    /** Fournit à la Facturation les comptages de séances des factures (SE7c2, ADR 0097 ; ADR 0191). */
    @Bean
    com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies facturationSeancesDesFacturesFournies(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees seances) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.facturation.SeancesDesFacturesParLaSeance(seances);
    }

    /** Fournit à la Facturation les séances à facturer (SE7c2, ADR 0097 ; ADR 0191). */
    @Bean
    com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies facturationSeancesAFacturerFournies(
            com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees seances) {
        return new com.hemodialyse.backend.contexts.seance.infrastructure.acl.facturation.SeancesFacturablesParLaSeance(seances);
    }
}
