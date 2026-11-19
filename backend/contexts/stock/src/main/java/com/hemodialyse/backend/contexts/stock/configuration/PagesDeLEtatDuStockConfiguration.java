package com.hemodialyse.backend.contexts.stock.configuration;

import com.hemodialyse.backend.contexts.stock.application.consultation.etat.ConsulterLEtatDuStock;
import com.hemodialyse.backend.contexts.stock.application.consultation.inventaire.ConsulterLesLignesDInventaire;
import com.hemodialyse.backend.contexts.stock.application.consultation.pmp.ConsulterLExplicationDuPmp;
import com.hemodialyse.backend.contexts.stock.application.consultation.seances.ConsulterLesSortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.EtatDuStockPagineJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.LignesDInventairePagineesJdbc;
import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages.SortiesPourSeancesPagineesJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Câblage des listes paginées de l'état du stock (contrat {@code pagination-serveur}, travailleur {@code stock-etat}) : état du
 * stock, sorties pour séances (pièces et lignes), feuille de comptage, explication du PMP.
 */
@Configuration(proxyBeanMethods = false)
public class PagesDeLEtatDuStockConfiguration {

    @Bean
    ConsulterLEtatDuStock stockConsulterLEtatDuStock(JdbcTemplate jdbc) {
        return new ConsulterLEtatDuStock(new EtatDuStockPagineJdbc(jdbc));
    }

    @Bean
    ConsulterLesSortiesPourSeances stockConsulterLesSortiesPourSeances(JdbcTemplate jdbc) {
        return new ConsulterLesSortiesPourSeances(new SortiesPourSeancesPagineesJdbc(jdbc));
    }

    @Bean
    ConsulterLesLignesDInventaire stockConsulterLesLignesDInventaire(JdbcTemplate jdbc) {
        return new ConsulterLesLignesDInventaire(new LignesDInventairePagineesJdbc(jdbc));
    }

    @Bean
    ConsulterLExplicationDuPmp stockConsulterLExplicationDuPmp(ConsulterLeStock stock) {
        return new ConsulterLExplicationDuPmp(stock::expliquerLePmp);
    }
}
