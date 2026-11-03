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
}
