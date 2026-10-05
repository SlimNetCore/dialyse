package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.UUID;

/**
 * Port d'entrée : raccourcis de consommables du poste infirmier. Quand le centre n'en a défini aucun, l'écran propose
 * les articles les plus sortis du jour.
 */
public interface SeanceRaccourciUseCase {
    /**
     * Nombre maximal d'articles en raccourci.
     */
    int MAX = 12;

    List<UUID> get(CenterId centerId);

    /**
     * Remplace les raccourcis du centre. Refuse plus de {@value #MAX} articles, un doublon, ou un article inconnu du
     * centre ou inactif.
     */
    List<UUID> replace(CenterId centerId, List<UUID> articleIds);
}
