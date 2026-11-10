package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Les trois niveaux de la chaîne des payeurs, du plus haut au plus bas : la caisse d'assurance, l'agence (d'une caisse), le centre
 * payeur (d'une agence). Le libellé pluriel et la désignation sont ceux de l'administration actuelle des référentiels (refonte sur
 * place : mêmes messages).
 */
public enum NiveauDePayeur {

    CAISSE("Caisses", "caisses"),
    AGENCE("Agences", "agences"),
    CENTRE_PAYEUR("Centres payeurs", "centres-payeurs");

    private final String libelle;
    private final String designation;

    NiveauDePayeur(String libelle, String designation) {
        this.libelle = libelle;
        this.designation = designation;
    }

    /** Le libellé pluriel du niveau (ex. « Caisses »). */
    public String libelle() {
        return libelle;
    }

    /** La désignation du niveau dans les messages d'import (ex. {@code caisses}). */
    public String designation() {
        return designation;
    }
}
