package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.UniteDeFrequence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de la création et de la modification d'une prescription médicale (mêmes champs que le legacy, tous facultatifs). */
public record PrescriptionDemande(UUID centerId, LocalDate datePrescription, UUID medecinId, Integer qbCible, Integer qdCible,
                                  Integer ufMaxMl, Integer dureeCibleMin, BigDecimal poidsSecCibleKg, String typeDialyseurPrescrit,
                                  String anticoagTypePrescrit, UUID epoArticleId, Integer epoDoseUi, String epoVoie,
                                  Integer epoFrequenceValeur, UniteDeFrequence epoFrequenceUnite, UUID ferArticleId, Integer ferDoseMg,
                                  String ferVoie, Integer ferFrequenceValeur, UniteDeFrequence ferFrequenceUnite) {

    public ContenuDePrescription contenu() {
        return new ContenuDePrescription(datePrescription, medecinId, qbCible, qdCible, ufMaxMl, dureeCibleMin, poidsSecCibleKg,
                typeDialyseurPrescrit, anticoagTypePrescrit,
                new Traitement(epoArticleId, epoDoseUi, epoVoie, epoFrequenceValeur, epoFrequenceUnite),
                new Traitement(ferArticleId, ferDoseMg, ferVoie, ferFrequenceValeur, ferFrequenceUnite));
    }
}
