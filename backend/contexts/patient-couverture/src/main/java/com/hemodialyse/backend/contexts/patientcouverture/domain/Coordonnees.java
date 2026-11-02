package com.hemodialyse.backend.contexts.patientcouverture.domain;

/** Profession, adresse et moyens de contact : téléphones et courriel normalisés à la construction (nul si vide). */
public record Coordonnees(String profession, String adresse, String telPersonnel, String telMobile, String telBureau,
                          String email) {

    public Coordonnees {
        telPersonnel = Telephone.ou(telPersonnel);
        telMobile = Telephone.ou(telMobile);
        telBureau = Telephone.ou(telBureau);
        email = Courriel.ou(email);
    }
}
