package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConstantesDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Pagination;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Double en mémoire du port {@link HistoriqueDesConstantes} (rejoue le même contrat que la base). */
public final class HistoriqueDesConstantesEnMemoire implements HistoriqueDesConstantes, HistoriqueDesConstantesContrat.Banc {

    private record Seance(CentreId centre, PatientId patient, SeanceId id, LocalDate date, Instant creeLe) {
    }

    private record CleDeVolet(CentreId centre, SeanceId seance) {
    }

    private final List<Seance> seances = new ArrayList<>();
    private final Map<CleDeVolet, Constantes> volets = new HashMap<>();

    @Override
    public void seance(CentreId centre, PatientId patient, SeanceId seance, LocalDate date, Instant creeLe) {
        seances.add(new Seance(centre, patient, seance, date, creeLe));
    }

    @Override
    public void volet(CentreId centre, SeanceId seance, Constantes constantes) {
        volets.put(new CleDeVolet(centre, seance), constantes);
    }

    @Override
    public HistoriqueDesConstantes historique() {
        return this;
    }

    @Override
    public PageDeConstantes dePatient(CentreId centre, PatientId patient, Pagination pagination) {
        List<Seance> toutes = seances.stream()
                .filter(s -> s.centre().equals(centre) && s.patient().equals(patient))
                .sorted(Comparator.comparing(Seance::date).thenComparing(Seance::creeLe).reversed())
                .toList();
        List<ConstantesDeSeance> page = toutes.stream()
                .skip(pagination.rang()).limit(pagination.taille())
                .map(s -> new ConstantesDeSeance(s.id(), s.date(), volets.getOrDefault(new CleDeVolet(centre, s.id()), Constantes.vides())))
                .toList();
        return new PageDeConstantes(page, toutes.size(), pagination.page(), pagination.taille());
    }
}
