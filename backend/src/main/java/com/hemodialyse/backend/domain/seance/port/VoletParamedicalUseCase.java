package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.util.UUID;

public interface VoletParamedicalUseCase {
    VoletParamedical save(CenterId centerId,
                          UUID seanceId,
                          BigDecimal poidsAvantKg,
                          BigDecimal poidsApresKg,
                          String taAvant,
                          String taApres,
                          Integer dureeMinutes,
                          Integer debitSangMlMin,
                          BigDecimal ultrafiltrationMl,
                          String anticoagulant,
                          String typeDialysat,
                          String incidents);
}

