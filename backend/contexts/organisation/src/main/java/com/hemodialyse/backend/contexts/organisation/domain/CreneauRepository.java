package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;

public interface CreneauRepository {
    Optional<Creneau> trouver(CentreId centreId, CreneauId id);

    void sauvegarder(Creneau creneau);

    /** Les créneaux proposés (actifs) du centre, par code. */
    List<Creneau> listerParCentre(CentreId centreId);

    /** PL11-2 : tous les créneaux du centre, désactivés compris (réactivation d'un code saisi à nouveau). */
    List<Creneau> listerTousParCentre(CentreId centreId);

    /** PL11-2 : une page des créneaux proposés, filtrés par la recherche (code ou libellé, casse ignorée), triés par code. */
    PageDeCreneaux page(CentreId centreId, String recherche, int page, int taille);

    /** PL11-2 : suppression physique (un créneau que plus rien ne référence). */
    void supprimer(CentreId centreId, CreneauId id);

    record PageDeCreneaux(List<Creneau> lignes, long total) {
    }
}
