package com.hemodialyse.backend.domain.referential.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import java.util.List;

/**
 * Port Out — Generic referential data access.
 * Each method returns simple DTOs since referential entities are read-only lookup tables.
 */
public interface ReferentialRepositoryPort {
    List<RefItem> findCentresPayeurs(CenterId centerId);
    List<RefItem> findAgences(CenterId centerId);
    List<RefItem> findCaisses(CenterId centerId);
    List<RefItem> findMedecins(CenterId centerId);
    List<RefItem> findSalles(CenterId centerId);
    List<RefItem> findPositions(CenterId centerId);
    List<RefItem> findTransporteurs(CenterId centerId);
    List<RefItem> findCategoriesTransport(CenterId centerId);
    List<RefItem> findForfaits(CenterId centerId);

    /** Simple read-only DTO for referential items */
    record RefItem(String id, String code, String nom, String adresse, String prenom, String libelle) {}
}

