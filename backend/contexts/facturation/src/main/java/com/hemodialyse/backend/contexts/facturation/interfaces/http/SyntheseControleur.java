package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ProfilDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.ImprimerLaSynthese;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

    /** Même valeur que {@code AuditRequestFilter.ATTRIBUT_LIBELLE} (le contexte ne dépend pas de la plateforme). */
    static final String ATTRIBUT_LIBELLE_D_AUDIT = "hemodialyse.audit.libelle";

    private final SessionCourante session;
    private final ImprimerLaSynthese imprimer;

    public SyntheseControleur(SessionCourante session, ImprimerLaSynthese imprimer) {
        this.session = session;
        this.imprimer = imprimer;
    }

    @PreAuthorize(ProfilDeLaSynthese.EXPRESSION)
    @PostMapping("/synthese/print")
    public ResponseEntity<byte[]> imprimer(@RequestBody @Valid SyntheseRequete requete, HttpServletRequest http) {
        LocalDate debut = requete.debut();
        LocalDate fin = requete.fin();
        CentreId centre = session.centre(requete.centerId());
        // PL8, KB-PL8-7 : action d'audit dédiée, libellé = période et format ; identifiant tracé = centre
        http.setAttribute("hemodialyse.audit.entityId", centre.valeur().toString());
        http.setAttribute(ATTRIBUT_LIBELLE_D_AUDIT, "Synthèse de facturation imprimée du " + debut + " au " + fin + " ("
                + formatPourLAudit(requete.format()) + ")");
        try {
            byte[] document = imprimer.executer(centre, debut, fin, requete.format());
            return reponse(document, FormatDImpression.depuis(requete.format()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body(e.getMessage().getBytes());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body(("Erreur impression synthese: " + e.getMessage()).getBytes());
        }
    }

    /** Le format tel que demandé, borné (le libellé d'audit tient en 500 caractères) ; un format inconnu est tracé tel quel, tronqué. */
    private static String formatPourLAudit(String format) {
        if (format == null || format.isBlank()) {
            return "PDF";
        }
        String normalise = format.trim().toUpperCase(java.util.Locale.ROOT);
        return normalise.length() > 20 ? normalise.substring(0, 20) : normalise;
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
