package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un intervenant a été désactivé (jamais supprimé). */
public record IntervenantDesactive(IntervenantId intervenantId, CentreId centreId, UtilisateurId par,
                                   Instant survenuLe) implements EvenementDeIntervenant {
}
