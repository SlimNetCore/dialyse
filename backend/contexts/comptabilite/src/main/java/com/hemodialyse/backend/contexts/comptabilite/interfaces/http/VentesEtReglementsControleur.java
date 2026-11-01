package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements;
import com.hemodialyse.backend.contexts.comptabilite.application.vente.ComptabiliserLesVentesEtReglements.Bilan;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.FactureNonComptabilisee;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.ReconstitutionReponse;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.ReconstitutionRequete;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Adaptateur HTTP entrant de la slice C5 : reconstitution des écritures de ventes et de règlements, et liste des factures
 * qui n'ont pas encore d'écriture (à régénérer).
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class VentesEtReglementsControleur {

    private final SessionCourante session;
    private final ComptabiliserLesVentesEtReglements comptabilisation;

    public VentesEtReglementsControleur(SessionCourante session, ComptabiliserLesVentesEtReglements comptabilisation) {
        this.session = session;
        this.comptabilisation = comptabilisation;
    }

    /**
     * Régénère les écritures manquantes des factures émises et des règlements encaissés sur la période ; rejouable.
     * Une source refusée (mois clôturé...) n'arrête pas les autres : elle figure dans {@code echecs}.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/rebuild")
    public ResponseEntity<ReconstitutionReponse> reconstruire(@RequestBody @Valid ReconstitutionRequete requete) {
        Bilan bilan = comptabilisation.reconstruire(session.centre(requete.centerId()), requete.from(), requete.to());
        return ResponseEntity.ok(new ReconstitutionReponse(bilan.ventes(), bilan.reglements(), requete.from(),
                requete.to(), bilan.echecs()));
    }

    /**
     * Factures émises sur la période qui n'ont aucune écriture de ventes : à régénérer par {@code POST /rebuild}.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/ventes/non-comptabilisees")
    public ResponseEntity<List<FactureNonComptabilisee>> nonComptabilisees(@RequestParam(required = false) UUID centerId,
                                                                           @RequestParam LocalDate from,
                                                                           @RequestParam LocalDate to) {
        return ResponseEntity.ok(comptabilisation.facturesSansEcriture(session.centre(centerId), from, to).stream()
                .map(FactureNonComptabilisee::depuis).toList());
    }
}


