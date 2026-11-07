package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.dossiermedical.application.SortiesDeStockDesAdministrations;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationsDeSeance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** ACL vers le langage publié du Stock ({@link ConsommationsDeSeance#administrer}) : la sortie d'une administration de traitement. */
public class SortiesDeStockDuStock implements SortiesDeStockDesAdministrations {

    private final ConsommationsDeSeance consommations;

    public SortiesDeStockDuStock(ConsommationsDeSeance consommations) {
        this.consommations = consommations;
    }

    @Override
    public void sortir(CentreId centre, UUID seance, PatientId patient, LocalDate jour, UUID article, BigDecimal quantite, String auteur) {
        consommations.administrer(centre.valeur(), seance, patient.valeur(), jour, article, quantite, auteur);
    }
}
