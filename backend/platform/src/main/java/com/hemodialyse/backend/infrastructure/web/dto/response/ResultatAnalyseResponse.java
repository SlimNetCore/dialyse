package com.hemodialyse.backend.infrastructure.web.dto.response;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse « résultats d'analyses » : NFS, bilan martial, adéquation de dialyse,
 * bilan phospho-calcique, nutrition et inflammation.
 */
public record ResultatAnalyseResponse(
        UUID id,
        UUID patientId,
        UUID centerId,
        LocalDate datePrelevement,
        BigDecimal hbGDl,
        BigDecimal htPct,
        Integer plaquettes,
        BigDecimal ferritineNgMl,
        BigDecimal cstfPct,
        BigDecimal epoEndogeneMuiMl,
        BigDecimal ureePreMgDl,
        BigDecimal ureePostMgDl,
        BigDecimal creatinineMgDl,
        BigDecimal ktVMensuel,
        BigDecimal phosphoreMgDl,
        BigDecimal calciumMgDl,
        BigDecimal pthPgMl,
        BigDecimal albumineGDl,
        BigDecimal proteinesGDl,
        BigDecimal crpMgL,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ResultatAnalyseResponse from(ResultatAnalyse a) {
        return new ResultatAnalyseResponse(
                a.getId(),
                a.getPatientId(),
                a.getCenterId(),
                a.getDatePrelevement(),
                a.getHbGDl(),
                a.getHtPct(),
                a.getPlaquettes(),
                a.getFerritineNgMl(),
                a.getCstfPct(),
                a.getEpoEndogeneMuiMl(),
                a.getUreePreMgDl(),
                a.getUreePostMgDl(),
                a.getCreatinineMgDl(),
                a.getKtVMensuel(),
                a.getPhosphoreMgDl(),
                a.getCalciumMgDl(),
                a.getPthPgMl(),
                a.getAlbumineGDl(),
                a.getProteinesGDl(),
                a.getCrpMgL(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
