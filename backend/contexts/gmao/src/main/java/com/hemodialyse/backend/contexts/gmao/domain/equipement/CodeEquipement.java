package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/**
 * Code d'un équipement dans son centre (R5 : immuable après enregistrement). Conservé tel que saisi : ni rogné ni
 * normalisé, l'unicité se compare au caractère près (KB-GMAO-33, confirme KB-GMAO-09).
 */
public record CodeEquipement(String valeur) {

    public CodeEquipement {
        if (valeur == null || valeur.isBlank()) throw new IllegalArgumentException("Code requis");
    }
}
