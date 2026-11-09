package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Le mois civil d'un instantané mensuel (PD2, ADR 0103), écrit {@code AAAA-MM}. Un mois ne se capture que s'il est écoulé (strictement avant
 * le mois courant) et de moins de cinq ans ; la LECTURE d'un instantané n'exige rien d'autre qu'un mois lisible (un mois futur est donc
 * « introuvable », pas « non terminé » : KB-DIR-06 b, reproduit).
 */
public record MoisDInstantane(YearMonth mois) {

    private static final Pattern FORME = Pattern.compile("\\d{4}-\\d{2}");
    /** Ancienneté maximale d'un mois capturable, en années pleines (le mois d'il y a exactement 60 mois reste capturable). */
    public static final int ANNEES_MAX = 5;

    public MoisDInstantane {
        Objects.requireNonNull(mois, "mois");
    }

    /** @throws RegleDuPilotageViolee {@code MOIS_INVALIDE} si le texte n'est pas un mois {@code AAAA-MM} réel */
    public static MoisDInstantane lire(String texte) {
        if (texte == null || !FORME.matcher(texte).matches()) {
            throw illisible();
        }
        try {
            return new MoisDInstantane(YearMonth.parse(texte));
        } catch (DateTimeParseException e) {
            throw illisible();
        }
    }

    private static RegleDuPilotageViolee illisible() {
        return new RegleDuPilotageViolee("MOIS_INVALIDE", "Mois invalide (attendu : AAAA-MM)");
    }

    /** Le mois précédent le mois courant : celui que la tâche du 1er du mois fige. */
    public static MoisDInstantane precedent(YearMonth courant) {
        return new MoisDInstantane(courant.minusMonths(1));
    }

    /**
     * @throws RegleDuPilotageViolee {@code MOIS_NON_TERMINE} (mois courant ou futur) ou {@code MOIS_TROP_ANCIEN} (plus de cinq ans)
     */
    public void exigerCapturable(YearMonth courant) {
        if (!mois.isBefore(courant)) {
            throw new RegleDuPilotageViolee("MOIS_NON_TERMINE", "Seul un mois écoulé peut être figé");
        }
        if (mois.isBefore(courant.minusYears(ANNEES_MAX))) {
            throw new RegleDuPilotageViolee("MOIS_TROP_ANCIEN", "Mois trop ancien");
        }
    }

    public LocalDate debut() {
        return mois.atDay(1);
    }

    public LocalDate fin() {
        return mois.atEndOfMonth();
    }

    /** {@code AAAA-MM}. */
    public String texte() {
        return mois.toString();
    }
}
