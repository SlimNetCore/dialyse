package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpsertResultatAnalyseRequest(
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
        BigDecimal crpMgL
) {
}

