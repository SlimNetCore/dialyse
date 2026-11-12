package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.CaissesDesAgences;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.AgencePubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.CaissePubliee;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers l'annuaire des payeurs ({@link PayeursDuCentre}, RF3c-J1) : la chaîne agence, caisse de toutes les agences du centre en deux
 * lectures groupées, sans jointure sur les tables des payeurs. Agence ou caisse d'un autre centre : absente.
 */
public final class CaissesDesAgencesParLAnnuaire implements CaissesDesAgences {

    private final PayeursDuCentre annuaire;

    public CaissesDesAgencesParLAnnuaire(PayeursDuCentre annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public Map<UUID, Caisse> parAgence(UUID centre) {
        Map<UUID, CaissePubliee> caisses = new HashMap<>();
        for (CaissePubliee caisse : annuaire.caisses(centre)) {
            caisses.put(caisse.id(), caisse);
        }
        Map<UUID, Caisse> parAgence = new HashMap<>();
        for (AgencePubliee agence : annuaire.agences(centre)) {
            CaissePubliee caisse = caisses.get(agence.caisse());
            if (caisse != null) {
                parAgence.put(agence.id(), new Caisse(caisse.code(), caisse.nom()));
            }
        }
        return parAgence;
    }
}
