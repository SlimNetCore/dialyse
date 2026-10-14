package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Double in-memory de {@link InterventionsHotes} : retient les interventions qu'on lui donne et les verrous demandés. */
public class InterventionsHotesEnMemoire implements InterventionsHotes {

    private final Map<InterventionId, InterventionHote> hotes = new LinkedHashMap<>();
    public final List<InterventionId> verrous = new ArrayList<>();

    public void ajouter(InterventionHote hote) {
        hotes.put(hote.id(), hote);
    }

    @Override
    public Optional<InterventionHote> trouver(InterventionId id) {
        return Optional.ofNullable(hotes.get(id));
    }

    @Override
    public Optional<InterventionHote> verrouiller(InterventionId id) {
        verrous.add(id);
        return trouver(id);
    }
}
