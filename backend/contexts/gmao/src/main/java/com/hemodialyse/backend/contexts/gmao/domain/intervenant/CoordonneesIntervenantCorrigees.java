package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Le nom, la nature, le téléphone ou l'email d'un intervenant ont été corrigés. */
public record CoordonneesIntervenantCorrigees(IntervenantId intervenantId, CentreId centreId, NatureIntervenant nature,
                                              UtilisateurId par, Instant survenuLe) implements EvenementDeIntervenant {
}
