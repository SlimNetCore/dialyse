package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.reglement.ConsulterLesReglements;
import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.EnregistrerUnReglement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Adapter entrant HTTP des règlements : mêmes URL et même JSON que le legacy (slice F4), réservé à {@code ADMIN} et
 * {@code SECRETAIRE}. Le centre du corps ou de la requête est confronté à celui de la session, en écriture comme en lecture (403
 * sinon, KB-FAC-37 corrigé). Refus propre à l'enregistrement : 422 remboursement supérieur au total réglé. Les erreurs de
 * protocole restent en 500 et une facture introuvable en 400 (KB-FAC-44 reproduit).
 */
@RestController
@RequestMapping("/api/v1/reglements")
public class ReglementControleur {

    private final SessionCourante session;
    private final EnregistrerUnReglement enregistrer;
    private final ConsulterLesReglements consulter;

    public ReglementControleur(SessionCourante session, EnregistrerUnReglement enregistrer, ConsulterLesReglements consulter) {
        this.session = session;
        this.enregistrer = enregistrer;
        this.consulter = consulter;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<?> rechercher(@RequestParam UUID centerId, @RequestParam int year,
                                        @RequestParam(required = false) Integer month,
                                        @RequestParam(required = false) UUID caisseId,
                                        @RequestParam(required = false) UUID agenceId,
                                        @RequestParam(required = false) UUID centrePayeurId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        CriteresDeSituation criteres = new CriteresDeSituation(session.centre(centerId), year, month, caisseId, agenceId,
                centrePayeurId);
        return ResponseEntity.ok(consulter.rechercher(criteres, page, size));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> tableauDeBord(@RequestParam UUID centerId, @RequestParam int year,
                                           @RequestParam(required = false) Integer month,
                                           @RequestParam(required = false) UUID caisseId,
                                           @RequestParam(required = false) UUID agenceId,
                                           @RequestParam(required = false) UUID centrePayeurId) {
        return ResponseEntity.ok(consulter.tableauDeBord(new CriteresDeSituation(session.centre(centerId), year, month, caisseId,
                agenceId, centrePayeurId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @PostMapping("/{factureId}/paiements")
    public ResponseEntity<?> enregistrerUnReglement(@PathVariable UUID factureId, @RequestBody @Valid ReglementRequete requete) {
        return ResponseEntity.ok(enregistrer.enregistrer(session.centre(requete.centerId()), factureId, requete.demande(),
                session.utilisateur()));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/{factureId}/paiements")
    public ResponseEntity<?> historique(@PathVariable UUID factureId, @RequestParam UUID centerId) {
        CentreId centre = session.centre(centerId);
        List<PaiementReponse> historique = consulter.historique(centre, factureId).stream().map(PaiementReponse::depuis).toList();
        return ResponseEntity.ok(historique);
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exporter(@RequestParam UUID centerId, @RequestParam int year,
                                           @RequestParam(required = false) Integer month,
                                           @RequestParam(required = false) UUID caisseId,
                                           @RequestParam(required = false) UUID agenceId,
                                           @RequestParam(required = false) UUID centrePayeurId,
                                           @RequestParam(defaultValue = "excel") String format) throws IOException {
        var lignes = consulter.exporter(new CriteresDeSituation(session.centre(centerId), year, month, caisseId, agenceId,
                centrePayeurId));
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("reglements-" + year + ".csv").build().toString())
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(ExportDesSituations.csv(lignes));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("reglements-" + year + ".xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExportDesSituations.excel(lignes));
    }
}
