package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.facturation.application.payeur.AgenceDuPayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;

import java.util.Optional;
import java.util.UUID;

/**
 * ACL vers l'annuaire des payeurs de Patient et couverture ({@link PayeursDuCentre}, RF3c, ADR 0128) : remplace la lecture SQL de
 * {@code centre_payeur} par la reprise des soldes. L'agence est celle enregistrée sur le centre payeur (comme avant), qu'elle soit ou non
 * présente dans le centre.
 */
public final class AgenceDuPayeurParLAnnuaire implements AgenceDuPayeur {

    private final PayeursDuCentre annuaire;

    public AgenceDuPayeurParLAnnuaire(PayeursDuCentre annuaire) {
        this.annuaire = annuaire;
    }

    @Override
    public Optional<UUID> agenceDuCentrePayeur(UUID centre, UUID centrePayeur) {
        return annuaire.detail(centre, centrePayeur).map(chaine -> chaine.centrePayeur().agence());
    }
}
