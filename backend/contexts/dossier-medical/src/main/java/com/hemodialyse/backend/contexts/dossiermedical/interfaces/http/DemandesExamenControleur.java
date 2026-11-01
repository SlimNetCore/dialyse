package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemandeExamenUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.AnnulerDemandeRequete;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.DemanderExamenRequete;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.PreleverDemandeRequete;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.ResultatDisponibleRequete;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.ValiderDemandeRequete;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dossier-medical/demandes-examen")
public class DemandesExamenControleur {

    private final DemandeExamenUseCases useCases;

    public DemandesExamenControleur(DemandeExamenUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public ResponseEntity<PagedResult<DemandeExamen>> lister(
            @RequestParam UUID centreId,
            @RequestParam UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerParPatient(new CentreId(centreId), patientId, page, size));
    }

    @GetMapping("/{demandeId}")
    public ResponseEntity<DemandeExamen> consulter(
            @PathVariable UUID demandeId,
            @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new DemandeExamenId(demandeId), new CentreId(centreId)));
    }

    @PostMapping
    public ResponseEntity<DemandeExamenId> demander(
            @RequestBody DemanderExamenRequete req) {
        List<LigneDemandeExamen> lignes = req.lignes().stream()
                .map(l -> new LigneDemandeExamen(
                        l.id() == null ? UUID.randomUUID() : l.id(),
                        l.codeSystem(), l.code(), l.codeDisplay(), l.libelle(), l.commentaire()))
                .collect(Collectors.toList());
        DemandeExamenId id = useCases.demander(new DemandeExamenUseCases.DemanderExamenCommande(
                req.patientId(), new CentreId(req.centreId()), req.prescripteurId(),
                req.dateDemande(), CategorieExamen.valueOf(req.categorie()), req.urgent(),
                req.motif(), lignes), Instant.now());
        return ResponseEntity.ok(id);
    }

    @PutMapping("/{demandeId}/preleve")
    public ResponseEntity<Void> preleve(
            @PathVariable UUID demandeId,
            @RequestBody PreleverDemandeRequete req) {
        DemandeExamen demande = useCases.consulter(new DemandeExamenId(demandeId), new CentreId(req.centreId()));
        demande.preleve((req.maintenant() == null ? Instant.now() : req.maintenant()).atOffset(java.time.ZoneOffset.UTC));
        // Note : la persistance de l'état se fait via le service d'application ;
        // cet endpoint illustre l'appel domaine. À compléter par un use case dédié si nécessaire.
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{demandeId}/resultat-disponible")
    public ResponseEntity<Void> marquerResultatDisponible(
            @PathVariable UUID demandeId,
            @RequestBody ResultatDisponibleRequete req) {
        useCases.recevoirResultat(new DemandeExamenUseCases.RecevoirResultatCommande(
                new DemandeExamenId(demandeId), new CentreId(req.centreId()), req.eventId(), req.maintenant()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{demandeId}/valider")
    public ResponseEntity<Void> valider(
            @PathVariable UUID demandeId,
            @RequestBody ValiderDemandeRequete req) {
        useCases.valider(new DemandeExamenUseCases.ValiderExamenCommande(
                new DemandeExamenId(demandeId), new CentreId(req.centreId()), req.conclusion(),
                Instant.now()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{demandeId}/annuler")
    public ResponseEntity<Void> annuler(
            @PathVariable UUID demandeId,
            @RequestBody AnnulerDemandeRequete req) {
        useCases.annuler(new DemandeExamenUseCases.AnnulerExamenCommande(
                new DemandeExamenId(demandeId), new CentreId(req.centreId()), Instant.now()));
        return ResponseEntity.noContent().build();
    }
}
