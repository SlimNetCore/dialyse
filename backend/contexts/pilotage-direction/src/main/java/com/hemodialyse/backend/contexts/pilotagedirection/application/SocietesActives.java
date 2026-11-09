package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.List;
import java.util.UUID;

/** Port sortant : les sociétés actives (lecture seule), pour la capture planifiée du mois écoulé. */
public interface SocietesActives {

    List<UUID> identifiants();
}
