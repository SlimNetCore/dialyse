package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.EtatEquipementPort;

import java.time.Instant;

/**
 * Démarre une intervention planifiée : l'équipement prend l'état relevé à la planification (R28), dans la même transaction
 * (KB-GMAO-56 : écart bénin accepté, le legacy écrivait l'équipement avant l'intervention, chacun de son côté). Un
 * équipement réformé refuse l'état : rien n'est enregistré et l'intervention reste planifiée (KB-GMAO-49).
 */
public final class DemarrerIntervention {

    private final InterventionRepository interventions;
    private final EtatEquipementPort etatEquipement;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public DemarrerIntervention(InterventionRepository interventions, EtatEquipementPort etatEquipement,
                                PublicateurDEvenements publicateur, Horloge horloge, UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.etatEquipement = etatEquipement;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(InterventionId id, CentreId centre, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(id).filter(i -> i.centreId().equals(centre))
                    .orElseThrow(InterventionIntrouvable::new);
            Instant maintenant = horloge.maintenant();
            intervention.demarrer(par, maintenant);
            // une intervention ancienne peut ne pas avoir d'état avant : l'équipement n'est alors pas touché
            if (intervention.etatAvant() != null) {
                etatEquipement.constaterEtat(intervention.equipementId().valeur(), centre.valeur(),
                        intervention.etatAvant().name(), "Intervention démarrée", par.valeur());
            }
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, maintenant);
        });
    }
}
