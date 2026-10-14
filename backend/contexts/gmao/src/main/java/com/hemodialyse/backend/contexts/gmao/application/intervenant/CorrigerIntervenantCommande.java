package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;

import java.math.BigDecimal;

/** Remplacement total (KB-GMAO-30) ; {@code centreId} = centre de la session ; {@code nature} : code reçu. */
public record CorrigerIntervenantCommande(IntervenantId id, CentreId centreId, String nom, String nature, String telephone,
                                          String email, BigDecimal tarifHoraire, UtilisateurId par) {
}
