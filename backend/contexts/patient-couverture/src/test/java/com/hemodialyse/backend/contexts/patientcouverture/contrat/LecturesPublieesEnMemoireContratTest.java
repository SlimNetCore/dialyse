package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NumeroAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Contrat du langage publié sur le double en mémoire (même jeu de tests que les tables réelles). */
class LecturesPublieesEnMemoireContratTest extends LecturesPublieesContrat {

    private final Map<PatientId, Patient> lignesPatients = new HashMap<>();
    private final Map<UUID, PriseEnCharge> lignesPec = new LinkedHashMap<>();
    private final Map<UUID, AttestationDroit> lignesAttestations = new LinkedHashMap<>();
    private final Map<UUID, LecturesPublieesEnMemoire.Forfait> lignesForfaits = new HashMap<>();

    private final LecturesPublieesEnMemoire lectures = new LecturesPublieesEnMemoire(lignesPatients::values, lignesPec::values,
            lignesAttestations::values, lignesForfaits);

    private final PatientRepository patients = new PatientRepository() {
        @Override
        public Optional<Patient> trouver(CentreId centre, PatientId id) {
            return Optional.ofNullable(lignesPatients.get(id)).filter(p -> p.centre().equals(centre));
        }

        @Override
        public Optional<PatientId> trouverLeDetenteurDuNumero(CentreId centre, NumeroAssurance numero) {
            return Optional.empty();
        }

        @Override
        public void enregistrer(Patient patient) {
            lignesPatients.put(patient.id(), patient);
        }
    };

    private final PrisesEnCharge pecs = new PrisesEnCharge() {
        @Override
        public Optional<PriseEnCharge> trouver(CentreId centre, UUID id) {
            return Optional.ofNullable(lignesPec.get(id)).filter(p -> p.centre().equals(centre));
        }

        @Override
        public boolean identifiantPris(UUID id) {
            return lignesPec.containsKey(id);
        }

        @Override
        public void enregistrer(PriseEnCharge pec) {
            lignesPec.put(pec.id(), pec);
        }

        @Override
        public void supprimer(UUID id) {
            lignesPec.remove(id);
        }

        @Override
        public List<PriseEnCharge> duPatient(CentreId centre, PatientId patient) {
            return new ArrayList<>(lignesPec.values().stream().filter(p -> p.centre().equals(centre) && p.patient().equals(patient)).toList());
        }

        @Override
        public List<PriseEnCharge> duCentre(CentreId centre) {
            return new ArrayList<>(lignesPec.values().stream().filter(p -> p.centre().equals(centre)).toList());
        }
    };

    private final Attestations attestations = new Attestations() {
        @Override
        public Optional<AttestationDroit> trouver(CentreId centre, UUID id) {
            return Optional.ofNullable(lignesAttestations.get(id)).filter(a -> a.centre().equals(centre));
        }

        @Override
        public void enregistrer(AttestationDroit a) {
            lignesAttestations.put(a.id(), a);
        }

        @Override
        public void supprimer(UUID id) {
            lignesAttestations.remove(id);
        }

        @Override
        public List<AttestationDroit> duPatient(CentreId centre, PatientId patient) {
            return lignesAttestations.values().stream().filter(a -> a.centre().equals(centre) && a.patient().equals(patient)).toList();
        }

        @Override
        public boolean existe(CentreId centre, PatientId patient) {
            return !duPatient(centre, patient).isEmpty();
        }

        @Override
        public boolean valideLe(CentreId centre, PatientId patient, LocalDate jour) {
            return duPatient(centre, patient).stream().anyMatch(a -> a.couvre(jour));
        }
    };

    @Override
    protected PatientRepository patients() {
        return patients;
    }

    @Override
    protected PrisesEnCharge pecs() {
        return pecs;
    }

    @Override
    protected Attestations attestations() {
        return attestations;
    }

    @Override
    protected UUID forfait(CentreId centre, String libelle, BigDecimal prix) {
        UUID id = UUID.randomUUID();
        lignesForfaits.put(id, new LecturesPublieesEnMemoire.Forfait(centre.valeur(), libelle, prix));
        return id;
    }

    @Override
    protected FichesDesPatients fiches() {
        return lectures;
    }

    @Override
    protected CouvertureDesPatients couverture() {
        return lectures;
    }

    @Override
    protected CompteursDeCouverture compteurs() {
        return lectures;
    }
}
