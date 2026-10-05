package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

/**
 * Port de sortie : articles proposés en un toucher à l'infirmier, par centre et dans l'ordre choisi.
 */
public interface SeanceRaccourciRepositoryPort {
    List<UUID> findArticleIds(CenterId centerId);

    /**
     * Remplace toute la liste du centre par {@code articleIds} (ordre conservé).
     */
    void replace(CenterId centerId, List<UUID> articleIds);
}
