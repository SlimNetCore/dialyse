package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du contexte GMAO : le nombre de générateurs de dialyse installés dans chaque centre, lu par le pilotage de la
 * direction pour la capacité théorique. Sont comptés les générateurs installés strictement avant {@code avant}, ni réformés,
 * ni désactivés, ni supprimés ; un centre sans générateur est absent du résultat.
 */
public interface GenerateursInstalles {

    Map<UUID, Long> parCentre(Collection<UUID> centres, Instant avant);

    /**
     * Les seuls générateurs de dialyse {@code EN_SERVICE} installés strictement avant {@code avant} (ni en maintenance, ni hors
     * service, ni réformés, ni désactivés, ni supprimés) : la capacité théorique ne compte que ce qui peut dialyser, comme le planning
     * de la semaine (P6, KB-PLN-76). Un centre sans générateur en service est absent du résultat.
     */
    Map<UUID, Long> enServiceParCentre(Collection<UUID> centres, Instant avant);
}
