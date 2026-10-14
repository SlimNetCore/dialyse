package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.time.Instant;

/** Le tarif d'un intervenant a été révisé ({@code null} = aucun tarif). */
public record TarifIntervenantRevise(IntervenantId intervenantId, CentreId centreId, TarifIntervenant ancienTarif,
                                     TarifIntervenant nouveauTarif, UtilisateurId par,
                                     Instant survenuLe) implements EvenementDeIntervenant {
}
