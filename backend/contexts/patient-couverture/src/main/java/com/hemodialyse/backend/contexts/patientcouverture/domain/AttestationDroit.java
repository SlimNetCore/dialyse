package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Attestation d'ouverture de droits d'un patient (table {@code attestation_droit}, vocabulaire du legacy) : une période, du
 * début à la fin comprises, pendant laquelle les droits du patient sont ouverts. Sert à autoriser une PEC et une séance d'un
 * patient non vacancier ({@link #couvre}).
 * <p>
 * REPRODUIT À L'IDENTIQUE, À DÉCIDER AVEC LES MÉTIERS (argent, KB-PAT-41, 46) : début = fin accepté, période échue, future ou
 * antérieure à l'admission acceptée, doublons, chevauchements et contiguïtés acceptés (aucun contrôle entre attestations), la
 * même période peut exister plusieurs fois ; la règle R5 (attestation liée à une PEC active) n'est pas retenue.
 */
public final class AttestationDroit {

    private final UUID id;
    private final PatientId patient;
    private final CentreId centre;
    private final LocalDate debut;
    private final LocalDate fin;
    private final Instant creeLe;

    private AttestationDroit(UUID id, PatientId patient, CentreId centre, LocalDate debut, LocalDate fin, Instant creeLe) {
        this.id = id;
        this.patient = patient;
        this.centre = centre;
        this.debut = debut;
        this.fin = fin;
        this.creeLe = creeLe;
    }

    /**
     * Déclare une attestation par la route : les deux dates sont obligatoires (« Dates attestation obligatoires ») et la fin ne
     * précède pas le début (« La date de début doit être antérieure à la date de fin », message du legacy conservé alors que
     * début = fin est accepté).
     */
    public static AttestationDroit declarer(UUID id, PatientId patient, CentreId centre, LocalDate debut, LocalDate fin,
                                            Instant creeLe) {
        if (debut != null && fin != null && debut.isAfter(fin)) {
            throw new SaisieInvalide("La date de début doit être antérieure à la date de fin");
        }
        return avecLesDates(id, patient, centre, debut, fin, creeLe);
    }

    /** Attestation saisie sur la fiche du patient : même règle, message de la fiche (« Date fin avant date debut »). */
    public static AttestationDroit saisieSurLaFiche(UUID id, PatientId patient, CentreId centre, LocalDate debut, LocalDate fin,
                                                    Instant creeLe) {
        if (debut != null && fin != null && fin.isBefore(debut)) throw new SaisieInvalide("Date fin avant date debut");
        return avecLesDates(id, patient, centre, debut, fin, creeLe);
    }

    private static AttestationDroit avecLesDates(UUID id, PatientId patient, CentreId centre, LocalDate debut, LocalDate fin,
                                                 Instant creeLe) {
        if (debut == null || fin == null) throw new SaisieInvalide("Dates attestation obligatoires");
        return new AttestationDroit(id, patient, centre, debut, fin, creeLe);
    }

    /** Relecture d'une ligne enregistrée, sans revalider (une ligne héritée n'est jamais refusée en lecture). */
    public static AttestationDroit reconstituer(UUID id, PatientId patient, CentreId centre, LocalDate debut, LocalDate fin,
                                                Instant creeLe) {
        return new AttestationDroit(id, patient, centre, debut, fin, creeLe);
    }

    /** Le jour est-il dans la période, bornes incluses ? */
    public boolean couvre(LocalDate jour) {
        return !jour.isBefore(debut) && !jour.isAfter(fin);
    }

    /** Est-ce exactement la même période ? (la fiche ne duplique pas une période identique) */
    public boolean memePeriode(LocalDate autreDebut, LocalDate autreFin) {
        return debut.equals(autreDebut) && fin.equals(autreFin);
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

    public LocalDate debut() {
        return debut;
    }

    public LocalDate fin() {
        return fin;
    }

    public Instant creeLe() {
        return creeLe;
    }
}
