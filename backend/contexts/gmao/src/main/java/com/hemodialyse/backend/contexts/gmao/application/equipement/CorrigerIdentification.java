package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.PrixAcquisition;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;

/**
 * Corrige l'identification d'un équipement du centre (désignation, fabricant, modèle, numéro de série, localisation, salle,
 * prix). Un équipement inconnu ou d'un autre centre est refusé de la même façon (KB-GMAO-35). La capacité de la salle est
 * contrôlée pour un générateur, en s'excluant lui-même.
 */
public final class CorrigerIdentification {

    private final EquipementRepository equipements;
    private final CapaciteDesSalles salles;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public CorrigerIdentification(EquipementRepository equipements, CapaciteDesSalles salles,
                                  PublicateurDEvenements publicateur, Horloge horloge, UniteDeTravail uniteDeTravail) {
        this.equipements = equipements;
        this.salles = salles;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public EquipementVue executer(CorrigerIdentificationCommande commande) {
        return uniteDeTravail.executer(() -> {
            Equipement equipement = equipements.trouverParId(commande.id())
                    .filter(e -> e.centreId().equals(commande.centreId()))
                    .orElseThrow(EquipementIntrouvable::new);
            SalleId salle = SalleId.ouAucune(commande.salleId());
            if (equipement.type().estGenerateurDeDialyse()) {
                salles.verifierAffectation(commande.centreId(), salle, equipement.id());
            }
            equipement.corrigerIdentification(commande.designation(), commande.fabricant(), commande.modele(),
                    commande.numeroSerie(), commande.localisation(), salle,
                    PrixAcquisition.ouAucun(commande.prixAcquisition()), commande.par(), horloge.maintenant());
            equipements.enregistrer(equipement);
            publicateur.publier(equipement.tirerEvenements());
            return EquipementVue.depuis(equipement);
        });
    }
}
