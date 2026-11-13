package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.AnnulerLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.application.DonneesDeLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.AnnulationDeLaReprise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Le contrat de l'annulation d'un lot sur un double en mémoire des tables (application seule, sans base). */
class AnnulationDeLaRepriseEnMemoireContratTest extends AnnulationDeLaRepriseContrat {

    private record Patient(UUID centre, String assure) {
    }

    private record Affectation(UUID centre, UUID patient, String assure) {
    }

    private final Map<UUID, Patient> patients = new HashMap<>();
    private final Map<UUID, Affectation> affectations = new HashMap<>();
    private final Set<String> assures = new HashSet<>();
    private final Map<UUID, UUID> pecs = new HashMap<>();
    private final Map<UUID, UUID> attestations = new HashMap<>();

    private final DonneesDeLaReprise donnees = new DonneesDeLaReprise() {
        @Override
        public List<UUID> prisesEnChargeDuPatient(UUID patient) {
            return pecs.entrySet().stream().filter(e -> e.getValue().equals(patient)).map(Map.Entry::getKey).toList();
        }

        @Override
        public List<UUID> attestationsDuPatient(UUID patient) {
            return attestations.entrySet().stream().filter(e -> e.getValue().equals(patient)).map(Map.Entry::getKey).toList();
        }

        @Override
        public List<AffectationDeLAssure> affectationsDeLAssure(UUID centre, String numeroAssurance) {
            List<AffectationDeLAssure> r = new ArrayList<>();
            affectations.forEach((id, a) -> {
                if (a.centre().equals(centre) && a.assure().equals(numeroAssurance)) r.add(new AffectationDeLAssure(id, a.patient()));
            });
            return r;
        }

        @Override
        public List<UUID> patientsDeLAssure(UUID centre, String numeroAssurance) {
            return patients.entrySet().stream()
                    .filter(e -> e.getValue().centre().equals(centre) && numeroAssurance.equals(e.getValue().assure()))
                    .map(Map.Entry::getKey).toList();
        }

        @Override
        public void supprimerLAffectation(UUID centre, UUID affectation) {
            Affectation a = affectations.get(affectation);
            if (a != null && a.centre().equals(centre)) affectations.remove(affectation);
        }

        @Override
        public void supprimerLesAffectationsDuPatient(UUID centre, UUID patient) {
            affectations.values().removeIf(a -> a.centre().equals(centre) && a.patient().equals(patient));
        }

        @Override
        public void supprimerLePatient(UUID centre, UUID patient) {
            Patient p = patients.get(patient);
            if (p != null && p.centre().equals(centre)) patients.remove(patient);
        }

        @Override
        public void supprimerLAssure(UUID centre, String numeroAssurance) {
            if (assureDuCentre.getOrDefault(numeroAssurance, centre).equals(centre)) assures.remove(numeroAssurance);
        }
    };

    private final Map<String, UUID> assureDuCentre = new HashMap<>();

    @Override
    protected AnnulationDeLaReprise annulation() {
        return new AnnulerLaReprise(donnees);
    }

    @Override
    protected void patient(UUID centre, UUID id, String numeroAssuranceDeLAssure) {
        patients.put(id, new Patient(centre, numeroAssuranceDeLAssure));
    }

    @Override
    protected void assure(UUID centre, String numero) {
        assures.add(numero);
        assureDuCentre.put(numero, centre);
    }

    @Override
    protected void affectation(UUID centre, UUID id, UUID patient, String numero) {
        affectations.put(id, new Affectation(centre, patient, numero));
    }

    @Override
    protected void priseEnCharge(UUID id, UUID patient) {
        pecs.put(id, patient);
    }

    @Override
    protected void attestation(UUID id, UUID patient) {
        attestations.put(id, patient);
    }

    @Override
    protected boolean patientExiste(UUID id) {
        return patients.containsKey(id);
    }

    @Override
    protected boolean assureExiste(String numero) {
        return assures.contains(numero);
    }

    @Override
    protected boolean affectationExiste(UUID id) {
        return affectations.containsKey(id);
    }
}
