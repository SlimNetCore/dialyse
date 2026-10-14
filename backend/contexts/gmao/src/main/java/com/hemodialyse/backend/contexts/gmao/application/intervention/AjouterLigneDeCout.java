package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;

import java.time.Instant;

/**
 * Ajoute une ligne de coût saisie. Pour R20, l'intervenant désigné n'est consulté que pour sa nature, et seulement s'il
 * appartient au centre (un intervenant d'un autre centre ou inconnu ne bloque rien, KB-GMAO-45). Aucune revalorisation
 * d'une intervention terminée.
 */
public final class AjouterLigneDeCout {

    private final InterventionRepository interventions;
    private final IntervenantsDuCentre intervenants;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public AjouterLigneDeCout(InterventionRepository interventions, IntervenantsDuCentre intervenants, Horloge horloge,
                              GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.intervenants = intervenants;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(AjouterLigneDeCoutCommande commande) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(commande.id())
                    .filter(i -> i.centreId().equals(commande.centre())).orElseThrow(InterventionIntrouvable::new);
            TypeLigneCout type = TypeLigneCout.valueOf(commande.type());
            LigneDeCout ligne = LigneDeCout.saisir(new LigneDeCoutId(identifiants.nouvelIdentifiant()), type,
                    commande.libelle(), commande.quantite(), commande.prixUnitaire(), commande.articleStockId());
            Instant maintenant = horloge.maintenant();
            intervention.ajouterLigneDeCout(ligne, natureDeLIntervenant(intervention, commande), commande.par(), maintenant);
            interventions.enregistrer(intervention);
            return InterventionVue.depuis(intervention, maintenant);
        });
    }

    private NatureIntervenant natureDeLIntervenant(Intervention intervention, AjouterLigneDeCoutCommande commande) {
        if (intervention.intervenantId() == null) return null;
        return intervenants.conditionsDe(intervention.intervenantId(), commande.centre())
                .map(ConditionsDeValorisation::nature)
                .orElse(null);
    }
}
