package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;

/**
 * Planifie une intervention sur un équipement du centre. L'équipement n'est ni modifié ni contrôlé au-delà de son existence
 * dans le centre : un équipement réformé ou supprimé logiquement se planifie (KB-GMAO-49), l'intervenant n'est pas contrôlé
 * (KB-GMAO-42).
 */
public final class PlanifierIntervention {

    private final InterventionRepository interventions;
    private final EquipementRepository equipements;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public PlanifierIntervention(InterventionRepository interventions, EquipementRepository equipements,
                                 PublicateurDEvenements publicateur, Horloge horloge, GenerateurIdentifiants identifiants,
                                 UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.equipements = equipements;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(PlanifierInterventionCommande commande) {
        return uniteDeTravail.executer(() -> {
            equipements.trouverParId(commande.equipementId()).filter(e -> e.centreId().equals(commande.centreId()))
                    .orElseThrow(EquipementIntrouvable::new);
            TypeIntervention type = TypeIntervention.valueOf(commande.type());
            StatutEquipement etatAvant = StatutEquipement.valueOf(commande.etatAvant());
            PrioriteIntervention priorite = commande.priorite() == null || commande.priorite().isBlank()
                    ? PrioriteIntervention.NORMALE : PrioriteIntervention.valueOf(commande.priorite());
            Intervention intervention = Intervention.planifier(new InterventionId(identifiants.nouvelIdentifiant()),
                    commande.equipementId(), commande.centreId(), type, commande.debut(), commande.description(),
                    commande.intervenantId(), etatAvant, commande.symptome(), priorite, commande.echeance(), commande.par(),
                    horloge.maintenant());
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, horloge.maintenant());
        });
    }
}
