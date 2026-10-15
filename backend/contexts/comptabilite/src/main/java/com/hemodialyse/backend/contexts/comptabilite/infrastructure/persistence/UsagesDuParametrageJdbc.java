package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lecture SQL de ce que le paramétrage et les autres slices font des comptes et des journaux du centre : mapping
 * ({@code mapping_comptable}), payeurs, modèles de pièces, écritures et, pour les comptes, articles du stock (ACL JDBC : pas
 * de dépendance au module des articles). Un mapping absent vaut le mapping par défaut ; une colonne vide, sa valeur par
 * défaut. Les comparaisons ignorent la casse (les numéros sont normalisés en majuscules, KB-CPT-04).
 */
public class UsagesDuParametrageJdbc implements UsagesDuParametrage {

    private static final List<String> COMPTES_DU_MAPPING = List.of("compte_ventes", "compte_client_patient",
            "compte_client_autre", "compte_banque", "compte_caisse", "compte_tva_collectee", "compte_stock",
            "compte_consommation", "compte_factures_non_parvenues", "compte_boni_inventaire", "compte_mali_inventaire");
    private static final Map<String, String> COMPTES_PAR_DEFAUT = Map.ofEntries(Map.entry("compte_ventes", "706"),
            Map.entry("compte_client_patient", "411100"), Map.entry("compte_client_autre", "411500"),
            Map.entry("compte_banque", "512"), Map.entry("compte_caisse", "530"), Map.entry("compte_tva_collectee", "44571"),
            Map.entry("compte_stock", "322"), Map.entry("compte_consommation", "602"),
            Map.entry("compte_factures_non_parvenues", "408"), Map.entry("compte_boni_inventaire", "757"),
            Map.entry("compte_mali_inventaire", "657"));
    private static final Map<String, String> JOURNAUX_PAR_DEFAUT = Map.of("journal_vente", "VE",
            "journal_reglement_banque", "BQ", "journal_reglement_caisse", "CA", "journal_stock_reception", "AC",
            "journal_stock_sortie", "ST", "journal_stock_inventaire", "ST");

    private final JdbcTemplate jdbc;

    public UsagesDuParametrageJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Les noms de colonnes rendus par JDBC varient de casse selon la base (H2 : majuscules, PostgreSQL : minuscules).
     */
    private static Object valeurDe(Map<String, Object> ligne, String colonne) {
        for (Map.Entry<String, Object> entree : ligne.entrySet()) {
            if (entree.getKey().equalsIgnoreCase(colonne)) {
                return entree.getValue();
            }
        }
        return null;
    }

    @Override
    public Set<NumeroDeCompte> comptesDuParametrage(CentreId centre) {
        Set<NumeroDeCompte> comptes = new LinkedHashSet<>();
        valeursDuMapping(centre, COMPTES_DU_MAPPING, COMPTES_PAR_DEFAUT).forEach(v -> comptes.add(NumeroDeCompte.de(v)));
        return comptes;
    }

    @Override
    public boolean compteUtilise(CentreId centre, NumeroDeCompte compte) {
        return comptesDuParametrage(centre).contains(compte)
                || existe("SELECT COUNT(*) FROM comptes_payeurs WHERE center_id = ? AND UPPER(compte) = ?", centre, compte.valeur())
                || existe("SELECT COUNT(*) FROM modeles_piece_lignes WHERE center_id = ? AND UPPER(compte) = ?", centre,
                compte.valeur());
    }

    @Override
    public boolean compteRattacheAUnArticle(CentreId centre, NumeroDeCompte compte) {
        return existe("SELECT COUNT(*) FROM articles WHERE center_id = ? AND (UPPER(TRIM(compte_stock)) = ? "
                + "OR UPPER(TRIM(compte_charge)) = ?)", centre, compte.valeur(), compte.valeur());
    }

    @Override
    public boolean compteAvecEcritures(CentreId centre, NumeroDeCompte compte) {
        return existe("SELECT COUNT(*) FROM lignes_ecriture l JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                + "WHERE e.center_id = ? AND UPPER(l.compte_scf) = ?", centre, compte.valeur());
    }

    @Override
    public boolean journalUtilise(CentreId centre, CodeJournal journal) {
        return valeursDuMapping(centre, List.copyOf(JOURNAUX_PAR_DEFAUT.keySet()), JOURNAUX_PAR_DEFAUT).contains(journal.valeur())
                || existe("SELECT COUNT(*) FROM modeles_piece WHERE center_id = ? AND UPPER(journal_code) = ?", centre,
                journal.valeur());
    }

    @Override
    public boolean journalAvecEcritures(CentreId centre, CodeJournal journal) {
        return existe("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? AND UPPER(journal_code) = ?", centre,
                journal.valeur());
    }

    /**
     * Valeurs (rognées, en majuscules) des colonnes demandées du mapping du centre ; la valeur par défaut d'une colonne vide.
     */
    private Set<String> valeursDuMapping(CentreId centre, List<String> colonnes, Map<String, String> parDefaut) {
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "SELECT " + String.join(", ", colonnes) + " FROM mapping_comptable WHERE center_id = ?", centre.valeur());
        Set<String> valeurs = new LinkedHashSet<>();
        for (String colonne : colonnes) {
            Object lue = lignes.isEmpty() ? null : valeurDe(lignes.get(0), colonne);
            String valeur = lue == null || lue.toString().isBlank() ? parDefaut.get(colonne) : lue.toString().trim();
            valeurs.add(valeur.toUpperCase(Locale.ROOT));
        }
        return valeurs;
    }

    private boolean existe(String requete, CentreId centre, Object... parametres) {
        Object[] tous = new Object[parametres.length + 1];
        tous[0] = centre.valeur();
        System.arraycopy(parametres, 0, tous, 1, parametres.length);
        Long total = jdbc.queryForObject(requete, Long.class, tous);
        return total != null && total > 0;
    }
}
