package com.hemodialyse.backend.contexts.organisation.infrastructure;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.UUID;

/**
 * Générateurs du centre mis en cache ({@code ref.generateurs}, clé = identifiant du
 * centre) : la liste est lue à chaque placement en séance mais ne change qu'aux
 * mouvements GMAO, qui évintent ce cache par son nom (comme le legacy).
 */
public class GenerateursDuCentreMisEnCache implements GenerateursDuCentre {

    private final GenerateursDuCentre delegue;

    public GenerateursDuCentreMisEnCache(GenerateursDuCentre delegue) {
        this.delegue = delegue;
    }

    @Override
    @Cacheable(cacheNames = "ref.generateurs", key = "#centreId.toString()")
    public List<Generateur> duCentre(UUID centreId) {
        return delegue.duCentre(centreId);
    }
}
