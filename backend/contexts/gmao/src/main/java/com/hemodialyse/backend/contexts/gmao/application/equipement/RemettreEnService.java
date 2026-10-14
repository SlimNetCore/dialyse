package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;

/** Remet en service un équipement du centre hors service (R1, R3). */
public final class RemettreEnService {

    private final EquipementRepository equipements;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public RemettreEnService(EquipementRepository equipements, PublicateurDEvenements publicateur, Horloge horloge,
                             UniteDeTravail uniteDeTravail) {
        this.equipements = equipements;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public EquipementVue executer(EquipementId id, CentreId centre, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Equipement equipement = equipements.trouverParId(id)
                    .filter(e -> e.centreId().equals(centre))
                    .orElseThrow(EquipementIntrouvable::new);
            equipement.remettreEnService(par, horloge.maintenant());
            equipements.enregistrer(equipement);
            publicateur.publier(equipement.tirerEvenements());
            return EquipementVue.depuis(equipement);
        });
    }
}
