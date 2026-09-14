package com.hemodialyse.backend.domain.medical.greffe.port;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutEtapeGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EtapeBilanPreGreffeUseCase {

    List<EtapeBilanPreGreffe> listByPatient(CenterId centerId, UUID patientId);

    EtapeBilanPreGreffe create(CenterId centerId, UUID patientId, CategorieEtapeGreffe categorie, String libelle);

    EtapeBilanPreGreffe update(CenterId centerId, UUID patientId, UUID etapeId, StatutEtapeGreffe statut,
                               LocalDate dateRealisation, String resultat, LocalDate dateExpiration,
                               UUID demandeExamenId, UUID serologieId);

    void delete(CenterId centerId, UUID patientId, UUID etapeId);

    /**
     * Génère la checklist standard pour ce patient — insertion idempotente : une étape
     * (catégorie, libellé) déjà présente n'est pas dupliquée.
     */
    List<EtapeBilanPreGreffe> genererEtapesStandard(CenterId centerId, UUID patientId);
}
