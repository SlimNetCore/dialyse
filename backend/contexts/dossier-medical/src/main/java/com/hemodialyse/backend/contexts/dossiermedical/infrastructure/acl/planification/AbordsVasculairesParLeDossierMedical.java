package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.AbordsVasculairesPublies;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AbordsVasculairesFournis;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Traducteur (DM9, ADR 0101) : fournit à la Planification {@link AbordsVasculairesFournis} (planification.v1) depuis le langage publié du
 * Dossier médical {@link AbordsVasculairesPublies} (dossier-medical.v1) : les porteurs d'un cathéter actif au jour, pour la compétence
 * {@code CATHETER} (RG-PLN-097). Hébergé par le Dossier médical (qui dépend de la Planification, jamais l'inverse), ADR 0190.
 */
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
