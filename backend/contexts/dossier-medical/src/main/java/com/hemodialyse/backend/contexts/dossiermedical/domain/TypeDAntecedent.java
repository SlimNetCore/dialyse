package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Nature d'un antécédent. Liste fermée du contrat HTTP (reproduit). */
public enum TypeDAntecedent {
    MEDICAL, CHIRURGICAL, FAMILIAL, OBSTETRICAL, COMORBIDITE;

    /** Lit le type saisi : absent, vide ou inconnu (y compris en minuscules) est une saisie invalide (400 sans nom interne). */
    public static TypeDAntecedent lire(String saisie) {
        if (saisie == null) throw new SaisieInvalide("Le type d'antécédent est requis");
        try {
            return valueOf(saisie);
        } catch (IllegalArgumentException inconnu) {
            throw new SaisieInvalide("Type d'antécédent inconnu");
        }
    }
}
