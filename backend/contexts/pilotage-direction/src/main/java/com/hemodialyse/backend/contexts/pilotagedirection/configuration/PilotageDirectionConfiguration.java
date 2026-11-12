package com.hemodialyse.backend.contexts.pilotagedirection.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.TraitementDeLAnemiePublie;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstituerLeRapportDeSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerLesInstantanesDuMoisEcoule;
import com.hemodialyse.backend.contexts.pilotagedirection.application.CapturerUnInstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaRepartition;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterUnInstantane;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ContenuDuMoisAFiger;
import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ListerLesInstantanes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesActives;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.ContenuFigeParLeTableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.InstantanesMensuelsJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.planifie.CaptureDuMoisEcoulePlanifiee;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.InstantanesMensuelsPublies;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.IndicateursCliniquesPublies;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLesIndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.application.IndicateursCliniquesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.dossiermedical.CliniquesParLeDossierMedical;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.indicateurs.IndicateursDuRapportParLesIndicateursPublies;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.stock.StockParLeStock;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.IndicateursPublies;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.IndicateursDuStock;
import tools.jackson.databind.ObjectMapper;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLHistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EnregistrerLesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SuivreLeTempsReel;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.tableaudebord.ConstatsDuTableauDeBordParLeTableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.diffusion.DiffusionParLeCanalFourni;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.HistoriqueDesAlertesJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.organisation.SocieteDUnCentrePublie;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.planifie.TempsReelPlanifie;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.AlertesPubliees;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.TempsReelPublie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AlertesDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.DiffusionDuTableauDeBordFournie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TempsReelDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ConsulterLaVueDEnsemble;
import com.hemodialyse.backend.contexts.pilotagedirection.application.EffectifsDesPatients;
import com.hemodialyse.backend.contexts.pilotagedirection.application.FacturationDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HorlogeDuPilotage;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SeancesRealiseesDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SocietesSuivies;
import com.hemodialyse.backend.contexts.pilotagedirection.application.TraitementsDeLAnemieDesCentres;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.dossiermedical.AnemieParLeDossierMedical;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.facturation.FacturationParLaFacturation;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.seance.SeancesRealiseesParLaSeance;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.PatientsDesCentresJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.organisation.SocietesEtCentresPublies;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.RapportDeSynthesePublie;
import com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie.TableauDeBordPublie;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Composition du lot A (PD1 + PD4, ADR 0102) : le tableau de bord et le rapport de synthèse de la direction, publiés. Mêmes tables
 * ({@code societes}, {@code centers}, {@code patients}) et mêmes URL (servies par la plateforme) que le legacy ; aucun flag, aucune
 * façade. L'horloge est celle du fuseau de référence Africa/Algiers (KB-DIR-03, lot L6). Les indicateurs (cliniques du Dossier médical, stock du Stock) sont
 * COMPOSÉS par le Pilotage depuis PD6 (ADR 0106) : {@link IndicateursDeLaDirection} ; le rapport les lit par
 * {@link IndicateursDeLaDirectionFournis}, dont l'adaptateur est du module.
 */
@Configuration(proxyBeanMethods = false)
public class PilotageDirectionConfiguration {

    /**
     * KB-DIR-03 corrigé (lot L6, ADR 0119) : « aujourd'hui » est le jour du fuseau de référence {@code Africa/Algiers} (propriété
     * {@code app.pilotage.fuseau-par-defaut}), et non celui de la JVM. Les instants viennent d'une {@link Clock} INJECTÉE, celle du bean
     * {@link InstantsDuPilotage} s'il existe (les tests le remplacent par une horloge fixe), sinon l'horloge système en UTC.
     */
    @Bean
    HorlogeDuPilotage horlogeDuPilotage(ObjectProvider<InstantsDuPilotage> instants,
                                        @Value(HorlogeDuPilotage.PROPRIETE_DU_FUSEAU) String fuseau) {
        Clock horloge = instants.getIfAvailable(() -> new InstantsDuPilotage(Clock.systemUTC())).horloge();
        return HorlogeDuPilotage.auFuseauDeReference(horloge, ZoneId.of(fuseau));
    }

    @Bean
    SocietesEtCentresPublies societesSuivies(AnnuaireDesSocietes annuaire) {
        return new SocietesEtCentresPublies(annuaire);
    }

    // ───────────── Lot B (PD2, ADR 0103) : les instantanés mensuels figés ─────────────

    @Bean
    InstantanesMensuels instantanesMensuels(JdbcTemplate jdbc) {
        return new InstantanesMensuelsJdbc(jdbc);
    }

    @Bean
    ContenuDuMoisAFiger contenuDuMoisAFiger(TableauDeBordDeLaDirection tableau, IndicateursDeLaDirection indicateurs,
                                            ObjectMapper mapper) {
        return new ContenuFigeParLeTableauDeBord(tableau, indicateurs, mapper);
    }

    @Bean
    CapturerLesInstantanesDuMoisEcoule capturerLesInstantanesDuMoisEcoule(SocietesActives societes, InstantanesMensuels instantanes,
                                                                          ContenuDuMoisAFiger contenu, HorlogeDuPilotage horloge) {
        return new CapturerLesInstantanesDuMoisEcoule(societes, instantanes, contenu, horloge);
    }

    @Bean
    InstantanesMensuelsDeLaDirection instantanesMensuelsDeLaDirection(InstantanesMensuels instantanes, ContenuDuMoisAFiger contenu,
                                                                      CapturerLesInstantanesDuMoisEcoule capturerLeMoisEcoule,
                                                                      HorlogeDuPilotage horloge, ObjectMapper mapper) {
        return new InstantanesMensuelsPublies(new ListerLesInstantanes(instantanes), new ConsulterUnInstantane(instantanes),
                new CapturerUnInstantaneMensuel(instantanes, contenu, horloge), capturerLeMoisEcoule, mapper);
    }

    /** Le cron du 1er du mois à 02:30 : un bean planifié du module (même expression que l'ancien {@code DirectionSnapshotScheduler}). */
    @Bean
    CaptureDuMoisEcoulePlanifiee captureDuMoisEcoulePlanifiee(CapturerLesInstantanesDuMoisEcoule capture) {
        return new CaptureDuMoisEcoulePlanifiee(capture);
    }

    // ───────────── Lot C (PD3, ADR 0104) : les alertes de la direction et leur temps réel ─────────────

    @Bean
    HistoriqueDesAlertes historiqueDesAlertes(JdbcTemplate jdbc) {
        return new HistoriqueDesAlertesJdbc(jdbc);
    }

    @Bean
    EnregistrerLesAlertes enregistrerLesAlertes(HistoriqueDesAlertes historique, HorlogeDuPilotage horloge) {
        return new EnregistrerLesAlertes(historique, horloge);
    }

    @Bean
    AlertesDeLaDirection alertesDeLaDirection(EnregistrerLesAlertes enregistrer, HistoriqueDesAlertes historique) {
        return new AlertesPubliees(enregistrer, new ConsulterLHistoriqueDesAlertes(historique));
    }

    @Bean
    SuivreLeTempsReel suivreLeTempsReel(TableauDeBordDeLaDirection tableau, IndicateursDeLaDirection indicateurs,
                                        EnregistrerLesAlertes alertes, AnnuaireDesSocietes annuaire,
                                        DiffusionDuTableauDeBordFournie canal, HorlogeDuPilotage horloge) {
        return new SuivreLeTempsReel(new ConstatsDuTableauDeBordParLeTableauDeBord(tableau, indicateurs), alertes,
                new SocieteDUnCentrePublie(annuaire), new DiffusionParLeCanalFourni(canal), horloge);
    }

    @Bean
    TempsReelDeLaDirection tempsReelDeLaDirection(SuivreLeTempsReel tempsReel) {
        return new TempsReelPublie(tempsReel);
    }

    /** Les cadences du temps réel : un bean planifié du module (mêmes délais que l'ancien {@code DirectionRealtimeScheduler}). */
    @Bean
    TempsReelPlanifie tempsReelPlanifie(SuivreLeTempsReel tempsReel) {
        return new TempsReelPlanifie(tempsReel);
    }

    @Bean
    EffectifsDesPatients effectifsDesPatients(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.pilotagedirection.application.CaissesDesCentresPayeurs caisses) {
        return new PatientsDesCentresJdbc(jdbc, caisses);
    }

    /** RF3b (ADR 0127) : la caisse d'un centre payeur est lue dans l'annuaire de Patient et couverture, plus par jointure SQL. */
    @Bean
    com.hemodialyse.backend.contexts.pilotagedirection.application.CaissesDesCentresPayeurs caissesDesCentresPayeurs(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.patientcouverture.CaissesParLAnnuaireDesPayeurs(annuaire);
    }

    @Bean
    SeancesRealiseesDesCentres seancesRealiseesDesCentres(SeancesRealiseesDesCentresPubliees seance) {
        return new SeancesRealiseesParLaSeance(seance);
    }

    @Bean
    FacturationDesCentres facturationDesCentres(SyntheseFinanciereDesCentres facturation) {
        return new FacturationParLaFacturation(facturation);
    }

    @Bean
    TraitementsDeLAnemieDesCentres traitementsDeLAnemieDesCentres(TraitementDeLAnemiePublie dossierMedical) {
        return new AnemieParLeDossierMedical(dossierMedical);
    }

    @Bean
    TableauDeBordDeLaDirection tableauDeBordDeLaDirection(SocietesSuivies societes, EffectifsDesPatients effectifs,
                                                          SeancesRealiseesDesCentres seances, FacturationDesCentres facturation,
                                                          TraitementsDeLAnemieDesCentres anemie, HorlogeDuPilotage horloge) {
        return new TableauDeBordPublie(new ConsulterLaSociete(societes),
                new ConsulterLaVueDEnsemble(societes, effectifs, seances, facturation, horloge),
                new ConsulterLaRepartition(societes, effectifs, seances, facturation, anemie, horloge));
    }

    // ───────────── PD6 (ADR 0106) : les indicateurs de la direction, composés par le Pilotage ─────────────

    @Bean
    IndicateursCliniquesDesCentres indicateursCliniquesDesCentres(IndicateursCliniquesPublies dossierMedical) {
        return new CliniquesParLeDossierMedical(dossierMedical);
    }

    @Bean
    StockDesCentres stockDesCentres(IndicateursDuStock stock) {
        return new StockParLeStock(stock);
    }

    @Bean
    IndicateursDeLaDirection indicateursDeLaDirection(SocietesSuivies societes, IndicateursCliniquesDesCentres cliniques,
                                                      StockDesCentres stocks, HorlogeDuPilotage horloge) {
        return new IndicateursPublies(new ConsulterLesIndicateursDeLaDirection(societes, cliniques, stocks, horloge));
    }

    /** Le rapport de synthèse lit les indicateurs sous la forme de {@link IndicateursDeLaDirectionFournis} (adaptateur du module). */
    @Bean
    IndicateursDeLaDirectionFournis indicateursDuRapport(IndicateursDeLaDirection indicateurs) {
        return new IndicateursDuRapportParLesIndicateursPublies(indicateurs);
    }

    @Bean
    ConstituerLeRapportDeSynthese constituerLeRapportDeSynthese(TableauDeBordDeLaDirection tableau,
                                                                IndicateursDeLaDirectionFournis indicateurs,
                                                                HorlogeDuPilotage horloge) {
        return new ConstituerLeRapportDeSynthese(tableau, indicateurs, horloge);
    }

    @Bean
    RapportDeSyntheseDeLaDirection rapportDeSyntheseDeLaDirection(ConstituerLeRapportDeSynthese rapport) {
        return new RapportDeSynthesePublie(rapport);
    }

    // ───────────── PL9 (ADR 0148) : le rapport PDF de la direction, rendu par Documents (documents.v1) ─────────────

    @Bean
    com.hemodialyse.backend.contexts.pilotagedirection.application.SessionDeLaDirection sessionDeLaDirection(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.legacyidentite.SessionLegacy(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.pilotagedirection.application.ImprimerLeRapport imprimerLeRapport(
            ConstituerLeRapportDeSynthese rapport, InstantanesMensuelsDeLaDirection instantanes, SocietesSuivies societes,
            com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument documents,
            @Value(HorlogeDuPilotage.PROPRIETE_DU_FUSEAU) String fuseau) {
        return new com.hemodialyse.backend.contexts.pilotagedirection.application.ImprimerLeRapport(rapport,
                new com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.instantane.SourceDuRapportParLesInstantanes(instantanes),
                societes,
                new com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.documents.RapportRenduParDocuments(documents),
                ZoneId.of(fuseau));
    }
}
