package com.hemodialyse.backend.contexts.stock.domain.peremptions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Règle de signalement des péremptions (RG-STK-061) : un lot en stock est signalé chaque jour depuis J+30 (borne incluse) jusqu'au
 * jour de sa péremption inclus ; une fois périmé, il est signalé le lendemain de sa péremption (première fois) puis rappelé toutes
 * les semaines (J+1, J+8, J+15…) ; un lot reçu déjà périmé est signalé au premier contrôle qui suit sa réception. Sans mémoire à conserver : la décision ne dépend que de la date de péremption, du jour du
 * centre et du jour de création du lot. Limite : si le contrôle de J+1 est manqué (panne, redéploiement), le lot reste silencieux jusqu au rappel de J+8.
 */
public final class RegleDesPeremptions {

    /** Horizon de l'alerte : un lot qui périme dans 30 jours au plus. */
    public static final int HORIZON_EN_JOURS = 30;
    /** Rappel des lots déjà périmés. */
    public static final int RAPPEL_EN_JOURS = 7;

    private RegleDesPeremptions() {
    }

    /**
     * Le dernier jour de péremption que le contrôle de ce jour considère.
     */
    public static LocalDate horizon(LocalDate aujourdhui) {
        return aujourdhui.plusDays(HORIZON_EN_JOURS);
    }

    /**
     * @param datePeremption date de péremption du lot (le lot est périmé à partir du lendemain)
     * @param aujourdhui     jour du centre
     * @return vrai si le lot doit figurer dans le signalement de ce jour
     */
    public static boolean aSignaler(LocalDate datePeremption, LocalDate aujourdhui) {
        long jours = ChronoUnit.DAYS.between(aujourdhui, datePeremption);
        if (jours > HORIZON_EN_JOURS) {
            return false;
        }
        if (jours >= 0) {
            return true;
        }
        return (-jours - 1) % RAPPEL_EN_JOURS == 0;
    }

    /**
     * Comme {@link #aSignaler(LocalDate, LocalDate)}, et un lot <b>reçu déjà périmé</b> est signalé au premier contrôle qui suit sa
     * réception (le lendemain du jour de sa création), sans attendre le rappel de sa semaine ; il ne l'est qu'une fois (le contrôle
     * suivant, la création date de plus d'un jour). Sans état : la décision ne dépend que des trois dates.
     *
     * @param recuLe jour de la création du lot (nul si inconnu)
     */
    public static boolean aSignaler(LocalDate datePeremption, LocalDate aujourdhui, LocalDate recuLe) {
        if (aSignaler(datePeremption, aujourdhui)) {
            return true;
        }
        return estPerime(datePeremption, aujourdhui) && recuLe != null && recuLe.equals(aujourdhui.minusDays(1));
    }

    /**
     * Le lot est-il déjà périmé (sa date est passée) ?
     */
    public static boolean estPerime(LocalDate datePeremption, LocalDate aujourdhui) {
        return datePeremption.isBefore(aujourdhui);
    }
}
