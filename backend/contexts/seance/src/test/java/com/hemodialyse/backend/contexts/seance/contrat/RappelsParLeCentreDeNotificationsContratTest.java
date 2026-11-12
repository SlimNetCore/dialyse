package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications.RappelsDeRegularisationParLeCentreDeNotifications;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contrat des rappels sur l'ACL vers notifications.v1 (PL13b, ADR 0154), au-dessus d'un journal qui dédoublonne par identifiant
 * déterministe comme le journal réel. Le jour du rappel est retrouvé depuis l'identifiant (UUID v3 de « type | centre | jour »), ce qui
 * fige aussi sa formule.
 */
class RappelsParLeCentreDeNotificationsContratTest extends RappelsContrat {

    private final List<String> emissions = new ArrayList<>();
    private final Set<UUID> journal = new HashSet<>();

    private final NotifierEnDeuxTemps notifier = demande -> {
        if (!journal.add(demande.identifiantDeterministe())) return Optional.empty();
        emissions.add(demande.centre() + "|" + jourDe(demande.centre(), demande.identifiantDeterministe()) + "|"
                + demande.contenu().get("nbSeances") + "|"
                + (demande.contenu().get("plusAncienne").isEmpty() ? "null" : demande.contenu().get("plusAncienne")));
        return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), demande.identifiantDeterministe(),
                Instant.parse("2026-06-15T08:00:00Z"), null));
    };

    private static LocalDate jourDe(UUID centre, UUID identifiant) {
        for (int decalage = -2; decalage <= 2; decalage++) {
            LocalDate jour = JOUR.plusDays(decalage);
            if (UUID.nameUUIDFromBytes(("SEANCES_A_REGULARISER|" + centre + "|" + jour).getBytes(StandardCharsets.UTF_8)).equals(identifiant)) {
                return jour;
            }
        }
        throw new AssertionError("identifiant du rappel inattendu : " + identifiant);
    }

    @Override
    protected RappelsDeRegularisation rappels() {
        return new RappelsDeRegularisationParLeCentreDeNotifications(notifier, (centre, type, contenu, identifiant, emisLe) -> { });
    }

    @Override
    protected List<String> emissions() {
        return emissions;
    }
}
