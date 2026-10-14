package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** R2 : la mise hors service d'un équipement déjà hors service est refusée (422 observé). */
public class EquipementDejaHorsService extends IllegalStateException {
    public EquipementDejaHorsService() {
        super("L'équipement est déjà hors service");
    }
}
