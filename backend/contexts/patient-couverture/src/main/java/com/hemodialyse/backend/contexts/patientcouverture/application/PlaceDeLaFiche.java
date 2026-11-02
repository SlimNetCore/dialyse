package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/**
 * Port sortant vers la Planification (ADR 0052 : la place est écrite par Planification, jamais par ce contexte). Implémenté par
 * une ACL sur les ports publiés {@code PlacementDesPatients} et {@code LiberationDesPlaces}. Tout est exécuté dans la
 * transaction de l'appelant ; le verrou du centre pris par {@link #verifier} tient jusqu'à son terme. Ordre global des verrous :
 * centre, fiches, app_user (ADR 0056, 0061).
 */
public interface PlaceDeLaFiche {

    /**
     * Contrôle la place saisie (422 {@code PLACEMENT_*}, 409 si le verrou du centre n'est pas obtenu) et prend le verrou du
     * centre.
     *
     * @param patient nul à la création
     */
    void verifier(CentreId centre, PatientId patient, Affectation affectation, EtatDuPatient etatAVenir,
                  LocalDate dateAVenir);

    /** Écrit la place d'un patient enregistré (ou l'efface si rien n'est renseigné). */
    void placer(CentreId centre, PatientId patient, Affectation affectation, EtatDuPatient etatAVenir, LocalDate dateAVenir);

    /** Libère la place si l'échéance est dépassée (mouvement {@code PLACE_LIBEREE} écrit par la Planification). */
    boolean libererSiEchue(CentreId centre, PatientId patient);
}
