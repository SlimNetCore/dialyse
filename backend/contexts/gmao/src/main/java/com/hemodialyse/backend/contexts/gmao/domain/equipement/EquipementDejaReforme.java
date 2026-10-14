package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** La réforme d'un équipement déjà réformé est refusée (422 observé). */
public class EquipementDejaReforme extends IllegalStateException {
    public EquipementDejaReforme() {
        super("L'équipement est déjà réformé");
    }
}
