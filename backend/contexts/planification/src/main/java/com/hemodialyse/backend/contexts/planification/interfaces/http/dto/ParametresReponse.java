package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public record ParametresReponse(List<JourDeLaSemaine> joursOuverts, List<UUID> sallesIsolement,
                                int patientsParInfirmier, int patientsParPosteEtSerie) {
    public static ParametresReponse depuis(ParametresDuPlanning p) {
        return new ParametresReponse(EnumSet.copyOf(p.joursOuverts()).stream().toList(),
                p.sallesIsolement().stream().map(SalleId::valeur).sorted(Comparator.naturalOrder()).toList(),
                p.ratio().patientsParInfirmier(), p.patientsParPoste().nombre());
    }
}
