package com.hemodialyse.backend.contexts.planification.application;

/**
 * Prend le verrou qui sérialise la création des identifiants de connexion, pour la transaction de l'appelant (l'administration
 * des utilisateurs du legacy, comme la création d'un compte depuis une fiche). Dernier verrou de l'ordre global (ADR 0056).
 */
public class VerrouillerLesIdentifiantsDeConnexion {
    private final UniteDeTravail uniteDeTravail;
    private final ComptesUtilisateurs comptes;

    public VerrouillerLesIdentifiantsDeConnexion(UniteDeTravail uniteDeTravail, ComptesUtilisateurs comptes) {
        this.uniteDeTravail = uniteDeTravail;
        this.comptes = comptes;
    }

    public void executer() {
        uniteDeTravail.executer(() -> {
            comptes.verrouillerLesIdentifiants();
            return null;
        });
    }
}
