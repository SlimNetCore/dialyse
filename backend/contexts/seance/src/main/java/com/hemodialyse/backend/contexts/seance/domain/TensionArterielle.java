package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Tension artérielle « systolique/diastolique » en mmHg, saisie sur le volet paramédical. Règles du legacy reproduites (DM1, golden
 * {@code tensions}) : espaces tolérés, virgule ou point, notation en cmHg (« 12/8 », « 11,5/7 » : systolique au plus 30) convertie en
 * mmHg, bornes physiologiques 20 à 400 mmHg après arrondi à l'entier le plus proche, systolique au moins égale à la diastolique. Une
 * saisie illisible ou hors bornes est refusée (422 {@code BUSINESS_ERROR}, messages du legacy) ; une saisie vide ou blanche est
 * conservée telle quelle par {@link MesuresDeSeance}, sans tension.
 */
public record TensionArterielle(int systolique, int diastolique) {

    private static final int MIN_MMHG = 20;
    private static final int MAX_MMHG = 400;
    private static final BigDecimal MAX_CMHG = new BigDecimal("30");

    public TensionArterielle {
        if (systolique < MIN_MMHG || systolique > MAX_MMHG) refuser("Valeur de tension hors bornes physiologiques: " + systolique);
        if (diastolique < MIN_MMHG || diastolique > MAX_MMHG) refuser("Valeur de tension hors bornes physiologiques: " + diastolique);
        if (systolique < diastolique) refuser("La systolique doit être supérieure ou égale à la diastolique");
    }

    /** Lit « sys/dia » ; vide si la saisie est nulle ou blanche. */
    public static Optional<TensionArterielle> lire(String saisie) {
        if (saisie == null || saisie.isBlank()) return Optional.empty();
        String[] parts = saisie.trim().split("/");
        if (parts.length != 2) refuser("Format de tension artérielle invalide (attendu: systolique/diastolique)");
        BigDecimal sys = nombre(parts[0]);
        BigDecimal dia = nombre(parts[1]);
        // Notation en centimètres de mercure, courante en pratique : aucune systolique réelle n'est inférieure ou égale à 30 mmHg.
        BigDecimal facteur = sys.compareTo(MAX_CMHG) <= 0 ? BigDecimal.TEN : BigDecimal.ONE;
        return Optional.of(new TensionArterielle(arrondie(sys.multiply(facteur)), arrondie(dia.multiply(facteur))));
    }

    /** « sys/dia » canonique, telle qu'écrite en base. */
    public String texte() {
        return systolique + "/" + diastolique;
    }

    private static BigDecimal nombre(String valeur) {
        try {
            return new BigDecimal(valeur.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new SeanceRefusee(MotifDeRefus.MESURE_INVALIDE, "Valeur de tension non numérique: " + valeur);
        }
    }

    private static int arrondie(BigDecimal mmHg) {
        int arrondi = mmHg.setScale(0, RoundingMode.HALF_UP).intValue();
        if (arrondi < MIN_MMHG || arrondi > MAX_MMHG) refuser("Valeur de tension hors bornes physiologiques: " + arrondi);
        return arrondi;
    }

    private static void refuser(String message) {
        throw new SeanceRefusee(MotifDeRefus.MESURE_INVALIDE, message);
    }
}
