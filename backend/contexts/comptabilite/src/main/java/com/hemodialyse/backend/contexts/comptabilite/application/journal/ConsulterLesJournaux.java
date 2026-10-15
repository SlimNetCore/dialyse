package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.JournauxDuCentre;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié : les journaux effectifs d'un centre, pour les autres slices de la Comptabilité (mapping, pièces).
 */
public final class ConsulterLesJournaux implements JournauxDuCentre {

    private final JournauxDuCentreVus journaux;

    public ConsulterLesJournaux(JournauxDuCentreVus journaux) {
        this.journaux = journaux;
    }

    @Override
    public List<JournalPublie> journaux(UUID centre) {
        return journaux.effectifs(new CentreId(centre)).stream()
                .map(j -> new JournalPublie(j.code().valeur(), j.libelle(), j.actif())).toList();
    }
}
