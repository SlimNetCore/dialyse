package com.hemodialyse.backend.application.planning.optimisation;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.PerimetreOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation.Indicateurs;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doublures communes aux tests des services d'optimisation.
 */
final class OptimisationTestSupport {

    static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);

    private OptimisationTestSupport() {
    }

    static Clock horloge(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    static ParametresOptimisation parametres(PerimetreOptimisation perimetre) {
        return ParametresOptimisation.parDefaut(perimetre, DIMANCHE);
    }

    static DonneesOptimisation donneesVides() {
        DonneesPlanning planning = new DonneesPlanning(List.of(), List.of(), List.of(), List.of());
        return new DonneesOptimisation(new DonneesPresence(planning, 4, List.of(), List.of(), List.of(), List.of()),
                List.of());
    }

    static ResultatOptimisation resultatVide() {
        Indicateurs vide = new Indicateurs(0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new ResultatOptimisation(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), vide, vide);
    }

    /**
     * Historique en mémoire, borné par centre comme l'adaptateur réel.
     */
    static final class RunsEnMemoire implements OptimisationRunRepositoryPort {
        final Map<UUID, RunOptimisation> parId = new LinkedHashMap<>();
        final List<String> journal = new ArrayList<>();
        boolean saveEnEchec;

        @Override
        public RunOptimisation save(RunOptimisation run) {
            if (saveEnEchec) throw new IllegalStateException("base inaccessible");
            parId.put(run.id(), run);
            journal.add(run.statut() + (run.phase() == null ? "" : ":" + run.phase()));
            return run;
        }

        @Override
        public Optional<RunOptimisation> findById(UUID centerId, UUID id) {
            return Optional.ofNullable(parId.get(id)).filter(r -> r.centerId().equals(centerId));
        }

        @Override
        public Optional<RunOptimisation> findEnCours(UUID centerId) {
            return parId.values().stream().filter(r -> r.centerId().equals(centerId) && r.enCours()).findFirst();
        }

        @Override
        public PagedResult<RunOptimisation> findPaged(UUID centerId, int page, int size) {
            List<RunOptimisation> tous = parId.values().stream().filter(r -> r.centerId().equals(centerId)).toList();
            return PagedResult.of(tous, tous.size(), page, size);
        }

        @Override
        public void purger(UUID centerId, int aGarder) {
            journal.add("PURGE:" + aGarder);
        }

        @Override
        public boolean supprimer(UUID centerId, UUID id) {
            if (findById(centerId, id).isEmpty()) return false;
            parId.remove(id);
            journal.add("SUPPRESSION");
            return true;
        }

        @Override
        public int interrompreEnCours(String motif) {
            return 0;
        }
    }
}
