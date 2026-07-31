package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ResultatAnalyseUseCase {
    List<ResultatAnalyse> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to);

    ResultatAnalyse save(CenterId centerId,
                         UUID patientId,
                         UUID analyseId,
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
                         BigDecimal crpMgL);

    void delete(CenterId centerId, UUID patientId, UUID analyseId);
}

