package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SocieteDUnCentre;

import java.util.Optional;
import java.util.UUID;

/** La société d'un centre, lue dans le langage publié d'Organisation (O4 lot A, ADR 0107) ; remplace {@code SocieteDUnCentreJdbc}. */
public final class SocieteDUnCentrePublie implements SocieteDUnCentre {

    private final AnnuaireDesSocietes societes;

    public SocieteDUnCentrePublie(AnnuaireDesSocietes societes) {
        this.societes = societes;
    }

    @Override
    public Optional<UUID> de(UUID centreId) {
        return societes.societeDuCentre(centreId);
    }
}
