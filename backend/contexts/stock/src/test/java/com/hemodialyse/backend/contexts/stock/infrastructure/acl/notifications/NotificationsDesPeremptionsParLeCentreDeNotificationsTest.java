package com.hemodialyse.backend.contexts.stock.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DiffusionEnTempsReel;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.JournalDesNotifications;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions.LotSignale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Message du Stock (péremptions) : contenu, audience, une seule fois par centre et jour, après commit, concurrence (PL13d, ADR 0156). */
class NotificationsDesPeremptionsParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("99997500-0000-0000-0000-00000000000a");
    private static final LocalDate JOUR = LocalDate.parse("2026-10-24");

    private final List<DemandeDeNotification> demandes = new ArrayList<>();
    private final List<String> diffusions = new ArrayList<>();
    private final Set<UUID> journalises = new HashSet<>();
    private RuntimeException pannePremierTemps;

    private final NotifierEnDeuxTemps notifier = demande -> {
        if (pannePremierTemps != null) throw pannePremierTemps;
        demandes.add(demande);
        UUID id = demande.identifiantDeterministe();
        if (id != null && !journalises.add(id)) return Optional.empty();
        return Optional.of(new NotificationAEnvoyer(demande.centre(), demande.type(), demande.contenu(), id, Instant.EPOCH, null));
    };
    private final DiffusionEnTempsReel diffusion = (centre, type, contenu, identifiant, emisLe) -> diffusions.add(type);
    private final JournalDesNotifications journal = new JournalDesNotifications() {
        @Override
        public void enregistrer(UUID id, UUID centre, String type, Map<String, String> contenu, Instant creeLe) {
            journalises.add(id);
        }

        @Override
        public boolean existe(UUID id) {
            return journalises.contains(id);
        }
    };
    private final NotificationsDesPeremptionsParLeCentreDeNotifications acl =
            new NotificationsDesPeremptionsParLeCentreDeNotifications(notifier, diffusion, journal);

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clear();
    }

    private static LotSignale lot(int n, boolean perime) {
        return new LotSignale("L-" + n, "ART", "Article", JOUR.plusDays(n), new BigDecimal("2.50"), perime);
    }

    @Test
    void l_identifiant_depend_du_centre_et_du_jour_et_est_stable() {
        UUID id = CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(CENTRE, JOUR);
        assertEquals(id, CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(CENTRE, JOUR));
        assertNotEquals(id, CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(CENTRE, JOUR.plusDays(1)));
        assertNotEquals(id, CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(UUID.randomUUID(), JOUR));
    }

    @Test
    void le_message_porte_les_comptes_la_date_la_plus_proche_et_vise_l_administrateur_et_le_pharmacien() {
        assertTrue(acl.signaler(CENTRE, JOUR, List.of(lot(-1, true), lot(3, false))));

        DemandeDeNotification demande = demandes.getFirst();
        assertEquals("LOTS_PROCHES_PEREMPTION", demande.type());
        assertEquals(Map.of("nbLots", "2", "nbPerimes", "1", "plusProche", JOUR.minusDays(1).toString(),
                "targetRoles", "ADMIN,PHARMACIEN"), demande.contenu());
        assertEquals(Audience.desRoles("ADMIN", "PHARMACIEN"), demande.audience());
        assertTrue(demande.journalisee());
        assertEquals(CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(CENTRE, JOUR), demande.identifiantDeterministe());
        assertEquals(List.of("LOTS_PROCHES_PEREMPTION"), diffusions);
    }

    @Test
    void une_alerte_deja_enregistree_ce_jour_n_est_pas_renvoyee() {
        assertTrue(acl.signaler(CENTRE, JOUR, List.of(lot(3, false))));
        assertFalse(acl.signaler(CENTRE, JOUR, List.of(lot(3, false))));
        assertEquals(1, diffusions.size());
        assertTrue(acl.signaler(CENTRE, JOUR.plusDays(1), List.of(lot(3, false))));
    }

    @Test
    void le_temps_reel_attend_le_commit_et_rien_ne_part_si_la_transaction_est_annulee() {
        TransactionSynchronizationManager.initSynchronization();
        assertTrue(acl.signaler(CENTRE, JOUR, List.of(lot(3, false))));
        assertEquals(1, demandes.size(), "journalisee dans la transaction");
        assertTrue(diffusions.isEmpty());
        for (TransactionSynchronization s : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            s.afterCommit();
        }
        assertEquals(List.of("LOTS_PROCHES_PEREMPTION"), diffusions);

        TransactionSynchronizationManager.clear();
        diffusions.clear();
        TransactionSynchronizationManager.initSynchronization();
        acl.signaler(CENTRE, JOUR.plusDays(1), List.of(lot(3, false)));
        TransactionSynchronizationManager.clear();
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_insertion_concurrente_de_l_identifiant_par_une_autre_instance_est_traitee_comme_deja_envoyee() {
        // l'autre instance a inséré l'identifiant entre le contrôle et l'insertion
        UUID id = CatalogueDesNotificationsDuStock.identifiantDesLotsProchesPeremption(CENTRE, JOUR);
        var concurrent = new NotificationsDesPeremptionsParLeCentreDeNotifications(
                demande -> {
                    journalises.add(id);
                    throw new DuplicateKeyException("notification_evenement_pkey");
                }, diffusion, journal);
        assertFalse(concurrent.signaler(CENTRE, JOUR, List.of(lot(3, false))), "pas d'erreur : l'autre instance a émis l'alerte");
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_violation_d_integrite_sans_alerte_enregistree_reste_une_erreur() {
        DuplicateKeyException cause = new DuplicateKeyException("autre contrainte");
        pannePremierTemps = cause;
        assertSame(cause, assertThrows(DuplicateKeyException.class, () -> acl.signaler(CENTRE, JOUR, List.of(lot(3, false)))));
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void une_panne_du_journal_avec_identifiant_impose_remonte_et_rien_n_est_diffuse() {
        pannePremierTemps = new IllegalStateException("base inaccessible");
        assertThrows(IllegalStateException.class, () -> acl.signaler(CENTRE, JOUR, List.of(lot(3, false))));
        assertTrue(diffusions.isEmpty());
    }
}
