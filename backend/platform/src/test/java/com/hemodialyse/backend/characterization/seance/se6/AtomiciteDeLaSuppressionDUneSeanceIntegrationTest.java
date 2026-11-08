package com.hemodialyse.backend.characterization.seance.se6;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SE6 (revue indépendante, atomicité) : la suppression d'une séance est « tout ou rien » sur la PILE RÉELLE (contexte Spring complet,
 * transaction Spring, Stock, volets et administrations JPA, journal JDBC, H2 de test), pas seulement sur un double en mémoire. Un échec
 * INJECTÉ en base (une contrainte qui refuse la ligne du journal) ou un refus du Stock doit laisser la séance, ses volets, ses
 * administrations, ses consommations et son stock exactement comme avant, sans trace au journal ni diffusion. Aucune donnée réelle.
 */
class AtomiciteDeLaSuppressionDUneSeanceIntegrationTest extends Se6CharacterizationSupport {

    private static final String MOTIF = "Seance saisie en double";
    private static final String CONTRAINTE = "se6_echec_injecte_du_journal";

    @Override
    protected String dossier() {
        return "atomicite";
    }

    @Test
    void un_echec_du_journal_defait_les_consommations_les_saisies_et_la_suppression_de_la_seance() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        UUID a2 = articleSe2(CENTRE_A, "ART_2", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        lotSe2(CENTRE_A, a2, "LOT_2", "10");
        UUID seance = seanceComplete(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        ajouter(seance, a1, 2, infirmierA());
        ajouter(seance, a2, 3, infirmierA());
        diffusions.clear();

        Map<String, Object> tracesAvant = traces(seance);
        List<Map<String, Object>> consommationsAvant = consommationsImputees();
        List<Object> stockAvant = List.of(lotsRestants(), stocksDesArticles());
        assertEquals(1, tracesAvant.get("seances"));
        assertEquals(1, tracesAvant.get("volets paramedicaux"));
        assertEquals(1, tracesAvant.get("volets medicaux"));
        assertEquals(1, tracesAvant.get("administrations"));
        assertTrue(!consommationsAvant.isEmpty(), "la séance doit porter des consommations imputées avant la suppression");

        // L'échec est réel : la base refuse la ligne du journal, APRÈS l'annulation des consommations, l'effacement des saisies
        // et la suppression de la séance dans la même unité de travail.
        jdbc.execute("ALTER TABLE seance_suppression ADD CONSTRAINT " + CONTRAINTE + " CHECK (1 = 0)");
        Resp reponse;
        try {
            reponse = supprimer(seance, MOTIF, adminA());
        } finally {
            jdbc.execute("ALTER TABLE seance_suppression DROP CONSTRAINT " + CONTRAINTE);
        }

        assertNotEquals(204, reponse.status(), "la suppression ne doit pas réussir quand le journal échoue : " + reponse.raw());
        assertTrue(reponse.status() >= 400, "un refus ou une erreur est attendu : " + reponse.status());
        assertEquals(tracesAvant, traces(seance), "séance, volets, administrations, consommations : rollback complet");
        assertEquals(consommationsAvant, consommationsImputees(), "les consommations annulées sont rétablies");
        assertEquals(stockAvant, List.of(lotsRestants(), stocksDesArticles()), "lots et stock inchangés");
        assertEquals(0, nombreDeSuppressions(), "aucune ligne au journal des suppressions");
        assertTrue(diffusions.isEmpty(), "aucune diffusion SEANCE_SUPPRIMEE quand la suppression est défaite");
    }

    @Test
    void un_echec_a_l_effacement_des_volets_defait_l_annulation_des_consommations_et_l_effacement_des_administrations() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID a1 = articleSe2(CENTRE_A, "ART_1", true, "10");
        lotSe2(CENTRE_A, a1, "LOT_1", "10");
        UUID seance = seanceComplete(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        ajouter(seance, a1, 4, infirmierA());
        // Une clé étrangère de test empêche d'effacer le volet paramédical : l'échec survient APRÈS l'annulation des consommations
        // (Stock) et l'effacement des administrations (JPA), dans la même unité de travail.
        UUID volet = jdbc.queryForObject("SELECT id FROM volet_paramedical WHERE seance_id = ?", UUID.class, seance);
        jdbc.execute("CREATE TABLE se6_blocage_de_test (volet_id UUID NOT NULL, CONSTRAINT se6_fk_de_test FOREIGN KEY (volet_id) "
                + "REFERENCES volet_paramedical (id))");
        jdbc.update("INSERT INTO se6_blocage_de_test (volet_id) VALUES (?)", volet);
        diffusions.clear();

        Map<String, Object> tracesAvant = traces(seance);
        List<Map<String, Object>> consommationsAvant = consommationsImputees();
        List<Object> stockAvant = List.of(lotsRestants(), stocksDesArticles());
        assertTrue(!consommationsAvant.isEmpty(), "la séance doit porter des consommations imputées avant la suppression");

        Resp reponse;
        try {
            reponse = supprimer(seance, MOTIF, adminA());
        } finally {
            jdbc.execute("DROP TABLE se6_blocage_de_test");
        }

        assertNotEquals(204, reponse.status(), "la suppression ne doit pas réussir : " + reponse.raw());
        assertTrue(reponse.status() >= 400, "un refus ou une erreur est attendu : " + reponse.status());
        assertEquals(tracesAvant, traces(seance));
        assertEquals(1, traces(seance).get("seances"));
        assertEquals(1, traces(seance).get("volets paramedicaux"));
        assertEquals(1, traces(seance).get("volets medicaux"));
        assertEquals(1, traces(seance).get("administrations"));
        assertEquals(consommationsAvant, consommationsImputees());
        assertEquals(stockAvant, List.of(lotsRestants(), stocksDesArticles()));
        assertEquals(0, nombreDeSuppressions());
        assertTrue(diffusions.isEmpty());
    }
}
