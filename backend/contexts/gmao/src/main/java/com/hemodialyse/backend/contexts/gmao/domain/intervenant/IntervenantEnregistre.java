package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Un intervenant a été enregistré (actif). */
public record IntervenantEnregistre(IntervenantId intervenantId, CentreId centreId, NatureIntervenant nature,
                                    UtilisateurId par, Instant survenuLe) implements EvenementDeIntervenant {
}
