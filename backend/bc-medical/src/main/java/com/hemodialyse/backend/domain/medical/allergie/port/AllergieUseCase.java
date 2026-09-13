package com.hemodialyse.backend.domain.medical.allergie.port;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CategorieAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CriticiteAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.StatutVerificationAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.TypeReaction;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.UUID;

public interface AllergieUseCase {

    PagedResult<Allergie> listPagedByPatient(CenterId centerId, UUID patientId, int page, int size);

    /**
     * Bandeau permanent du dossier : liste non paginée des allergies de criticité haute.
     */
    java.util.List<Allergie> listCritiquesByPatient(CenterId centerId, UUID patientId);

    Allergie create(CenterId centerId, UUID patientId, ConceptCode substance, CategorieAllergie categorie,
                    CriticiteAllergie criticite, TypeReaction typeReaction, String manifestations,
                    LocalDate dateConstatation, StatutVerificationAllergie statutVerification);

    Allergie update(CenterId centerId, UUID patientId, UUID allergieId, CriticiteAllergie criticite,
                    String manifestations, StatutVerificationAllergie statutVerification);

    void delete(CenterId centerId, UUID patientId, UUID allergieId);
}
