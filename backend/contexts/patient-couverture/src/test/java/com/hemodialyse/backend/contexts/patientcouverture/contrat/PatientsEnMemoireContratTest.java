package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.domain.NumeroAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Contrat sur le double en mémoire (même jeu de tests que la table réelle). */
class PatientsEnMemoireContratTest extends PatientsContrat {

    private final Map<PatientId, Patient> lignes = new HashMap<>();

    private final PatientRepository double_ = new PatientRepository() {
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
            // relecture fidèle : un patient relu est une autre instance, comme depuis une table
            lignes.put(patient.id(), Patient.reconstituer(patient.id(), patient.code(), patient.centre(), patient.vacancier(),
                    patient.creeLe(), patient.fiche()));
        }
    };

    @Override
    protected PatientRepository depot() {
        return double_;
    }

    private final Map<java.util.UUID, com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit> attestationsLignes =
            new HashMap<>();

    private final com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations attestationsDouble =
            new com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations() {
                @Override
                public Optional<com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit> trouver(CentreId centre,
                                                                                                                  java.util.UUID id) {
                    return Optional.ofNullable(attestationsLignes.get(id)).filter(a -> a.centre().equals(centre));
                }

                @Override
                public void enregistrer(com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit a) {
                    attestationsLignes.put(a.id(), a);
                }

                @Override
                public void supprimer(java.util.UUID id) {
                    attestationsLignes.remove(id);
                }

                @Override
                public java.util.List<com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit> duPatient(
                        CentreId centre, PatientId patient) {
                    return attestationsLignes.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                            .toList();
                }

                @Override
                public boolean existe(CentreId centre, PatientId patient) {
                    return !duPatient(centre, patient).isEmpty();
                }

                @Override
                public boolean valideLe(CentreId centre, PatientId patient, java.time.LocalDate jour) {
                    return duPatient(centre, patient).stream().anyMatch(a -> a.couvre(jour));
                }
            };

    @Override
    protected com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations attestations() {
        return attestationsDouble;
    }
}
