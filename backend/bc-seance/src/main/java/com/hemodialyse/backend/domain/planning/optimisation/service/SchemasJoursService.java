package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.service.PlanificationAffectationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service de domaine pur : schémas de jours candidats pour un patient dont l'optimisation choisit les jours. Seuls les
 * schémas parmi les jours d'ouverture sont retenus, du mieux au moins bien espacé (même note d'espacement que l'aide au
 * placement, RG-PLN-031) ; les schémas trop mal espacés sont écartés pour garder un rythme de dialyse acceptable.
 */
public final class SchemasJoursService {

    /**
     * Un schéma est candidat si son espacement est au plus à cet écart du meilleur schéma possible.
     */
    public static final int ECART_ESPACEMENT_TOLERE = 15;
    public static final int SCHEMAS_MAX = 8;

    private SchemasJoursService() {
    }

    public record Schema(Set<JourSemaine> jours, int espacement) {
        public Schema {
            jours = Set.copyOf(jours);
        }
    }

    /**
     * @param seances nombre de séances par semaine (1 à 7)
     * @param ouverts jours d'ouverture du centre
     * @return schémas candidats, du mieux espacé au moins bien espacé ; vide si le centre n'ouvre pas assez de jours
     */
    public static List<Schema> candidats(int seances, Set<JourSemaine> ouverts) {
        if (seances < 1 || seances > JourSemaine.NB_JOURS) {
            throw new IllegalArgumentException("Le nombre de séances par semaine doit être compris entre 1 et 7");
        }
        JourSemaine[] jours = JourSemaine.values();
        List<Schema> tous = new ArrayList<>();
        for (int mask = 1; mask < (1 << jours.length); mask++) {
            if (Integer.bitCount(mask) != seances) continue;
            Set<JourSemaine> schema = EnumSet.noneOf(JourSemaine.class);
            for (int i = 0; i < jours.length; i++) {
                if ((mask & (1 << i)) != 0) schema.add(jours[i]);
            }
            if (ouverts.containsAll(schema)) {
                tous.add(new Schema(schema, PlanificationAffectationService.scoreEspacement(schema, seances)));
            }
        }
        tous.sort(Comparator.comparingInt(Schema::espacement).reversed()
                .thenComparing(s -> s.jours().stream().sorted().toList().toString()));
        if (tous.isEmpty()) return List.of();
        int meilleur = tous.getFirst().espacement();
        return tous.stream().filter(s -> s.espacement() >= meilleur - ECART_ESPACEMENT_TOLERE).limit(SCHEMAS_MAX).toList();
    }
}
