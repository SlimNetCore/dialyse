package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.ConsulterLeTableauDeBordAdministratif;
import com.hemodialyse.backend.contexts.patientcouverture.application.TableauDeBordAdministratif;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter entrant HTTP du tableau de bord administratif (PL3) : MÊMES URLs ({@code /api/v1/dashboard/stats} et {@code /stats/search}),
 * même JSON (le champ {@code month} n'est rendu que s'il y a un mois), mêmes droits (aucune restriction par route : tout compte
 * authentifié ; DIRECTION et SUPERADMIN sont refusés plus haut en 403 {@code ROLE_SCOPE}, KB-PL3-5 reproduit).
 * <p>
 * Écarts décidés le 2026-11-11 : garde de centre (403 {@code ACCESS_DENIED}, KB-PL3-1) ; centre, délai ou corps absent ou illisible
 * 400 {@code BAD_REQUEST} (avant : 500, KB-PL3-2) ; mois illisible et délai négatif 400 (KB-PL3-4) ; corps sans {@code centerId} :
 * 400 {@code VALIDATION_ERROR} comme avant ; PEC déjà échues exclues de {@code pecExpiring} (KB-PL3-3).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class TableauDeBordAdministratifControleur {

    record Recherche(@NotNull UUID centerId, Integer expirationDays, String month) {
    }

    private final ConsulterLeTableauDeBordAdministratif consulter;

    public TableauDeBordAdministratifControleur(ConsulterLeTableauDeBordAdministratif consulter) {
        this.consulter = consulter;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(@RequestParam UUID centerId,
                                                     @RequestParam(defaultValue = "30") int expirationDays,
                                                     @RequestParam(required = false) String month) {
        return ResponseEntity.ok(json(consulter.executer(centerId, expirationDays, month)));
    }

    @PostMapping("/stats/search")
    public ResponseEntity<Map<String, Object>> rechercher(@RequestBody @Valid Recherche criteres) {
        return ResponseEntity.ok(json(consulter.executer(criteres.centerId(), criteres.expirationDays(), criteres.month())));
    }

    private static Map<String, Object> json(TableauDeBordAdministratif t) {
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("pecCree", t.pecCree());
        reponse.put("pecValidee", t.pecValidee());
        reponse.put("pecExpiring", t.pecExpiring());
        reponse.put("attestationTotal", t.attestationTotal());
        reponse.put("attestationExpiring", t.attestationExpiring());
        reponse.put("patientCount", t.patientCount());
        reponse.put("expirationDays", t.expirationDays());
        if (t.mois() != null) reponse.put("month", t.mois().toString());
        return reponse;
    }
}
