package com.hemodialyse.backend.contexts.stock.application.inventaire;

import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;

/**
 * Port sortant : le procès-verbal d'inventaire, selon le modèle de document « INVENTAIRE_STOCK » du centre (personnalisable), avec
 * l'en-tête et le pied de page de la société et du centre ; filigrane « PROVISOIRE » tant que l'inventaire est en cours. Implémenté
 * par la plateforme (moteur de documents).
 */
public interface ProcesVerbalDInventaire {

    Document imprimer(Inventaire inventaire, String utilisateur);

    /**
     * @param format {@code PDF} (par défaut), {@code EXCEL} ou {@code HTML}
     */
    record Document(String format, byte[] contenu) {
    }
}

