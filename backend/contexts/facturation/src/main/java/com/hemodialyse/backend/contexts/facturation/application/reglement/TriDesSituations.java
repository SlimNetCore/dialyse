package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.DemandeDePage;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tri de la saisie des règlements paginée (RG-REG-009) : liste blanche des champs d'API, défaut {@code dateFacturation desc}
 * (comme {@code GET /reglements}). Valeurs absentes en dernier quel que soit le sens ; textes insensibles à la casse ;
 * {@code etat} dans l'ordre de l'énumération (NON_REGLEE &lt; PARTIELLEMENT_REGLEE &lt; REGLEE). Départage stable toujours
 * ajouté : date de facturation décroissante, numéro décroissant, identifiant de facture.
 */
public final class TriDesSituations {

    public static final String CHAMP_PAR_DEFAUT = "dateFacturation";

    private record Cle<V>(Function<LigneDeSituation, V> valeur, Comparator<V> ordre) {

        Comparator<LigneDeSituation> comparateur(boolean descendant) {
            Comparator<V> sens = descendant ? ordre.reversed() : ordre;
            return Comparator.comparing(valeur, Comparator.nullsLast(sens));
        }
    }

    private static final Map<String, Cle<?>> CHAMPS = champs();

    private static final Comparator<LigneDeSituation> DEPARTAGE =
            Comparator.comparing(LigneDeSituation::dateFacturation, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
                    .thenComparing(LigneDeSituation::numeroFacture, Comparator.nullsLast(Comparator.<String>reverseOrder()))
                    .thenComparing(LigneDeSituation::factureId, Comparator.nullsLast(Comparator.<UUID>naturalOrder()));

    private TriDesSituations() {
    }

    public static Set<String> champsTriables() {
        return CHAMPS.keySet();
    }

    private static Map<String, Cle<?>> champs() {
        Map<String, Cle<?>> champs = new LinkedHashMap<>();
        champs.put("numeroFacture", texte(LigneDeSituation::numeroFacture));
        champs.put("numeroAssurance", texte(LigneDeSituation::numeroAssurance));
        champs.put("patientNom", texte(LigneDeSituation::patientNom));
        champs.put("patientPrenom", texte(LigneDeSituation::patientPrenom));
        champs.put("caisse", texte(LigneDeSituation::caisse));
        champs.put("agence", texte(LigneDeSituation::agence));
        champs.put("centrePayeur", texte(LigneDeSituation::centrePayeur));
        champs.put("montantFacture", new Cle<>(LigneDeSituation::montantFacture, Comparator.naturalOrder()));
        champs.put("montantRegle", new Cle<>(LigneDeSituation::montantRegle, Comparator.naturalOrder()));
        champs.put("etat", new Cle<>(LigneDeSituation::etat, Comparator.naturalOrder()));
        champs.put("codeReglement", texte(LigneDeSituation::latestCodeReglement));
        champs.put("dateFacturation", new Cle<>(LigneDeSituation::dateFacturation, Comparator.naturalOrder()));
        return Map.copyOf(champs);
    }

    private static Cle<String> texte(Function<LigneDeSituation, String> valeur) {
        return new Cle<>(valeur, String.CASE_INSENSITIVE_ORDER);
    }

    /** Lit la page demandée avec la liste blanche et le tri par défaut de la saisie des règlements. */
    public static DemandeDePage demande(Integer page, Integer taille, String tri) {
        return DemandeDePage.lire(page, taille, tri, champsTriables(), CHAMP_PAR_DEFAUT, true);
    }

    /** Comparateur du champ demandé (valeurs absentes en dernier), puis départage stable. */
    public static Comparator<LigneDeSituation> ordre(DemandeDePage demande) {
        Cle<?> cle = CHAMPS.get(demande.champ());
        if (cle == null) {
            throw new IllegalArgumentException("Champ de tri hors liste blanche : " + demande.champ());
        }
        return cle.comparateur(demande.descendant()).thenComparing(DEPARTAGE);
    }
}
