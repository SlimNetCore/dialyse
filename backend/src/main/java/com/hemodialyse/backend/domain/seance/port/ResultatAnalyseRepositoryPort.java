package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ResultatAnalyseRepositoryPort {
    List<ResultatAnalyse> findByPatientId(UUID patientId, CenterId centerId, LocalDate from, LocalDate to);

    ResultatAnalyse save(ResultatAnalyse resultat);

    void deleteById(UUID analyseId, UUID patientId, CenterId centerId);
}

