package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;

public interface SalleRepository {
    Optional<Salle> trouver(CentreId centreId, SalleId id);

    void sauvegarder(Salle salle);

    /** Les salles proposées (actives) du centre, par code. */
    List<Salle> listerParCentre(CentreId centreId);

    /** Toutes les salles du centre, désactivées comprises (appariement et réactivation). */
    List<Salle> listerTousParCentre(CentreId centreId);

    /** Une page des salles proposées, filtrées sur le code ou le nom (casse ignorée), triées par code. */
    PageDeSalles page(CentreId centreId, String recherche, int page, int taille);

    /** Supprime physiquement une salle que plus rien ne référence. */
    void supprimer(CentreId centreId, SalleId id);

    record PageDeSalles(List<Salle> lignes, long total) {
    }
}
