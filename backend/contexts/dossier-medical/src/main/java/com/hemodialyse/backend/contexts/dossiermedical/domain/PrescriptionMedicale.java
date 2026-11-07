package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Prescription médicale d'un patient : les cibles de séance de dialyse et les traitements de l'anémie en vigueur à une date. Racine
 * d'agrégat distincte de l'{@link Ordonnance} (le document destiné au patient). La plus récente (date de prescription au plus tard
 * égale au jour considéré) fait foi.
 * <p>
 * Une modification REMPLACE tout le contenu et réécrit la date de création (reproduit, KB-MED-17 : « garder created_at » n'a pas
 * été décidé). Elle ne change jamais ni le patient ni le centre : la réaffectation inter-centres du legacy est supprimée.
 */
public final class PrescriptionMedicale {

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private ContenuDePrescription contenu;
    private Instant creeeLe;
    private Instant modifieeLe;

    private PrescriptionMedicale(UUID id, PatientId patient, CentreId centre, ContenuDePrescription contenu, Instant creeeLe,
                                 Instant modifieeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.contenu = contenu;
        this.creeeLe = creeeLe;
        this.modifieeLe = modifieeLe;
    }

    public static PrescriptionMedicale prescrire(UUID id, PatientId patient, CentreId centre, ContenuDePrescription contenu,
                                                 Instant maintenant) {
        return new PrescriptionMedicale(id, patient, centre, contenu, maintenant, maintenant);
    }

    public static PrescriptionMedicale reconstituer(UUID id, PatientId patient, CentreId centre, ContenuDePrescription contenu,
                                                    Instant creeeLe, Instant modifieeLe) {
        return new PrescriptionMedicale(id, patient, centre, contenu, creeeLe, modifieeLe);
    }

    /** Remplace tout le contenu (et réécrit la date de création, comme le legacy). */
    public void remplacerPar(ContenuDePrescription nouveau, Instant maintenant) {
        this.contenu = nouveau;
        this.creeeLe = maintenant;
        this.modifieeLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public PatientId patient() {
        return patient;
    }

    public CentreId centre() {
        return centre;
    }

    public ContenuDePrescription contenu() {
        return contenu;
    }

    public Instant creeeLe() {
        return creeeLe;
    }

    public Instant modifieeLe() {
        return modifieeLe;
    }
}
