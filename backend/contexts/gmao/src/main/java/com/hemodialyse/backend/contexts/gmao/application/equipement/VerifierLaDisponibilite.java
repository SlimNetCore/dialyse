package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

/**
 * Vérifie, avant d'affecter un patient à un générateur du centre, que l'équipement est disponible (RG-GMA-066) : seul son
 * statut compte. Équipement inconnu ou d'un autre centre : {@link EquipementIntrouvable} (400) ; supprimé logiquement : servi.
 */
public final class VerifierLaDisponibilite {

    private final LectureEquipements equipements;

    public VerifierLaDisponibilite(LectureEquipements equipements) {
        this.equipements = equipements;
    }

    public DisponibilitePourUnPatient executer(EquipementId id, CentreId centre) {
        EquipementVue equipement = equipements.parId(id).filter(e -> e.centreId().equals(centre.valeur()))
                .orElseThrow(EquipementIntrouvable::new);
        return new DisponibilitePourUnPatient(equipement.statut().disponiblePourUnPatient(), equipement.statut());
    }
}
