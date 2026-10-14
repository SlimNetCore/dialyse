package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.time.Instant;

/** Événement de domaine d'un intervenant (nom au passé, immuable). */
public interface EvenementDeIntervenant {

    IntervenantId intervenantId();

    CentreId centreId();

    Instant survenuLe();
}
