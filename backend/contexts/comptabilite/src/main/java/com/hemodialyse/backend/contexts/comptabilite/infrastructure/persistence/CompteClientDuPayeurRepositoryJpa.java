package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;

/**
 * Adapter JPA du compte client de chaque payeur ({@code comptes_payeurs}). Chaque requête est bornée au centre.
 */
public class CompteClientDuPayeurRepositoryJpa implements CompteClientDuPayeurRepository {

    private final ComptePayeurSpringData donnees;

    public CompteClientDuPayeurRepositoryJpa(ComptePayeurSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public void enregistrer(CompteClientDuPayeur compteClient) {
        ComptePayeurJpa.Cle cle = new ComptePayeurJpa.Cle(compteClient.centre().valeur(), compteClient.payeur().valeur());
        ComptePayeurJpa ligne = donnees.findById(cle).orElseGet(() -> new ComptePayeurJpa(compteClient.centre().valeur(),
                compteClient.payeur().valeur(), compteClient.compte().valeur()));
        ligne.setCompte(compteClient.compte().valeur());
        donnees.saveAndFlush(ligne);
    }

    @Override
    public void retirer(CentreId centre, PayeurId payeur) {
        donnees.deleteByCenterIdAndPayeurId(centre.valeur(), payeur.valeur());
    }
}
