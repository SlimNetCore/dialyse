package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.List;
import java.util.UUID;

/**
 * La société dont la direction suit les centres : son périmètre borne chaque lecture (une requête ne porte jamais que sur les centres de
 * la société). Les centres sont triés par nom par la source ; la société peut n'en avoir aucun.
 */
public record SocieteSuivie(UUID id, String raisonSociale, List<CentreDeLaSociete> centres) {

    public SocieteSuivie {
        centres = List.copyOf(centres);
    }

    public boolean estSansCentre() {
        return centres.isEmpty();
    }

    public List<UUID> identifiantsDesCentres() {
        return centres.stream().map(CentreDeLaSociete::id).toList();
    }
}
