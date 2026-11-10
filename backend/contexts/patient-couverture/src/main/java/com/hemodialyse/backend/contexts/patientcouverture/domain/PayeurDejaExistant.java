package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Un autre payeur du même niveau et du même centre porte déjà ce code (clé naturelle : sans casse ni espaces répétés). */
public class PayeurDejaExistant extends RuntimeException {
    public PayeurDejaExistant(NiveauDePayeur niveau, String code) {
        super("Un élément de « " + niveau.libelle() + " » de code « " + code + " » existe déjà dans ce centre.");
    }
}
