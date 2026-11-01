package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.DetecterUnePeriodePassee;
import com.hemodialyse.backend.contexts.absencespatients.application.SessionCourante;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto.PeriodeDemande;
import com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto.RattrapageReponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Adapter entrant HTTP du rattrapage de détection (slice A3) : même URL et même JSON {@code {creees, annulees}} que le legacy,
 * réservé à l'administrateur du centre. Champs additifs, présents seulement quand ils comptent : {@code fin} et
 * {@code finRamenee} quand la fin demandée est postérieure à hier (le legacy la ramenait en silence), {@code echecs} quand des
 * absences ont été en incident (isolées, journalisées), et {@code joursIgnores} (A6, KB-ABS-37) : les jours de la période laissés
 * tels quels parce que leur mois d'absences est clos (rouvrir le mois pour les rattraper). Entrées illisibles : 400 (c'étaient des 500, KB-ABS-28).
 */
@RestController
@RequestMapping("/api/v1/absences-patients")
public class DetectionControleur {

    private final SessionCourante session;
    private final DetecterUnePeriodePassee detecter;

    public DetectionControleur(SessionCourante session, DetecterUnePeriodePassee detecter) {
        this.session = session;
        this.detecter = detecter;
    }

    @PostMapping("/rattrapage-detection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RattrapageReponse> rattraper(@RequestParam(required = false) UUID centerId,
                                                       @Valid @RequestBody PeriodeDemande demande) {
        CentreId centre = session.centre(centerId);
        var rattrapage = detecter.executer(centre, demande.from(), demande.to());
        var bilan = rattrapage.bilan();
        var periode = rattrapage.periode();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RattrapageReponse(bilan.creees(),
                bilan.annulees(), periode.finRamenee() ? periode.au() : null, periode.finRamenee() ? Boolean.TRUE : null,
                bilan.echecs().isEmpty() ? null : bilan.echecs().size(),
                bilan.joursIgnores().isEmpty() ? null : bilan.joursIgnores().stream().sorted().toList()));
    }
}
