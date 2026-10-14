package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.TarifIntervenant;

/** Enregistre un intervenant (interne ou externe) dans le référentiel du centre. */
public final class EnregistrerIntervenant {

    private final IntervenantRepository intervenants;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public EnregistrerIntervenant(IntervenantRepository intervenants, PublicateurDEvenements publicateur, Horloge horloge,
                                  GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        this.intervenants = intervenants;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public IntervenantVue executer(EnregistrerIntervenantCommande commande) {
        // Ordre du legacy : la nature est reconnue avant les règles du domaine (nom, tarif).
        NatureIntervenant nature = NatureIntervenant.depuis(commande.nature());
        return uniteDeTravail.executer(() -> {
            Intervenant intervenant = Intervenant.enregistrer(
                    new IntervenantId(identifiants.nouvelIdentifiant()), commande.centreId(), commande.nom(), nature,
                    commande.telephone(), commande.email(), TarifIntervenant.ouAucun(commande.tarifHoraire()), commande.par(),
                    horloge.maintenant());
            intervenants.enregistrer(intervenant);
            publicateur.publier(intervenant.tirerEvenements());
            return IntervenantVue.depuis(intervenant);
        });
    }
}
