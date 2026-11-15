package com.hemodialyse.backend.contexts.planification.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Évince les listes de synthèse des patients (caches {@code patient.list.summary} et {@code patient.list.summary.details}, produits
 * par le contexte patient-couverture) quand la Planification libère une place. Les anciens caches de la fiche (fiche, numéro
 * d'assurance, liste et comptage du centre) n'ont plus de producteur depuis S6c (ADR 0133). L'éviction a lieu APRÈS
 * la validation de la transaction quand il y en a une (sinon tout de suite).
 */
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
        toutVider("patient.list.summary");
        toutVider("patient.list.summary.details");
    }

    private void toutVider(String nom) {
        Cache cache = caches.getCache(nom);
        if (cache != null) cache.clear();
    }
}
