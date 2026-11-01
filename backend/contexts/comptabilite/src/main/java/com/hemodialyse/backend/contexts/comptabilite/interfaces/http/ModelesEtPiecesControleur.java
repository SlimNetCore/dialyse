package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.SessionCourante;
import com.hemodialyse.backend.contexts.comptabilite.application.piece.GererLesModelesEtPieces;
import com.hemodialyse.backend.contexts.comptabilite.application.piece.GererLesModelesEtPieces.CommandeSaisie;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.ModeleReponse;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.ModeleRequete;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.PieceReponse;
import com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto.PieceRequete;
import jakarta.validation.Valid;
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
}
