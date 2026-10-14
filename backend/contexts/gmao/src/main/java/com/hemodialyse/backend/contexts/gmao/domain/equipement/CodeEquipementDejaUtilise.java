package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** R7 : le code est déjà porté par un équipement du centre (400 observé, message identique au legacy). */
public class CodeEquipementDejaUtilise extends IllegalArgumentException {
    public CodeEquipementDejaUtilise() {
        super("Un équipement avec ce code existe déjà dans ce centre");
    }
}
