package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.SaisirUneAdministration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de la saisie d'une administration de traitement de l'anémie (mêmes champs que le legacy). */
public record AdministrationDemande(UUID centerId, UUID prescriptionMedicaleId, String typeTraitement, String molecule, BigDecimal dose,
                                    String uniteDose, String voie, LocalDate dateAdministration, UUID seanceId, String administrePar,
                                    Boolean administree, String motifNonAdministration, UUID articleId, BigDecimal quantiteArticle) {

    public SaisirUneAdministration.Saisie saisie() {
        return new SaisirUneAdministration.Saisie(prescriptionMedicaleId, typeTraitement, molecule, dose, uniteDose, voie, dateAdministration,
                seanceId, administrePar, administree, motifNonAdministration, articleId, quantiteArticle);
    }
}
