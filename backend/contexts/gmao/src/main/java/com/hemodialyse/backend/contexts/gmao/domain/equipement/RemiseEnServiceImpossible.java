package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/** R3 : la remise en service n'est possible que depuis {@code HORS_SERVICE} (422 observé). */
public class RemiseEnServiceImpossible extends IllegalStateException {
    public RemiseEnServiceImpossible() {
        super("Seul un équipement hors service peut être réactivé");
    }
}
