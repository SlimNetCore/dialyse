package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;

import java.time.Instant;

/** Ajoute une tâche à faire à une intervention ouverte (R21) ; publie {@code TacheAjoutee}. Fonctionnalité nouvelle (KB-GMAO-55). */
public final class AjouterTache {

    private final InterventionRepository interventions;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public AjouterTache(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                        GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(AjouterTacheCommande commande) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(commande.id())
                    .filter(i -> i.centreId().equals(commande.centre())).orElseThrow(InterventionIntrouvable::new);
            Instant maintenant = horloge.maintenant();
            intervention.ajouterTache(new TacheId(identifiants.nouvelIdentifiant()), commande.description(), commande.par(),
                    maintenant);
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, maintenant);
        });
    }
}
