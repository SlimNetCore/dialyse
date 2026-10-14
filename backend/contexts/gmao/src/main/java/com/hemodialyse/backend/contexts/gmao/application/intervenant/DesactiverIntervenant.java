package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;

/** Désactive un intervenant du centre (jamais supprimé, R24). Idempotent : désactiver un désactivé répond la même vue. */
public final class DesactiverIntervenant {

    private final IntervenantRepository intervenants;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final UniteDeTravail uniteDeTravail;

    public DesactiverIntervenant(IntervenantRepository intervenants, PublicateurDEvenements publicateur, Horloge horloge,
                                 UniteDeTravail uniteDeTravail) {
        this.intervenants = intervenants;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.uniteDeTravail = uniteDeTravail;
    }

    public IntervenantVue executer(IntervenantId id, CentreId centre, UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Intervenant intervenant = intervenants.trouverParId(id)
                    .filter(i -> i.centreId().equals(centre))
                    .orElseThrow(IntervenantIntrouvable::new);
            intervenant.desactiver(par, horloge.maintenant());
            intervenants.enregistrer(intervenant);
            publicateur.publier(intervenant.tirerEvenements());
            return IntervenantVue.depuis(intervenant);
        });
    }
}
