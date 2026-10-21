package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ProfilDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.ImprimerLaSynthese;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adapter entrant HTTP de l'impression de la synthèse mensuelle de facturation : même URL, mêmes corps et mêmes réponses que le
 * legacy (slice F5). Le centre du corps est confronté à celui de la session (403 sinon, KB-FAC-52 corrigé), avant toute
 * impression. Comme avant : période ou format refusés en 400 texte, autre échec d'impression en 500 texte (KB-FAC-59 reproduit).
 */
@RestController
@RequestMapping("/api/v1/facturation")
public class SyntheseControleur {

    private final SessionCourante session;
    private final ImprimerLaSynthese imprimer;

    public SyntheseControleur(SessionCourante session, ImprimerLaSynthese imprimer) {
        this.session = session;
        this.imprimer = imprimer;
    }

    @PreAuthorize(ProfilDeLaSynthese.EXPRESSION)
    @PostMapping("/synthese/print")
    public ResponseEntity<byte[]> imprimer(@RequestBody @Valid SyntheseRequete requete) {
        CentreId centre = session.centre(requete.centerId());
        try {
            byte[] document = imprimer.executer(centre, requete.periodStart(), requete.periodEnd(), requete.format());
            return reponse(document, FormatDImpression.depuis(requete.format()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body(e.getMessage().getBytes());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur impression synthese: " + e.getMessage()).getBytes());
        }
    }

    private static ResponseEntity<byte[]> reponse(byte[] document, FormatDImpression format) {
        HttpHeaders entetes = new HttpHeaders();
        if (format.estUnClasseur()) {
            entetes.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            entetes.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=synthese-facturation-mensuelle.xlsx");
        } else if (format == FormatDImpression.HTML) {
            entetes.setContentType(MediaType.TEXT_HTML);
        } else {
            entetes.setContentType(MediaType.APPLICATION_PDF);
            entetes.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=synthese-facturation-mensuelle.pdf");
        }
        return ResponseEntity.ok().headers(entetes).body(document);
    }
}
