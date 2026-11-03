package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
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
 * Suivi des mouvements des patients du centre de la session (liste paginée filtrable, lecture seule) : même URL, même JSON, mêmes
 * rôles que le legacy. Écarts décidés (02/11/2026) : paramètre illisible 400 (avant : 500), taille de 1 à 100 et page positive 400
 * (avant : recadrage silencieux).
 */
@RestController
@RequestMapping("/api/v1/mouvements-patients")
public class MouvementsControleur {

    private static final String ACCES = "hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')";

    private final ConsulterLesMouvements consulter;

    public MouvementsControleur(ConsulterLesMouvements consulter) {
        this.consulter = consulter;
    }

    @GetMapping
    @PreAuthorize(ACCES)
    public ResponseEntity<LectureDesMouvements.Page> lister(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) TypeDeMouvement type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var lues = consulter.executer(centerId, new LectureDesMouvements.Filtre(patientId, type, from, to), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lues);
    }
}
