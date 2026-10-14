package com.hemodialyse.backend.characterization.gmao.documents;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet G5 : autorisations (ADMIN seulement, aucune habilitation propre), isolation de centre, traçabilité
 * transversale, absence d'effet de bord et de lecteur externe.
 */
class DocumentsAutorisationsCharacterizationIntegrationTest extends DocumentsCharacterizationSupport {

    private List<String> quatreRoutes(RequestPostProcessor who) throws Exception {
        semer(doc(1), I_PLAN, CENTRE_A, "AUTRE", "existant.pdf", "application/pdf", PDF, 1);
        List<String> l = new ArrayList<>();
        l.add("POST : " + resume(upload(I_PLAN, "n.pdf", PDF, null, who)));
        l.add("GET liste : " + resume(liste(I_PLAN, null, who)));
        l.add("GET piece : " + resume(telecharger(I_PLAN, doc(1), who)).substring(0, 3));
        l.add("DELETE : " + resume(retirer(I_PLAN, doc(1), who)));
        l.add("pieces en base apres : " + nombre(I_PLAN));
        return l;
    }

    @Test
    void un_utilisateur_sans_le_role_ADMIN_est_refuse_sur_les_quatre_routes() throws Exception {
        goldenLignes("autorisations_non_admin", quatreRoutes(nonAdmin()));
    }

    @Test
    void les_habilitations_gmao_sans_ADMIN_ne_donnent_aucun_acces_aux_pieces_jointes() throws Exception {
        goldenLignes("autorisations_habilitations_sans_admin", quatreRoutes(habiliteSansAdmin()));
    }

    @Test
    void un_appelant_anonyme_est_refuse_sur_les_quatre_routes() throws Exception {
        goldenLignes("autorisations_anonyme", quatreRoutes(null));
    }

    @Test
    void un_admin_du_centre_a_acces_aux_quatre_routes() throws Exception {
        goldenLignes("autorisations_admin", quatreRoutes(adminA()));
    }

    @Test
    void le_refus_precede_la_validation_un_non_admin_n_apprend_ni_l_existence_ni_les_erreurs_de_contenu() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("intervention inconnue : " + resume(upload(I_INCONNU, "n.pdf", PDF, null, nonAdmin())));
        l.add("contenu invalide : " + resume(upload(I_PLAN, "n.txt", octets("texte"), null, nonAdmin())));
        l.add("type invalide : " + resume(upload(I_PLAN, "n.pdf", PDF, "FOO", nonAdmin())));
        l.add("intervention d'un autre centre : " + resume(liste(I_B, null, nonAdmin())));
        goldenLignes("autorisations_refus_avant_validation", l);
    }

    @Test
    void isolation_de_centre_un_admin_d_un_autre_centre_ne_voit_ni_ne_modifie_rien() throws Exception {
        semer(doc(1), I_PLAN, CENTRE_A, "AUTRE", "secret.pdf", "application/pdf", PDF, 1);
        semer(doc(2), I_B, CENTRE_B, "AUTRE", "b.pdf", "application/pdf", PDF, 2);
        List<String> l = new ArrayList<>();
        l.add("B -> POST sur A : " + resume(upload(I_PLAN, "intrus.pdf", PDF, null, adminB())));
        l.add("B -> liste de A : " + resume(liste(I_PLAN, null, adminB())));
        l.add("B -> telechargement de A : " + resume(telecharger(I_PLAN, doc(1), adminB())));
        l.add("B -> DELETE de A : " + resume(retirer(I_PLAN, doc(1), adminB())));
        l.add("A -> POST sur B : " + resume(upload(I_B, "intrus.pdf", PDF, null, adminA())));
        l.add("A -> liste de B : " + resume(liste(I_B, null, adminA())));
        l.add("A -> telechargement de B : " + resume(telecharger(I_B, doc(2), adminA())));
        l.add("A -> DELETE de B : " + resume(retirer(I_B, doc(2), adminA())));
        l.add("pieces A=" + nombre(I_PLAN) + " B=" + nombre(I_B));
        goldenLignes("isolation_de_centre", l);
    }

    @Test
    void la_tracabilite_transversale_reconnait_les_ecritures_mais_ni_la_liste_ni_le_telechargement() {
        String gabarit = "/api/v1/gmao/interventions/{interventionId}/documents";
        List<String> l = new ArrayList<>();
        for (String[] c : new String[][]{{"POST", gabarit}, {"GET", gabarit}, {"GET", gabarit + "/{documentId}"},
                {"DELETE", gabarit + "/{documentId}"}}) {
            boolean suivi = AuditActionResolver.isTracked(c[0], c[1]);
            l.add(c[0] + " " + c[1] + " : suivi=" + suivi
                    + (suivi ? " " + AuditActionResolver.resolve(c[0], c[1]) : ""));
        }
        goldenLignes("tracabilite_journal_d_audit", l);
    }

    @Test
    void aucun_effet_de_bord_hors_de_la_table_des_pieces_jointes_et_des_evenements_consommes() throws Exception {
        int notifications = jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id IN (?, ?)",
                Integer.class, CENTRE_A, CENTRE_B);
        int evenements = jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class);
        String avant = jdbc.queryForList("SELECT statut, description, date_modification FROM gmao_interventions "
                + "WHERE id = ?", I_PLAN).toString();
        UUID d = idCree(upload(I_PLAN, "a.pdf", PDF));
        retirer(I_PLAN, d, adminA());
        upload(I_PLAN, "b.pdf", PDF);
        assertEquals(notifications, jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id IN (?, ?)",
                Integer.class, CENTRE_A, CENTRE_B));
        // nouveau code (G5) : l'ajout et le retrait publient PieceJointeAjoutee / PieceJointeRetiree par l'outbox ; elles sont
        // consommées sans délai (journal) et aucune publication ne reste en attente
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication", Integer.class) <= evenements));
        assertEquals(avant, jdbc.queryForList("SELECT statut, description, date_modification FROM gmao_interventions "
                + "WHERE id = ?", I_PLAN).toString());
    }

    @Test
    void le_bon_d_intervention_et_la_chronologie_ne_lisent_pas_les_pieces_jointes() throws Exception {
        List<String> l = new ArrayList<>();
        semer(doc(1), I_TERM, CENTRE_A, "BON_INTERVENTION", "bon-signe.pdf", "application/pdf", PDF, 1);
        for (String suffixe : new String[]{"/chronologie", "/indicateurs"}) {
            Resp r = envoyer(HttpMethod.GET, "/api/v1/gmao/interventions/" + I_TERM + suffixe, adminA());
            l.add(suffixe + " : " + r.status + " mentionne-la-piece=" + r.raw.contains("bon-signe"));
        }
        Resp bon = envoyer(HttpMethod.GET, "/api/v1/gmao/interventions/" + I_TERM + "/bon", adminA());
        l.add("/bon : " + bon.status + " octets>0=" + (bon.bytes.length > 0) + " mentionne-la-piece="
                + new String(bon.bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("bon-signe"));
        goldenLignes("consommateurs_ne_lisent_pas_les_pieces", l);
    }
}
