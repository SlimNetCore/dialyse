package com.hemodialyse.backend.contexts.planification.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLesSalles;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.application.VueDesSalles;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.PageReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.SalleDetailReponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP de la vue d'ensemble des salles du centre (slice P5) : générateurs affectés, capacité et places restantes.
 * Lecture seule, paginée (taille plafonnée à 100, page ou taille négative : 400), bornée au centre, jamais mise en cache. Mêmes URL,
 * droits (administration, secrétariat) et JSON que le legacy.
 */
@RestController
@RequestMapping("/api/v1/planning/salles")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
public class SallesControleur {

    private final SessionCourante session;
    private final ConsulterLesSalles consulter;

    public SallesControleur(SessionCourante session, ConsulterLesSalles consulter) {
        this.session = session;
        this.consulter = consulter;
    }

    @GetMapping
    public ResponseEntity<PageReponse<SalleDetailReponse>> lister(@RequestParam(required = false) UUID centerId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        CentreId centre = session.centre(centerId);
        VueDesSalles.Page lue = consulter.executer(centre, page, size);
        int taille = Math.min(size, com.hemodialyse.backend.contexts.planification.domain.Pagination.TAILLE_MAXIMALE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PageReponse<>(lue.items().stream().map(SalleDetailReponse::depuis).toList(), page, taille, lue.total()));
    }
}
