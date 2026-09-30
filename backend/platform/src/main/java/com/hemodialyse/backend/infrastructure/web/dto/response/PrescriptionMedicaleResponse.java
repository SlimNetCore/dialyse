package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.model.UniteFrequence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse « prescription médicale » : cibles de dialyse + traitement de l'anémie (EPO et fer injectable).
 * Les libellés d'article ({@code epoArticleCode}/{@code epoArticleLibelle} et équivalents fer) sont
 * résolus par le contrôleur à partir de {@code ArticleRepositoryPort} (bc-article), pour éviter que
 * bc-seance ne dépende du contexte article.
 */
public record PrescriptionMedicaleResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        LocalDate datePrescription,
        UUID medecinId,
        Integer qbCible,
        Integer qdCible,
        Integer ufMaxMl,
        Integer dureeCibleMin,
        BigDecimal poidsSecCibleKg,
        String typeDialyseurPrescrit,
        String anticoagTypePrescrit,
        UUID epoArticleId,
        String epoArticleCode,
        String epoArticleLibelle,
        Integer epoDoseUi,
        String epoVoie,
        Integer epoFrequenceValeur,
        UniteFrequence epoFrequenceUnite,
        UUID ferArticleId,
        String ferArticleCode,
        String ferArticleLibelle,
        Integer ferDoseMg,
        String ferVoie,
        Integer ferFrequenceValeur,
        UniteFrequence ferFrequenceUnite,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static PrescriptionMedicaleResponse from(PrescriptionMedicale p) {
        return from(p, null, null, null, null);
    }

    public static PrescriptionMedicaleResponse from(PrescriptionMedicale p,
                                                    String epoArticleCode,
                                                    String epoArticleLibelle,
                                                    String ferArticleCode,
                                                    String ferArticleLibelle) {
        return new PrescriptionMedicaleResponse(
                p.getId(),
                p.getPatientId(),
                p.getCenterId(),
                p.getDatePrescription(),
                p.getMedecinId(),
                p.getQbCible(),
                p.getQdCible(),
                p.getUfMaxMl(),
                p.getDureeCibleMin(),
                p.getPoidsSecCibleKg(),
                p.getTypeDialyseurPrescrit(),
                p.getAnticoagTypePrescrit(),
                p.getEpoArticleId(),
                epoArticleCode,
                epoArticleLibelle,
                p.getEpoDoseUi(),
                p.getEpoVoie(),
                p.getEpoFrequenceValeur(),
                p.getEpoFrequenceUnite(),
                p.getFerArticleId(),
                ferArticleCode,
                ferArticleLibelle,
                p.getFerDoseMg(),
                p.getFerVoie(),
                p.getFerFrequenceValeur(),
                p.getFerFrequenceUnite(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
