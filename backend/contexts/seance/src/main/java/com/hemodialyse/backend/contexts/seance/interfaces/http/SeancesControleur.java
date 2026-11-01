package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.SeanceUseCases;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitApplique;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PecId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seances")
public class SeancesControleur {
    private final SeanceUseCases useCases;

    public SeancesControleur(SeanceUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping
    public ResponseEntity<SeanceId> creer(@RequestBody CreerRequest req) {
        SeanceId id = useCases.creer(new SeanceUseCases.CreerSeanceCommande(
                new PatientId(req.patientId()), new CentreId(req.centreId()), new PecId(req.pecId()),
                req.dateSeance(), req.typeSeance()), java.time.Instant.now());
        return ResponseEntity.ok(id);
    }

    @GetMapping
    public ResponseEntity<PagedResult<Seance>> lister(@RequestParam UUID centreId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.lister(new CentreId(centreId), page, size));
    }

    @GetMapping("/jour")
    public ResponseEntity<PagedResult<Seance>> listerDuJour(@RequestParam UUID centreId,
                                                            @RequestParam LocalDate date,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCases.listerDuJour(new CentreId(centreId), date, page, size));
    }

    @GetMapping("/{seanceId}")
    public ResponseEntity<Seance> consulter(@PathVariable UUID seanceId, @RequestParam UUID centreId) {
        return ResponseEntity.ok(useCases.consulter(new SeanceId(seanceId), new CentreId(centreId)));
    }

    @PostMapping("/{seanceId}/validate")
    @CacheEvict(cacheNames = {"ref.seances.du.jour", "ref.seances.validees.non.signees"}, allEntries = true)
    public ResponseEntity<Void> valider(@PathVariable UUID seanceId, @RequestBody CentreRequest req) {
        useCases.valider(new SeanceUseCases.ValiderSeanceCommande(new SeanceId(seanceId), new CentreId(req.centreId())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seanceId}/sign")
    @CacheEvict(cacheNames = {"ref.seances.du.jour", "ref.seances.validees.non.signees"}, allEntries = true)
    public ResponseEntity<Void> signer(@PathVariable UUID seanceId, @RequestBody CentreRequest req) {
        useCases.signer(new SeanceUseCases.SignerSeanceCommande(new SeanceId(seanceId), new CentreId(req.centreId())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seanceId}/deverrouiller")
    @CacheEvict(cacheNames = {"ref.seances.du.jour", "ref.seances.validees.non.signees"}, allEntries = true)
    public ResponseEntity<Void> deverrouiller(@PathVariable UUID seanceId, @RequestBody CentreRequest req) {
        useCases.deverrouiller(new SeanceUseCases.DeverrouillerPourRegularisationCommande(new SeanceId(seanceId), new CentreId(req.centreId())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seanceId}/debloquer")
    @CacheEvict(cacheNames = {"ref.seances.du.jour", "ref.seances.validees.non.signees"}, allEntries = true)
    public ResponseEntity<Void> debloquer(@PathVariable UUID seanceId, @RequestBody CentreRequest req) {
        useCases.debloquer(new SeanceUseCases.DebloquerPourRegularisationCommande(new SeanceId(seanceId), new CentreId(req.centreId())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seanceId}/unlock")
    @CacheEvict(cacheNames = {"ref.seances.du.jour", "ref.seances.validees.non.signees"}, allEntries = true)
    public ResponseEntity<Void> unlock(@PathVariable UUID seanceId, @RequestBody CentreRequest req) {
        useCases.deverrouiller(new SeanceUseCases.DeverrouillerPourRegularisationCommande(new SeanceId(seanceId), new CentreId(req.centreId())));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seanceId}/forfait")
    public ResponseEntity<Void> declarerForfaitSurcharge(@PathVariable UUID seanceId, @RequestBody ForfaitRequest req) {
        useCases.declarerForfaitSurcharge(new SeanceUseCases.DeclarerForfaitSurchargeCommande(
                new SeanceId(seanceId), new CentreId(req.centreId()),
                new ForfaitApplique(req.forfaitId(), req.code(), req.libelle(), req.prixHt(),
                        req.auteur() == null ? "system" : req.auteur(), java.time.OffsetDateTime.now()),
                req.montantSurcharge(), req.auteur(), java.time.Instant.now()));
        return ResponseEntity.noContent().build();
    }

    public record CreerRequest(UUID patientId, UUID centreId, UUID pecId, LocalDate dateSeance, String typeSeance) {
    }

    public record CentreRequest(UUID centreId) {
    }

    public record ForfaitRequest(UUID centreId, UUID forfaitId, String code, String libelle,
                                 java.math.BigDecimal prixHt, java.math.BigDecimal montantSurcharge, String auteur) {
    }
}