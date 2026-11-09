package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * La matrice « caisse d'assurance x centre » du rapport, une par mesure (patients, séances, chiffre d'affaires HT) : une ligne par caisse
 * de la société, une colonne par centre, plus le total de la société. Une caisse sans ligne pour un centre vaut zéro ; une valeur MASQUÉE
 * (effectif sous le seuil) reste masquée (nulle) et ne devient jamais zéro. Une caisse sans nom s'appelle « Caisse non renseignée ».
 */
public final class MatriceDesCaisses {

    public static final String CAISSE_NON_RENSEIGNEE = "Caisse non renseignée";

    private MatriceDesCaisses() {
    }

    public record CentreDeLaMatrice(String id, String nom) {
    }

    public record CelluleDeCaisse(String centreId, String code, Long patients, Long seances, BigDecimal caHt) {
    }

    public record TotalDeCaisse(String code, String nom, Long patients, Long seances, BigDecimal caHt) {
    }

    public record LigneDeMatrice(String caisse, List<Long> patients, Long totalPatients, List<Long> seances, Long totalSeances,
                                 List<BigDecimal> caHt, BigDecimal totalCaHt) {
    }

    public static List<LigneDeMatrice> constituer(List<CentreDeLaMatrice> centres, List<CelluleDeCaisse> cellules,
                                                  List<TotalDeCaisse> totaux) {
        List<LigneDeMatrice> lignes = new ArrayList<>();
        for (TotalDeCaisse total : totaux) {
            String nom = total.nom() == null ? "" : total.nom();
            List<Long> patients = new ArrayList<>();
            List<Long> seances = new ArrayList<>();
            List<BigDecimal> caHt = new ArrayList<>();
            for (CentreDeLaMatrice centre : centres) {
                CelluleDeCaisse trouvee = null;
                for (CelluleDeCaisse cellule : cellules) {
                    if (centre.id().equals(cellule.centreId()) && total.code().equals(cellule.code())) trouvee = cellule;
                }
                patients.add(trouvee == null ? Long.valueOf(0) : trouvee.patients());
                seances.add(trouvee == null ? Long.valueOf(0) : trouvee.seances());
                caHt.add(trouvee == null ? BigDecimal.ZERO : trouvee.caHt());
            }
            lignes.add(new LigneDeMatrice(nom.isEmpty() ? CAISSE_NON_RENSEIGNEE : nom, patients, total.patients(), seances,
                    total.seances(), caHt, total.caHt()));
        }
        return lignes;
    }
}
