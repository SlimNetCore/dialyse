package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.ConsulterLeTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Adapter entrant HTTP du tableau de bord de facturation : même URL et même JSON que le legacy (slice F5). Le centre demandé
 * est confronté à celui de la session (403 sinon, KB-FAC-52 corrigé). Les erreurs de protocole restent en 500 (mois illisible
 * ou absent, centre illisible : KB-FAC-53 reproduit) ; un mois blanc donne 400 « Le mois est obligatoire ».
 */
@RestController
@RequestMapping("/api/v1/facturation")
public class TableauDeBordControleur {

    private static final DateTimeFormatter FORMAT_DU_MOIS = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SessionCourante session;
    private final ConsulterLeTableauDeBord consulter;

    public TableauDeBordControleur(SessionCourante session, ConsulterLeTableauDeBord consulter) {
        this.session = session;
        this.consulter = consulter;
    }

    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<TableauDeBordReponse> tableauDeBord(@RequestParam UUID centerId, @RequestParam String month) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok(TableauDeBordReponse.depuis(consulter.executer(centre, lireLeMois(month))));
    }

    private static YearMonth lireLeMois(String mois) {
        if (mois == null || mois.isBlank()) {
            return null;
        }
        return YearMonth.parse(mois.trim(), FORMAT_DU_MOIS);
    }
}
