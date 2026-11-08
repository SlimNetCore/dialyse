package com.hemodialyse.backend.contexts.seance.configuration;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
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
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.DiffusionsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.ForfaitsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.GenerateursParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.HistoriqueParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PatientsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PlanningDesPatientsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.VoletsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DiffusionsFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.GenerateursFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.JourDuCentreFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlanningFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.VoletsFournis;
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
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PlaceParLePlanning;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock.ConsommationsALaValidationDuStock;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlaceFournie;
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
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyorganisation.CentresARappelerJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.RappelsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VerrousDesRappelsJdbc;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.RappelsFournis;
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

    @Bean
    SessionCourante sessionCouranteDeLaSeance() {
        return new SessionLegacy();
    }

    @Bean
    HorlogeDuCentre horlogeDeLaSeance(JourDuCentreFourni jourDuCentre) {
        return new HorlogeDuCentre(Clock.systemUTC(), jourDuCentre::aujourdhui);
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

    // ---- ACL du legacy : traduisent les langages publiés fournis par la plateforme (ADR 0088)

    @Bean
    PlanningDesPatients planningDesPatientsDeLaSeance(PlanningFourni planning) {
        return new PlanningDesPatientsParLeLegacy(planning);
    }

    @Bean
    PatientsParLeLegacy patientsDeLaSeance(FichesFournies fiches) {
        return new PatientsParLeLegacy(fiches);
    }

    @Bean
    GenerateursDesPatients generateursDesPatientsDeLaSeance(GenerateursFournis generateurs) {
        return new GenerateursParLeLegacy(generateurs);
    }

    @Bean
    VoletsDeLaSeance voletsDeLaSeance(VoletsFournis volets) {
        return new VoletsParLeLegacy(volets);
    }

    @Bean
    ForfaitsDeSeance forfaitsDeLaSeance(ForfaitsFournis forfaits) {
        return new ForfaitsParLeLegacy(forfaits);
    }

    @Bean
    HistoriqueDesSeances historiqueDesSeances(HistoriqueFourni historique) {
        return new HistoriqueParLeLegacy(historique);
    }

    @Bean
    DiffusionsDeSeance diffusionsDeLaSeance(DiffusionsFournies diffusions) {
        return new DiffusionsParLeLegacy(diffusions);
    }

    @Bean
    PlaceDuPatient placeDuPatientDeLaSeance(PlaceFournie places) {
        return new PlaceParLePlanning(places);
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
    CentresARappeler centresARappelerDeLaSeance(JdbcTemplate jdbc) {
        return new CentresARappelerJdbc(jdbc);
    }

    @Bean
    RappelsDeRegularisation rappelsDeRegularisationDeLaSeance(RappelsFournis rappels) {
        return new RappelsParLeLegacy(rappels);
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
}
