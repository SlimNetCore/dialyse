package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Un autre forfait du même centre porte déjà ce code (clé naturelle : sans casse ni espaces répétés). */
public class ForfaitDejaExistant extends RuntimeException {
    public ForfaitDejaExistant(String code) {
        super("Un forfait de code « " + code + " » existe déjà dans ce centre.");
    }
}
