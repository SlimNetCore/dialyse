package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.AlertesDeLOptimisation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link AlertesDeLOptimisation} (ACL vers le centre de notifications legacy).
 * Les alertes des infirmiers concernés par une proposition appliquée ont un identifiant déterministe (UUID v3 de leur clé) : déjà
 * enregistrée, l'alerte n'est ni renvoyée ni dupliquée, même rejouée ou avec deux instances du serveur.
 */
@Component
public class AlertesDeLOptimisationLegacy implements AlertesDeLOptimisation {

    static final String ROULEMENT_MODIFIE = "INFIRMIER_ROULEMENT_MODIFIE";
    static final String REMPLACEMENT_AFFECTE = "INFIRMIER_REMPLACEMENT_AFFECTE";

    private final JournalDesNotifications journal;
    private final NotificationService notifications;

    public AlertesDeLOptimisationLegacy(JournalDesNotifications journal, NotificationService notifications) {
        this.journal = journal;
        this.notifications = notifications;
    }

    static UUID identifiant(String cle) {
        return UUID.nameUUIDFromBytes(cle.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void seancesDeplacees(UUID centre, String perimetre, int patients, int seancesTemporaires) {
        notifications.notifySeancesDeplacees(centre, perimetre, patients, seancesTemporaires);
    }

    @Override
    public void propositionASignaler(UUID centre, UUID execution, String perimetre, String motif, int valeur) {
        notifications.notifyOptimisationProposition(centre, execution, perimetre, motif, valeur);
    }

    @Override
    public void bilanDeLaNuit(UUID centre, String issue, int propositions, int echecs) {
        notifications.notifyReplanificationNocturne(centre, issue, propositions, echecs);
    }

    @Override
    public boolean roulementModifie(UUID centre, UUID infirmier, UUID compte, UUID execution) {
        UUID id = identifiant(ROULEMENT_MODIFIE + "|" + execution + "|" + infirmier);
        return emettre(id, () -> notifications.notifyRoulementModifie(centre, infirmier, compte, execution, id));
    }

    @Override
    public boolean remplacementAffecte(UUID centre, UUID infirmier, UUID compte, LocalDate date, UUID salle, UUID creneau,
                                       UUID execution) {
        UUID id = identifiant(REMPLACEMENT_AFFECTE + "|" + execution + "|" + infirmier + "|" + date + "|" + salle + "|" + creneau);
        return emettre(id, () -> notifications.notifyRemplacementAffecte(centre, infirmier, compte, date, salle, creneau, execution, id));
    }

    private boolean emettre(UUID id, Runnable envoi) {
        if (journal.existe(id)) return false;
        try {
            envoi.run();
        } catch (DataIntegrityViolationException concurrente) {
            // une autre instance a inséré l'identifiant déterministe d'abord : l'alerte est déjà émise
            if (journal.existe(id)) return false;
            throw concurrente;
        }
        return true;
    }
}
