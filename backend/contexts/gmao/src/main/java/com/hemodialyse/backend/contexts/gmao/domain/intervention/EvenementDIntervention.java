package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.time.Instant;

/** Événement de domaine d'une intervention (nom au passé, immuable). */
public interface EvenementDIntervention {

    InterventionId interventionId();

    CentreId centreId();

    Instant survenuLe();
}
