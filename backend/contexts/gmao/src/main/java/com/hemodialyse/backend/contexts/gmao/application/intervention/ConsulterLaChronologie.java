package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chronologie d'une intervention du centre (RG-GMA-031), chaque événement avec le nom de son auteur. Intervention inconnue,
 * supprimée ou d'un autre centre : {@link InterventionIntrouvable} (400, KB-GMAO-53).
 */
public final class ConsulterLaChronologie {

    private final LectureInterventions interventions;
    private final NomsDesUtilisateurs noms;

    public ConsulterLaChronologie(LectureInterventions interventions, NomsDesUtilisateurs noms) {
        this.interventions = interventions;
        this.noms = noms;
    }

    public List<EvenementVu> executer(InterventionId id, CentreId centre) {
        return de(interventions.parId(id).filter(i -> i.centreId().equals(centre)).orElseThrow(InterventionIntrouvable::new));
    }

    /** La chronologie d'une intervention déjà chargée (le bon d'intervention la cite). */
    List<EvenementVu> de(Intervention intervention) {
        Map<UtilisateurId, Optional<String>> connus = new HashMap<>();
        return intervention.chronologie().stream()
                .map(e -> new EvenementVu(e.type(), e.le(), e.par() == null ? null : e.par().valeur(),
                        e.par() == null ? null : connus.computeIfAbsent(e.par(), noms::de).orElse(null), e.detail()))
                .toList();
    }
}
