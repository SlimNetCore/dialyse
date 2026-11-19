package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.util.Arrays;
import java.util.Optional;

/** Les colonnes sur lesquelles la liste des factures peut être triée (RG-FAC-046), avec leur nom de paramètre. */
public enum ChampDeTri {
    DATE_FACTURATION("dateFacturation"),
    NUMERO_FACTURE("numeroFacture"),
    PATIENT("patient"),
    CAISSE("caisse"),
    NOMBRE_DE_SEANCES("nombreDeSeances"),
    MONTANT_TTC("montantTtc"),
    MONTANT_REGLE("montantRegle"),
    RESTE("reste"),
    ETAT("etat");

    private final String parametre;

    ChampDeTri(String parametre) {
        this.parametre = parametre;
    }

    public String parametre() {
        return parametre;
    }

    /** Le champ dont le nom de paramètre est exactement celui donné ; vide si inconnu. */
    public static Optional<ChampDeTri> depuis(String parametre) {
        return Arrays.stream(values()).filter(c -> c.parametre.equals(parametre)).findFirst();
    }
}
