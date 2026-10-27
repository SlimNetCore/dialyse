package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.IndisponibilitesDesGenerateurs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Adapter JPA du langage publié {@link IndisponibilitesDesGenerateurs} : lecture de {@code gmao_interventions}. */
public class IndisponibilitesDesGenerateursJpa implements IndisponibilitesDesGenerateurs {

    private final InterventionSpringData interventions;

    public IndisponibilitesDesGenerateursJpa(InterventionSpringData interventions) {
        this.interventions = interventions;
    }

    @Override
    public List<Indisponibilite> entre(UUID centreId, LocalDate du, LocalDate au) {
        Instant limite = au.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return interventions.immobilisantUnGenerateur(centreId, limite).stream()
                .map(l -> versUneIndisponibilite((UUID) l[0], (String) l[1], (String) l[2], (Instant) l[3], (Instant) l[4], au))
                .filter(i -> !i.fin().isBefore(du))
                .toList();
    }

    private static Indisponibilite versUneIndisponibilite(UUID generateur, String statut, String type, Instant debutLe,
                                                          Instant finLe, LocalDate au) {
        LocalDate debut = debutLe.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate fin = finLe != null ? finLe.atZone(ZoneOffset.UTC).toLocalDate()
                : "EN_COURS".equals(statut) ? au : debut;
        return new Indisponibilite(generateur, debut, fin.isBefore(debut) ? debut : fin, type);
    }
}
