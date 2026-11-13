package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Reprise de l'historique de couverture d'un patient (RP3a, ADR 0171). L'application orchestre : l'état final d'une ligne reprise ne
 * porte aucune règle de cycle de vie (l'outil contrôle le fichier avant d'écrire), donc aucune règle de domaine ici.
 */
public class ReprendreLHistoriqueDeCouverture implements RepriseDeLHistoriqueDeCouverture {

    private final CouvertureDeLaReprise couverture;

    public ReprendreLHistoriqueDeCouverture(CouvertureDeLaReprise couverture) {
        this.couverture = couverture;
    }

    @Override
    public Optional<UUID> attestationDebutee(UUID centre, UUID patient, LocalDate dateDebut) {
        return couverture.attestationDebutee(centre, patient, dateDebut);
    }

    @Override
    public UUID reprendreUneAttestation(UUID centre, UUID patient, ReportDUneAttestation report) {
        return couverture.ajouterUneAttestation(centre, patient, report);
    }

    @Override
    public void completerUneAttestation(UUID centre, UUID attestation, ReportDUneAttestation report) {
        couverture.completerUneAttestation(centre, attestation, report);
    }

    @Override
    public Optional<UUID> priseEnChargeDemandee(UUID centre, UUID patient, LocalDate dateDebutDemande) {
        return couverture.priseEnChargeDemandee(centre, patient, dateDebutDemande);
    }

    @Override
    public UUID reprendreUnePriseEnCharge(UUID centre, UUID patient, ReportDUnePriseEnCharge report) {
        return couverture.ajouterUnePriseEnCharge(centre, patient, report);
    }

    @Override
    public void completerUnePriseEnCharge(UUID centre, UUID priseEnCharge, ReportDUnePriseEnCharge report) {
        couverture.completerUnePriseEnCharge(centre, priseEnCharge, report);
    }
}
