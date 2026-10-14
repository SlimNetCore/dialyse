package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.PrixAcquisition;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.UniciteDuCodeEquipement;

/** Enregistre un équipement en service dans le parc du centre (R7 : code unique ; capacité de la salle pour un générateur). */
public final class EnregistrerEquipement {

    private final EquipementRepository equipements;
    private final UniciteDuCodeEquipement unicite;
    private final CapaciteDesSalles salles;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public EnregistrerEquipement(EquipementRepository equipements, UniciteDuCodeEquipement unicite, CapaciteDesSalles salles,
                                 PublicateurDEvenements publicateur, Horloge horloge, GenerateurIdentifiants identifiants,
                                 UniteDeTravail uniteDeTravail) {
        this.equipements = equipements;
        this.unicite = unicite;
        this.salles = salles;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public EquipementVue executer(EnregistrerEquipementCommande commande) {
        return uniteDeTravail.executer(() -> {
            // Ordre du legacy : l'unicité du code, puis la reconnaissance du type, puis la capacité de la salle.
            CodeEquipement code = new CodeEquipement(commande.code());
            unicite.verifier(commande.centreId(), code);
            TypeEquipement type = TypeEquipement.depuis(commande.type());
            SalleId salle = SalleId.ouAucune(commande.salleId());
            if (type.estGenerateurDeDialyse()) {
                salles.verifierAffectation(commande.centreId(), salle, null);
            }
            Equipement equipement = Equipement.enregistrer(new EquipementId(identifiants.nouvelIdentifiant()),
                    commande.centreId(), code, commande.designation(), type, commande.fabricant(), commande.modele(),
                    commande.numeroSerie(), commande.dateInstallation(), commande.localisation(), salle,
                    PrixAcquisition.ouAucun(commande.prixAcquisition()), commande.par(), horloge.maintenant());
            equipements.enregistrer(equipement);
            publicateur.publier(equipement.tirerEvenements());
            return EquipementVue.depuis(equipement);
        });
    }
}
