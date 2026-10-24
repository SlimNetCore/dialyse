package com.hemodialyse.backend.contexts.stock.configuration;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.commandes.GestionDesBonsDeCommande;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.LecturesDuStockJdbc;
import com.hemodialyse.backend.contexts.stock.domain.commandes.BonsDeCommande;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.BonsDeCommandeJdbc;
import com.hemodialyse.backend.contexts.stock.application.groupes.GestionDesGroupesDArticles;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesFournisseursEtEmplacements;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupesDArticles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Emplacements;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;
import com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.ArticlesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.EmplacementsJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.FournisseursJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.GroupesDArticlesJdbc;
import com.hemodialyse.backend.contexts.stock.interfaces.catalogue.CatalogueDArticlesDuReferentiel;
import com.hemodialyse.backend.contexts.stock.interfaces.catalogue.GroupesDArticlesDesCentresDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CatalogueDArticles;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.GroupesDArticlesDesCentres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Composition root du contexte Stock : seul endroit qui connaît toutes les couches. Slice S1 : référentiel (articles,
 * fournisseurs, emplacements) et catalogue publié. Les beans sont préfixés {@code stock} (un seul contexte Spring avec le legacy
 * et les autres contextes) ; l'horloge n'est pas un bean.
 */
@Configuration(proxyBeanMethods = false)
public class StockContexteConfiguration {

    @Bean
    com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres stockHorloge(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacycentres.HorlogeDesCentresLegacy(jdbc, Clock.systemUTC());
    }

    @Bean
    SessionCourante stockSessionCourante() {
        return new SessionLegacy();
    }

    @Bean
    Articles stockArticles(JdbcTemplate jdbc) {
        return new ArticlesJdbc(jdbc);
    }

    @Bean
    Fournisseurs stockFournisseurs(JdbcTemplate jdbc) {
        return new FournisseursJdbc(jdbc);
    }

    @Bean
    Emplacements stockEmplacements(JdbcTemplate jdbc) {
        return new EmplacementsJdbc(jdbc);
    }

    @Bean
    ReferentielDesArticles stockReferentielDesArticles(Articles articles) {
        return new ReferentielDesArticles(articles, UUID::randomUUID, Clock.systemUTC());
    }

    @Bean
    ReferentielDesFournisseursEtEmplacements stockReferentielDesFournisseursEtEmplacements(Fournisseurs fournisseurs,
                                                                                           Emplacements emplacements) {
        return new ReferentielDesFournisseursEtEmplacements(fournisseurs, emplacements, UUID::randomUUID);
    }

    @Bean
    CatalogueDArticles stockCatalogueDArticles(ReferentielDesArticles referentiel) {
        return new CatalogueDArticlesDuReferentiel(referentiel);
    }

    // ------------------------------------------------------------------ slice S2 : groupes d'articles

    @Bean
    GroupesDArticles stockGroupesDArticles(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new GroupesDArticlesJdbc(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    GestionDesGroupesDArticles stockGestionDesGroupesDArticles(GroupesDArticles groupes, Articles articles) {
        return new GestionDesGroupesDArticles(groupes, articles, UUID::randomUUID, Clock.systemUTC());
    }

    @Bean
    GroupesDArticlesDesCentres stockGroupesDArticlesDesCentres(GestionDesGroupesDArticles groupes) {
        return new GroupesDArticlesDesCentresDuStock(groupes);
    }

    // ------------------------------------------------------------------ slice S3 : bons de commande

    @Bean
    BonsDeCommande stockBonsDeCommande(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new BonsDeCommandeJdbc(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    GestionDesBonsDeCommande stockGestionDesBonsDeCommande(BonsDeCommande bons, Articles articles, Fournisseurs fournisseurs) {
        return new GestionDesBonsDeCommande(bons, articles, fournisseurs, UUID::randomUUID, Clock.systemUTC());
    }

    // ------------------------------------------------------------------ slice S4 : lectures du stock

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock stockLecturesDuStock(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.LecturesDuStockJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock stockConsulterLeStock(
            com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock lectures, Articles articles,
            com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock(lectures, articles,
                horloge);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.publishedlanguage.OperationsDeStock stockOperationsDeStock(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.OperationsDeStockJdbc(jdbc);
    }

    // ------------------------------------------------------------------ slice S5 : réceptions et PMP

    @Bean
    com.hemodialyse.backend.contexts.stock.application.UniteDeTravail stockUniteDeTravail(PlatformTransactionManager transactions) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.transaction.UniteDeTravailSpring(
                new TransactionTemplate(transactions));
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock stockNotifications(
            org.springframework.context.ApplicationEventPublisher evenements) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.evenements.NotificationsDuStockSpring(evenements);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.reception.BonsDeReception stockBonsDeReception(JdbcTemplate jdbc,
                                                                                                 PlatformTransactionManager transactions) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.BonsDeReceptionJdbc(jdbc,
                new TransactionTemplate(transactions));
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock stockEcrituresDuStock(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.EcrituresDuStockJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp stockRejeuDuPmp(
            com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock lectures,
            com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock ecritures) {
        return new com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp(lectures, ecritures);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.reception.GestionDesReceptions stockGestionDesReceptions(
            com.hemodialyse.backend.contexts.stock.domain.reception.BonsDeReception bons, BonsDeCommande commandes,
            Articles articles, Fournisseurs fournisseurs,
            com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock ecritures,
            com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp rejeu,
            com.hemodialyse.backend.contexts.stock.application.UniteDeTravail uniteDeTravail,
            com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock notifications,
            com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.stock.application.reception.GestionDesReceptions(bons, commandes, articles,
                fournisseurs, ecritures, rejeu, uniteDeTravail, notifications, UUID::randomUUID, horloge);
    }

    // ------------------------------------------------------------------ slice S6 : sorties manuelles

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.sortie.BonsDeSortie stockBonsDeSortie(JdbcTemplate jdbc,
                                                                                        PlatformTransactionManager transactions) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.BonsDeSortieJdbc(jdbc,
                new TransactionTemplate(transactions));
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock stockSortiesDuStock(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.SortiesDuStockJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances stockStatutDesSeances(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacyseances.StatutDesSeancesLegacy(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties stockGestionDesSorties(
            com.hemodialyse.backend.contexts.stock.domain.sortie.BonsDeSortie bons, Articles articles,
            com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock sorties,
            com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock ecritures,
            com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp rejeu,
            com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances seances,
            com.hemodialyse.backend.contexts.stock.application.UniteDeTravail uniteDeTravail,
            com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock notifications,
            com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties(bons, articles, sorties, ecritures,
                rejeu, seances, uniteDeTravail, notifications, UUID::randomUUID, horloge);
    }

    // ------------------------------------------------------------------ slice S6b : sorties pour séances

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances stockSortiesPourSeances(JdbcTemplate jdbc,
                                                                                                           PlatformTransactionManager transactions) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.SortiesPourSeancesJdbc(jdbc,
                new TransactionTemplate(transactions));
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.publishedlanguage.SortiesPourSeancesEnAttente stockSortiesPourSeancesEnAttente(
            com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances pieces) {
        return new com.hemodialyse.backend.contexts.stock.interfaces.catalogue.SortiesPourSeancesEnAttenteDuStock(pieces);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.sortieseances.GestionDesSortiesPourSeances stockGestionDesSortiesPourSeances(
            com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances pieces, Articles articles,
            com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock sorties,
            com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock ecritures,
            com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp rejeu,
            com.hemodialyse.backend.contexts.stock.application.UniteDeTravail uniteDeTravail,
            com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock notifications,
            com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.stock.application.sortieseances.GestionDesSortiesPourSeances(pieces, articles,
                sorties, ecritures, rejeu, uniteDeTravail, notifications, UUID::randomUUID, horloge);
    }

    // ------------------------------------------------------------------ slice S6b : consommations de séance

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationsImputees stockConsommationsImputees(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.ConsommationsImputeesJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.consommations.PerimetreDesSeances stockPerimetreDesSeances(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacyseances.PerimetreDesSeancesLegacy(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.consommations.GestionDesConsommations stockGestionDesConsommations(
            com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationsImputees imputees,
            com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances pieces,
            com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock sorties, Articles articles,
            com.hemodialyse.backend.contexts.stock.domain.consommations.PerimetreDesSeances perimetres,
            com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances statuts,
            com.hemodialyse.backend.contexts.stock.application.UniteDeTravail uniteDeTravail) {
        return new com.hemodialyse.backend.contexts.stock.application.consommations.GestionDesConsommations(imputees, pieces, sorties,
                articles, perimetres, statuts, uniteDeTravail, UUID::randomUUID);
    }

    /**
     * Consommation de séance imputée sur la sortie pour séances : seule implémentation du port publié appelé par la Séance.
     */
    @Bean
    com.hemodialyse.backend.contexts.stock.interfaces.consommations.ConsommationsDeSeanceDuStock stockConsommationsDeSeance(
            com.hemodialyse.backend.contexts.stock.application.consommations.GestionDesConsommations consommations) {
        return new com.hemodialyse.backend.contexts.stock.interfaces.consommations.ConsommationsDeSeanceDuStock(consommations);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp stockRecalculMassifDuPmp(
            com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp rejeu,
            com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock ecritures, Articles articles,
            com.hemodialyse.backend.contexts.stock.application.UniteDeTravail uniteDeTravail,
            com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock notifications) {
        return new com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp(rejeu, ecritures, articles,
                uniteDeTravail, notifications, java.util.concurrent.ForkJoinPool.commonPool(), UUID::randomUUID,
                Clock.systemUTC());
    }

    // ------------------------------------------------------------------ slice S7 : inventaire

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires stockInventaires(JdbcTemplate jdbc,
                                                                                          PlatformTransactionManager transactions) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.InventairesJdbc(jdbc,
                new TransactionTemplate(transactions));
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.domain.inventaire.StockInventorie stockStockInventorie(JdbcTemplate jdbc) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.persistence.StockInventorieJdbc(jdbc);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.inventaire.NotificationsDInventaire stockNotificationsDInventaire(
            org.springframework.context.ApplicationEventPublisher evenements) {
        return new com.hemodialyse.backend.contexts.stock.infrastructure.evenements.NotificationsDInventaireSpring(evenements);
    }

    @Bean
    com.hemodialyse.backend.contexts.stock.application.inventaire.GestionDesInventaires stockGestionDesInventaires(
            com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires inventaires,
            com.hemodialyse.backend.contexts.stock.domain.inventaire.StockInventorie stock, Articles articles,
            com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock ecritures,
            com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp rejeu,
            com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances pieces,
            com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp recalcul,
            com.hemodialyse.backend.contexts.stock.application.UniteDeTravail uniteDeTravail,
            com.hemodialyse.backend.contexts.stock.application.inventaire.NotificationsDInventaire notifications,
            com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres horloge) {
        return new com.hemodialyse.backend.contexts.stock.application.inventaire.GestionDesInventaires(inventaires, stock, articles,
                ecritures, rejeu, pieces, centre -> !recalcul.enCours(centre).isEmpty(), uniteDeTravail, notifications, horloge,
                UUID::randomUUID);
    }
}
