package com.hemodialyse.backend.domain.medical.serologie.port;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SerologieUseCase {

    PagedResult<Serologie> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    /**
     * Dernier résultat connu par marqueur — alimente le bandeau « statut sérologique » du dossier.
     */
    List<Serologie> listDerniersResultatsByPatient(CenterId centerId, UUID patientId);

    Serologie create(CenterId centerId, UUID patientId, MarqueurSerologique marqueur, ResultatSerologique resultat,
                     BigDecimal titre, String unite, LocalDate datePrelevement, String laboratoire,
                     LocalDate dateProchainControle, String conduiteATenir);

    Serologie update(CenterId centerId, UUID patientId, UUID serologieId, ResultatSerologique resultat,
                     String conduiteATenir);

    void delete(CenterId centerId, UUID patientId, UUID serologieId);
}
