package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.cloture.MoisDeFacturation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Les 12 mois d'une année pour un centre. {@code statut} vaut {@code OUVERT} ou {@code CLOTURE}. Les champs de dernière clôture
 * et de dernière réouverture restent renseignés après une réouverture (l'historique complet est au journal).
 */
public record PeriodesReponse(UUID centerId, int annee, List<Mois> periodes) {

    public record Mois(String mois, String statut, UUID cloturePar, Instant clotureLe, UUID rouvertPar, Instant rouvertLe,
                       String motifDeReouverture) {
    }

    static PeriodesReponse depuis(CentreId centre, int annee, List<MoisDeFacturation> mois) {
        return new PeriodesReponse(centre.valeur(), annee, mois.stream().map(m -> new Mois(m.mois().toString(),
                m.estClos() ? "CLOTURE" : "OUVERT", m.closPar(), m.closLe(), m.rouvertPar(), m.rouvertLe(),
                m.motifDeReouverture())).toList());
    }
}
