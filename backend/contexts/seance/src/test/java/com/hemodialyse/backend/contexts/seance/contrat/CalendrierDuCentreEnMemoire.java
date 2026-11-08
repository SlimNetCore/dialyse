package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.JourDejaAuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.JourDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuCalendrier;
import com.hemodialyse.backend.contexts.seance.domain.NatureDeJour;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Double en mémoire du calendrier du centre : le même contrat que les tables (H2, PostgreSQL 16) lui est appliqué. */
public class CalendrierDuCentreEnMemoire implements CalendrierDuCentre {

    private final List<JourDuCalendrier> jours = new ArrayList<>();

    @Override
    public List<JourDuCalendrier> duMois(CentreId centre, NatureDeJour nature, MoisDuCalendrier mois) {
        return jours.stream()
                .filter(j -> j.centre().equals(centre) && j.nature() == nature && !j.jour().isBefore(mois.premierJour())
                        && !j.jour().isAfter(mois.dernierJour()))
                .sorted(Comparator.comparing(JourDuCalendrier::jour).thenComparing(JourDuCalendrier::id)).toList();
    }

    @Override
    public void ajouter(JourDuCalendrier jour) {
        boolean doublon = jours.stream().anyMatch(j -> j.centre().equals(jour.centre()) && j.nature() == jour.nature()
                && j.jour().equals(jour.jour()));
        if (doublon) throw new JourDejaAuCalendrier(jour.nature(), jour.jour());
        jours.add(jour);
    }

    @Override
    public boolean retirer(CentreId centre, NatureDeJour nature, UUID id) {
        return jours.removeIf(j -> j.centre().equals(centre) && j.nature() == nature && j.id().equals(id));
    }
}
