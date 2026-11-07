package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le statut sérologique du patient : le dernier résultat connu pour chaque marqueur (bandeau du dossier), le plus récent par date de
 * prélèvement ; à date égale (doublon issu d'un import, KB-MED-56) le dernier créé, puis l'identifiant, pour un choix stable. Les
 * résultats sont rendus dans l'ordre des marqueurs. La règle de risque infectieux de la Planification (KB-MED-53) est autre : elle
 * reste dans la Planification et la Direction.
 */
public final class StatutSerologique {

    private static final Comparator<Serologie> DU_PLUS_ANCIEN_AU_PLUS_RECENT = Comparator.comparing(Serologie::datePrelevement)
            .thenComparing(Serologie::creeeLe, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Serologie::id);

    private StatutSerologique() {
    }

    public static List<Serologie> derniersResultats(List<Serologie> resultats) {
        Map<MarqueurSerologique, Serologie> derniers = new LinkedHashMap<>();
        for (Serologie s : resultats) {
            derniers.merge(s.marqueur(), s, (a, b) -> DU_PLUS_ANCIEN_AU_PLUS_RECENT.compare(a, b) >= 0 ? a : b);
        }
        return derniers.values().stream().sorted(Comparator.comparing(Serologie::marqueur)).toList();
    }
}
