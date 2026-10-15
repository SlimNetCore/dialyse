package com.hemodialyse.backend.contexts.comptabilite.domain.plan;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * Plan comptable d'un centre (RG-CPT-018). Un centre qui n'a rien enregistré dispose du <b>plan de départ</b> : les 20
 * comptes du SCF, complétés des comptes que son paramétrage utilise déjà (un centre a pu les personnaliser avant l'arrivée
 * du plan comptable). Ce plan est enregistré comme le sien à sa première modification.
 */
public final class PlanComptable {

    private final List<CompteComptable> comptes;

    private PlanComptable(List<CompteComptable> comptes) {
        this.comptes = comptes;
    }

    /**
     * Plan de départ : le plan SCF, plus les comptes du paramétrage qui n'y figurent pas (libellé « Compte N »).
     */
    public static PlanComptable deDepart(CentreId centre, Set<NumeroDeCompte> comptesDuParametrage) {
        TreeMap<NumeroDeCompte, CompteComptable> plan = new TreeMap<>();
        planScf(centre).forEach(c -> plan.put(c.numero(), c));
        for (NumeroDeCompte numero : comptesDuParametrage) {
            plan.computeIfAbsent(numero, n -> CompteComptable.reconstitue(centre, n, "Compte " + n, true));
        }
        return new PlanComptable(List.copyOf(plan.values()));
    }

    private static List<CompteComptable> planScf(CentreId centre) {
        return List.of(
                compte(centre, "322", "Fournitures consommables"),
                compte(centre, "401", "Fournisseurs de stocks et services"),
                compte(centre, "408", "Fournisseurs — factures non parvenues"),
                compte(centre, "411100", "Clients — patients"),
                compte(centre, "411500", "Clients — organismes payeurs"),
                compte(centre, "421", "Personnel — rémunérations dues"),
                compte(centre, "431", "Sécurité sociale"),
                compte(centre, "4456", "TVA déductible"),
                compte(centre, "44571", "TVA collectée"),
                compte(centre, "512", "Banque"),
                compte(centre, "530", "Caisse"),
                compte(centre, "602", "Achats consommés — autres approvisionnements"),
                compte(centre, "613", "Locations"),
                compte(centre, "615", "Entretien, réparations et maintenance"),
                compte(centre, "626", "Frais postaux et de télécommunications"),
                compte(centre, "631", "Rémunérations du personnel"),
                compte(centre, "635", "Cotisations aux organismes sociaux"),
                compte(centre, "657", "Charges exceptionnelles de gestion courante"),
                compte(centre, "706", "Prestations de services"),
                compte(centre, "757", "Produits exceptionnels sur opérations de gestion"));
    }

    private static CompteComptable compte(CentreId centre, String numero, String libelle) {
        return CompteComptable.reconstitue(centre, NumeroDeCompte.de(numero), libelle, true);
    }

    /**
     * Comptes du plan, triés par numéro (ordre alphabétique : {@code 10} avant {@code 5121}).
     */
    public List<CompteComptable> comptes() {
        return comptes;
    }

    /**
     * Comptes dont le numéro ou le libellé contient le fragment (sans tenir compte de la casse) ; tous si le fragment est vide.
     */
    public List<CompteComptable> rechercher(String recherche) {
        String filtre = recherche == null ? "" : recherche.trim().toLowerCase(Locale.ROOT);
        return comptes.stream()
                .filter(c -> filtre.isEmpty() || c.numero().valeur().toLowerCase(Locale.ROOT).contains(filtre)
                        || c.libelle().toLowerCase(Locale.ROOT).contains(filtre))
                .toList();
    }

    public boolean contientUnCompteActif(NumeroDeCompte numero) {
        return comptes.stream().anyMatch(c -> c.numero().equals(numero) && c.actif());
    }
}
