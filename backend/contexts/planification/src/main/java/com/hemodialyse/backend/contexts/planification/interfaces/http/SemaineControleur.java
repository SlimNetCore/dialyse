package com.hemodialyse.backend.contexts.planification.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLaSemaine;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.CelluleReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.ConflitReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.CreneauReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.JourReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.OccupantReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.SalleReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.SeanceSansCaseReponse;
import com.hemodialyse.backend.contexts.planification.interfaces.http.dto.SemaineReponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter entrant HTTP du planning réel d'une semaine (slice P6) : qui dialyse où, jours fermés et conflits. Lecture seule, bornée au
 * centre, jamais mise en cache. Mêmes URL, droits et JSON que le legacy ; une date ou un centre illisible répond 400 (KB-PLN-67) et la
 * date par défaut est celle du centre (KB-PLN-69). Les méthodes et chemins inconnus restent du ressort du traitement global.
 */
@RestController
@RequestMapping("/api/v1/planning/semaine")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class SemaineControleur {

    private final SessionCourante session;
    private final ConsulterLaSemaine consulter;

    public SemaineControleur(SessionCourante session, ConsulterLaSemaine consulter) {
        this.session = session;
        this.consulter = consulter;
    }

    /** @param date un jour de la semaine voulue (défaut : aujourd'hui au fuseau du centre) */
    @GetMapping
    public ResponseEntity<SemaineReponse> semaine(@RequestParam(required = false) UUID centerId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SemaineReponse.depuis(consulter.executer(centre, date)));
    }
}
