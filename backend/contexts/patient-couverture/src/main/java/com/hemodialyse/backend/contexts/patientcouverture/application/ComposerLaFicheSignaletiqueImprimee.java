package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.FicheSignaletiqueAImprimer.FicheImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * La fiche signalétique imprimée (DOC5i, ADR 0201) : ce que l'ancienne requête du gabarit joignait en SQL ({@code patients} de Patient et
 * couverture, la table des centres de l'Organisation en jointure externe) est composé ici par ports. Comme la jointure externe d'avant, la fiche d'un
 * patient du centre s'imprime même si le centre est inconnu de l'annuaire (le nom du centre est alors absent) ; rien n'est imprimé (vide)
 * si le patient est inconnu, d'un autre centre, ou non désigné.
 */
public final class ComposerLaFicheSignaletiqueImprimee {

    /** La fiche et le nom du centre imprimé en titre ({@code null} si l'annuaire ne connaît pas le centre). */
    public record FicheSignaletiqueImprimee(FicheImprimable fiche, String nomDuCentre) {
    }

    private final FicheSignaletiqueAImprimer fiches;
    private final NomsDesCentres centres;

    public ComposerLaFicheSignaletiqueImprimee(FicheSignaletiqueAImprimer fiches, NomsDesCentres centres) {
        this.fiches = fiches;
        this.centres = centres;
    }

    /** @param patient l'identifiant du patient, ou {@code null} s'il n'est pas fourni */
    public Optional<FicheSignaletiqueImprimee> executer(UUID centre, UUID patient) {
        if (centre == null || patient == null) return Optional.empty();
        CentreId centreId = new CentreId(centre);
        return fiches.duPatient(centreId, patient)
                .map(fiche -> new FicheSignaletiqueImprimee(fiche, centres.duCentre(centreId).orElse(null)));
    }
}
