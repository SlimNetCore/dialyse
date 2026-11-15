package com.hemodialyse.backend.contexts.gmao.application.equipement;

/**
 * Refus d'affecter un générateur à une salle qui n'a plus de place (RG-PLN-061) : HTTP 422 {@code SALLE_CAPACITE_DEPASSEE}, avec le message
 * de la Planification (contrat observé, verrouillé par les golden masters).
 */
public class SalleADCapaciteMaximale extends RuntimeException {
    public static final String CODE = "SALLE_CAPACITE_DEPASSEE";

    public SalleADCapaciteMaximale(String message) {
        super(message);
    }
}
