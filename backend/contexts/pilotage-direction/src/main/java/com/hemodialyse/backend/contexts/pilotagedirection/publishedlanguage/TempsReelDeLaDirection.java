package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié du Pilotage direction pour le temps réel du tableau de bord (PD3, ADR 0104) : la plateforme signale les abonnements des
 * directions (suivi STOMP) et les changements de données d'un centre ; le Pilotage recalcule, diffuse les changements et alimente
 * l'historique des alertes. Sans abonné, un signal de centre ne fait rien.
 */
public interface TempsReelDeLaDirection {

    /** Une direction vient de s'abonner au canal de la société. */
    void abonneAjoute(UUID societeId);

    /** Un abonnement de direction à la société se termine. */
    void abonneRetire(UUID societeId);

    boolean estSuivie(UUID societeId);

    /** Une donnée d'un centre vient de changer (saisie, import, stock, notification) : sa société sera recalculée au prochain passage. */
    void unCentreAChange(UUID centreId);
}
