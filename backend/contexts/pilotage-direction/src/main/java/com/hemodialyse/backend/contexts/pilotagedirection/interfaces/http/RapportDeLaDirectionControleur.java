package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.http;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ImpressionDuRapport.Impression;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ImprimerLeRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.application.SessionDeLaDirection;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Adapter entrant HTTP du rapport PDF de la direction (PL9, ADR 0148) : mêmes URL, mêmes rôles (DIRECTION) et mêmes codes que le legacy.
 * La société vient de la session (jamais d'un paramètre), revérifiée à chaque appel. Dates illisibles : 400 ; {@code Accept} sans PDF : 406
 * (KB-PL9-4 corrigé). Le rapport est une consultation journalisée (KB-PL9-8 corrigé, action {@code DIRECTION_RAPPORT_IMPRESSION}).
 */
@RestController
@RequestMapping("/api/v1/direction")
@PreAuthorize("hasRole('DIRECTION')")
public class RapportDeLaDirectionControleur {

    private final SessionDeLaDirection session;
    private final ImprimerLeRapport imprimer;

    public RapportDeLaDirectionControleur(SessionDeLaDirection session, ImprimerLeRapport imprimer) {
        this.session = session;
        this.imprimer = imprimer;
    }

    /** Rapport imprimable de la période (par défaut : depuis le 1er janvier jusqu'à aujourd'hui). */
    @GetMapping("/report")
    public ResponseEntity<byte[]> rapport(HttpServletRequest requete) throws HttpMediaTypeNotAcceptableException {
        exigerUnPdfAcceptable(requete);
        LocalDate debut = date(requete, "from");
        LocalDate fin = date(requete, "to");
        return pdf(imprimer.enDirect(session.societe(), debut, fin));
    }

    /** Rapport d'un mois figé. */
    @GetMapping("/snapshots/{mois}/report")
    public ResponseEntity<byte[]> rapportMensuel(@PathVariable String mois, HttpServletRequest requete)
            throws HttpMediaTypeNotAcceptableException {
        exigerUnPdfAcceptable(requete);
        return pdf(imprimer.mensuel(session.societe(), mois));
    }

    private static ResponseEntity<byte[]> pdf(Impression impression) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(impression.nomDeFichier()).build().toString())
                .body(impression.octets());
    }

    /** Un {@code Accept} qui n'admet pas le PDF est refusé en 406 ; sans {@code Accept}, tout est admis. */
    private static void exigerUnPdfAcceptable(HttpServletRequest requete) throws HttpMediaTypeNotAcceptableException {
        List<String> entetes = java.util.Collections.list(requete.getHeaders(HttpHeaders.ACCEPT));
        if (entetes.isEmpty()) return;
        try {
            if (MediaType.parseMediaTypes(entetes).stream().anyMatch(t -> t.isCompatibleWith(MediaType.APPLICATION_PDF))) return;
        } catch (RuntimeException e) {
            // un Accept illisible n'admet rien
        }
        throw new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_PDF));
    }

    /** Date ISO (aaaa-mm-jj) ; paramètre absent ou vide : {@code null} ; la première valeur d'un paramètre répété. */
    private static LocalDate date(HttpServletRequest requete, String nom) {
        String brut = requete.getParameter(nom);
        if (brut == null || brut.isBlank()) return null;
        try {
            return LocalDate.from(DateTimeFormatter.ISO_DATE.parse(brut.trim()));
        } catch (DateTimeParseException e) {
            throw new ParametreIllisible(nom);
        }
    }

    /** Un paramètre de date illisible : 400. */
    static final class ParametreIllisible extends RuntimeException {
        private final String nom;

        ParametreIllisible(String nom) {
            super("Paramètre illisible : " + nom);
            this.nom = nom;
        }

        String nom() {
            return nom;
        }
    }
}
