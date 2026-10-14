package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementIntrouvable;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Bon d'intervention imprimable d'une intervention du centre : réunit les données et confie la mise en page au port
 * {@link ImpressionDuBon}. Intervention inconnue, supprimée ou d'un autre centre : {@link InterventionIntrouvable} ;
 * équipement inconnu ou d'un autre centre : {@link EquipementIntrouvable} (400 tous deux). Un intervenant inconnu ou d'un autre
 * centre est simplement omis du bon.
 */
public final class ImprimerLeBon {

    private final LectureInterventions interventions;
    private final LectureEquipements equipements;
    private final LectureIntervenants intervenants;
    private final ConsulterLaChronologie chronologie;
    private final ImpressionDuBon impression;
    private final Horloge horloge;

    public ImprimerLeBon(LectureInterventions interventions, LectureEquipements equipements, LectureIntervenants intervenants,
                         ConsulterLaChronologie chronologie, ImpressionDuBon impression, Horloge horloge) {
        this.interventions = interventions;
        this.equipements = equipements;
        this.intervenants = intervenants;
        this.chronologie = chronologie;
        this.impression = impression;
        this.horloge = horloge;
    }

    public DocumentImprime executer(InterventionId id, CentreId centre, ZoneId zone, String editePar) {
        Intervention intervention = interventions.parId(id).filter(i -> i.centreId().equals(centre))
                .orElseThrow(InterventionIntrouvable::new);
        EquipementVue equipement = equipements.parId(intervention.equipementId())
                .filter(e -> e.centreId().equals(centre.valeur())).orElseThrow(EquipementIntrouvable::new);
        IntervenantVue intervenant = intervention.intervenantId() == null ? null
                : intervenants.parId(intervention.intervenantId()).filter(i -> i.centreId().equals(centre.valeur())).orElse(null);
        Instant maintenant = horloge.maintenant();

        return impression.imprimer(centre, new DonneesDuBon(InterventionVue.depuis(intervention, maintenant), equipement,
                intervenant, chronologie.de(intervention), maintenant), zone, editePar);
    }
}
