package com.hemodialyse.backend.domain.planning.optimisation.port;

import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.ProfilInfirmier;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Map;
import java.util.UUID;

/**
 * Port de persistance des profils de planification des infirmiers (toujours borné au centre — AGENTS.md §2).
 */
public interface ProfilInfirmierPort {

    Map<UUID, ProfilInfirmier> profils(UUID centerId);

    /**
     * Infirmiers actifs du centre avec leur profil, par nom (AGENTS.md §9).
     */
    PagedResult<Ligne> lister(UUID centerId, int page, int size);

    /**
     * @return faux si l'infirmier n'appartient pas au centre
     */
    boolean enregistrer(UUID centerId, ProfilInfirmier profil);

    record Ligne(UUID infirmierId, String nom, QualificationInfirmier qualification, ProfilInfirmier profil) {
    }
}
