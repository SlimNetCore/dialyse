package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.port.ComptesPayeursUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.ComptesPayeursUseCase.PayeurCompte;
import com.hemodialyse.backend.domain.comptabilite.port.PiecesComptablesUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.PiecesComptablesUseCase.SaisirPieceCommand;
import com.hemodialyse.backend.domain.comptabilite.port.PlanComptableUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.CompteComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.SensEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Paramétrage comptable sans développement : plan comptable du centre, compte client de chaque payeur, modèles de
 * pièces et saisie de pièces. Le centre de chaque requête est confronté à celui de la session.
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ComptabiliteParametrageRestController {

    private final PlanComptableUseCase plan;
    private final ComptesPayeursUseCase comptesPayeurs;
    private final PiecesComptablesUseCase pieces;
    private final CenterAccessGuard centerAccessGuard;

    public ComptabiliteParametrageRestController(PlanComptableUseCase plan, ComptesPayeursUseCase comptesPayeurs,
                                                 PiecesComptablesUseCase pieces, CenterAccessGuard centerAccessGuard) {
        this.plan = plan;
        this.comptesPayeurs = comptesPayeurs;
        this.pieces = pieces;
        this.centerAccessGuard = centerAccessGuard;
    }

    private static <S, T> PagedResult<T> page(PagedResult<S> source, java.util.function.Function<S, T> vers) {
        return PagedResult.of(source.items().stream().map(vers).toList(), source.total(), source.page(), source.size());
    }

    private UUID centre(UUID demande) {
        return centerAccessGuard.requireCenter(demande).value();
    }

    // ─── Plan comptable ──────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/comptes")
    public ResponseEntity<PagedResult<CompteResponse>> comptes(@RequestParam(required = false) UUID centerId,
                                                               @RequestParam(required = false) String recherche,
                                                               @RequestParam(defaultValue = "false") boolean actifs,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(page(plan.lister(centre(centerId), recherche, actifs, page, size), CompteResponse::de));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/comptes/{numero}")
    public ResponseEntity<CompteResponse> enregistrerCompte(@PathVariable String numero,
                                                            @RequestParam(required = false) UUID centerId,
                                                            @RequestBody @Valid CompteRequest req) {
        return ResponseEntity.ok(CompteResponse.de(plan.enregistrer(centre(centerId),
                new CompteComptable(numero, req.libelle(), req.actif()))));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/comptes/{numero}")
    public ResponseEntity<Void> supprimerCompte(@PathVariable String numero,
                                                @RequestParam(required = false) UUID centerId) {
        plan.supprimer(centre(centerId), numero);
        return ResponseEntity.noContent().build();
    }

    // ─── Compte client de chaque payeur ──────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/payeurs")
    public ResponseEntity<PagedResult<PayeurCompte>> payeurs(@RequestParam(required = false) UUID centerId,
                                                             @RequestParam(required = false) String recherche,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(comptesPayeurs.lister(centre(centerId), recherche, page, size));
    }

    /**
     * Affecte un compte client au payeur ; un compte vide lui rend le compte client par défaut du centre.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/payeurs/{payeurId}/compte")
    public ResponseEntity<PayeurCompte> definirComptePayeur(@PathVariable UUID payeurId,
                                                            @RequestParam(required = false) UUID centerId,
                                                            @RequestBody @Valid ComptePayeurRequest req) {
        return ResponseEntity.ok(comptesPayeurs.definir(centre(centerId), payeurId, req.compte()));
    }

    // ─── Modèles de pièces ───────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/modeles")
    public ResponseEntity<PagedResult<ModeleResponse>> modeles(@RequestParam(required = false) UUID centerId,
                                                               @RequestParam(defaultValue = "false") boolean actifs,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(page(pieces.listerModeles(centre(centerId), actifs, page, size), ModeleResponse::de));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/modeles")
    public ResponseEntity<ModeleResponse> creerModele(@RequestParam(required = false) UUID centerId,
                                                      @RequestBody @Valid ModeleRequest req) {
        ModelePiece modele = pieces.enregistrerModele(req.versModele(UUID.randomUUID(), centre(centerId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ModeleResponse.de(modele));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/modeles/{id}")
    public ResponseEntity<ModeleResponse> modifierModele(@PathVariable UUID id,
                                                         @RequestParam(required = false) UUID centerId,
                                                         @RequestBody @Valid ModeleRequest req) {
        return ResponseEntity.ok(ModeleResponse.de(pieces.enregistrerModele(req.versModele(id, centre(centerId)))));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/modeles/{id}")
    public ResponseEntity<Void> supprimerModele(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        pieces.supprimerModele(centre(centerId), id);
        return ResponseEntity.noContent().build();
    }

    // ─── Pièces saisies ──────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @PostMapping("/pieces")
    public ResponseEntity<PieceResponse> saisirPiece(@RequestParam(required = false) UUID centerId,
                                                     @RequestBody @Valid PieceRequest req) {
        EcritureComptable piece = pieces.saisir(new SaisirPieceCommand(centre(centerId), req.modeleId(), req.date(),
                req.libelle(), req.montants()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PieceResponse.de(piece));
    }

    /**
     * Annule une pièce saisie par une écriture inverse datée du jour.
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/pieces/{ecritureId}/extourne")
    public ResponseEntity<PieceResponse> extournerPiece(@PathVariable UUID ecritureId,
                                                        @RequestParam(required = false) UUID centerId) {
        EcritureComptable extourne = pieces.extourner(centre(centerId), ecritureId, LocalDate.now(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).body(PieceResponse.de(extourne));
    }

    // ─── DTO ─────────────────────────────────────────────────────────────────

    record CompteRequest(@NotBlank @Size(max = CompteComptable.LIBELLE_MAX) String libelle, boolean actif) {
    }

    record CompteResponse(String numero, String libelle, boolean actif) {
        static CompteResponse de(CompteComptable compte) {
            return new CompteResponse(compte.numero(), compte.libelle(), compte.actif());
        }
    }

    record ComptePayeurRequest(@Size(max = CompteComptable.NUMERO_MAX) String compte) {
    }

    record LigneModeleDto(@NotBlank String sens, @NotBlank @Size(max = CompteComptable.NUMERO_MAX) String compte,
                          @Size(max = ModelePiece.LIBELLE_MAX) String libelle) {
    }

    record ModeleRequest(@NotBlank @Size(max = ModelePiece.CODE_MAX) String code,
                         @NotBlank @Size(max = ModelePiece.LIBELLE_MAX) String libelle,
                         @NotBlank String journal, boolean actif,
                         @NotEmpty @Size(max = ModelePiece.LIGNES_MAX) List<@Valid LigneModeleDto> lignes) {

        private static SensEcriture sens(String valeur) {
            try {
                return SensEcriture.valueOf(valeur);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Sens inconnu : " + valeur + " (DEBIT ou CREDIT)");
            }
        }

        ModelePiece versModele(UUID id, UUID centerId) {
            return new ModelePiece(id, centerId, code, libelle, JournalCode.de(journal), actif, lignes.stream()
                    .map(l -> new ModelePiece.Ligne(sens(l.sens()), l.compte(), l.libelle())).toList());
        }
    }

    record ModeleResponse(UUID id, String code, String libelle, String journal, boolean actif,
                          List<LigneModeleDto> lignes) {
        static ModeleResponse de(ModelePiece m) {
            return new ModeleResponse(m.id(), m.code(), m.libelle(), m.journal().valeur(), m.actif(), m.lignes().stream()
                    .map(l -> new LigneModeleDto(l.sens().name(), l.compte(), l.libelle())).toList());
        }
    }

    record PieceRequest(@NotNull UUID modeleId, @NotNull LocalDate date, @Size(max = 255) String libelle,
                        @NotEmpty List<BigDecimal> montants) {
    }

    record PieceResponse(UUID id, String numeroPiece, String journalCode, LocalDate dateEcriture, String libelle,
                         BigDecimal total) {
        static PieceResponse de(EcritureComptable e) {
            return new PieceResponse(e.getId(), e.getNumeroPiece(), e.getJournalCode().valeur(), e.getDateEcriture(),
                    e.getLibelle(), e.totalDebit());
        }
    }
}
