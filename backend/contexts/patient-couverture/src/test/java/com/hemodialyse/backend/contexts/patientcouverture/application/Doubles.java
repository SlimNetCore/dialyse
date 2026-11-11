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

    /** Double de la couverture de la fiche (cas d'usage de la fiche testés sans attestations réelles). */
    static final class CouvertureEnMemoire extends CouvertureDeLaFiche {
        final List<String> appels = new ArrayList<>();
        final List<PatientId> avecAttestation = new ArrayList<>();
        AttestationSaisie derniereAttestation;

        CouvertureEnMemoire() {
            super(null, null, null);
        }

        @Override
        public boolean attestationEnregistree(CentreId centre, PatientId patient) {
            return avecAttestation.contains(patient);
        }

        @Override
        public void enregistrer(CentreId centre, PatientId patient, AttestationSaisie attestation) {
            appels.add("enregistrer");
            derniereAttestation = attestation;
            if (attestation != null && attestation.complete() && !avecAttestation.contains(patient)) avecAttestation.add(patient);
        }
    }

    /** Attestations en mémoire (le contrat PC4 prouve leur fidélité à la table réelle). */
    static final class AttestationsEnMemoire implements com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations {
        final java.util.Map<UUID, com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit> lignes =
                new java.util.LinkedHashMap<>();

        @Override
        public Optional<com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit> trouver(CentreId centre, UUID id) {
            return Optional.ofNullable(lignes.get(id)).filter(a -> a.centre().equals(centre));
        }

        @Override
        public void enregistrer(com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit a) {
            lignes.put(a.id(), a);
        }

        @Override
        public void supprimer(UUID id) {
            lignes.remove(id);
        }

        @Override
        public List<com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit> duPatient(CentreId centre,
                                                                                                         PatientId patient) {
            return lignes.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient)).toList();
        }

        @Override
        public boolean existe(CentreId centre, PatientId patient) {
            return !duPatient(centre, patient).isEmpty();
        }

        @Override
        public boolean valideLe(CentreId centre, PatientId patient, LocalDate jour) {
            return duPatient(centre, patient).stream().anyMatch(a -> a.couvre(jour));
        }
    }

    /** Prises en charge en mémoire. */
    static final class PrisesEnChargeEnMemoire implements com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge {
        final java.util.Map<UUID, com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge> lignes =
                new java.util.LinkedHashMap<>();

        @Override
        public Optional<com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge> trouver(CentreId centre, UUID id) {
            return Optional.ofNullable(lignes.get(id)).filter(p -> p.centre().equals(centre));
        }

        @Override
        public boolean identifiantPris(UUID id) {
            return lignes.containsKey(id);
        }

        @Override
        public void enregistrer(com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge pec) {
            lignes.put(pec.id(), pec);
        }

        @Override
        public void supprimer(UUID id) {
            lignes.remove(id);
        }

        @Override
        public List<com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge> duPatient(CentreId centre, PatientId patient) {
            return lignes.values().stream().filter(p -> p.centre().equals(centre) && p.patient().equals(patient)).toList();
        }

        @Override
        public List<com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge> duCentre(CentreId centre) {
            return lignes.values().stream().filter(p -> p.centre().equals(centre)).toList();
        }
    }

    /** Assurés et affectations en mémoire (fidèle à la table : le principal est la ligne la plus récente marquée principale). */
    static final class AssuresEnMemoire implements AssuresDuCentre {
        final List<Assure> assures = new ArrayList<>();
        final List<Affectation> affectations = new ArrayList<>();

        @Override
        public List<Assure> rechercher(CentreId centre, String texte) {
            return assures.stream().filter(a -> a.centre().equals(centre.valeur())).toList();
        }

        @Override
        public Optional<Assure> trouver(String numero) {
            return assures.stream().filter(a -> a.numeroAssurance().equals(numero)).findFirst();
        }

        @Override
        public void mettreAJour(Assure assure) {
            assures.removeIf(a -> a.numeroAssurance().equals(assure.numeroAssurance()));
            assures.add(assure);
        }

        @Override
        public List<Affectation> historique(CentreId centre, PatientId patient) {
            return affectations.stream().filter(a -> a.patientId().equals(patient.valeur()) && a.centre().equals(centre.valeur()))
                    .toList();
        }

        @Override
        public Optional<Affectation> trouverAffectation(UUID id) {
            return affectations.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public void fermerLAffectationPrincipale(CentreId centre, PatientId patient, LocalDate fin) {
            for (int i = 0; i < affectations.size(); i++) {
                Affectation a = affectations.get(i);
                if (a.patientId().equals(patient.valeur()) && a.principal() && a.dateFin() == null) {
                    affectations.set(i, new Affectation(a.id(), a.patientId(), a.numeroAssurance(), a.centre(), false,
                            a.dateAffectation(), a.dateDebut(), fin));
                }
            }
        }

        @Override
        public UUID ajouterUneAffectationPrincipale(CentreId centre, PatientId patient, String numero, LocalDate debut) {
            UUID id = UUID.randomUUID();
            affectations.add(new Affectation(id, patient.valeur(), numero, centre.valeur(), true,
                    java.time.OffsetDateTime.now().plusNanos(affectations.size()), debut, null));
            return id;
        }

        @Override
        public void modifierLesDates(UUID affectation, LocalDate debut, LocalDate fin) {
            Affectation a = trouverAffectation(affectation).orElseThrow();
            affectations.remove(a);
            affectations.add(new Affectation(a.id(), a.patientId(), a.numeroAssurance(), a.centre(), a.principal(),
                    a.dateAffectation(), debut, fin));
        }

        @Override
        public Optional<Affectation> principalActuel(CentreId centre, PatientId patient) {
            return affectations.stream().filter(a -> a.patientId().equals(patient.valeur()) && a.principal())
                    .max(java.util.Comparator.comparing(Affectation::dateAffectation));
        }

        @Override
        public void retirerLePrincipal(CentreId centre, PatientId patient) {
            for (int i = 0; i < affectations.size(); i++) {
                Affectation a = affectations.get(i);
                if (a.patientId().equals(patient.valeur()) && a.principal()) {
                    affectations.set(i, new Affectation(a.id(), a.patientId(), a.numeroAssurance(), a.centre(), false,
                            a.dateAffectation(), a.dateDebut(), a.dateFin()));
                }
            }
        }

        @Override
        public void declarer(Assure assure) {
            Optional<Assure> existant = trouver(assure.numeroAssurance());
            com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePrincipal.verifierQueLAssureEstDuCentre(
                    existant.map(Assure::centre).orElse(null), new CentreId(assure.centre()));
            LocalDate naissance = assure.dateNaissance() != null ? assure.dateNaissance()
                    : existant.map(Assure::dateNaissance).orElse(null);
            UUID centre = existant.map(Assure::centre).orElse(assure.centre());
            assures.removeIf(a -> a.numeroAssurance().equals(assure.numeroAssurance()));
            assures.add(new Assure(assure.numeroAssurance(), assure.nom(), assure.prenom(), assure.sexe(), naissance,
                    assure.telPersonnel(), assure.telMobile(), assure.telBureau(), assure.adresse(), assure.groupeSanguin(), centre));
        }

        @Override
        public Optional<Assure> trouverSansCasse(String numero) {
            return assures.stream().filter(a -> a.numeroAssurance().equalsIgnoreCase(numero)).findFirst();
        }

        @Override
        public UUID ajouterUneAffectation(CentreId centre, PatientId patient, String numero, boolean principale, LocalDate debut,
                                          LocalDate fin) {
            UUID id = UUID.randomUUID();
            affectations.add(new Affectation(id, patient.valeur(), numero, centre.valeur(), principale,
                    java.time.OffsetDateTime.now().plusNanos(affectations.size()), debut, fin));
            return id;
        }

        @Override
        public void remplacerUneAffectation(CentreId centre, UUID affectation, String numero, boolean principale, LocalDate debut,
                                            LocalDate fin) {
            Affectation a = affectations.stream().filter(x -> x.id().equals(affectation) && x.centre().equals(centre.valeur()))
                    .findFirst().orElseThrow();
            affectations.set(affectations.indexOf(a), new Affectation(a.id(), a.patientId(), numero, a.centre(), principale,
                    a.dateAffectation(), debut, fin));
        }

        @Override
        public UUID lierSansDates(CentreId centre, PatientId patient, String numero) {
            UUID id = UUID.randomUUID();
            affectations.add(new Affectation(id, patient.valeur(), numero, centre.valeur(), true,
                    java.time.OffsetDateTime.now().plusNanos(affectations.size()), null, null));
            return id;
        }
    }

    static SaisieDeLaFiche saisie(Fiche fiche, UUID centre) {
        return new SaisieDeLaFiche(centre, fiche, new CouvertureDeLaFiche.AttestationSaisie(null, LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1)), null);
    }
}
