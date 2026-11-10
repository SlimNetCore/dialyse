package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Un autre transporteur du même centre porte déjà ce nom (clé naturelle : sans casse ni espaces répétés). */
public class TransporteurDejaExistant extends RuntimeException {
    public TransporteurDejaExistant(String nom) {
        super("Un transporteur nommé « " + nom + " » existe déjà dans ce centre.");
    }
}
