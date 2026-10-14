package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;

/** Consigne une observation sur un équipement du centre, réformé compris. Aucun événement (aucun consommateur). */
public final class ConsignerObservation {

    private final EquipementRepository equipements;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public ConsignerObservation(EquipementRepository equipements, Horloge horloge, UniteDeTravail uniteDeTravail) {
        this.equipements = equipements;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public EquipementVue executer(EquipementId id, CentreId centre, String observation, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Equipement equipement = equipements.trouverParId(id)
                    .filter(e -> e.centreId().equals(centre))
                    .orElseThrow(EquipementIntrouvable::new);
            equipement.consignerObservation(observation, par, horloge.maintenant());
            equipements.enregistrer(equipement);
            return EquipementVue.depuis(equipement);
        });
    }
}
