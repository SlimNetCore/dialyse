package com.hemodialyse.backend.contexts.gmao.domain.equipement;

/**
 * R4 : {@code REFORME} et {@code DESACTIF} ne sont jamais fixés par une intervention (la réforme est réservée à
 * {@link Equipement#reformer}). Étend {@link IllegalArgumentException} : 400 observé (KB-GMAO-40).
 */
public class EtatNonFixableParIntervention extends IllegalArgumentException {
    public EtatNonFixableParIntervention() {
        super("Cet état ne peut pas être fixé par une intervention");
    }
}
