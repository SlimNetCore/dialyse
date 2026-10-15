package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /payeurs/{payeurId}/compte} ; un compte vide, blanc, {@code null} ou absent retire le compte du payeur.
 */
public record ComptePayeurRequete(@Size(max = NumeroDeCompte.LONGUEUR_MAX) String compte) {
}
