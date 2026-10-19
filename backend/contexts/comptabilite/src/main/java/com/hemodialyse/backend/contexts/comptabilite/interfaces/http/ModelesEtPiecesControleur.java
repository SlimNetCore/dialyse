package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.piece.GererLesModelesEtPieces;
import com.hemodialyse.backend.contexts.comptabilite.application.piece.GererLesModelesEtPieces.CommandeSaisie;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
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
import java.util.List;
import java.util.UUID;

/**
 * Routes C4 du contexte Comptabilité (modèles de pièces, saisie, extourne) ; le contrôleur legacy est retiré.
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ModelesEtPiecesControleur {

    private final SessionCourante session;
    private final GererLesModelesEtPieces gerer;

    public ModelesEtPiecesControleur(SessionCourante session, GererLesModelesEtPieces gerer) {
        this.session = session;
        this.gerer = gerer;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/modeles")
    public ResponseEntity<PageReponse<ModeleReponse>> lister(@RequestParam(required = false) UUID centerId,
                                                             @RequestParam(defaultValue = "false") boolean actifs,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageReponse.depuis(gerer.lister(session.centre(centerId), actifs, page, size),
                ModeleReponse::depuis));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/modeles")
    public ResponseEntity<ModeleReponse> creer(@RequestParam(required = false) UUID centerId,
                                               @RequestBody @Valid ModeleRequete requete) {
        ModeleDePiece modele = gerer.enregistrer(requete.vers(UUID.randomUUID(), session.centre(centerId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ModeleReponse.depuis(modele));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/modeles/{id}")
    public ResponseEntity<ModeleReponse> modifier(@PathVariable UUID id,
                                                  @RequestParam(required = false) UUID centerId,
                                                  @RequestBody @Valid ModeleRequete requete) {
        return ResponseEntity.ok(ModeleReponse.depuis(gerer.enregistrer(requete.vers(id, session.centre(centerId)))));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/modeles/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        gerer.supprimer(session.centre(centerId), id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @PostMapping("/pieces")
    public ResponseEntity<PieceReponse> saisir(@RequestParam(required = false) UUID centerId,
                                               @RequestBody @Valid PieceRequete requete) {
        EcritureComptable ecriture = gerer.saisir(new CommandeSaisie(session.centre(centerId), requete.modeleId(),
                requete.date(), requete.libelle(), requete.montants()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PieceReponse.depuis(ecriture));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/pieces/{ecritureId}/extourne")
    public ResponseEntity<PieceReponse> extourner(@PathVariable UUID ecritureId,
                                                  @RequestParam(required = false) UUID centerId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PieceReponse.depuis(gerer.extourner(session.centre(centerId), ecritureId)));
    }

    record LigneModeleRequete(@NotBlank String sens, @NotBlank @Size(max = 20) String compte,
                              @Size(max = ModeleDePiece.LIBELLE_MAX) String libelle) {
        ModeleDePiece.Ligne vers() {
            ModeleDePiece.Sens valeur;
            try {
                valeur = ModeleDePiece.Sens.valueOf(sens);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Sens inconnu : " + sens + " (DEBIT ou CREDIT)");
            }
            return new ModeleDePiece.Ligne(valeur, compte, libelle);
        }
    }

    record ModeleRequete(@NotBlank @Size(max = ModeleDePiece.CODE_MAX) String code,
                         @NotBlank @Size(max = ModeleDePiece.LIBELLE_MAX) String libelle,
                         @NotBlank String journal, boolean actif,
                         @NotEmpty @Size(max = ModeleDePiece.LIGNES_MAX) List<@Valid LigneModeleRequete> lignes) {
        ModeleDePiece vers(UUID id, CentreId centre) {
            return new ModeleDePiece(id, centre, code, libelle, CodeJournal.de(journal), actif,
                    lignes.stream().map(LigneModeleRequete::vers).toList());
        }
    }

    record LigneModeleReponse(String sens, String compte, String libelle) {
        static LigneModeleReponse depuis(ModeleDePiece.Ligne ligne) {
            return new LigneModeleReponse(ligne.sens().name(), ligne.compte(), ligne.libelle());
        }
    }

    record ModeleReponse(UUID id, String code, String libelle, String journal, boolean actif,
                         List<LigneModeleReponse> lignes) {
        static ModeleReponse depuis(ModeleDePiece modele) {
            return new ModeleReponse(modele.id(), modele.code(), modele.libelle(), modele.journal().valeur(),
                    modele.actif(), modele.lignes().stream().map(LigneModeleReponse::depuis).toList());
        }
    }

    record PieceRequete(@NotNull UUID modeleId, @NotNull LocalDate date, @Size(max = 255) String libelle,
                        @NotEmpty List<BigDecimal> montants) {
    }

    record PieceReponse(UUID id, String numeroPiece, String journalCode, LocalDate dateEcriture,
                        String libelle, BigDecimal total) {
        static PieceReponse depuis(EcritureComptable ecriture) {
            return new PieceReponse(ecriture.id(), ecriture.numeroDePiece().valeur(), ecriture.journal().valeur(),
                    ecriture.dateEcriture(), ecriture.libelle(), ecriture.totalDebit());
        }
    }
}
