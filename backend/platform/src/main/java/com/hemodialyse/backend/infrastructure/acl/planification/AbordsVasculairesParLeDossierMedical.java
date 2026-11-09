package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.AbordsVasculairesPublies;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AbordsVasculairesFournis;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Traducteur (DM9, ADR 0101) : fournit à la Planification {@link AbordsVasculairesFournis} (planification.v1) depuis le langage publié du
 * Dossier médical {@link AbordsVasculairesPublies} (dossier-medical.v1) : les porteurs d'un cathéter actif au jour, pour la compétence
 * {@code CATHETER} (RG-PLN-097). Pont de la plateforme, cycle Maven évité.
 */
@Component
public class AbordsVasculairesParLeDossierMedical implements AbordsVasculairesFournis {

    private final AbordsVasculairesPublies abords;

    public AbordsVasculairesParLeDossierMedical(AbordsVasculairesPublies abords) {
        this.abords = abords;
    }

    @Override
    public Set<UUID> porteursDUnCatheter(UUID centre, LocalDate auJour) {
        return abords.porteursDUnCatheter(centre, auJour);
    }
}
