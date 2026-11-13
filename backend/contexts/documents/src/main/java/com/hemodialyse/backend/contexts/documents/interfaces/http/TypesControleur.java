package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.ListerLesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.TypeDisponible;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Types de documents proposés (DOC4b, ex {@code DocumentRestController}) : {@code GET /api/v1/documents/types}. MÊME URL, toute session, même
 * forme ({@code code}, {@code label}) ; les types sont ceux que les contextes déclarent (ADR 0165).
 *
 * <p>Écarts décidés (KB-DOC4B-1) : un verbe non géré répond 405 (avec {@code Allow}) et un {@code Accept} indisponible 406, et non 500.
 */
@RestController
@RequestMapping("/api/v1/documents/types")
public class TypesControleur {

    private final ListerLesTypesDeDocument types;

    public TypesControleur(ListerLesTypesDeDocument types) {
        this.types = types;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> lister() {
        List<Map<String, String>> lignes = new ArrayList<>();
        for (TypeDisponible type : types.lister()) {
            Map<String, String> ligne = new LinkedHashMap<>();
            ligne.put("code", type.code());
            ligne.put("label", type.libelle());
            lignes.add(ligne);
        }
        return ResponseEntity.ok(lignes);
    }

    @RequestMapping(value = "", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<ProblemDetail> verbeNonGere(HttpServletRequest requete) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        probleme.setTitle("Methode non supportee");
        probleme.setDetail("Méthode non supportée : " + requete.getMethod());
        probleme.setProperty("code", "METHOD_NOT_ALLOWED");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "GET, HEAD, OPTIONS").body(probleme);
    }
}
