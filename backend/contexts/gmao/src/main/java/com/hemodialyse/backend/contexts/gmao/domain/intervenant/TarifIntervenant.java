package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import java.math.BigDecimal;

/**
 * Tarif intervenant (terme du glossaire ; ex-{@code TarifHoraire}, renommé le 2026-10-10 sans effet sur le JSON ni la base) :
 * tarif horaire d'un intervenant (valorise le temps d'intervention : de l'argent). Positif ou nul (R24).
 * <p>
 * L'échelle reçue est conservée telle quelle (KB-GMAO-26 : la réponse d'un enregistrement ou d'une correction reprend
 * l'échelle envoyée, alors que la base arrondit à deux décimales, demi supérieur). Le domaine n'arrondit donc pas.
 */
public record TarifIntervenant(BigDecimal valeur) {

    public TarifIntervenant {
        if (valeur == null) throw new IllegalArgumentException("Tarif horaire requis");
        if (valeur.signum() < 0) throw new TarifNegatif();
    }

    /** Pas de tarif : {@code null} (l'intervenant n'est alors pas valorisé à la clôture d'une intervention, KB-GMAO-31). */
    public static TarifIntervenant ouAucun(BigDecimal valeur) {
        return valeur == null ? null : new TarifIntervenant(valeur);
    }

    /** Égalité de valeur, indifférente à l'échelle ({@code 45.5} et {@code 45.50}). */
    public boolean memeValeurQue(TarifIntervenant autre) {
        return autre != null && valeur.compareTo(autre.valeur) == 0;
    }
}
