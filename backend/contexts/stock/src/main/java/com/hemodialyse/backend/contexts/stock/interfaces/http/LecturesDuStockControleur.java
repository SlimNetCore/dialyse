package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.TriDuClassement;
import com.hemodialyse.backend.contexts.stock.interfaces.http.LecturesDuStockJson.Alerte;
import com.hemodialyse.backend.contexts.stock.interfaces.http.LecturesDuStockJson.Explication;
import com.hemodialyse.backend.contexts.stock.interfaces.http.LecturesDuStockJson.Tableau;
import com.hemodialyse.backend.contexts.stock.interfaces.http.LecturesDuStockJson.Tracabilite;
import com.hemodialyse.backend.contexts.stock.interfaces.http.LecturesDuStockJson.Valorise;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des lectures du Stock : {@code /api/v1/stock/dashboard/{analytics, stock-valorise, alertes,
 * tracabilite/{lotId}, pmp-explain/{articleId}}}, mêmes routes, rôles et JSON que le legacy ({@code StockDashboardRestController},
 * slice S4). Centre de la session, un autre centre demandé est refusé (403, KB-STK-23) ; les routes {@code pmp-recalc/*}
 * restent au legacy jusqu'à la slice S5.
 */
@RestController
@RequestMapping("/api/v1/stock/dashboard")
public class LecturesDuStockControleur {

    private final SessionCourante session;
    private final ConsulterLeStock stock;

    public LecturesDuStockControleur(SessionCourante session, ConsulterLeStock stock) {
        this.session = session;
        this.stock = stock;
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/analytics")
    public Tableau tableauDeBord(@RequestParam(required = false) UUID centerId, @RequestParam(defaultValue = "30") int days,
                                 @RequestParam(defaultValue = "10") int topN,
                                 @RequestParam(defaultValue = "VALUE") String sortBy) {
        return Tableau.depuis(stock.tableauDeBord(session.centre(centerId), days, topN, TriDuClassement.depuis(sortBy)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/stock-valorise")
    public List<Valorise> stockValorise(@RequestParam(required = false) UUID centerId) {
        return stock.stockValorise(session.centre(centerId)).stream().map(Valorise::depuis).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN')")
    @GetMapping("/tracabilite/{lotId}")
    public List<Tracabilite> tracabilite(@PathVariable UUID lotId, @RequestParam(required = false) UUID centerId) {
        return stock.tracabilite(session.centre(centerId), lotId).stream().map(Tracabilite::depuis).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/alertes")
    public List<Alerte> alertes(@RequestParam(required = false) UUID centerId) {
        return stock.alertes(session.centre(centerId)).stream().map(Alerte::depuis).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')")
    @GetMapping("/pmp-explain/{articleId}")
    public Explication expliquerLePmp(@PathVariable UUID articleId, @RequestParam(required = false) UUID centerId) {
        return Explication.depuis(stock.expliquerLePmp(session.centre(centerId), articleId));
    }
}

