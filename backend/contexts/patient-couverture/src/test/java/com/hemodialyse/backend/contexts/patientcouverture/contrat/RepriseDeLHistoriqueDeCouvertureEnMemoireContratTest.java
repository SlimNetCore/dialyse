package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.application.ReprendreLHistoriqueDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUneAttestation;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDeLHistoriqueDeCouverture.ReportDUnePriseEnCharge;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Le contrat de la reprise de l'historique de couverture sur un double en mémoire des tables (application seule, sans base). */
class RepriseDeLHistoriqueDeCouvertureEnMemoireContratTest extends RepriseDeLHistoriqueDeCouvertureContrat {

    private record Ligne<T>(UUID centre, UUID patient, T valeurs) {
    }

    private final Map<UUID, Ligne<ReportDUneAttestation>> attestations = new LinkedHashMap<>();
    private final Map<UUID, Ligne<ReportDUnePriseEnCharge>> prises = new LinkedHashMap<>();

    /** Complète un report existant avec les seules valeurs fournies par le nouveau. */
    private static ReportDUnePriseEnCharge complete(ReportDUnePriseEnCharge a, ReportDUnePriseEnCharge n) {
        return new ReportDUnePriseEnCharge(
                n.dateDebutDemande() != null ? n.dateDebutDemande() : a.dateDebutDemande(),
                n.dateFinDemande() != null ? n.dateFinDemande() : a.dateFinDemande(),
                n.forfaitDemande() != null ? n.forfaitDemande() : a.forfaitDemande(),
                n.statut() != null ? n.statut() : a.statut(),
                n.dateDebutEffectif() != null ? n.dateDebutEffectif() : a.dateDebutEffectif(),
                n.dateFinEffectif() != null ? n.dateFinEffectif() : a.dateFinEffectif(),
                n.forfaitEffectif() != null ? n.forfaitEffectif() : a.forfaitEffectif());
    }

    private final CouvertureDeLaReprise donnees = new CouvertureDeLaReprise() {
        @Override
        public Optional<UUID> attestationDebutee(UUID centre, UUID patient, LocalDate dateDebut) {
            return attestations.entrySet().stream().filter(e -> e.getValue().centre().equals(centre)
                    && e.getValue().patient().equals(patient) && java.util.Objects.equals(e.getValue().valeurs().dateDebut(), dateDebut))
                    .map(Map.Entry::getKey).findFirst();
        }

        @Override
        public UUID ajouterUneAttestation(UUID centre, UUID patient, ReportDUneAttestation report) {
            UUID id = UUID.randomUUID();
            attestations.put(id, new Ligne<>(centre, patient, report));
            return id;
        }

        @Override
        public void completerUneAttestation(UUID centre, UUID attestation, ReportDUneAttestation report) {
            Ligne<ReportDUneAttestation> l = attestations.get(attestation);
            if (l == null || !l.centre().equals(centre)) return;
            ReportDUneAttestation a = l.valeurs();
            attestations.put(attestation, new Ligne<>(centre, l.patient(), new ReportDUneAttestation(
                    report.dateDebut() != null ? report.dateDebut() : a.dateDebut(),
                    report.dateFin() != null ? report.dateFin() : a.dateFin())));
        }

        @Override
        public Optional<UUID> priseEnChargeDemandee(UUID centre, UUID patient, LocalDate dateDebutDemande) {
            return prises.entrySet().stream().filter(e -> e.getValue().centre().equals(centre)
                    && e.getValue().patient().equals(patient)
                    && java.util.Objects.equals(e.getValue().valeurs().dateDebutDemande(), dateDebutDemande))
                    .map(Map.Entry::getKey).findFirst();
        }

        @Override
        public UUID ajouterUnePriseEnCharge(UUID centre, UUID patient, ReportDUnePriseEnCharge report) {
            UUID id = UUID.randomUUID();
            prises.put(id, new Ligne<>(centre, patient, report));
            return id;
        }

        @Override
        public void completerUnePriseEnCharge(UUID centre, UUID priseEnCharge, ReportDUnePriseEnCharge report) {
            Ligne<ReportDUnePriseEnCharge> l = prises.get(priseEnCharge);
            if (l == null || !l.centre().equals(centre)) return;
            prises.put(priseEnCharge, new Ligne<>(centre, l.patient(), complete(l.valeurs(), report)));
        }
    };

    @Override
    protected RepriseDeLHistoriqueDeCouverture reprise() {
        return new ReprendreLHistoriqueDeCouverture(donnees);
    }

    @Override
    protected ReportDUneAttestation attestationEnBase(UUID id) {
        Ligne<ReportDUneAttestation> l = attestations.get(id);
        return l == null ? null : l.valeurs();
    }

    @Override
    protected ReportDUnePriseEnCharge priseEnChargeEnBase(UUID id) {
        Ligne<ReportDUnePriseEnCharge> l = prises.get(id);
        return l == null ? null : l.valeurs();
    }

    @Override
    protected boolean dateDeCreationPosee(UUID id) {
        return true; // le double n'a pas de colonne de création : seul l'adapter réel la pose
    }
}
