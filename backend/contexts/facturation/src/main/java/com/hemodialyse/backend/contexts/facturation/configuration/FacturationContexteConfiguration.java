package com.hemodialyse.backend.contexts.facturation.configuration;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PeriodesComptables;
import com.hemodialyse.backend.contexts.facturation.application.cloture.AbsencesDuMois;
import com.hemodialyse.backend.contexts.facturation.application.cloture.ClotureComptable;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.absences.AbsencesDuMoisPubliees;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ClotureDesAbsences;
import com.hemodialyse.backend.contexts.facturation.application.cloture.CloturerLeMois;
import com.hemodialyse.backend.contexts.facturation.application.cloture.ConsulterLesPeriodes;
import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.facturation.application.cloture.PublicationDesPeriodes;
import com.hemodialyse.backend.contexts.facturation.application.cloture.RouvrirLeMois;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.ClotureComptableComptabilite;
import com.hemodialyse.backend.contexts.facturation.infrastructure.messaging.PublicationDesPeriodesSpring;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.MoisDeFacturationJdbc;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.FacturesEmisesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.ReglementsEncaissesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.FacturationDuPatientJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.RepriseDesSoldesJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.DeclarationDesAbsences;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseDeFacturation;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.absences.AbsencesDesPatientsPubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.parametres.ConfigurerLaFacturation;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.ConsulterLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.ConsulterLeTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ImpressionDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.ImprimerLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.application.reglement.ConsulterLesReglements;
import com.hemodialyse.backend.contexts.facturation.application.reglement.EnregistrerUnReglement;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PublicationDesReglements;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;
import com.hemodialyse.backend.contexts.facturation.infrastructure.messaging.PublicationDesReglementsSpring;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.LectureDeLaSyntheseJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.LectureDuTableauDeBordJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ReglementsJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SyntheseFinanciereDesCentresJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SituationsDesFacturesJdbc;
import com.hemodialyse.backend.contexts.facturation.application.simulation.AbsencesDesPatients;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ChangerLeForfaitDUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ExclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.ReinclureUneSeance;
import com.hemodialyse.backend.contexts.facturation.application.simulation.SimulerLaFacturation;
import com.hemodialyse.backend.contexts.facturation.application.simulation.TauxDeTvaApplicable;
import com.hemodialyse.backend.contexts.facturation.application.validation.PublicationDeLaValidation;
import com.hemodialyse.backend.contexts.facturation.application.validation.ValiderLaFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite.TauxDeTvaComptabilite;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance.LiaisonDesSeancesPubliee;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance.SeancesDeFacturationPubliees;
import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance.SeancesFacturablesPubliees;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ExclusionsDeSeancesFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.LiaisonDesSeancesFournie;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.facturation.infrastructure.messaging.PublicationDeLaValidationSpring;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.FacturesJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ParametresDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.SequenceDeFacturationJdbc;
import com.hemodialyse.backend.contexts.facturation.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.facturation.infrastructure.horloge.FuseauParDefaut;
import com.hemodialyse.backend.contexts.facturation.application.cloture.FuseauxDesCentres;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Composition root du contexte Facturation : seul endroit qui connaît toutes les couches. Slice F1 : paramètres de
 * facturation du centre ; slice F2 : simulation de facturation ; slice F3 : validation et émission des factures. Les noms de beans sont préfixés {@code facturation} (un seul
 * contexte Spring avec le legacy et les autres contextes) ; l'horloge n'est pas un bean, pour ne pas concurrencer celle de la
 * Comptabilité. {@link AbsencesDesPatients} passe par le langage publié {@link DeclarationDesAbsences}, fourni par le contexte {@code absences-patients} (A2).
 */
@Configuration(proxyBeanMethods = false)
public class FacturationContexteConfiguration {

    @Bean
    SessionCourante facturationSessionCourante() {
        return new SessionLegacy();
    }

    /**
     * Délai d'attente d'un verrou de ligne posé par la transaction qui l'ouvre (ADR 0043 : même délai que les Absences).
     */
    static final Duration DELAI_DE_VERROU = Duration.ofSeconds(5);

    @Bean
    UniteDeTravail facturationUniteDeTravail(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager), jdbc, DELAI_DE_VERROU);
    }

    @Bean
    ParametresDeFacturationRepository facturationParametres(JdbcTemplate jdbc) {
        return new ParametresDeFacturationJdbc(jdbc);
    }

    @Bean
    ConfigurerLaFacturation facturationConfigurerLaFacturation(ParametresDeFacturationRepository parametres,
                                                               UniteDeTravail uniteDeTravail) {
        return new ConfigurerLaFacturation(parametres, uniteDeTravail, Clock.systemUTC());
    }

    // Les séances (ARGENT) viennent du langage publié FOURNI par la plateforme à partir de seance.v1 (ADR 0097) : Facturation ne peut pas
    // dépendre du module Séance (cycle Maven Séance - Absences-patients - Facturation).
    @Bean
    SeancesFacturables facturationSeancesFacturables(SeancesAFacturerFournies seances, CouvertureDesPatients couverture) {
        return new SeancesFacturablesPubliees(seances, couverture);
    }

    @Bean
    SeancesDeFacturation facturationSeancesDeFacturation(ExclusionsDeSeancesFournies seances) {
        return new SeancesDeFacturationPubliees(seances);
    }

    @Bean
    LiaisonDesSeances facturationLiaisonDesSeances(LiaisonDesSeancesFournie liaison) {
        return new LiaisonDesSeancesPubliee(liaison);
    }

    @Bean
    ForfaitsDuCentre facturationForfaitsDuCentre(
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre catalogue) {
        return new com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.ForfaitsDuCentreParLeCatalogue(catalogue);
    }

    /** RF2 (ADR 0124) : les lignes de facture comptent dans l'usage d'un forfait ; le catalogue additionne les usages. */
    @Bean
    com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait usageDuForfaitParLesFactures(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.UsageDuForfaitParLesFactures(jdbc);
    }

    @Bean
    SequenceDeFacturation facturationSequence(JdbcTemplate jdbc) {
        return new SequenceDeFacturationJdbc(jdbc);
    }

    @Bean
    TauxDeTvaApplicable facturationTauxDeTva(ParametrageFiscal parametrageFiscal) {
        return new TauxDeTvaComptabilite(parametrageFiscal);
    }

    @Bean
    SimulerLaFacturation facturationSimulerLaFacturation(SeancesFacturables seances, ParametresDeFacturationRepository parametres,
                                                         SequenceDeFacturation sequence, TauxDeTvaApplicable tva,
                                                         UniteDeTravail uniteDeTravail) {
        return new SimulerLaFacturation(seances, parametres, sequence, tva, uniteDeTravail, Clock.systemUTC());
    }

    @Bean
    ExclureUneSeance facturationExclureUneSeance(SeancesDeFacturation seances, AbsencesDesPatients absences, GardeDesMoisClos garde,
                                                 SimulerLaFacturation simulation, UniteDeTravail uniteDeTravail) {
        return new ExclureUneSeance(seances, absences, simulation, uniteDeTravail, garde);
    }

    @Bean
    ReinclureUneSeance facturationReinclureUneSeance(SeancesDeFacturation seances, AbsencesDesPatients absences, GardeDesMoisClos garde,
                                                     SimulerLaFacturation simulation, UniteDeTravail uniteDeTravail) {
        return new ReinclureUneSeance(seances, absences, simulation, uniteDeTravail, garde);
    }

    @Bean
    ChangerLeForfaitDUneSeance facturationChangerLeForfait(SeancesDeFacturation seances, ForfaitsDuCentre forfaits, GardeDesMoisClos garde,
                                                           SimulerLaFacturation simulation, UniteDeTravail uniteDeTravail) {
        return new ChangerLeForfaitDUneSeance(seances, forfaits, simulation, uniteDeTravail, Clock.systemUTC(), garde);
    }

    @Bean
    Factures facturationFactures(JdbcTemplate jdbc) {
        return new FacturesJdbc(jdbc);
    }

    @Bean
    PublicationDeLaValidation facturationPublicationDeLaValidation(ApplicationEventPublisher publisher) {
        return new PublicationDeLaValidationSpring(publisher);
    }

    @Bean
    Reglements facturationReglements(JdbcTemplate jdbc) {
        return new ReglementsJdbc(jdbc);
    }

    @Bean
    SituationsDesFactures facturationSituationsDesFactures(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new SituationsDesFacturesJdbc(jdbc, annuaire);
    }

    @Bean
    PublicationDesReglements facturationPublicationDesReglements(ApplicationEventPublisher publisher) {
        return new PublicationDesReglementsSpring(publisher);
    }

    @Bean
    EnregistrerUnReglement facturationEnregistrerUnReglement(Reglements reglements, SituationsDesFactures situations,
                                                             PublicationDesReglements publication, UniteDeTravail uniteDeTravail) {
        return new EnregistrerUnReglement(reglements, situations, publication, uniteDeTravail, Clock.systemUTC(),
                UUID::randomUUID);
    }

    @Bean
    ConsulterLesReglements facturationConsulterLesReglements(SituationsDesFactures situations, Reglements reglements) {
        return new ConsulterLesReglements(situations, reglements);
    }

    @Bean
    LectureDuTableauDeBord facturationLectureDuTableauDeBord(JdbcTemplate jdbc, SeancesDesFacturesFournies seances,
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new LectureDuTableauDeBordJdbc(jdbc, seances, annuaire);
    }

    @Bean
    ConsulterLeTableauDeBord facturationConsulterLeTableauDeBord(LectureDuTableauDeBord lecture) {
        return new ConsulterLeTableauDeBord(lecture);
    }

    @Bean
    LectureDeLaSynthese facturationLectureDeLaSynthese(JdbcTemplate jdbc, SeancesDesFacturesFournies seances,
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new LectureDeLaSyntheseJdbc(jdbc, seances,
                new com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.CaissesDesAgencesParLAnnuaire(annuaire));
    }

    /** PL8 : la synthèse est rendue par Documents (documents.v1) avec les données de la Facturation ; Excel : classeur de la Facturation. */
    @Bean
    ImpressionDeLaSynthese facturationImpressionDeLaSynthese(com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument documents) {
        return new com.hemodialyse.backend.contexts.facturation.infrastructure.acl.documents.SyntheseRendueParDocuments(documents);
    }

    @Bean
    ImprimerLaSynthese facturationImprimerLaSynthese(LectureDeLaSynthese lecture, ImpressionDeLaSynthese impression) {
        return new ImprimerLaSynthese(lecture, impression);
    }

    @Bean
    ConsulterLaSynthese facturationConsulterLaSynthese(LectureDeLaSynthese lecture) {
        return new ConsulterLaSynthese(lecture);
    }

    /** Lecture publiée consommée par le pilotage de la Direction (slice F5). */
    @Bean
    SyntheseFinanciereDesCentres facturationSyntheseFinanciere(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new SyntheseFinanciereDesCentresJdbc(jdbc, annuaire);
    }

    @Bean
    AbsencesDesPatients facturationAbsencesDesPatients(DeclarationDesAbsences absences) {
        return new AbsencesDesPatientsPubliees(absences);
    }

    @Bean
    SyntheseDeFacturation facturationSyntheseDeFacturation(ConsulterLaSynthese synthese) {
        return (centre, debut, fin) -> synthese.executer(new CentreId(centre), debut, fin);
    }

    /** Le type de document de la Facturation, déclaré à Documents (DOC4c, ADR 0166) ; 7e dans l'ordre des types proposés. */
    @Bean
    @org.springframework.core.annotation.Order(20)
    com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument facturationTypesDeDocument() {
        return new com.hemodialyse.backend.contexts.facturation.infrastructure.acl.documents.TypesDeDocumentDeLaFacturation();
    }

    /** La règle d'impression de la synthèse mensuelle, publiée à Documents (DOC4c, ADR 0166). */
    @Bean
    com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType facturationRegleDImpressionDeLaSynthese(
            SessionCourante session, SyntheseDeFacturation synthese) {
        return new com.hemodialyse.backend.contexts.facturation.interfaces.publie.RegleDImpressionDeLaSyntheseMensuelle(session, synthese);
    }

    /** Lectures publiées de la Facturation consommées par les autres contextes (slice F6). */
    @Bean
    FacturationDuPatient facturationDuPatient(JdbcTemplate jdbc) {
        return new FacturationDuPatientJdbc(jdbc);
    }

    @Bean
    RepriseDesSoldes facturationRepriseDesSoldes(JdbcTemplate jdbc,
            com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre annuaire) {
        return new RepriseDesSoldesJdbc(jdbc,
                new com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.AgenceDuPayeurParLAnnuaire(annuaire));
    }

    /** Fournis à la Comptabilité, qui publie les ports dont elle a besoin (la Facturation la connaît, pas l'inverse). */
    @Bean
    FacturesEmises facturationFacturesEmises(JdbcTemplate jdbc) {
        return new FacturesEmisesJdbc(jdbc);
    }

    @Bean
    ReglementsEncaisses facturationReglementsEncaisses(JdbcTemplate jdbc) {
        return new ReglementsEncaissesJdbc(jdbc);
    }

    /** Clôture mensuelle de facturation (slice F7, ADR 0037). */
    @Bean
    MoisDeFacturationRepository facturationMoisDeFacturation(JdbcTemplate jdbc) {
        return new MoisDeFacturationJdbc(jdbc);
    }

    @Bean
    GardeDesMoisClos facturationGardeDesMoisClos(MoisDeFacturationRepository mois) {
        return new GardeDesMoisClos(mois);
    }

    @Bean
    ClotureComptable facturationClotureComptable(PeriodesComptables periodes) {
        return new ClotureComptableComptabilite(periodes);
    }

    @Bean
    AbsencesDuMois facturationAbsencesDuMois(
            ClotureDesAbsences absences) {
        return new AbsencesDuMoisPubliees(absences);
    }

    @Bean
    PublicationDesPeriodes facturationPublicationDesPeriodes(ApplicationEventPublisher publisher) {
        return new PublicationDesPeriodesSpring(publisher);
    }

    @Bean
    FuseauxDesCentres facturationFuseauxDesCentres(
            @Value("${app.facturation.fuseau-par-defaut:${app.absences.fuseau-par-defaut:Africa/Algiers}}") String fuseauParDefaut) {
        return new FuseauParDefaut(ZoneId.of(fuseauParDefaut));
    }

    @Bean
    CloturerLeMois facturationCloturerLeMois(MoisDeFacturationRepository mois, SeancesFacturables seances,
                                             PublicationDesPeriodes publication, UniteDeTravail uniteDeTravail,
                                             AbsencesDuMois absences, FuseauxDesCentres fuseaux) {
        return new CloturerLeMois(mois, seances, publication, uniteDeTravail, Clock.systemUTC(), absences, fuseaux);
    }

    @Bean
    RouvrirLeMois facturationRouvrirLeMois(MoisDeFacturationRepository mois, ClotureComptable comptabilite,
                                           PublicationDesPeriodes publication, UniteDeTravail uniteDeTravail) {
        return new RouvrirLeMois(mois, comptabilite, publication, uniteDeTravail, Clock.systemUTC());
    }

    @Bean
    ConsulterLesPeriodes facturationConsulterLesPeriodes(MoisDeFacturationRepository mois) {
        return new ConsulterLesPeriodes(mois);
    }

    @Bean
    ValiderLaFacturation facturationValiderLaFacturation(SeancesFacturables seances, ParametresDeFacturationRepository parametres,
                                                         SequenceDeFacturation sequence, TauxDeTvaApplicable tva, Factures factures,
                                                         LiaisonDesSeances liaison, PublicationDeLaValidation publication,
                                                         UniteDeTravail uniteDeTravail, GardeDesMoisClos garde) {
        return new ValiderLaFacturation(seances, parametres, sequence, tva, factures, liaison, publication, uniteDeTravail,
                Clock.systemUTC(), UUID::randomUUID, garde);
    }
}
