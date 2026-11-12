package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SaisieDeLAdministrationSignalee;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SignalementDesPrescriptions;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL sortante du Dossier médical vers le contexte Notifications ({@code notifications.v1}, ADR 0153) : fournit les ports publiés
 * {@link NotificationsDeLAnemie} (alerte durable d'observance) et {@link SignalementDesPrescriptions} (diffusion {@code PRESCRIPTION_CHANGED}).
 * Les messages viennent du {@link CatalogueDesNotificationsDuDossierMedical}. Dans une transaction, le signal attend la validation (jamais
 * d'alerte fantôme si l'écriture est annulée) ; sans transaction, il part aussitôt. La saisie {@code ANEMIE} de l'infirmier n'est pas du
 * catalogue du Dossier médical : elle est déléguée au port {@link SaisieDeLAdministrationSignalee} jusqu'à PL13b.
 */
public class NotificationsDuDossierMedicalParLeCentreDeNotifications implements NotificationsDeLAnemie, SignalementDesPrescriptions {

    private static final Logger log = LoggerFactory.getLogger(NotificationsDuDossierMedicalParLeCentreDeNotifications.class);

    private final NotifierEnDeuxTemps notifier;
    private final DiffusionEnTempsReel diffusion;
    private final SaisieDeLAdministrationSignalee saisies;

    public NotificationsDuDossierMedicalParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion,
                                                                   SaisieDeLAdministrationSignalee saisies) {
        this.notifier = notifier;
        this.diffusion = diffusion;
        this.saisies = saisies;
    }

    @Override
    public void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration) {
        saisies.administrationSaisie(centre, patient, jourDeLAdministration);
    }

    @Override
    public void observanceNonRespectee(UUID centre, UUID patient, String message, Map<String, String> details) {
        apresLeCommit(() -> emettre(CatalogueDesNotificationsDuDossierMedical.observanceNonRespectee(centre, patient, message, details)));
    }

    @Override
    public void prescriptionChangee(UUID centre, UUID patient) {
        apresLeCommit(() -> emettre(CatalogueDesNotificationsDuDossierMedical.prescriptionChangee(centre, patient)));
    }

    private void emettre(DemandeDeNotification demande) {
        Optional<NotificationAEnvoyer> journalisee = notifier.journaliser(demande);
        if (journalisee.isEmpty()) {
            return;
        }
        NotificationAEnvoyer aEnvoyer = journalisee.get();
        if (aEnvoyer.panneDuJournal() != null) {
            // le temps réel reste prioritaire : une base indisponible n'empêche pas d'alerter les connectés
            log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", demande.type(), demande.centre(), aEnvoyer.panneDuJournal());
        }
        diffusion.diffuser(aEnvoyer.centre(), aEnvoyer.type(), aEnvoyer.contenu(), aEnvoyer.identifiant(), aEnvoyer.emisLe());
    }

    private static void apresLeCommit(Runnable signal) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal.run();
                }
            });
        } else {
            signal.run();
        }
    }
}
