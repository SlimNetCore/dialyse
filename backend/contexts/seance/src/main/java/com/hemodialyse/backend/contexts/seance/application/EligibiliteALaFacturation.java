package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/** Port sortant : le patient est-il facturable le jour de la séance ? Vide : oui ; sinon la première cause rencontrée. */
public interface EligibiliteALaFacturation {
    Optional<CauseDeNonFacturation> cause(CentreId centre, PatientId patient, LocalDate jour);
}
