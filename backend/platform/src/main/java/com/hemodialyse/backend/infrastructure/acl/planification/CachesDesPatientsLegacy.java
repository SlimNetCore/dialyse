package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Évince les caches de la fiche du patient (fiche, recherche par numéro d'assurance, liste du centre, comptage, listes de synthèse)
 * quand la Planification libère une place : mêmes caches que ceux évincés par l'enregistrement d'une fiche. L'éviction a lieu APRÈS
 * la validation de la transaction quand il y en a une (sinon tout de suite).
 */
@Component
public class CachesDesPatientsLegacy implements CachesDesPatients {

    private final CacheManager caches;

    public CachesDesPatientsLegacy(CacheManager caches) {
        this.caches = caches;
    }

    @Override
    public void evincer(UUID centre, UUID patient) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    vider(centre, patient);
                }
            });
        } else {
            vider(centre, patient);
        }
    }

    private void vider(UUID centre, UUID patient) {
        String c = centre.toString();
        retirer("patient.byId", c + ':' + patient);
        retirer("patient.byCenter", c);
        retirer("patient.countByCenter", c);
        toutVider("patient.byNumeroAssurance");
        toutVider("patient.list.summary");
        toutVider("patient.list.summary.details");
    }

    private void retirer(String nom, String cle) {
        Cache cache = caches.getCache(nom);
        if (cache != null) cache.evict(cle);
    }

    private void toutVider(String nom) {
        Cache cache = caches.getCache(nom);
        if (cache != null) cache.clear();
    }
}
