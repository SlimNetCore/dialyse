package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;
import com.hemodialyse.backend.contexts.gmao.application.Horloge;
import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;
import com.hemodialyse.backend.contexts.gmao.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;

import java.time.Instant;

/**
 * Rectifie une intervention terminée : la rouvre avec un motif (R15). L'équipement n'est pas touché (KB-GMAO-51) : il reste
 * dans l'état posé par la clôture annulée. Réservé aux administrateurs détenteurs de l'habilitation {@code GMAO_RECTIFICATION} (KB-GMAO-52, contrôle à
 * l'interface).
 */
public final class Rectifier {

    private final InterventionRepository interventions;
    private final PublicateurDEvenements publicateur;
    private final Horloge horloge;
    private final GenerateurIdentifiants identifiants;
    private final UniteDeTravail uniteDeTravail;

    public Rectifier(InterventionRepository interventions, PublicateurDEvenements publicateur, Horloge horloge,
                     GenerateurIdentifiants identifiants, UniteDeTravail uniteDeTravail) {
        this.interventions = interventions;
        this.publicateur = publicateur;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.uniteDeTravail = uniteDeTravail;
    }

    public InterventionVue executer(InterventionId id, CentreId centre, String motif, Instant nouveauDebut,
                                    UtilisateurId par) {
        return uniteDeTravail.executer(() -> {
            Intervention intervention = interventions.chargerPourModification(id).filter(i -> i.centreId().equals(centre))
                    .orElseThrow(InterventionIntrouvable::new);
            Instant maintenant = horloge.maintenant();
            intervention.rectifier(motif, nouveauDebut, identifiants.nouvelIdentifiant(), par, maintenant);
            interventions.enregistrer(intervention);
            publicateur.publier(intervention.tirerEvenements());
            return InterventionVue.depuis(intervention, maintenant);
        });
    }
}
