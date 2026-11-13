package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la reprise de l'historique de couverture d'un patient, attestations de droits et prises
 * en charge, dans leur ÉTAT FINAL (outil de migration historique, RP3a, ADR 0171) : aucun circuit de validation, aucune notification.
 * L'appelant contrôle tout le fichier (patient repris, dates, forfaits, doublons) AVANT d'écrire. Toutes les opérations sont bornées au
 * centre. Une valeur absente ({@code null}) n'est jamais écrite : une reprise n'efface rien.
 */
public interface RepriseDeLHistoriqueDeCouverture {

    /** Nature des lignes d'attestation de droits dans le compte rendu d'un lot (nom historique de la table). */
    String NATURE_DES_ATTESTATIONS = "attestation_droit";

    /** Nature des lignes de prise en charge dans le compte rendu d'un lot (nom historique de la table). */
    String NATURE_DES_PRISES_EN_CHARGE = "prise_en_charge";

    /** Plus ancienne attestation du patient dans le centre qui commence ce jour-là (un début absent ne correspond qu'à un début absent). */
    Optional<UUID> attestationDebutee(UUID centre, UUID patient, LocalDate dateDebut);

    /** Ajoute une attestation et renvoie son identifiant. */
    UUID reprendreUneAttestation(UUID centre, UUID patient, ReportDUneAttestation report);

    /** Complète l'attestation du centre avec les seules valeurs fournies (sans effet si elle est d'un autre centre). */
    void completerUneAttestation(UUID centre, UUID attestation, ReportDUneAttestation report);

    /** Plus ancienne prise en charge du patient dans le centre dont la demande commence ce jour-là. */
    Optional<UUID> priseEnChargeDemandee(UUID centre, UUID patient, LocalDate dateDebutDemande);

    /** Ajoute une prise en charge et renvoie son identifiant. */
    UUID reprendreUnePriseEnCharge(UUID centre, UUID patient, ReportDUnePriseEnCharge report);

    /** Complète la prise en charge du centre avec les seules valeurs fournies (sans effet si elle est d'un autre centre). */
    void completerUnePriseEnCharge(UUID centre, UUID priseEnCharge, ReportDUnePriseEnCharge report);

    /** Valeurs du fichier de reprise d'une attestation de droits. */
    record ReportDUneAttestation(LocalDate dateDebut, LocalDate dateFin) {
    }

    /** Valeurs du fichier de reprise d'une prise en charge (forfaits désignés par leur identifiant). */
    record ReportDUnePriseEnCharge(LocalDate dateDebutDemande, LocalDate dateFinDemande, UUID forfaitDemande, String statut,
                                   LocalDate dateDebutEffectif, LocalDate dateFinEffectif, UUID forfaitEffectif) {
    }
}
