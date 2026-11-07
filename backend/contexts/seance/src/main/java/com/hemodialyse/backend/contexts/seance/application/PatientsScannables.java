package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : retrouver le patient d'un QR scanné parmi les patients du CENTRE (Patient et couverture, hors contexte). Un patient d'un
 * autre centre n'est jamais rendu.
 */
public interface PatientsScannables {

    Optional<PatientId> parIdentifiant(CentreId centre, UUID identifiant);

    /** Par code patient (majuscules attendues). */
    Optional<PatientId> parCode(CentreId centre, String code);

    Optional<PatientId> parNumeroDAssurance(CentreId centre, String numero);

    /** Tous les patients du centre avec leurs références (comparaison tolérante en dernier recours, comme le legacy). */
    List<References> tous(CentreId centre);

    record References(PatientId patient, String code, String numeroDAssurance, String numeroDAssuranceDeLAssure) {
    }
}
