package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/** Débit de filtration glomérulaire estimé (CKD-EPI 2021, sans coefficient ethnique) et stade KDIGO correspondant, RG-MED-082. */
public record FonctionRenale(BigDecimal egfrMlMin173m2, StadeDeMaladieRenale stade, String referenceKdigo) {
}
