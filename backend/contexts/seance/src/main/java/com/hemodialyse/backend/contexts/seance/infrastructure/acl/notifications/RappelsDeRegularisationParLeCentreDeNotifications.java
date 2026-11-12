package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;

import java.util.UUID;

/**
 * ACL sortante de la Séance vers le contexte Notifications ({@code notifications.v1}, ADR 0154) : l'alerte durable
 * {@code SEANCES_A_REGULARISER} pour {@code ADMIN}. Son identifiant est déterministe (centre, jour du centre) : une seule alerte par centre et
 * par jour du centre (KB-SEA-35).
 * <p>
 * GARANTIE multi-instances : le VERROU CONSULTATIF (centre, jour) pris par le cas d'usage dans la même transaction, pas un repli sur violation
 * de clé. Une seconde instance attend le verrou, puis voit l'identifiant déjà journalisé et n'émet rien ({@code false}). Aucun repli sur
 * {@code DataIntegrityViolationException} : sur PostgreSQL un INSERT en échec abandonne la transaction en cours (25P02) ; si le verrou était
 * contourné, l'erreur remonte, le centre est rendu en incident et il n'y a pas de doublon (la clé primaire du journal l'interdit). Le journal
 * s'écrit dans la transaction du cas d'usage, le temps réel part après son commit.
 */
public class RappelsDeRegularisationParLeCentreDeNotifications implements RappelsDeRegularisation {

    private final EmissionDesNotificationsDeLaSeance emission;

    public RappelsDeRegularisationParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        this.emission = new EmissionDesNotificationsDeLaSeance(notifier, diffusion);
    }

    @Override
    public boolean rappeler(RappelDeRegularisation rappel) {
        UUID centre = rappel.centre().valeur();
        UUID id = CatalogueDesNotificationsDeLaSeance.identifiantDuRappel(centre, rappel.jour());
        return emission.emettreApresLeCommit(CatalogueDesNotificationsDeLaSeance.seancesARegulariser(centre,
                rappel.seances().total(), rappel.seances().plusAncienne(), id));
    }
}
