package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesTransports;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

/**
 * Cache des listes de choix des transports par centre (caches {@code ref.transporteurs} et {@code ref.categoriesTransport}, comme
 * aujourd'hui, clé = identifiant du centre). La garde de centre est exercée par le contrôleur AVANT l'appel : une valeur en cache
 * n'est servie qu'à un appelant passé par la garde. Les transporteurs sont invalidés à chaque écriture (après commit) ; les
 * catégories ne le sont jamais (aucun écrivain, KB-RF1-02).
 */
public class ListesDeTransportEnCache {

    private final ConsulterLesTransports consulter;

    public ListesDeTransportEnCache(ConsulterLesTransports consulter) {
        this.consulter = consulter;
    }

    @Cacheable(cacheNames = "ref.transporteurs", key = "#centre.toString()")
    public List<ChoixDeTransport> transporteurs(UUID centre) {
        return consulter.transporteurs(centre).stream()
                .map(t -> new ChoixDeTransport(t.id().toString(), null, t.nom(), null, null, null, null)).toList();
    }

    @Cacheable(cacheNames = "ref.categoriesTransport", key = "#centre.toString()")
    public List<ChoixDeTransport> categories(UUID centre) {
        return consulter.categories(centre).stream()
                .map(c -> new ChoixDeTransport(c.id().toString(), null, null, null, null, c.libelle(), null)).toList();
    }

    /** Une ligne de liste de choix : même forme JSON que les autres référentiels communs (sept champs, nuls compris). */
    public record ChoixDeTransport(String id, String code, String nom, String adresse, String prenom, String libelle,
                                   Boolean gereParLot) {
    }
}
