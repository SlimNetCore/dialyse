package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (besoin des Absences, fourni par la plateforme : centre de notifications, hors contexte) : l'alerte durable
 * « absences à qualifier » du centre (journal des alertes et diffusion temps réel). L'alerte a une clé déterministe par centre et par
 * jour du centre : deux contrôles du même jour (même simultanés, plusieurs instances) n'en créent qu'une.
 */
public interface NotificationsDuCentre {

    /**
     * @param jour jour du centre du contrôle (la clé de l'alerte avec le centre)
     * @return vrai si l'alerte a été émise, faux si celle de ce jour existait déjà
     */
    boolean signalerLesAbsencesAQualifier(UUID centre, LocalDate jour, long aQualifier, long enRetard);
}
