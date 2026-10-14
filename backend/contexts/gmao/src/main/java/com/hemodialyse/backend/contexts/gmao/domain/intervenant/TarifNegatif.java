package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

/** R24 : un tarif horaire est positif ou nul. Message identique au legacy (parité). */
public class TarifNegatif extends IllegalArgumentException {
    public TarifNegatif() {
        super("Le tarif horaire ne peut pas être négatif");
    }
}
