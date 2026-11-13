package com.hemodialyse.backend.infrastructure.referential.admin.port;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Le catalogue des référentiels que la façade d'administration distribue (PL11-1, ADR 0157) : ceux qu'elle décrit en direct
 * (créneaux, salles, médecins) et ceux que les contextes publient par leurs {@link ReferentialAdminDelegate#descripteurs()}.
 */
public final class ReferentialCatalogue {

    private final List<ReferentialKind> kinds;

    public ReferentialCatalogue(List<ReferentialAdminDelegate> delegues) {
        List<ReferentialKind> tous = new ArrayList<>(ReferentialKind.directs());
        delegues.forEach(d -> tous.addAll(d.descripteurs()));
        this.kinds = List.copyOf(tous);
    }

    /** À ordre d'import égal, l'ordre d'affichage historique de l'écran est conservé (écran et goldens inchangés). */
    private static final List<String> ORDRE_HISTORIQUE = List.of("forfaits", "creneaux", "salles", "medecins", "caisses",
            "agences", "centres-payeurs", "transporteurs");

    /** Dans l'ordre d'import conseillé. */
    public List<ReferentialKind> kinds() {
        return kinds.stream()
                .sorted(Comparator.comparingInt(ReferentialKind::importOrder)
                        .thenComparingInt(k -> {
                            int i = ORDRE_HISTORIQUE.indexOf(k.slug());
                            return i < 0 ? Integer.MAX_VALUE : i;
                        }))
                .toList();
    }

    public Optional<ReferentialKind> parSlug(String slug) {
        return kinds.stream().filter(k -> k.slug().equalsIgnoreCase(slug)).findFirst();
    }

    /** Le référentiel de ce segment d'URL ; inconnu : le même refus qu'auparavant. */
    public ReferentialKind fromSlug(String slug) {
        return parSlug(slug).orElseThrow(() -> new IllegalArgumentException("Référentiel inconnu : " + slug));
    }
}
