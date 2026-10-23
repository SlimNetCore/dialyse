package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp;
import com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp.Suivi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP du recalcul massif du PMP : {@code /api/v1/stock/dashboard/pmp-recalc/{start, jobs/{id}, locks}}, mêmes
 * routes, rôles et JSON que le legacy ({@code StockDashboardRestController}, slice S5). Centre de la session ; une tâche d'un
 * autre centre est refusée (403).
 */
@RestController
@RequestMapping("/api/v1/stock/dashboard/pmp-recalc")
public class RecalculDuPmpControleur {

    private final SessionCourante session;
    private final RecalculMassifDuPmp recalcul;

    public RecalculDuPmpControleur(SessionCourante session, RecalculMassifDuPmp recalcul) {
        this.session = session;
        this.recalcul = recalcul;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @PostMapping("/start")
    public Demarrage demarrer(@RequestBody Demande demande) {
        return new Demarrage(recalcul.demarrer(session.centre(demande.centerId()), demande.articleIds()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/jobs/{jobId}")
    public Suivi suivre(@PathVariable UUID jobId) {
        Suivi suivi = recalcul.suivre(jobId);
        session.centre(suivi.centerId());
        return suivi;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')")
    @GetMapping("/locks")
    public List<UUID> verrous(@RequestParam(required = false) UUID centerId) {
        return recalcul.enCours(session.centre(centerId));
    }

    record Demande(UUID centerId, List<UUID> articleIds) {
    }

    record Demarrage(UUID jobId) {
    }
}

