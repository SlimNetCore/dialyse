package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;

import java.util.Map;
import java.util.UUID;

/**
 * Lecture des profils de planification des infirmiers, pour l'optimisation. Les profils sont tenus par le contexte
 * Planification (slice P1) : ce port en est la lecture, implémentée par une ACL sur son langage publié.
 */
public interface ProfilInfirmierPort {

    Map<UUID, ProfilInfirmier> profils(UUID centerId);
}
