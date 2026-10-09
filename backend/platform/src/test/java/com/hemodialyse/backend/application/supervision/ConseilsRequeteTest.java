package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.Conseil.NiveauConseil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConseilsRequeteTest {

    private static RequeteStatistique requete(String sql, long appels, double moyenMs, double maxMs, long lignes) {
        return new RequeteStatistique("1", sql, appels, moyenMs * appels, moyenMs, maxMs, lignes, 0, 0, 0);
    }

    private static List<String> codes(RequeteStatistique r, double part) {
        return ConseilsRequete.evaluer(r, part).stream().map(Conseil::code).toList();
    }

    @Test
    void a_healthy_query_gets_no_advice() {
        assertTrue(codes(requete("SELECT id FROM patients WHERE id = $1", 500, 0.4, 3, 500), 1).isEmpty());
    }

    @Test
    void a_slow_filtered_query_returning_few_rows_probably_scans_the_table() {
        var r = requete("SELECT id FROM seances WHERE center_id = $1 AND statut = $2", 200, 80, 150, 400);

        assertEquals(List.of("SCAN_PROBABLE"), codes(r, 5));
    }

    @Test
    void reading_many_rows_without_limit_calls_for_pagination() {
        var r = requete("SELECT id, nom FROM patients WHERE center_id = $1", 100, 30, 60, 250_000);

        assertEquals("LIGNES_PAR_APPEL_ELEVE", codes(r, 5).get(0));
        assertEquals("2500", ConseilsRequete.evaluer(r, 5).get(0).valeur());
        // avec LIMIT, plus de conseil de pagination
        var limitee = requete("SELECT id FROM patients WHERE center_id = $1 LIMIT $2", 100, 30, 60, 250_000);
        assertTrue(codes(limitee, 5).stream().noneMatch("LIGNES_PAR_APPEL_ELEVE"::equals));
    }

    @Test
    void a_frequent_fast_query_taking_a_large_share_suggests_caching_or_batching() {
        var r = requete("SELECT id FROM articles WHERE id = $1", 50_000, 0.5, 4, 50_000);

        var conseils = ConseilsRequete.evaluer(r, 25);

        assertEquals("APPELS_TRES_NOMBREUX", conseils.get(0).code());
        assertEquals(NiveauConseil.INFO, conseils.get(0).niveau());
        assertTrue(codes(r, 2).isEmpty(), "un petit poids dans le total ne justifie pas le conseil");
    }

    @Test
    void count_star_select_star_and_text_search_are_flagged_when_slow() {
        assertTrue(codes(requete("SELECT count(*) FROM seances WHERE center_id = $1", 50, 90, 100, 50), 5).contains("COMPTAGE_EXHAUSTIF"));
        assertTrue(codes(requete("SELECT * FROM patients WHERE center_id = $1", 50, 30, 60, 500), 5).contains("SELECT_ETOILE"));
        assertTrue(codes(requete("SELECT id FROM patients WHERE nom ILIKE $1", 50, 80, 90, 500), 5).contains("RECHERCHE_TEXTE"));
        // rapides : aucun bruit
        assertTrue(codes(requete("SELECT * FROM patients WHERE id = $1", 50, 1, 2, 50), 5).isEmpty());
    }

    @Test
    void a_function_on_a_filtered_column_and_a_long_in_list_are_recognised() {
        assertTrue(codes(requete("SELECT id FROM patients WHERE lower(nom) = $1", 50, 40, 60, 100), 5).contains("FONCTION_SUR_COLONNE"));
        String in = "SELECT id FROM seances WHERE id IN ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)";
        assertTrue(codes(requete(in, 50, 1, 2, 500), 5).contains("IN_LONG"));
    }

    @Test
    void temporary_files_and_a_weak_memory_cache_call_for_action() {
        var disque = new RequeteStatistique("1", "SELECT id FROM seances ORDER BY date_seance", 10, 100, 10, 20, 100, 0, 0, 800);
        assertEquals("TRI_SUR_DISQUE", codes(disque, 5).get(0));

        var cache = new RequeteStatistique("1", "SELECT id FROM seances", 10, 100, 10, 20, 100, 700, 900, 0);
        var conseils = ConseilsRequete.evaluer(cache, 5);
        assertEquals("CACHE_FAIBLE", conseils.get(0).code());
        assertEquals("44", conseils.get(0).valeur());
    }

    @Test
    void slow_writes_and_erratic_durations_are_reported() {
        assertTrue(codes(requete("UPDATE seances SET statut = $1 WHERE id = $2", 40, 120, 200, 40), 5).contains("ECRITURE_LENTE"));
        assertTrue(codes(requete("SELECT id FROM seances WHERE id = $1", 100, 5, 900, 100), 5).contains("VARIABILITE"));
    }

    @Test
    void actions_come_before_information() {
        var r = requete("SELECT * FROM seances WHERE center_id = $1", 200, 80, 150, 400);

        var niveaux = ConseilsRequete.evaluer(r, 5).stream().map(Conseil::niveau).toList();

        assertEquals(NiveauConseil.ACTION, niveaux.get(0));
        assertEquals(NiveauConseil.INFO, niveaux.get(niveaux.size() - 1));
    }
}
