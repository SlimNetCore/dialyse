package com.hemodialyse.backend.contexts.gmao.interfaces.http;

import com.hemodialyse.backend.contexts.gmao.application.SessionCourante;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.JoindrePieceJointe;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.JoindrePieceJointeCommande;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.ListerPiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PieceTelechargee;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.RetirerPieceJointe;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.TelechargerPieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePieceInconnu;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Adapter entrant HTTP des pièces jointes : {@code /api/v1/gmao/interventions/{interventionId}/documents}, derrière la chaîne
 * de filtres commune (sécurité, licence, rôle, audit : le POST et le DELETE sont journalisés, les lectures non, KB-GMAO-65).
 * Contrat (routes, statuts, corps, erreurs) verrouillé par la suite de caractérisation, aux écarts décidés le 2026-10-12 près :
 * structure du fichier et extension vérifiées (KB-GMAO-59), nom assaini (KB-GMAO-60), nom et type MIME re-contrôlés au
 * téléchargement (KB-GMAO-61), refus sur une intervention annulée, terminée ou supprimée (KB-GMAO-64, 422). Les identifiants
 * en chemin restent des {@link UUID} décodés par Spring : un identifiant illisible répond 500 comme avant (KB-GMAO-62).
 * Accès réservé au rôle {@code ADMIN} (aucune habilitation propre, KB-GMAO-65).
 */
@RestController
@RequestMapping(PieceJointeControleur.BASE)
@PreAuthorize("hasRole('ADMIN')")
public class PieceJointeControleur {

    public static final String BASE = "/api/v1/gmao/interventions/{interventionId}/documents";

    private final SessionCourante session;
    private final JoindrePieceJointe joindre;
    private final RetirerPieceJointe retirer;
    private final ListerPiecesJointes lister;
    private final TelechargerPieceJointe telecharger;

    public PieceJointeControleur(SessionCourante session, JoindrePieceJointe joindre, RetirerPieceJointe retirer,
                                 ListerPiecesJointes lister, TelechargerPieceJointe telecharger) {
        this.session = session;
        this.joindre = joindre;
        this.retirer = retirer;
        this.lister = lister;
        this.telecharger = telecharger;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PieceJointeReponse> joindre(@PathVariable UUID interventionId, @RequestParam("file") MultipartFile file,
                                                      @RequestParam(defaultValue = "AUTRE") String type) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(PieceJointeReponse.depuis(joindre.executer(
                new JoindrePieceJointeCommande(new InterventionId(interventionId), session.centre(), type,
                        file.getOriginalFilename(), file.getBytes(), session.utilisateur()))));
    }

    @GetMapping
    public ResponseEntity<PieceJointeReponse.Page> lister(@PathVariable UUID interventionId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PieceJointeReponse.Page.depuis(
                lister.executer(new InterventionId(interventionId), session.centre(), page, size)));
    }

    /** Le type MIME vient du format détecté à l'envoi et stocké, jamais d'un en-tête du client ; toujours en pièce jointe. */
    @GetMapping("/{documentId}")
    public ResponseEntity<byte[]> telecharger(@PathVariable UUID interventionId, @PathVariable UUID documentId) {
        PieceTelechargee piece = telecharger.executer(new InterventionId(interventionId), session.centre(),
                new PieceJointeId(documentId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(piece.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(piece.nom(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(piece.octets());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> retirer(@PathVariable UUID interventionId, @PathVariable UUID documentId) {
        retirer.executer(new InterventionId(interventionId), session.centre(), new PieceJointeId(documentId),
                session.utilisateur());
        return ResponseEntity.noContent().build();
    }

    /**
     * Type de pièce inconnu : 400 {@code BAD_REQUEST} dont le détail reproduit le texte observé du legacy (KB-GMAO-63 : nom de
     * l'énumération legacy {@code TypeDocumentIntervention}), le contrat HTTP étant verrouillé par les golden masters.
     */
    @ExceptionHandler(TypeDePieceInconnu.class)
    ProblemDetail typeInconnu(TypeDePieceInconnu erreur) {
        ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        probleme.setTitle("Requete invalide");
        probleme.setDetail("No enum constant com.hemodialyse.backend.domain.gmao.model.TypeDocumentIntervention." + erreur.code());
        probleme.setProperty("code", "BAD_REQUEST");
        return probleme;
    }
}
