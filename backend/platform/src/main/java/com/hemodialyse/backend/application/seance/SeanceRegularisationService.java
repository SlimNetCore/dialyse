package com.hemodialyse.backend.application.seance;

import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Séances restées « créées » (validation oubliée) sur les derniers jours : celles que l'administrateur doit régulariser
 * (RG-SEA-046). Lecture seule ; la validation passe par le cas d'usage de la séance.
 */
@Service
public class SeanceRegularisationService {

    /**
     * Jours passés examinés (hier compris, aujourd'hui exclu) ; même fenêtre que la détection des absences.
     */
    public static final int JOURS = 7;

    private final SeanceUseCase seances;

    public SeanceRegularisationService(SeanceUseCase seances) {
        this.seances = seances;
    }

    /**
     * Nombre de séances « créées » des {@value #JOURS} derniers jours du centre que l'administrateur n'a pas encore
     * déverrouillées, et date de la plus ancienne ({@code null} quand il n'y en a aucune).
     */
    public Resume aRegulariser(UUID centerId, LocalDate aujourdhui) {
        SeanceSearch criteres = new SeanceSearch(aujourdhui.minusDays(JOURS), aujourdhui.minusDays(1),
                Set.of(SeanceStatus.CREE), null, SeanceSearch.Sort.DATE, false, false);
        var page = seances.search(CenterId.of(centerId), criteres, 0, 1);
        LocalDate plusAncienne = page.items().isEmpty() ? null : page.items().getFirst().dateSeance();
        return new Resume(page.total(), plusAncienne);
    }

    public record Resume(long total, LocalDate plusAncienne) {
    }
}
