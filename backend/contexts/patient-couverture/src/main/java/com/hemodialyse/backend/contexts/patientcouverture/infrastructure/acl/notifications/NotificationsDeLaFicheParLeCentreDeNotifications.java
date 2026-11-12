package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDeLaPec;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.NotificationsDesAttestations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * ACL sortante de Patient et couverture vers le contexte Notifications ({@code notifications.v1}, ADR 0153) : fournit les ports publiés
 * {@link NotificationsDeLaFiche}, {@link NotificationsDeLaPec} et {@link NotificationsDesAttestations}. Les messages viennent du
 * {@link CatalogueDesNotificationsDeLaFiche} ; ici, le journal puis la diffusion immédiate (ces messages sont de simples diffusions, jamais
 * au journal). Une panne du journal n'empêche pas la diffusion.
 */
public class NotificationsDeLaFicheParLeCentreDeNotifications implements NotificationsDeLaFiche, NotificationsDeLaPec,
        NotificationsDesAttestations {

    private static final Logger log = LoggerFactory.getLogger(NotificationsDeLaFicheParLeCentreDeNotifications.class);

    private final NotifierEnDeuxTemps notifier;
    private final DiffusionEnTempsReel diffusion;

    public NotificationsDeLaFicheParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        this.notifier = notifier;
        this.diffusion = diffusion;
    }

    @Override
    public void ficheCreee(UUID centre, UUID patient, String code, String nom, String prenom) {
        emettre(CatalogueDesNotificationsDeLaFiche.ficheCreee(centre, patient, code, nom, prenom));
    }

    @Override
    public void ficheModifiee(UUID centre, UUID patient, String code, String nom, String prenom) {
        emettre(CatalogueDesNotificationsDeLaFiche.ficheModifiee(centre, patient, code, nom, prenom));
    }

    @Override
    public void pecValidee(UUID centre, UUID pec, UUID patient, String nomDuPatient) {
        emettre(CatalogueDesNotificationsDeLaFiche.pecValidee(centre, pec, patient, nomDuPatient));
    }

    @Override
    public void pecCloturee(UUID centre, UUID pec, UUID patient, String nomDuPatient) {
        emettre(CatalogueDesNotificationsDeLaFiche.pecCloturee(centre, pec, patient, nomDuPatient));
    }

    @Override
    public void pecSupprimee(UUID centre, UUID pec, UUID patient) {
        emettre(CatalogueDesNotificationsDeLaFiche.pecSupprimee(centre, pec, patient));
    }

    @Override
    public void attestationCreee(UUID centre, UUID attestation, UUID patient) {
        emettre(CatalogueDesNotificationsDeLaFiche.attestationCreee(centre, attestation, patient));
    }

    @Override
    public void attestationSupprimee(UUID centre, UUID attestation, UUID patient) {
        emettre(CatalogueDesNotificationsDeLaFiche.attestationSupprimee(centre, attestation, patient));
    }

    private void emettre(DemandeDeNotification demande) {
        Optional<NotificationAEnvoyer> journalisee = notifier.journaliser(demande);
        if (journalisee.isEmpty()) {
            return;
        }
        NotificationAEnvoyer aEnvoyer = journalisee.get();
        if (aEnvoyer.panneDuJournal() != null) {
            log.warn("[NOTIFICATION] Alerte {} non enregistrée pour le centre {}", demande.type(), demande.centre(), aEnvoyer.panneDuJournal());
        }
        diffusion.diffuser(aEnvoyer.centre(), aEnvoyer.type(), aEnvoyer.contenu(), aEnvoyer.identifiant(), aEnvoyer.emisLe());
    }
}
