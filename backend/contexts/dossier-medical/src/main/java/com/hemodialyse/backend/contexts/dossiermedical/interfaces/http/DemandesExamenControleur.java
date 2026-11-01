package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemandeExamenUseCases;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDemandeExamen;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
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
            @RequestBody DemanderRequest req) {
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
            @RequestBody PreleveRequest req) {
        DemandeExamen demande = useCases.consulter(new DemandeExamenId(demandeId), new CentreId(req.centreId()));
        demande.preleve((req.maintenant() == null ? Instant.now() : req.maintenant()).atOffset(java.time.ZoneOffset.UTC));
        // Note : la persistance de l'\u00e9tat se fait via le service d'application ;
        // cet endpoint illustre l'appel domaine. \u00c0 compl\u00e9ter par un use case d\u00e9di\u00e9 si n\u00e9cessaire.
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{demandeId}/resultat-disponible")
    public ResponseEntity<Void> marquerResultatDisponible(
            @PathVariable UUID demandeId,
            @RequestBody ResultatDisponibleRequest req) {
        useCases.recevoirResultat(new DemandeExamenUseCases.RecevoirResultatCommande(
                new DemandeExamenId(demandeId), new CentreId(req.centreId()), req.eventId(), req.maintenant()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{demandeId}/valider")
    public ResponseEntity<Void> valider(
            @PathVariable UUID demandeId,
            @RequestBody ValiderRequest req) {
        useCases.valider(new DemandeExamenUseCases.ValiderExamenCommande(
                new DemandeExamenId(demandeId), new CentreId(req.centreId()), req.conclusion(),
                Instant.now()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{demandeId}/annuler")
    public ResponseEntity<Void> annuler(
            @PathVariable UUID demandeId,
            @RequestBody AnnulerRequest req) {
        useCases.annuler(new DemandeExamenUseCases.AnnulerExamenCommande(
                new DemandeExamenId(demandeId), new CentreId(req.centreId()), Instant.now()));
        return ResponseEntity.noContent().build();
    }

    // ---------- DTOs de requ\u00eate ----------

    public record DemanderRequest(UUID patientId, UUID centreId, String prescripteurId,
                                  LocalDate dateDemande, String categorie, boolean urgent, String motif,
                                  List<LigneRequest> lignes) {
    }

    public record LigneRequest(UUID id, String codeSystem, String code, String codeDisplay,
                               String libelle, String commentaire) {
    }

    public record PreleveRequest(UUID centreId, UUID eventId, Instant maintenant) {
    }

    public record ResultatDisponibleRequest(UUID centreId, UUID eventId, Instant maintenant) {
    }

    public record ValiderRequest(UUID centreId, String conclusion) {
    }

    public record AnnulerRequest(UUID centreId) {
    }
}
