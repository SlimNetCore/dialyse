package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.notifications;

import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.Audience;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.DemandeDeNotification;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotificationAEnvoyer;
import com.hemodialyse.backend.contexts.notifications.publishedlanguage.NotifierEnDeuxTemps;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proposition de réforme : contenu, habilitation visée, identifiant déterministe, rejeu, panne du journal (PL13d, ADR 0156). */
class ReformeProposeeParLeCentreDeNotificationsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EQUIPEMENT = UUID.fromString("00000000-0000-0000-0000-000000000e01");
    private static final Instant T1 = Instant.parse("2026-10-12T08:00:00Z");

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
    private final ReformeProposeeParLeCentreDeNotifications reforme =
            new ReformeProposeeParLeCentreDeNotifications(notifier, (c, t, contenu, id, e) -> diffusions.add(t));

    @AfterEach
    void nettoyer() {
        TransactionSynchronizationManager.clear();
    }

    private static EquipementMisHorsService evenement(StatutEquipement statut, CauseDIndisponibilite cause) {
        return new EquipementMisHorsService(new EquipementId(EQUIPEMENT), new CentreId(CENTRE), TypeEquipement.GENERATEUR_DIALYSE,
                new CodeEquipement("G-01"), "Générateur 01", StatutEquipement.EN_SERVICE, statut, cause,
                new UtilisateurId(UUID.randomUUID()), T1);
    }

    @Test
    void une_intervention_qui_constate_l_etat_a_reformer_previent_les_detenteurs_de_l_habilitation() {
        reforme.surEquipementMisHorsService(evenement(StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION));

        DemandeDeNotification demande = demandes.getFirst();
        assertEquals("REFORME_PROPOSEE", demande.type());
        assertEquals(Map.of("equipementId", EQUIPEMENT.toString(), "code", "G-01", "statut", "A_REFORMER",
                "message", "Équipement G-01 : état constaté « à réformer » à la clôture d'une intervention. "
                        + "Aucune réforme n'est décidée automatiquement : consultez l'aide à la décision de maintenance.",
                "targetRoles", "GMAO_REFORME"), demande.contenu());
        assertEquals(Audience.desRoles("GMAO_REFORME"), demande.audience());
        assertTrue(demande.journalisee());
        assertEquals(CatalogueDesNotificationsDeLaGmao.identifiantDeLaReformeProposee(EQUIPEMENT, T1), demande.identifiantDeterministe());
        assertEquals(List.of("REFORME_PROPOSEE"), diffusions);
    }

    @Test
    void les_autres_statuts_et_les_autres_causes_ne_proposent_aucune_reforme() {
        reforme.surEquipementMisHorsService(evenement(StatutEquipement.HORS_SERVICE, CauseDIndisponibilite.INTERVENTION));
        reforme.surEquipementMisHorsService(evenement(StatutEquipement.A_REFORMER, CauseDIndisponibilite.MISE_HORS_SERVICE));
        assertTrue(demandes.isEmpty());
    }

    @Test
    void un_evenement_rejoue_ne_cree_pas_de_doublon() {
        reforme.surEquipementMisHorsService(evenement(StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION));
        reforme.surEquipementMisHorsService(evenement(StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION));
        assertEquals(1, diffusions.size());
    }

    @Test
    void le_temps_reel_attend_le_commit() {
        TransactionSynchronizationManager.initSynchronization();
        reforme.surEquipementMisHorsService(evenement(StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION));
        assertEquals(1, demandes.size());
        assertTrue(diffusions.isEmpty());
        for (TransactionSynchronization s : new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())) {
            s.afterCommit();
        }
        assertEquals(List.of("REFORME_PROPOSEE"), diffusions);
    }

    @Test
    void une_panne_du_journal_remonte_pour_que_l_evenement_soit_rejoue_et_rien_n_est_diffuse() {
        pannePremierTemps = new IllegalStateException("base inaccessible");
        assertThrows(IllegalStateException.class,
                () -> reforme.surEquipementMisHorsService(evenement(StatutEquipement.A_REFORMER, CauseDIndisponibilite.INTERVENTION)));
        assertTrue(diffusions.isEmpty());
    }
}
