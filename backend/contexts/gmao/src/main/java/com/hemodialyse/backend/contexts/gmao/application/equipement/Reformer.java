package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;

/**
 * Réforme un équipement du centre (R1 : état terminal). L'habilitation {@code GMAO_REFORME} est exigée par l'adapter HTTP
 * (en plus du rôle d'administrateur, KB-GMAO-32).
 */
public final class Reformer {

    private final EquipementRepository equipements;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public Reformer(EquipementRepository equipements, PublicateurDEvenements publicateur, Horloge horloge,
                    UniteDeTravail uniteDeTravail) {
        this.equipements = equipements;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public EquipementVue executer(EquipementId id, CentreId centre, String motif, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Equipement equipement = equipements.trouverParId(id)
                    .filter(e -> e.centreId().equals(centre))
                    .orElseThrow(EquipementIntrouvable::new);
            equipement.reformer(motif, par, horloge.maintenant());
            equipements.enregistrer(equipement);
            publicateur.publier(equipement.tirerEvenements());
            return EquipementVue.depuis(equipement);
        });
    }
}
