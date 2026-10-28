package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Objects;
import java.util.UUID;

/**
 * Fournisseur d'un centre (référentiel simple, RG-STK-003) : raison sociale obligatoire ; toutes les valeurs sont nettoyées
 * (espaces de bord retirés, facultatives blanches ramenées à « absent » : KB-STK-09 corrigé, le legacy les gardait telles
 * quelles). Un fournisseur référencé est actif.
 */
public record Fournisseur(UUID id, CentreId centre, String code, String raisonSociale, String contact, String telephone,
                          String email, boolean actif) {

    public Fournisseur {
        Objects.requireNonNull(id, "L'identifiant est obligatoire");
        Objects.requireNonNull(centre, "Le centre est obligatoire");
    }

    private static String nettoye(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    public static Fournisseur referencer(UUID id, CentreId centre, String code, String raisonSociale, String contact,
                                         String telephone, String email) {
        if (raisonSociale == null || raisonSociale.isBlank()) {
            throw new RefusDuReferentiel("FOURNISSEUR_RAISON_SOCIALE_REQUISE", "La raison sociale est obligatoire");
        }
        return new Fournisseur(id, centre, nettoye(code), raisonSociale.trim(), nettoye(contact), nettoye(telephone),
                nettoye(email), true);
    }
}

