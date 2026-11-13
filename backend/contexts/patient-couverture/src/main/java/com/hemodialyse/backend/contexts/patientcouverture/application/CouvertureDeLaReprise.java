package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUneAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUnePriseEnCharge;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant de la reprise de l'historique de couverture (RP3a, ADR 0171) sur {@code attestation_droit} et {@code prise_en_charge} :
 * mêmes écritures que l'outil de migration d'origine (état final, date de création posée, aucune date de modification).
 */
public interface CouvertureDeLaReprise {

    Optional<UUID> attestationDebutee(UUID centre, UUID patient, LocalDate dateDebut);

    UUID ajouterUneAttestation(UUID centre, UUID patient, ReportDUneAttestation report);

    void completerUneAttestation(UUID centre, UUID attestation, ReportDUneAttestation report);

    Optional<UUID> priseEnChargeDemandee(UUID centre, UUID patient, LocalDate dateDebutDemande);

    UUID ajouterUnePriseEnCharge(UUID centre, UUID patient, ReportDUnePriseEnCharge report);

    void completerUnePriseEnCharge(UUID centre, UUID priseEnCharge, ReportDUnePriseEnCharge report);
}
