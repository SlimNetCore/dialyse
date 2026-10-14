package com.hemodialyse.backend.characterization.gmao.interventions;

import com.hemodialyse.backend.application.notification.NotificationJournalPort;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CauseDIndisponibilite;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2, proposition de réforme (KB-GMAO-57) : FONCTIONNALITÉ NOUVELLE créée à la demande de l'utilisateur le 2026-10-12, sans
 * équivalent legacy. Quand la clôture d'une intervention constate l'état {@code A_REFORMER}, les détenteurs de l'habilitation
 * {@code GMAO_REFORME} du centre de l'équipement reçoivent une notification durable {@code REFORME_PROPOSEE} ; rien n'est
 * réformé (RG-GMA-063). Texte et destinataires À VALIDER PAR LE MÉTIER.
 */
@RecordApplicationEvents
class InterventionReformeProposeeNouveauCodeIntegrationTest extends InterventionCharacterizationSupport {

    @Autowired
    ApplicationEventPublisher evenements;
    @Autowired
    TransactionTemplate transaction;
    @Autowired
    NotificationJournalPort journal;
    @Autowired
    ApplicationEvents publies;

    private List<Map<String, Object>> propositions(UUID centre) {
        return jdbc.queryForList("SELECT id, payload, target_roles FROM notification_evenement WHERE center_id = ? "
                + "AND type = 'REFORME_PROPOSEE' ORDER BY cree_le", centre);
    }

    private void cloturerEn(String etatApres) throws Exception {
        UUID i = demarree(null);
        assertEquals(200, post("/" + i + "/terminer", cloture(etatApres, DEBUT.plusMinutes(30)), adminA()).status);
    }

    @Test
    void cloturer_en_a_reformer_notifie_les_detenteurs_de_l_habilitation_de_reforme_du_centre_sans_reformer() throws Exception {
        cloturerEn("A_REFORMER");

        assertEquals("A_REFORMER", statutEquipement(GEN));
        List<Map<String, Object>> alertes = propositions(CENTRE_A);
        assertEquals(1, alertes.size());
        assertEquals(",GMAO_REFORME,", alertes.getFirst().get("TARGET_ROLES"));
        JsonNode payload = mapper.readTree(String.valueOf(alertes.getFirst().get("PAYLOAD")));
        assertEquals(GEN.toString(), payload.path("equipementId").asString());
        assertEquals("G-01", payload.path("code").asString());
        assertEquals("A_REFORMER", payload.path("statut").asString());
        assertEquals("Équipement G-01 : état constaté « à réformer » à la clôture d'une intervention. "
                + "Aucune réforme n'est décidée automatiquement : consultez l'aide à la décision de maintenance.",
                payload.path("message").asString());
        // jamais de réforme automatique : l'équipement n'est pas réformé, aucun changement hors l'état constaté
        assertEquals(List.of("EN_SERVICE>A_REFORMER|Intervention terminée"), historique(GEN));
    }

    @Test
    void seuls_les_detenteurs_de_l_habilitation_de_reforme_du_centre_voient_la_proposition() throws Exception {
        cloturerEn("A_REFORMER");

        assertEquals(1, journal.lister(CENTRE_A, ADMIN_A.toString(), Set.of("GMAO_REFORME"), 0, 20).items().size());
        assertEquals(1, journal.lister(CENTRE_A, ADMIN_A.toString(), Set.of("ADMIN", "GMAO_REFORME"), 0, 20).items().stream()
                .filter(a -> a.type().equals("REFORME_PROPOSEE")).count());
        assertTrue(journal.lister(CENTRE_A, ADMIN_A.toString(), Set.of("ADMIN", "SECRETAIRE"), 0, 20).items().stream()
                .noneMatch(a -> a.type().equals("REFORME_PROPOSEE")));
        assertEquals(0, journal.lister(CENTRE_B, ADMIN_B.toString(), Set.of("GMAO_REFORME"), 0, 20).items().size());
    }

    @Test
    void un_equipement_d_un_autre_centre_ne_notifie_que_son_propre_centre() throws Exception {
        Resp creee = post("", creation(AUTRE_CENTRE, "EN_SERVICE", null), adminB());
        assertEquals(201, creee.status, creee.raw);
        UUID i = UUID.fromString(json(creee).get("id").asString());
        assertEquals(200, post("/" + i + "/demarrer", null, adminB()).status);
        assertEquals(200, post("/" + i + "/terminer", cloture("A_REFORMER", DEBUT.plusMinutes(30)), adminB()).status);

        assertEquals(1, propositions(CENTRE_B).size());
        assertEquals(0, propositions(CENTRE_A).size());
    }

    @Test
    void les_autres_etats_constates_ne_proposent_pas_de_reforme() throws Exception {
        for (String etat : new String[]{"EN_SERVICE", "EN_ATTENTE_PIECE", "HORS_SERVICE"}) {
            cleanup();
            setUp();
            cloturerEn(etat);
            assertEquals(0, propositions(CENTRE_A).size(), "état constaté " + etat);
        }
    }

    @Test
    void un_evenement_rejoue_ne_cree_pas_de_doublon() throws Exception {
        cloturerEn("A_REFORMER");
        assertEquals(1, propositions(CENTRE_A).size());

        // livraison « au moins une fois » : le même événement, rejoué à l'identique
        EquipementMisHorsService original = publies.stream(EquipementMisHorsService.class)
                .filter(e -> e.statut() == StatutEquipement.A_REFORMER).findFirst().orElseThrow();
        List<Map<String, Object>> avant = propositions(CENTRE_A);
        rejouer(original);
        rejouer(original);

        assertEquals(avant.size(), propositions(CENTRE_A).size());
        assertEquals(avant.getFirst().get("ID"), propositions(CENTRE_A).getFirst().get("ID"));
    }

    @Test
    void un_evenement_distinct_pour_le_meme_equipement_cree_une_nouvelle_proposition() throws Exception {
        cloturerEn("A_REFORMER");
        rejouer(new EquipementMisHorsService(new EquipementId(GEN), new CentreId(CENTRE_A), TypeEquipement.GENERATEUR_DIALYSE,
                new CodeEquipement("G-01"), StatutEquipement.EN_SERVICE, StatutEquipement.A_REFORMER,
                CauseDIndisponibilite.INTERVENTION, new UtilisateurId(ADMIN_A), Instant.parse("2026-12-01T10:00:00Z")));

        assertEquals(2, propositions(CENTRE_A).size());
    }

    /** Republie un événement d'équipement devenu « à réformer » par intervention, comme le ferait une livraison au moins une fois. */
    private void rejouer(EquipementMisHorsService evenement) {
        transaction.executeWithoutResult(statut -> evenements.publishEvent(evenement));
        attendreLaLivraisonDesEvenements();
    }
}
