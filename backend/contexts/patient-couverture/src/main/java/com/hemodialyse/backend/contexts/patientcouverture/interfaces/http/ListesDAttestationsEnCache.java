package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.GererLesAttestations;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

/**
 * Cache de la liste des attestations par centre et par patient (cache {@code patient.attestation.byPatient}, comme aujourd'hui :
 * une écriture directe en base reste périmée jusqu'à une écriture par l'API, reproduit KB-PAT-44). La création renouvelle la
 * liste du même patient seulement, toute suppression vide tout le cache. La garde de centre est exercée par le contrôleur AVANT
 * l'appel (une valeur en cache n'est servie qu'à un appelant passé par la garde).
 */
public class ListesDAttestationsEnCache {

    private final GererLesAttestations gerer;

    public ListesDAttestationsEnCache(GererLesAttestations gerer) {
        this.gerer = gerer;
    }

    @Cacheable(cacheNames = "patient.attestation.byPatient", key = "#centre.toString() + ':' + #patient.toString()")
    public List<AttestationsControleur.AttestationReponse> duPatient(UUID centre, UUID patient) {
        return gerer.duPatient(centre, patient).stream().map(AttestationsControleur.AttestationReponse::de).toList();
    }

    @CacheEvict(cacheNames = "patient.attestation.byPatient", key = "#centre.toString() + ':' + #patient.toString()")
    public void apresCreation(UUID centre, UUID patient) {
        // l'éviction est portée par l'annotation
    }

    @CacheEvict(cacheNames = "patient.attestation.byPatient", allEntries = true)
    public void apresSuppression() {
        // l'éviction est portée par l'annotation
    }
}
