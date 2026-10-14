package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.EtatEquipementPort;

import java.util.UUID;

/** Applique l'état constaté par une intervention (R4, KB-GMAO-40). Rien n'est écrit si l'état ne change pas. */
public final class ConstaterEtat implements EtatEquipementPort {

    private final EquipementRepository equipements;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public ConstaterEtat(EquipementRepository equipements, PublicateurDEvenements publicateur, Horloge horloge,
                         UniteDeTravail uniteDeTravail) {
        this.equipements = equipements;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    @Override
    public void constaterEtat(UUID equipementId, UUID centreId, String etat, String motif, UUID parUtilisateur) {
        uniteDeTravail.executer(() -> {
            CentreId centre = new CentreId(centreId);
            Equipement equipement = equipements.trouverParId(new EquipementId(equipementId))
                    .filter(e -> e.centreId().equals(centre))
                    .orElseThrow(EquipementIntrouvable::new);
            StatutEquipement statut = etat == null ? null : StatutEquipement.valueOf(etat);
            if (equipement.constaterEtat(statut, motif, new UtilisateurId(parUtilisateur), horloge.maintenant())) {
                equipements.enregistrer(equipement);
                publicateur.publier(equipement.tirerEvenements());
            }
            return null;
        });
    }
}
