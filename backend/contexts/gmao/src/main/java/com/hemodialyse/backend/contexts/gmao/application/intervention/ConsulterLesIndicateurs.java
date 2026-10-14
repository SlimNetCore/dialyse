package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementIntrouvable;
import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.IndicateursDIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.IndicateursDIntervention.Indicateurs;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

/**
 * Indicateurs de réactivité d'une intervention du centre (RG-GMA-031). Intervention inconnue, supprimée ou d'un autre centre :
 * {@link InterventionIntrouvable} ; équipement inconnu ou d'un autre centre : {@code EquipementIntrouvable} (400 tous deux).
 */
public final class ConsulterLesIndicateurs {

    private final LectureInterventions interventions;
    private final LectureEquipements equipements;
    private final HistoriqueDesStatuts historique;
    private final Horloge horloge;

    public ConsulterLesIndicateurs(LectureInterventions interventions, LectureEquipements equipements,
                                   HistoriqueDesStatuts historique, Horloge horloge) {
        this.interventions = interventions;
        this.equipements = equipements;
        this.historique = historique;
        this.horloge = horloge;
    }

    public Indicateurs executer(InterventionId id, CentreId centre) {
        Intervention intervention = interventions.parId(id).filter(i -> i.centreId().equals(centre))
                .orElseThrow(InterventionIntrouvable::new);
        var equipement = equipements.parId(intervention.equipementId()).filter(e -> e.centreId().equals(centre.valeur()))
                .orElseThrow(EquipementIntrouvable::new);
        return IndicateursDIntervention.calculer(intervention, historique.deLEquipement(intervention.equipementId()),
                equipement.statut(), horloge.maintenant());
    }
}
