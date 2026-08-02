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

    List<RefItem> findArticles(CenterId centerId);

    /**
     * Returns all dialysis generators belonging to the given centre.
     * Each item carries: id, code=numero, nom=label, libelle=marque+modele, adresse=salleId.
     */
    List<RefItem> findGenerateurs(CenterId centerId);

    /**
     * Returns only the generators of a specific salle (room filter for UI).
     */
    List<RefItem> findGenerateursBySalle(CenterId centerId, java.util.UUID salleId);

    List<CentrePayeurDetail> findCentresPayeursDetails(CenterId centerId);

    /** Simple read-only DTO for referential items */
    record RefItem(String id, String code, String nom, String adresse, String prenom, String libelle,
                   Boolean gereParLot) {
    }

    record CentrePayeurDetail(
            String id,
            String codeCentrePayeur,
            String libelleCentrePayeur,
            String adresseCentrePayeur,
            String codeAgence,
            String libelleAgence,
            String codeCaisse,
            String libelleCaisse
    ) {
    }
}

