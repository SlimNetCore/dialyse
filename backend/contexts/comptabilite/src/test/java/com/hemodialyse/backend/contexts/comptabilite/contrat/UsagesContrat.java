package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la lecture des usages du paramétrage (mapping, payeurs, modèles de pièces, articles, écritures) : exécuté sur
 * H2 et sur PostgreSQL 16, avec les mêmes tables (celles des migrations Flyway) et les mêmes insertions.
 */
public abstract class UsagesContrat {

    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996500-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996500-0000-0000-0000-00000000000b"));
    private static final Set<String> COMPTES_PAR_DEFAUT = Set.of("706", "411100", "411500", "512", "530", "44571", "322", "602",
            "408", "757", "657");

    private static Set<String> valeurs(Set<NumeroDeCompte> comptes) {
        return comptes.stream().map(NumeroDeCompte::valeur).collect(Collectors.toSet());
    }

    protected abstract UsagesDuParametrage usages();

    protected abstract JdbcTemplate jdbc();

    /**
     * Une ligne de mapping : les comptes par défaut, remplacés par ceux demandés ; une colonne à {@code null} est vide.
     */
    protected void mapping(CentreId centre, Map<String, String> colonnes) {
        Map<String, String> toutes = new LinkedHashMap<>();
        toutes.put("compte_ventes", "706");
        toutes.put("compte_client_patient", "411100");
        toutes.put("compte_client_autre", "411500");
        toutes.put("compte_banque", "512");
        toutes.put("compte_caisse", "530");
        toutes.put("compte_tva_collectee", "44571");
        toutes.put("compte_stock", "322");
        toutes.put("compte_consommation", "602");
        toutes.put("compte_factures_non_parvenues", "408");
        toutes.put("compte_boni_inventaire", "757");
        toutes.put("compte_mali_inventaire", "657");
        toutes.put("journal_vente", null);
        toutes.put("journal_reglement_banque", null);
        toutes.put("journal_reglement_caisse", null);
        toutes.put("journal_stock_reception", null);
        toutes.put("journal_stock_sortie", null);
        toutes.put("journal_stock_inventaire", null);
        toutes.putAll(colonnes);
        String noms = String.join(", ", toutes.keySet());
        String jokers = String.join(", ", toutes.keySet().stream().map(k -> "?").toList());
        Object[] parametres = new Object[toutes.size() + 2];
        parametres[0] = UUID.randomUUID();
        parametres[1] = centre.valeur();
        int i = 2;
        for (String valeur : toutes.values()) parametres[i++] = valeur;
        jdbc().update("INSERT INTO mapping_comptable (id, center_id, " + noms + ") VALUES (?, ?, " + jokers + ")", parametres);
    }

    protected void payeurAvecCompte(CentreId centre, String compte) {
        jdbc().update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, ?)", centre.valeur(),
                UUID.randomUUID(), compte);
    }

    protected void modele(CentreId centre, String code, String journal, String... comptes) {
        UUID modele = UUID.randomUUID();
        jdbc().update("INSERT INTO modeles_piece (id, center_id, code, libelle, journal_code, actif) VALUES (?, ?, ?, ?, ?, TRUE)",
                modele, centre.valeur(), code, "Modèle " + code, journal);
        int position = 0;
        for (String compte : comptes) {
            jdbc().update("INSERT INTO modeles_piece_lignes (modele_id, center_id, position_ligne, sens, compte, libelle) "
                    + "VALUES (?, ?, ?, 'DEBIT', ?, 'ligne')", modele, centre.valeur(), position++, compte);
        }
    }

    protected void article(CentreId centre, String compteStock, String compteCharge) {
        jdbc().update("INSERT INTO articles (id, active, center_id, code, gere_par_lot, libelle, seuil_alerte, stock_quantity, "
                        + "unite, compte_stock, compte_charge) VALUES (?, TRUE, ?, ?, FALSE, 'Article', 0, 0, 'U', ?, ?)",
                UUID.randomUUID(), centre.valeur(), "ART-" + UUID.randomUUID(), compteStock, compteCharge);
    }

    protected void ecriture(CentreId centre, String journal, String... comptes) {
        UUID ecriture = UUID.randomUUID();
        jdbc().update("INSERT INTO ecritures_comptables (id, center_id, created_at, date_ecriture, date_piece, journal_code, "
                        + "numero_piece, statut) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_DATE, CURRENT_DATE, ?, ?, 'VALIDEE')",
                ecriture, centre.valeur(), journal, journal + "-2026-" + UUID.randomUUID().toString().substring(0, 6));
        for (String compte : comptes) {
            jdbc().update("INSERT INTO lignes_ecriture (id, ecriture_id, compte_scf) VALUES (?, ?, ?)", UUID.randomUUID(),
                    ecriture, compte);
        }
    }

    // ------------------------------------------------------------------ mapping

    @Test
    void should_use_the_default_mapping_when_the_centre_has_none() {
        assertThat(valeurs(usages().comptesDuParametrage(CENTRE_A))).isEqualTo(COMPTES_PAR_DEFAUT);
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("512"))).isTrue();
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("999"))).isFalse();
        for (String journal : new String[]{"VE", "BQ", "CA", "AC", "ST"}) {
            assertThat(usages().journalUtilise(CENTRE_A, CodeJournal.de(journal))).as(journal).isTrue();
        }
        assertThat(usages().journalUtilise(CENTRE_A, CodeJournal.de("OD"))).isFalse();
    }

    @Test
    void should_read_the_accounts_and_journals_of_the_saved_mapping_and_default_the_empty_columns() {
        mapping(CENTRE_A, Map.of("compte_ventes", "7061", "compte_stock", "  ", "journal_vente", "od"));

        Set<String> comptes = valeurs(usages().comptesDuParametrage(CENTRE_A));

        assertThat(comptes).contains("7061", "322").doesNotContain("706");
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("7061"))).isTrue();
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("706"))).isFalse();
        assertThat(usages().journalUtilise(CENTRE_A, CodeJournal.de("OD"))).isTrue();
        assertThat(usages().journalUtilise(CENTRE_A, CodeJournal.de("VE"))).isFalse();
        assertThat(usages().journalUtilise(CENTRE_A, CodeJournal.de("BQ"))).isTrue();
    }

    @Test
    void should_default_the_vat_account_when_its_column_is_empty() {
        mapping(CENTRE_A, new LinkedHashMap<>(Map.of("compte_ventes", "7061")) {{
            put("compte_tva_collectee", null);
        }});

        assertThat(valeurs(usages().comptesDuParametrage(CENTRE_A))).contains("44571");
    }

    @Test
    void should_keep_the_mapping_of_another_centre_apart() {
        mapping(CENTRE_B, Map.of("compte_banque", "5129"));

        assertThat(usages().compteUtilise(CENTRE_B, NumeroDeCompte.de("5129"))).isTrue();
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("5129"))).isFalse();
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("512"))).isTrue();
    }

    // ------------------------------------------------------------------ payeurs, modèles, articles, écritures

    @Test
    void should_see_an_account_chosen_by_a_payer_ignoring_case() {
        payeurAvecCompte(CENTRE_A, "abc7");

        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("ABC7"))).isTrue();
        assertThat(usages().compteUtilise(CENTRE_B, NumeroDeCompte.de("ABC7"))).isFalse();
    }

    @Test
    void should_see_an_account_and_a_journal_chosen_by_a_document_template() {
        modele(CENTRE_A, "OD1", "OD", "6811", "4711");

        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("4711"))).isTrue();
        assertThat(usages().compteUtilise(CENTRE_B, NumeroDeCompte.de("4711"))).isFalse();
        assertThat(usages().journalUtilise(CENTRE_A, CodeJournal.de("OD"))).isTrue();
        assertThat(usages().journalUtilise(CENTRE_B, CodeJournal.de("OD"))).isFalse();
    }

    @Test
    void should_see_an_account_attached_to_a_stock_article_KB_CPT_13a() {
        article(CENTRE_A, " 3221 ", "6021");
        article(CENTRE_A, null, "  ");

        assertThat(usages().compteRattacheAUnArticle(CENTRE_A, NumeroDeCompte.de("3221"))).isTrue();
        assertThat(usages().compteRattacheAUnArticle(CENTRE_A, NumeroDeCompte.de("6021"))).isTrue();
        assertThat(usages().compteRattacheAUnArticle(CENTRE_A, NumeroDeCompte.de("3222"))).isFalse();
        assertThat(usages().compteRattacheAUnArticle(CENTRE_B, NumeroDeCompte.de("3221"))).isFalse();
        // un compte d'article n'est pas un usage du paramétrage : la règle d'usage les distingue (messages différents)
        assertThat(usages().compteUtilise(CENTRE_A, NumeroDeCompte.de("3221"))).isFalse();
    }

    @Test
    void should_see_the_accounts_and_journals_that_carry_entries() {
        ecriture(CENTRE_A, "VE", "411100", "706");

        assertThat(usages().compteAvecEcritures(CENTRE_A, NumeroDeCompte.de("411100"))).isTrue();
        assertThat(usages().compteAvecEcritures(CENTRE_A, NumeroDeCompte.de("512"))).isFalse();
        assertThat(usages().compteAvecEcritures(CENTRE_B, NumeroDeCompte.de("411100"))).isFalse();
        assertThat(usages().journalAvecEcritures(CENTRE_A, CodeJournal.VE)).isTrue();
        assertThat(usages().journalAvecEcritures(CENTRE_A, CodeJournal.BQ)).isFalse();
        assertThat(usages().journalAvecEcritures(CENTRE_B, CodeJournal.VE)).isFalse();
    }
}
