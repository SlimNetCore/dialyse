package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.FiltresDePage;
import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.FiltresDePage.FiltreNumerique;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Filtres de colonne de la saisie des règlements (RG-REG-009), combinés en ET : texte « contient » sur {@code numeroFacture},
 * {@code numeroAssurance}, {@code patientNom}, {@code patientPrenom}, {@code caisse}, {@code agence}, {@code centrePayeur},
 * {@code codeReglement} (dernier code de règlement) ; numérique sur {@code montantFacture}, {@code montantRegle} ; énumération sur
 * {@code etat} ({@link EtatDeReglement}) et {@code soldeType} ({@link TypeDeSolde}).
 */
public record FiltresDeSituation(Map<String, String> textes, Optional<FiltreNumerique> montantFacture,
                                 Optional<FiltreNumerique> montantRegle, Set<EtatDeReglement> etats, Set<TypeDeSolde> soldes) {

    /** Champ d'API du filtre texte → valeur lue sur la ligne. */
    private static final Map<String, Function<LigneDeSituation, String>> CHAMPS_TEXTE = champsTexte();

    public static final FiltresDeSituation AUCUN = lire(Map.of());

    public FiltresDeSituation {
        textes = Map.copyOf(textes);
        etats = Set.copyOf(etats);
        soldes = Set.copyOf(soldes);
    }

    private static Map<String, Function<LigneDeSituation, String>> champsTexte() {
        Map<String, Function<LigneDeSituation, String>> champs = new LinkedHashMap<>();
        champs.put("numeroFacture", LigneDeSituation::numeroFacture);
        champs.put("numeroAssurance", LigneDeSituation::numeroAssurance);
        champs.put("patientNom", LigneDeSituation::patientNom);
        champs.put("patientPrenom", LigneDeSituation::patientPrenom);
        champs.put("caisse", LigneDeSituation::caisse);
        champs.put("agence", LigneDeSituation::agence);
        champs.put("centrePayeur", LigneDeSituation::centrePayeur);
        champs.put("codeReglement", LigneDeSituation::latestCodeReglement);
        return Map.copyOf(champs);
    }

    /**
     * Lit les filtres parmi les paramètres de la requête (les autres paramètres sont ignorés).
     *
     * @throws com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.PaginationInvalide
     *         {@code FILTRE_INVALIDE} (nombre illisible, valeur d'énumération inconnue)
     */
    public static FiltresDeSituation lire(Map<String, String> parametres) {
        Map<String, String> textes = new LinkedHashMap<>();
        CHAMPS_TEXTE.keySet().forEach(champ -> FiltresDePage.texte(parametres.get(champ)).ifPresent(t -> textes.put(champ, t)));
        return new FiltresDeSituation(textes,
                FiltresDePage.numerique("montantFacture", parametres.get("montantFacture")),
                FiltresDePage.numerique("montantRegle", parametres.get("montantRegle")),
                FiltresDePage.enumeration("etat", parametres.get("etat"), EtatDeReglement.class),
                FiltresDePage.enumeration("soldeType", parametres.get("soldeType"), TypeDeSolde.class));
    }

    public boolean accepte(LigneDeSituation ligne) {
        for (Map.Entry<String, String> filtre : textes.entrySet()) {
            String valeur = CHAMPS_TEXTE.get(filtre.getKey()).apply(ligne);
            if (valeur == null || !valeur.toLowerCase(Locale.ROOT).contains(filtre.getValue())) {
                return false;
            }
        }
        return accepte(montantFacture, ligne.montantFacture()) && accepte(montantRegle, ligne.montantRegle())
                && (etats.isEmpty() || etats.contains(ligne.etat()))
                && (soldes.isEmpty() || soldes.contains(ligne.soldeType()));
    }

    private static boolean accepte(Optional<FiltreNumerique> filtre, BigDecimal montant) {
        return filtre.map(f -> f.accepte(montant)).orElse(true);
    }
}
