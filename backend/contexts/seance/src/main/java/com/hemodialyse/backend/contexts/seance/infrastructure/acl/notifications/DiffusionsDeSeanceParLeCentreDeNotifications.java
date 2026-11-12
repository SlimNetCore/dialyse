package com.hemodialyse.backend.contexts.seance.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.seance.application.DiffusionsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * ACL sortante de la Séance vers le contexte Notifications ({@code notifications.v1}, ADR 0154) : les diffusions temps réel aux écrans du
 * centre, composées par le {@link CatalogueDesNotificationsDeLaSeance}. Appelées après la validation de l'unité de travail ; un échec de
 * diffusion est journalisé et ne défait jamais la séance déjà enregistrée.
 */
public class DiffusionsDeSeanceParLeCentreDeNotifications implements DiffusionsDeSeance {

    private static final Logger JOURNAL = LoggerFactory.getLogger(DiffusionsDeSeanceParLeCentreDeNotifications.class);

    private final EmissionDesNotificationsDeLaSeance emission;

    public DiffusionsDeSeanceParLeCentreDeNotifications(NotifierEnDeuxTemps notifier, DiffusionEnTempsReel diffusion) {
        this.emission = new EmissionDesNotificationsDeLaSeance(notifier, diffusion);
    }

    @Override
    public void seanceCreee(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffuser("SEANCE_CREATED", () -> CatalogueDesNotificationsDeLaSeance.seanceCreee(centre.valeur(), seance.id().valeur(),
                seance.patient().valeur(), patient.nom(), patient.prenom(), seance.jour()));
    }

    @Override
    public void seanceValidee(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffuser("SEANCE_VALIDATED", () -> CatalogueDesNotificationsDeLaSeance.seanceValidee(centre.valeur(), seance.id().valeur(),
                seance.patient().valeur(), patient.nom(), patient.prenom(), seance.jour()));
    }

    @Override
    public void saisieDeLInfirmier(CentreId centre, String type, Seance seance, FicheDuPatient patient, String auteur) {
        diffuser("SAISIE_INFIRMIER", () -> CatalogueDesNotificationsDeLaSeance.saisieDeLInfirmier(centre.valeur(), type,
                seance.patient().valeur(), patient.nom(), patient.prenom(), auteur, seance.jour()));
    }

    @Override
    public void voletParamedicalEnregistre(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffuser("SEANCE_PARAMEDICAL_SAVED", () -> CatalogueDesNotificationsDeLaSeance.voletParamedicalEnregistre(centre.valeur(),
                seance.id().valeur(), seance.patient().valeur(), patient.nom(), patient.prenom(), seance.jour()));
    }

    @Override
    public void seanceDeverrouillee(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffuser("SEANCE_DEVERROUILLEE", () -> CatalogueDesNotificationsDeLaSeance.seanceDeverrouillee(centre.valeur(),
                seance.id().valeur(), patient.nom(), patient.prenom(), seance.jour()));
    }

    @Override
    public void consommablesModifies(CentreId centre, Seance seance) {
        diffuser("SEANCE_CONSOMMABLE_CHANGED",
                () -> CatalogueDesNotificationsDeLaSeance.consommablesModifies(centre.valeur(), seance.id().valeur()));
    }

    @Override
    public void seanceSupprimee(CentreId centre, SuppressionDeSeance suppression) {
        diffuser("SEANCE_SUPPRIMEE", () -> CatalogueDesNotificationsDeLaSeance.seanceSupprimee(centre.valeur(),
                suppression.seance().valeur(), suppression.patient().valeur(), suppression.jour()));
    }

    private void diffuser(String evenement, Supplier<DemandeDeNotification> demande) {
        try {
            emission.emettre(demande.get());
        } catch (RuntimeException e) {
            JOURNAL.warn("Diffusion {} en échec (la séance est enregistrée) : {}", evenement, e.getClass().getSimpleName());
        }
    }
}
