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
}
