package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Le double en mémoire de la dernière créatinine passe le contrat. */
class DerniereCreatinineEnMemoireContratTest extends DerniereCreatinineContrat {

    private final DerniereCreatinineEnMemoire analyses = new DerniereCreatinineEnMemoire();

    @Override
    protected DerniereCreatininePubliee creatinine() {
        return analyses;
    }

    @Override
    protected void analyse(UUID centre, LocalDate prelevement, String creatinine) {
        analyses.ajouter(new DerniereCreatinineEnMemoire.Analyse(centre, PATIENT, prelevement, creatinine == null ? null : new BigDecimal(creatinine)));
    }
}
