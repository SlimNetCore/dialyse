package com.hemodialyse.backend.contexts.comptabilite.configuration;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.CloturerUnePeriode;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.ExporterUnJournal;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.ListerLesEcritures;
import com.hemodialyse.backend.contexts.comptabilite.application.ecriture.ValiderUneEcriture;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.ConsulterLesJournaux;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.EnregistrerUnJournal;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.JournauxDuCentreVus;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.ListerLesJournaux;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.SupprimerUnJournal;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.ConsulterLeMapping;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.ConsulterLeMappingDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.MappingDuCentreVu;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.ModifierLeMapping;
import com.hemodialyse.backend.contexts.comptabilite.application.piece.GererLesModelesEtPieces;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.ConsulterLeParametrageFiscal;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.EnregistrerUneRegleDeTva;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.ListerLesReglesDeTva;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.AffecterUnCompteAuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.ConsulterLeCompteDuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.ListerLesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.ConsulterLePlan;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.EnregistrerUnCompte;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.ListerLesComptes;
import com.hemodialyse.backend.contexts.comptabilite.application.plan.SupprimerUnCompte;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.ExportComptablePort;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePieceRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteClientDuPayeurRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.EcritureComptableRepositoryJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.PeriodeComptableRepositoryJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.ModeleDePieceRepositoryJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.SequenceDesNumerosDePiecesJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.ComptePayeurSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.RegleTvaRepositoryJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.PayeursDuCentreJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.UsagesDuParametrageJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.export.Sage100ExportAdapter;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ComptesClientsDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.JournauxDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.MappingComptableDuCentre;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PlanComptableEnLecture;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements;
import com.hemodialyse.backend.contexts.comptabilite.application.stock.ComptabiliserLeStock;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.OperationsDeStockJdbc;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.planification.ComptabilisationDuStockPlanifiee;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.FacturesAComptabiliserJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.ReglementsAComptabiliserJdbc;

import java.time.Clock;

/**
 * Composition root du contexte Comptabilité : seul endroit qui connaît toutes les couches. Les cas d'usage et les règles du
 * domaine sont des objets Java purs ; leurs ports sont câblés ici sur les adaptateurs. Slice C1 (référentiel comptable) :
 * plan comptable, journaux, compte client de chaque payeur. Slice C2 : mapping comptable et règles de TVA.
 */
@Configuration(proxyBeanMethods = false)
public class ComptabiliteConfiguration {

    // ------------------------------------------------------------------ socle

    @Bean
    SessionCourante comptabiliteSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    UniteDeTravail comptabiliteUniteDeTravail(PlatformTransactionManager transactionManager) {
        return new UniteDeTravailSpring(new TransactionTemplate(transactionManager));
    }

    @Bean
    Clock comptabiliteHorloge() {
        return Clock.systemUTC();
    }

    // ------------------------------------------------------------------ adaptateurs sortants

    /**
     * Le même adaptateur écrit le plan ({@code CompteRepository}) et en lit une page ({@code LecturePlan}).
     */
    @Bean
    CompteRepositoryJpa comptabiliteComptes(CompteComptableSpringData donnees) {
        return new CompteRepositoryJpa(donnees);
    }

    @Bean
    JournalRepository comptabiliteJournaux(JournalComptableSpringData donnees) {
        return new JournalRepositoryJpa(donnees);
    }

    @Bean
    CompteClientDuPayeurRepository comptabiliteComptesClients(ComptePayeurSpringData donnees) {
        return new CompteClientDuPayeurRepositoryJpa(donnees);
    }

    @Bean
    LectureDesPayeurs comptabilitePayeurs(JdbcTemplate jdbc) {
        return new PayeursDuCentreJdbc(jdbc);
    }

    @Bean
    UsagesDuParametrage comptabiliteUsagesDuParametrage(JdbcTemplate jdbc) {
        return new UsagesDuParametrageJdbc(jdbc);
    }

    @Bean
    EcritureComptableRepository comptabiliteEcritures(JdbcTemplate jdbc) {
        return new EcritureComptableRepositoryJdbc(jdbc);
    }

    @Bean
    PeriodeComptableRepository comptabilitePeriodes(JdbcTemplate jdbc) {
        return new PeriodeComptableRepositoryJdbc(jdbc);
    }

    @Bean
    ExportComptablePort comptabiliteExportateur() {
        return new Sage100ExportAdapter();
    }

    @Bean
    ListerLesEcritures comptabiliteListerLesEcritures(EcritureComptableRepository ecritures, UniteDeTravail uniteDeTravail) {
        return new ListerLesEcritures(ecritures, uniteDeTravail);
    }

    @Bean
    ValiderUneEcriture comptabiliteValiderUneEcriture(EcritureComptableRepository ecritures, UniteDeTravail uniteDeTravail) {
        return new ValiderUneEcriture(ecritures, uniteDeTravail);
    }

    @Bean
    ExporterUnJournal comptabiliteExporterUnJournal(EcritureComptableRepository ecritures, ExportComptablePort exportateur,
                                                    UniteDeTravail uniteDeTravail, Clock horloge) {
        return new ExporterUnJournal(ecritures, exportateur, uniteDeTravail, horloge);
    }

    @Bean
    CloturerUnePeriode comptabiliteCloturerUnePeriode(PeriodeComptableRepository periodes,
                                                      UniteDeTravail uniteDeTravail, Clock horloge) {
        return new CloturerUnePeriode(periodes, uniteDeTravail, horloge);
    }

    // ------------------------------------------------------------------ plan comptable

    @Bean
    PlanDuCentre comptabilitePlanDuCentre(CompteRepositoryJpa comptes, UsagesDuParametrage usages) {
        return new PlanDuCentre(comptes, usages);
    }

    @Bean
    ListerLesComptes comptabiliteListerLesComptes(PlanDuCentre plan, CompteRepositoryJpa comptes, UniteDeTravail uniteDeTravail) {
        return new ListerLesComptes(plan, comptes, uniteDeTravail);
    }

    @Bean
    EnregistrerUnCompte comptabiliteEnregistrerUnCompte(CompteRepositoryJpa comptes, PlanDuCentre plan, UsagesDuParametrage usages,
                                                        UniteDeTravail uniteDeTravail) {
        return new EnregistrerUnCompte(comptes, plan, usages, uniteDeTravail);
    }

    @Bean
    SupprimerUnCompte comptabiliteSupprimerUnCompte(CompteRepositoryJpa comptes, PlanDuCentre plan, UsagesDuParametrage usages,
                                                    UniteDeTravail uniteDeTravail) {
        return new SupprimerUnCompte(comptes, plan, usages, uniteDeTravail);
    }

    // ------------------------------------------------------------------ journaux

    @Bean
    JournauxDuCentreVus comptabiliteJournauxDuCentreVus(JournalRepository journaux) {
        return new JournauxDuCentreVus(journaux);
    }

    @Bean
    ListerLesJournaux comptabiliteListerLesJournaux(JournauxDuCentreVus journaux, UniteDeTravail uniteDeTravail) {
        return new ListerLesJournaux(journaux, uniteDeTravail);
    }

    @Bean
    EnregistrerUnJournal comptabiliteEnregistrerUnJournal(JournalRepository journaux, JournauxDuCentreVus vus,
                                                          UsagesDuParametrage usages, UniteDeTravail uniteDeTravail) {
        return new EnregistrerUnJournal(journaux, vus, usages, uniteDeTravail);
    }

    @Bean
    SupprimerUnJournal comptabiliteSupprimerUnJournal(JournalRepository journaux, JournauxDuCentreVus vus,
                                                      UsagesDuParametrage usages, UniteDeTravail uniteDeTravail) {
        return new SupprimerUnJournal(journaux, vus, usages, uniteDeTravail);
    }

    // ------------------------------------------------------------------ compte client de chaque payeur

    @Bean
    ListerLesPayeurs comptabiliteListerLesPayeurs(LectureDesPayeurs payeurs, UniteDeTravail uniteDeTravail) {
        return new ListerLesPayeurs(payeurs, uniteDeTravail);
    }

    @Bean
    AffecterUnCompteAuPayeur comptabiliteAffecterUnCompteAuPayeur(LectureDesPayeurs payeurs,
                                                                  CompteClientDuPayeurRepository comptesClients, PlanDuCentre plan,
                                                                  UniteDeTravail uniteDeTravail) {
        return new AffecterUnCompteAuPayeur(payeurs, comptesClients, plan, uniteDeTravail);
    }

    // ------------------------------------------------------------------ mapping comptable (C2)

    @Bean
    MappingComptableRepository comptabiliteMappings(MappingComptableSpringData donnees) {
        return new MappingComptableRepositoryJpa(donnees);
    }

    @Bean
    MappingDuCentreVu comptabiliteMappingDuCentreVu(MappingComptableRepository mappings) {
        return new MappingDuCentreVu(mappings);
    }

    @Bean
    ConsulterLeMapping comptabiliteConsulterLeMapping(MappingDuCentreVu mappings, UniteDeTravail uniteDeTravail) {
        return new ConsulterLeMapping(mappings, uniteDeTravail);
    }

    @Bean
    ModifierLeMapping comptabiliteModifierLeMapping(MappingComptableRepository mappings, MappingDuCentreVu vus,
                                                    JournauxDuCentreVus journaux, PlanDuCentre plan,
                                                    UniteDeTravail uniteDeTravail) {
        return new ModifierLeMapping(mappings, vus, journaux, plan, uniteDeTravail);
    }

    // ------------------------------------------------------------------ règles de TVA comptables (C2)

    @Bean
    RegleTvaRepository comptabiliteReglesTva(JdbcTemplate jdbc) {
        return new RegleTvaRepositoryJdbc(jdbc);
    }

    @Bean
    ListerLesReglesDeTva comptabiliteListerLesReglesDeTva(RegleTvaRepository regles, UniteDeTravail uniteDeTravail) {
        return new ListerLesReglesDeTva(regles, uniteDeTravail);
    }

    @Bean
    EnregistrerUneRegleDeTva comptabiliteEnregistrerUneRegleDeTva(RegleTvaRepository regles, UniteDeTravail uniteDeTravail) {
        return new EnregistrerUneRegleDeTva(regles, uniteDeTravail);
    }

    // ------------------------------------------------------------------ modèles et pièces (C4)

    @Bean
    ModeleDePieceRepository comptabiliteModelesDePiece(JdbcTemplate jdbc) {
        return new ModeleDePieceRepositoryJdbc(jdbc);
    }

    @Bean
    SequenceDesNumerosDePieces comptabiliteSequenceDesNumerosDePieces(JdbcTemplate jdbc) {
        return new SequenceDesNumerosDePiecesJdbc(jdbc);
    }

    @Bean
    GererLesModelesEtPieces comptabiliteGererLesModelesEtPieces(ModeleDePieceRepository modeles,
                                                                EcritureComptableRepository ecritures,
                                                                PeriodeComptableRepository periodes,
                                                                SequenceDesNumerosDePieces numerotation,
                                                                PlanComptableEnLecture plan,
                                                                JournauxDuCentre journaux,
                                                                UniteDeTravail uniteDeTravail,
                                                                Clock horloge) {
        return new GererLesModelesEtPieces(modeles, ecritures, periodes, numerotation, plan, journaux,
                uniteDeTravail, horloge);
    }

    // ------------------------------------------------------------------ ventes et règlements (C5)

    /**
     * Chaque facture ou règlement est comptabilisé dans sa propre transaction (propagation « nouvelle transaction ») : un
     * refus n'annule jamais la facturation ou le règlement de l'appelant (décision KB-CPT-37). L'unité de travail n'est pas
     * un bean, pour ne pas concurrencer l'unité de travail ordinaire des autres cas d'usage.
     */
    @Bean
    ComptabiliserLesVentesEtReglements comptabiliteComptabiliserLesVentesEtReglements(
            EcritureComptableRepository ecritures, PeriodeComptableRepository periodes,
            SequenceDesNumerosDePieces numerotation, MappingDuCentreVu mappings, ComptesClientsDesPayeurs comptesDesPayeurs,
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        TransactionTemplate nouvelleTransaction = new TransactionTemplate(transactionManager);
        nouvelleTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new ComptabiliserLesVentesEtReglements(ecritures, periodes, numerotation, mappings, comptesDesPayeurs,
                new FacturesAComptabiliserJdbc(jdbc), new ReglementsAComptabiliserJdbc(jdbc),
                new UniteDeTravailSpring(nouvelleTransaction));
    }

    // ------------------------------------------------------------------ comptabilisation du stock (C6)

    /**
     * Une synchronisation est une transaction par centre (unité de travail ordinaire), comme le legacy.
     */
    @Bean
    ComptabiliserLeStock comptabiliteComptabiliserLeStock(EcritureComptableRepository ecritures,
                                                          PeriodeComptableRepository periodes,
                                                          SequenceDesNumerosDePieces numerotation,
                                                          MappingDuCentreVu mappings, JdbcTemplate jdbc,
                                                          UniteDeTravail uniteDeTravail) {
        return new ComptabiliserLeStock(ecritures, periodes, numerotation, mappings, new OperationsDeStockJdbc(jdbc),
                uniteDeTravail);
    }

    @Bean
    ComptabilisationDuStockPlanifiee comptabiliteComptabilisationDuStockPlanifiee(ComptabiliserLeStock comptabilisation,
                                                                                  Clock horloge) {
        return new ComptabilisationDuStockPlanifiee(comptabilisation, horloge);
    }

    // ------------------------------------------------------------------ langage publié (lectures pour les autres slices)

    @Bean
    MappingComptableDuCentre comptabiliteLeMappingDuCentre(MappingDuCentreVu mappings) {
        return new ConsulterLeMappingDuCentre(mappings);
    }

    @Bean
    ParametrageFiscal comptabiliteLeParametrageFiscal(RegleTvaRepository regles) {
        return new ConsulterLeParametrageFiscal(regles);
    }


    @Bean
    PlanComptableEnLecture comptabiliteLePlanEnLecture(PlanDuCentre plan) {
        return new ConsulterLePlan(plan);
    }

    @Bean
    JournauxDuCentre comptabiliteLesJournauxDuCentre(JournauxDuCentreVus journaux) {
        return new ConsulterLesJournaux(journaux);
    }

    @Bean
    ComptesClientsDesPayeurs comptabiliteLesComptesClientsDesPayeurs(LectureDesPayeurs payeurs) {
        return new ConsulterLeCompteDuPayeur(payeurs);
    }
}
