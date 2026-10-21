package com.hemodialyse.backend.contexts.facturation.domain.reglement;

import java.util.Locale;

/**
 * Mode de règlement d'un règlement (KB-FAC-40, ajout F4, ADR 0033, à valider par le métier). Un règlement sans mode (anciens
 * règlements, requêtes qui n'en envoient pas) est comptabilisé comme avant : en banque. La Comptabilité en déduit l'opération
 * « règlement en caisse » ou « règlement en banque » (journal de caisse ou {@code BQ}) : {@link #enCaisse()}.
 */
public enum ModeDeReglement {
    BANQUE(false),
    CHEQUE(false),
    CAISSE(true),
    ESPECES(true);

    private final boolean enCaisse;

    ModeDeReglement(boolean enCaisse) {
        this.enCaisse = enCaisse;
    }

    /**
     * Le mode du texte reçu ; {@code null} si le texte est absent ou vide.
     *
     * @throws IllegalArgumentException si le mode est inconnu (400)
     */
    public static ModeDeReglement depuis(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        try {
            return valueOf(texte.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException inconnu) {
            throw new IllegalArgumentException("Mode de reglement inconnu : " + texte.trim()
                    + " (BANQUE, CHEQUE, CAISSE ou ESPECES)");
        }
    }

    /**
     * Vrai si le règlement entre en caisse ; faux s'il entre en banque.
     */
    public boolean enCaisse() {
        return enCaisse;
    }
}
