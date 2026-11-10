package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.AgencePubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.CaissePubliee;
import com.hemodialyse.backend.infrastructure.reporting.CaissesDesAgences;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL des impressions vers l'annuaire des payeurs de {@code patientcouverture.v1} (RF3b, ADR 0127) : remplace la jointure SQL
 * {@code agence} / {@code caisse_assurance} de la synthèse mensuelle de facturation. Agence et caisse du centre seulement.
 */
@Component
public class CaissesDesAgencesParLAnnuaire implements CaissesDesAgences {

    private final PayeursDuCentre annuaire;

    public CaissesDesAgencesParLAnnuaire(PayeursDuCentre annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public Map<UUID, String> codesParAgence(UUID centre) {
        Map<UUID, String> codesDesCaisses = new HashMap<>();
        for (CaissePubliee caisse : annuaire.caisses(centre)) {
            codesDesCaisses.put(caisse.id(), caisse.code());
        }
        Map<UUID, String> codes = new HashMap<>();
        for (AgencePubliee agence : annuaire.agences(centre)) {
            String code = codesDesCaisses.get(agence.caisse());
            if (code != null) codes.put(agence.id(), code);
        }
        return codes;
    }
}
