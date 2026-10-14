package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.math.BigDecimal;

/** {@code nature} : code reçu ({@code INTERNE} / {@code EXTERNE}), reconnu par le use case. */
public record EnregistrerIntervenantCommande(CentreId centreId, String nom, String nature, String telephone, String email,
                                             BigDecimal tarifHoraire, UtilisateurId par) {
}
