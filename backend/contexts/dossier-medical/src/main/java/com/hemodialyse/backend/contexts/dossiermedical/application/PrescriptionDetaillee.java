package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles.ArticleDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;

import java.util.Optional;

/** Une prescription avec le code et le libellé de ses articles (vides si l'article est inconnu du centre : reproduit, KB-MED-32). */
public record PrescriptionDetaillee(PrescriptionMedicale prescription, Optional<ArticleDeTraitement> epo, Optional<ArticleDeTraitement> fer) {
}
