package com.hemodialyse.backend.contexts.seance.domain;

import java.math.BigDecimal;

/**
 * Poids du patient en kilogrammes, saisi sur le volet paramédical (avant et après la séance). Strictement positif et au plus
 * {@value #BORNE_PLAUSIBLE_KG} kg : au-delà, la saisie est refusée (422 {@code BUSINESS_ERROR}, messages du legacy, DM1). La précision
 * est celle de la colonne ({@code numeric(38, 2)}, arrondie par la base) : aucun arrondi ici.
 */
public record Poids(BigDecimal kilogrammes) {

    public static final String BORNE_PLAUSIBLE_KG = "500";
    private static final BigDecimal BORNE = new BigDecimal(BORNE_PLAUSIBLE_KG);

    public Poids {
        if (kilogrammes == null) throw new SeanceRefusee(MotifDeRefus.MESURE_INVALIDE, "Le poids est obligatoire");
        if (kilogrammes.signum() <= 0) throw new SeanceRefusee(MotifDeRefus.MESURE_INVALIDE, "Le poids doit être strictement positif");
        if (kilogrammes.compareTo(BORNE) > 0) {
            throw new SeanceRefusee(MotifDeRefus.MESURE_INVALIDE, "Le poids dépasse la borne plausible (" + BORNE_PLAUSIBLE_KG + " kg)");
        }
    }
}
