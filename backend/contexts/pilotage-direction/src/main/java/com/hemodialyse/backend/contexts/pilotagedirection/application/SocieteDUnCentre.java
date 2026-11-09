package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : la société à laquelle appartient un centre (vide si le centre est inconnu). */
public interface SocieteDUnCentre {

    Optional<UUID> de(UUID centreId);
}
