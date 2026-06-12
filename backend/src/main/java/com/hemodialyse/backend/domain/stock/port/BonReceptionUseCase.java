package com.hemodialyse.backend.domain.stock.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.BonReception;
import com.hemodialyse.backend.domain.stock.model.LigneReception;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primary port: bon de reception (BR) + lots + PMP.
 */
public interface BonReceptionUseCase {
    BonReception create(CenterId centerId, UUID bonCommandeId, UUID fournisseurId,
                        LocalDate dateReception, List<LigneReception> lignes, String userId);

    /**
     * Update a DRAFT (BROUILLON) bon de reception: header + lots.
     * A validated BR cannot be edited this way (it already created lots/PMP).
     */
    BonReception update(CenterId centerId, UUID bonId, UUID fournisseurId,
                        LocalDate dateReception, List<LigneReception> lignes);

    /**
     * Validate a BR: create lots, ENTREE movements, recompute PMP.
     */
    BonReception valider(CenterId centerId, UUID bonId, String userId);

    /**
     * Create a draft BR from an existing validated BL (transformation).
     */
    BonReception fromBonCommande(CenterId centerId, UUID bonCommandeId, String userId);

    BonReception get(CenterId centerId, UUID bonId);

    List<BonReception> list(CenterId centerId);
}


