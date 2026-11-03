package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesPec;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

/**
 * Cache des listes de PEC par centre et par patient (caches {@code patient.pec.byCenter} et {@code patient.pec.byPatient}, comme
 * aujourd'hui : une écriture directe en base reste périmée jusqu'à une écriture par l'API, reproduit KB-PAT-36). La garde de
 * centre est exercée par le contrôleur AVANT l'appel (une valeur en cache n'est servie qu'à un appelant passé par la garde).
 */
public class ListesDePecEnCache {

    private final GererLesPec gerer;

    public ListesDePecEnCache(GererLesPec gerer) {
        this.gerer = gerer;
    }

    @Cacheable(cacheNames = "patient.pec.byCenter", key = "#centre.toString()")
    public List<PecControleur.PecReponse> duCentre(UUID centre) {
        return gerer.duCentre(centre).stream().map(PecControleur.PecReponse::de).toList();
    }

    @Cacheable(cacheNames = "patient.pec.byPatient", key = "#centre.toString() + ':' + #patient.toString()")
    public List<PecControleur.PecReponse> duPatient(UUID centre, UUID patient) {
        return gerer.duPatient(centre, patient).stream().map(PecControleur.PecReponse::de).toList();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.pec.byPatient", key = "#centre.toString() + ':' + #patient.toString()"),
            @CacheEvict(cacheNames = "patient.pec.byCenter", key = "#centre.toString()")
    })
    public void apresEcriture(UUID centre, UUID patient) {
        // l'éviction est portée par les annotations
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "patient.pec.byPatient", allEntries = true),
            @CacheEvict(cacheNames = "patient.pec.byCenter", allEntries = true)
    })
    public void apresSuppression() {
        // l'éviction est portée par les annotations
    }
}
