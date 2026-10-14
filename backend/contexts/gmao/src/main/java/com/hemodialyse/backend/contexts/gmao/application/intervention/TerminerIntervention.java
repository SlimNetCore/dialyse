package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.EtatEquipementPort;

import java.time.Instant;

/**
 * Clôture une intervention en cours : valorise le temps de l'intervenant (R19) d'après sa nature et son tarif lus à
 * l'instant de la clôture (KB-GMAO-46, reproduit : aucun instantané), puis l'équipement prend l'état relevé après
 * intervention (R28) ; l'événement {@code InterventionTerminee} est publié. Tout s'écrit dans une seule transaction
 * (KB-GMAO-56) : un équipement réformé refuse l'état, rien n'est enregistré et l'intervention reste en cours (KB-GMAO-49).
 * Un intervenant inconnu, d'un autre centre ou sans tarif ne produit aucune ligne, sans erreur (KB-GMAO-42).
 */
public final class TerminerIntervention {

    private final InterventionRepository interventions;
    private final IntervenantsDuCentre intervenants;
    private final EtatEquipementPort etatEquipement;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public TerminerIntervention(InterventionRepository interventions, IntervenantsDuCentre intervenants,
                                EtatEquipementPort etatEquipement, PublicateurDEvenements publicateur, Horloge horloge,
                                GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.intervenants = intervenants;
        this.etatEquipement = etatEquipement;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(TerminerInterventionCommande commande) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(commande.id())
                    .filter(i -> i.centreId().equals(commande.centre())).orElseThrow(InterventionIntrouvable::new);
            StatutEquipement etatApres = StatutEquipement.valueOf(commande.etatApres());
            Instant maintenant = horloge.maintenant();
            intervention.terminer(commande.actions(), etatApres, commande.fin(), commande.cause(),
                    conditionsDeValorisation(intervention, commande), new LigneDeCoutId(identifiants.nouvelIdentifiant()),
                    commande.par(), maintenant);
            etatEquipement.constaterEtat(intervention.equipementId().valeur(), commande.centre().valeur(), etatApres.name(),
                    "Intervention terminée", commande.par().valeur());
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, maintenant);
        });
    }

    private ConditionsDeValorisation conditionsDeValorisation(Intervention intervention, TerminerInterventionCommande commande) {
        if (intervention.intervenantId() == null) return null;
        return intervenants.conditionsDe(intervention.intervenantId(), commande.centre()).orElse(null);
    }
}
