package com.hemodialyse.backend.contexts.planification.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service de domaine pur : schémas de jours candidats pour un patient dont l'optimisation choisit les jours (RG-PLN-093). Seuls les
 * schémas parmi les jours d'ouverture sont retenus, du mieux au moins bien espacé (même note d'espacement que l'aide au placement,
 * RG-PLN-031) ; les schémas trop mal espacés sont écartés pour garder un rythme de dialyse acceptable.
 */
public final class SchemasDeJours {

    /**
     * Un schéma est candidat si son espacement est au plus à cet écart du meilleur schéma possible.
     */
    public static final int ECART_ESPACEMENT_TOLERE = 15;
    public static final int SCHEMAS_MAX = 8;

    private SchemasDeJours() {
    }

    /**
     * @param seances nombre de séances par semaine (1 à 7)
     * @param ouverts jours d'ouverture du centre
     * @return schémas candidats, du mieux espacé au moins bien espacé ; vide si le centre n'ouvre pas assez de jours
     */
    public static List<Schema> candidats(int seances, Set<JourDeLaSemaine> ouverts) {
        if (seances < 1 || seances > JourDeLaSemaine.NOMBRE_DE_JOURS) {
            throw new SaisieInvalide("Le nombre de séances par semaine doit être compris entre 1 et 7");
        }
        JourDeLaSemaine[] jours = JourDeLaSemaine.values();
        List<Schema> tous = new ArrayList<>();
        for (int mask = 1; mask < (1 << jours.length); mask++) {
            if (Integer.bitCount(mask) != seances) continue;
            Set<JourDeLaSemaine> schema = EnumSet.noneOf(JourDeLaSemaine.class);
            for (int i = 0; i < jours.length; i++) {
                if ((mask & (1 << i)) != 0) schema.add(jours[i]);
            }
            if (ouverts.containsAll(schema)) {
                tous.add(new Schema(schema, PropositionsDePlace.scoreEspacement(schema, seances)));
            }
        }
        tous.sort(Comparator.comparingInt(Schema::espacement).reversed()
                .thenComparing(s -> s.jours().stream().sorted().toList().toString()));
        if (tous.isEmpty()) return List.of();
        int meilleur = tous.getFirst().espacement();
        return tous.stream().filter(s -> s.espacement() >= meilleur - ECART_ESPACEMENT_TOLERE).limit(SCHEMAS_MAX).toList();
    }

    public record Schema(Set<JourDeLaSemaine> jours, int espacement) {
        public Schema {
            jours = Set.copyOf(jours);
        }
    }
}
