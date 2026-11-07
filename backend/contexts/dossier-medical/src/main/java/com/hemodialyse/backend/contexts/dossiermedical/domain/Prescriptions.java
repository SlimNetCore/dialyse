package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant du domaine : les prescriptions médicales (table {@code prescriptions_medicales}, inchangée). */
public interface Prescriptions {

    void ajouter(PrescriptionMedicale prescription);

    /** Enregistre le contenu remplacé d'une prescription existante (patient et centre ne changent jamais). */
    void enregistrerLeRemplacement(PrescriptionMedicale prescription);

    /** Cherchée par (identifiant, patient, centre), fiche verrouillée jusqu'à la fin de la transaction ({@link EcritureConcurrente} au-delà de 5 s). */
    Optional<PrescriptionMedicale> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime par (identifiant, patient, centre) ; sans effet si aucune ne correspond (204 reproduit, KB-MED-31). */
    void supprimer(CentreId centre, PatientId patient, UUID id);

    /** Page, de la date de prescription la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDePrescriptions duPatient(CentreId centre, PatientId patient, Periode periode, int page, int taille);

    /** La prescription la plus récente du patient (seule celle-là fait foi pour l'observance). */
    default Optional<PrescriptionMedicale> laPlusRecente(CentreId centre, PatientId patient) {
        return duPatientSurLaPeriode(centre, patient, Periode.ILLIMITEE).stream().findFirst();
    }

    /**
     * Pour chaque patient du centre, sa prescription la plus récente (plusieurs si des prescriptions du même patient portent la même
     * date maximale : toutes sont contrôlées, reproduit du planificateur).
     */
    List<PrescriptionMedicale> lesPlusRecentesDuCentre(CentreId centre);

    /** Toutes celles de la période, dans le même ordre (la première est la plus récente). */
    List<PrescriptionMedicale> duPatientSurLaPeriode(CentreId centre, PatientId patient, Periode periode);
}
