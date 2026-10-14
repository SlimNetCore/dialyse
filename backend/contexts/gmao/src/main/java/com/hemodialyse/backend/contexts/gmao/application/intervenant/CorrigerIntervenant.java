package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenant;

/**
 * Corrige un intervenant du centre : nom, nature, coordonnées et tarif horaire (révision du tarif comprise). Un intervenant
 * inconnu ou d'un autre centre est refusé de la même façon (KB-GMAO-23).
 */
public final class CorrigerIntervenant {

    private final IntervenantRepository intervenants;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public CorrigerIntervenant(IntervenantRepository intervenants, PublicateurDEvenements publicateur, Horloge horloge,
                               UniteDeTravail uniteDeTravail) {
        this.intervenants = intervenants;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public IntervenantVue executer(CorrigerIntervenantCommande commande) {
        return uniteDeTravail.executer(() -> {
            Intervenant intervenant = intervenants.trouverParId(commande.id())
                    .filter(i -> i.centreId().equals(commande.centreId()))
                    .orElseThrow(IntervenantIntrouvable::new);
            // Ordre du legacy : le code de nature n'est reconnu qu'une fois l'intervenant trouvé.
            NatureIntervenant nature = NatureIntervenant.depuis(commande.nature());
            intervenant.corriger(commande.nom(), nature, commande.telephone(), commande.email(),
                    TarifIntervenant.ouAucun(commande.tarifHoraire()), commande.par(), horloge.maintenant());
            intervenants.enregistrer(intervenant);
            publicateur.publier(intervenant.tirerEvenements());
            return IntervenantVue.depuis(intervenant);
        });
    }
}
