package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;

/**
 * Liste paginée des interventions du centre, filtrable par statut, ou de l'une de ses machines. Reproduit le legacy
 * (KB-GMAO-54) : quand un équipement est donné, le statut est ignoré, et l'équipement doit exister dans le centre (400
 * sinon, comme les commandes) ; les bornes reproduisent les refus de Spring Data exposés en 400 ; aucune borne haute sur
 * {@code size}.
 */
public final class ListerInterventions {

    private final LectureInterventions lecture;
    private final EquipementRepository equipements;
    private final Horloge horloge;

    public ListerInterventions(LectureInterventions lecture, EquipementRepository equipements, Horloge horloge) {
        this.lecture = lecture;
        this.equipements = equipements;
        this.horloge = horloge;
    }

    public PageDInterventions<InterventionVue> executer(CentreId centre, String statut, EquipementId equipement, int page,
                                                        int size) {
        if (equipement != null) {
            equipements.trouverParId(equipement).filter(e -> e.centreId().equals(centre))
                    .orElseThrow(EquipementIntrouvable::new);
        }
        verifierLesBornes(page, size);
        var resultat = equipement != null ? lecture.deLEquipement(equipement, page, size)
                : lecture.duCentre(centre, statut, page, size);
        var maintenant = horloge.maintenant();
        return resultat.convertir(i -> InterventionVue.depuis(i, maintenant));
    }

    private static void verifierLesBornes(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Page index must not be less than zero");
        if (size < 1) throw new IllegalArgumentException("Page size must not be less than one");
    }
}
