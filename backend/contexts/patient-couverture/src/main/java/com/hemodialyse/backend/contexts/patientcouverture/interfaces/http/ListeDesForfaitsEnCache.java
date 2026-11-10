package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesForfaits;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

/**
 * Cache de la liste de choix des forfaits par centre (cache {@code ref.forfaits}, comme aujourd'hui, clé = identifiant du centre).
 * La garde de centre est exercée par le contrôleur AVANT l'appel : une valeur en cache n'est servie qu'à un appelant passé par la
 * garde. Invalidé à chaque écriture du catalogue (après commit).
 */
public class ListeDesForfaitsEnCache {

    private final ConsulterLesForfaits consulter;

    public ListeDesForfaitsEnCache(ConsulterLesForfaits consulter) {
        this.consulter = consulter;
    }

    @Cacheable(cacheNames = "ref.forfaits", key = "#centre.toString()")
    public List<ChoixDeForfait> forfaits(UUID centre) {
        return consulter.forfaits(centre).stream()
                .map(f -> new ChoixDeForfait(f.id().toString(), f.code(), f.libelle(), null, null,
                        f.prix() == null ? null : f.prix().toPlainString(), null)).toList();
    }

    /**
     * Une ligne de liste de choix : même forme JSON que les autres référentiels communs (sept champs, nuls compris). Pour un forfait,
     * {@code nom} porte le libellé et {@code libelle} le prix en texte, comme avant.
     */
    public record ChoixDeForfait(String id, String code, String nom, String adresse, String prenom, String libelle,
                                 Boolean gereParLot) {
    }
}
