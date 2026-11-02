package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NumeroAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Doubles en mémoire des ports sortants (fakes, pas de mocks interactionnels). */
final class Doubles {

    static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000d"));
    static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 2);
    static final Instant MAINTENANT = Instant.parse("2026-11-02T08:00:00Z");

    private Doubles() {
    }

    static final class SessionFixe implements SessionCourante {
        CentreId centreDeLaSession = CENTRE;

        @Override
        public CentreId centre(UUID centreDemande) {
            if (centreDeLaSession == null) throw new AccesRefuse();
            if (centreDemande != null && !centreDemande.equals(centreDeLaSession.valeur())) throw new AccesRefuse();
            return centreDeLaSession;
        }
    }

    static final class HorlogeFixe implements HorlogeDesCentres {
        @Override
        public LocalDate aujourdhui(CentreId centre) {
            return AUJOURDHUI;
        }

        @Override
        public Instant maintenant() {
            return MAINTENANT;
        }
    }

    static final class IdentifiantsSequentiels implements Identifiants {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public UUID nouveau() {
            return new UUID(0, sequence.incrementAndGet());
        }

        @Override
        public String nouveauCodePatient() {
            return String.format("PAT-%08X", sequence.incrementAndGet());
        }
    }

    static final class UniteImmediate implements UniteDeTravail {
        int appels;

        @Override
        public <T> T executer(Supplier<T> action) {
            appels++;
            return action.get();
        }
    }

    static final class PatientsEnMemoire implements PatientRepository {
        final Map<PatientId, Patient> lignes = new HashMap<>();

        @Override
        public Optional<Patient> trouver(CentreId centre, PatientId id) {
            return Optional.ofNullable(lignes.get(id)).filter(p -> p.centre().equals(centre));
        }

        @Override
        public Optional<PatientId> trouverLeDetenteurDuNumero(CentreId centre, NumeroAssurance numero) {
            return lignes.values().stream().filter(p -> p.centre().equals(centre))
                    .filter(p -> p.fiche().couverture().numeroAssurance().equals(numero)).map(Patient::id).findFirst();
        }

        @Override
        public void enregistrer(Patient patient) {
            lignes.put(patient.id(), patient);
        }
    }

    static final class MouvementsEnMemoire implements MouvementsDesPatients {
        final List<MouvementDuPatient> ecrits = new ArrayList<>();

        @Override
        public void ajouter(MouvementDuPatient mouvement) {
            ecrits.add(mouvement);
        }
    }

    /** Place : enregistre les appels dans l'ordre ; peut refuser (comme la Planification) ou signaler la libération. */
    static final class PlaceEnregistree implements PlaceDeLaFiche {
        final List<String> appels = new ArrayList<>();
        RuntimeException refus;
        boolean libere;

        @Override
        public void verifier(CentreId centre, PatientId patient, Affectation affectation, EtatDuPatient etat, LocalDate date) {
            appels.add("verifier");
            if (refus != null) throw refus;
        }

        @Override
        public void placer(CentreId centre, PatientId patient, Affectation affectation, EtatDuPatient etat, LocalDate date) {
            appels.add("placer");
        }

        @Override
        public boolean libererSiEchue(CentreId centre, PatientId patient) {
            appels.add("libererSiEchue");
            return libere;
        }
    }

    static final class CouvertureEnMemoire implements CouvertureDeLaFiche {
        final List<String> appels = new ArrayList<>();
        final List<PatientId> avecAttestation = new ArrayList<>();
        Optional<Couverture.AssureSurLaFiche> principal = Optional.empty();
        AttestationSaisie derniereAttestation;
        DemandeDePec derniereDemande;

        @Override
        public boolean attestationEnregistree(CentreId centre, PatientId patient) {
            return avecAttestation.contains(patient);
        }

        @Override
        public Optional<Couverture.AssureSurLaFiche> assurePrincipal(CentreId centre, PatientId patient) {
            return principal;
        }

        @Override
        public void enregistrer(CentreId centre, PatientId patient, Couverture couverture, AttestationSaisie attestation,
                                DemandeDePec demande) {
            appels.add("enregistrer");
            derniereAttestation = attestation;
            derniereDemande = demande;
            if (attestation != null && attestation.complete() && !avecAttestation.contains(patient)) avecAttestation.add(patient);
        }
    }

    static SaisieDeLaFiche saisie(Fiche fiche, UUID centre) {
        return new SaisieDeLaFiche(centre, fiche, new CouvertureDeLaFiche.AttestationSaisie(null, LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1)), null);
    }
}
