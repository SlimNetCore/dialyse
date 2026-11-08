package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DiffusionsFournies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link DiffusionsFournies} (ACL vers le centre de notifications legacy) : mêmes appels que les
 * handlers legacy. Un échec de diffusion est journalisé et ne défait jamais la séance déjà enregistrée.
 */
@Component
public class DiffusionsFournieesLegacy implements DiffusionsFournies {

    private static final Logger JOURNAL = LoggerFactory.getLogger(DiffusionsFournieesLegacy.class);

    private final NotificationService notifications;

    public DiffusionsFournieesLegacy(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Override
    public void seanceCreee(UUID centre, UUID seance, UUID patient, String nom, String prenom, String jour) {
        diffuser("SEANCE_CREATED", () -> notifications.notifySeanceCreated(centre, seance, patient, nom, prenom, jour));
    }

    @Override
    public void seanceValidee(UUID centre, UUID seance, UUID patient, String nom, String prenom, String jour) {
        diffuser("SEANCE_VALIDATED", () -> notifications.notifySeanceValidated(centre, seance, patient, nom, prenom, jour));
    }

    @Override
    public void saisieDeLInfirmier(UUID centre, String type, UUID patient, String nom, String prenom, String auteur, String jour) {
        diffuser("SAISIE_INFIRMIER", () -> notifications.notifySaisieInfirmier(centre, type, patient, nom, prenom, auteur, jour));
    }

    @Override
    public void seanceDeverrouillee(UUID centre, UUID seance, String nom, String prenom, String jour) {
        diffuser("SEANCE_DEVERROUILLEE", () -> notifications.notifySeanceDeverrouillee(centre, seance, nom, prenom, jour));
    }

    @Override
    public void consommablesModifies(UUID centre, UUID seance) {
        diffuser("SEANCE_CONSOMMABLE_CHANGED", () -> notifications.notifySeanceUpdated(centre, seance));
    }

    private static void diffuser(String evenement, Runnable envoi) {
        try {
            envoi.run();
        } catch (RuntimeException e) {
            JOURNAL.warn("Diffusion {} en échec (la séance est enregistrée) : {}", evenement, e.getClass().getSimpleName());
        }
    }
}
