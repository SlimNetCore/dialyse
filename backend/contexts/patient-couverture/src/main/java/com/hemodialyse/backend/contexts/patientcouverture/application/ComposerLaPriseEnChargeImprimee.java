package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.PriseEnChargeAImprimer.PriseEnChargeImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * La prise en charge imprimée (DOC5h, ADR 0200) : ce que l'ancienne requête du gabarit joignait en SQL sur trois tables
 * ({@code prise_en_charge} et {@code patients} de Patient et couverture, {@code centers} de l'Organisation) est composé ici par ports.
 * Comme la jointure interne d'avant, rien n'est imprimé (vide) si la prise en charge est inconnue ou d'un autre centre, si son patient
 * n'est pas un patient du centre, si le centre est inconnu ou si aucun identifiant n'est fourni. L'identifiant de la prise en charge
 * l'emporte sur celui du patient (qui désigne alors sa dernière prise en charge), sans repli quand elle est introuvable.
 */
public final class ComposerLaPriseEnChargeImprimee {

    /** La prise en charge et le nom du centre imprimé en titre. */
    public record PriseEnChargeImprimee(PriseEnChargeImprimable priseEnCharge, String nomDuCentre) {
    }

    private final PriseEnChargeAImprimer prisesEnCharge;
    private final NomsDesCentres centres;

    public ComposerLaPriseEnChargeImprimee(PriseEnChargeAImprimer prisesEnCharge, NomsDesCentres centres) {
        this.prisesEnCharge = prisesEnCharge;
        this.centres = centres;
    }

    /** @param priseEnCharge son identifiant, ou {@code null} ; @param patient celui du patient, ou {@code null} */
    public Optional<PriseEnChargeImprimee> executer(UUID centre, UUID priseEnCharge, UUID patient) {
        if (centre == null) return Optional.empty();
        CentreId centreId = new CentreId(centre);
        Optional<PriseEnChargeImprimable> trouvee;
        if (priseEnCharge != null) trouvee = prisesEnCharge.parIdentifiant(centreId, priseEnCharge);
        else if (patient != null) trouvee = prisesEnCharge.laPlusRecenteDuPatient(centreId, patient);
        else trouvee = Optional.empty();
        if (trouvee.isEmpty()) return Optional.empty();
        return centres.duCentre(centreId).map(nom -> new PriseEnChargeImprimee(trouvee.get(), nom));
    }
}
