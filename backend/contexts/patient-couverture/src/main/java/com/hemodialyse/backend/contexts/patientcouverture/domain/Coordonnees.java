package com.hemodialyse.backend.contexts.patientcouverture.domain;

/**
 * Profession, adresse et moyens de contact. {@link #saisies} normalise les téléphones et le courriel (nul si vide, refus 400 si
 * illisible) ; le constructeur, lui, relit tel quel une ligne enregistrée.
 */
public record Coordonnees(String profession, String adresse, String telPersonnel, String telMobile, String telBureau,
                          String email) {

    public static Coordonnees saisies(String profession, String adresse, String telPersonnel, String telMobile,
                                      String telBureau, String email) {
        return new Coordonnees(profession, adresse, Telephone.ou(telPersonnel), Telephone.ou(telMobile),
                Telephone.ou(telBureau), Courriel.ou(email));
    }
}
