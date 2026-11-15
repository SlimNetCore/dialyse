package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationAImprimer.AttestationImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * L'attestation d'ouverture de droit imprimée (DOC5h, ADR 0200) : ce que l'ancienne requête du gabarit joignait en SQL sur trois tables
 * ({@code attestation_droit} et {@code patients} de Patient et couverture, {@code centers} de l'Organisation) est composé ici par ports.
 * Comme la jointure interne d'avant, rien n'est imprimé (vide) si l'ouverture de droit est inconnue ou d'un autre centre, si son patient
 * n'est pas un patient du centre, si le centre est inconnu ou si aucun identifiant n'est fourni. L'identifiant de l'attestation l'emporte
 * sur celui du patient (qui désigne alors sa dernière attestation), sans repli quand l'attestation est introuvable.
 */
public final class ComposerLAttestationImprimee {

    /** L'attestation et le nom du centre imprimé en titre. */
    public record AttestationImprimee(AttestationImprimable attestation, String nomDuCentre) {
    }

    private final AttestationAImprimer attestations;
    private final NomsDesCentres centres;

    public ComposerLAttestationImprimee(AttestationAImprimer attestations, NomsDesCentres centres) {
        this.attestations = attestations;
        this.centres = centres;
    }

    /** @param attestation l'identifiant de l'attestation, ou {@code null} ; @param patient celui du patient, ou {@code null} */
    public Optional<AttestationImprimee> executer(UUID centre, UUID attestation, UUID patient) {
        if (centre == null) return Optional.empty();
        CentreId centreId = new CentreId(centre);
        Optional<AttestationImprimable> trouvee;
        if (attestation != null) trouvee = attestations.parIdentifiant(centreId, attestation);
        else if (patient != null) trouvee = attestations.laPlusRecenteDuPatient(centreId, patient);
        else trouvee = Optional.empty();
        if (trouvee.isEmpty()) return Optional.empty();
        return centres.duCentre(centreId).map(nom -> new AttestationImprimee(trouvee.get(), nom));
    }
}
