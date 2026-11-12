package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.DocumentImprime;
import com.hemodialyse.backend.contexts.documents.application.ImprimerUnDocument;
import com.hemodialyse.backend.contexts.documents.application.RenduEchoue;
import com.hemodialyse.backend.contexts.documents.domain.DemandeInvalide;
import com.hemodialyse.backend.contexts.documents.domain.FormatDeSortie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Adapter entrant HTTP des routes d'impression (DOC1, ex {@code DocumentRestController.print / printById}) : MEMES URL, MEMES rôles
 * (session + garde de centre, aucun profil), MEMES en-têtes, MEMES refus en texte brut (400 demande invalide, 500 échec du moteur).
 * Écarts décidés (ADR 0142) : le centre du corps doit être celui de la session, 403 sinon (KB-DOC1-1) ; corps, centre, paramètres ou
 * identifiant de modèle illisibles = 400 {@code BAD_REQUEST} et non 500 (KB-DOC1-5 c).
 */
@RestController
@RequestMapping("/api/v1/documents")
public class ImpressionControleur {

    private final ImprimerUnDocument imprimer;

    public ImpressionControleur(ImprimerUnDocument imprimer) {
        this.imprimer = imprimer;
    }

    /** Point d'entrée principal : le modèle actif du centre pour le type demandé. */
    @PostMapping("/print")
    public ResponseEntity<byte[]> imprimerParType(@RequestBody DemandeDImpressionRequete requete) {
        try {
            return reponse(imprimer.parType(requete.centerId(), requete.typeDocument(), requete.formatOverride(), requete.params()));
        } catch (DemandeInvalide e) {
            return texte(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RenduEchoue e) {
            return echec(e, "Erreur d'impression: ", "Erreur critique d'impression: ");
        }
    }

    /** Impression par identifiant de modèle (utile si plusieurs modèles du même type). */
    @PostMapping("/print/{modeleId}")
    public ResponseEntity<byte[]> imprimerParModele(@PathVariable UUID modeleId, @RequestBody DemandeDImpressionRequete requete) {
        try {
            return reponse(imprimer.parModele(modeleId, requete.centerId(), requete.formatOverride(), requete.params()));
        } catch (DemandeInvalide e) {
            return texte(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RenduEchoue e) {
            return echec(e, "Erreur: ", "Erreur critique: ");
        }
    }

    private static ResponseEntity<byte[]> reponse(DocumentImprime document) {
        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(MediaType.parseMediaType(document.format().typeMime()));
        if (document.format() == FormatDeSortie.TABLEUR) {
            entetes.add("Content-Disposition", "attachment; filename=" + document.nomDeFichier());
        } else if (document.format() == FormatDeSortie.PDF) {
            entetes.add("Content-Disposition", "inline; filename=" + document.nomDeFichier());
        }
        return ResponseEntity.ok().headers(entetes).body(document.octets());
    }

    private static ResponseEntity<byte[]> echec(RenduEchoue e, String prefixe, String prefixeCritique) {
        Throwable cause = e.getCause();
        String message = e.estCritique()
                ? prefixeCritique + cause.getClass().getSimpleName() + " - " + cause.getMessage()
                : prefixe + cause.getMessage();
        return texte(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private static ResponseEntity<byte[]> texte(HttpStatus statut, String message) {
        return ResponseEntity.status(statut).contentType(MediaType.TEXT_PLAIN)
                .body((message == null ? "" : message).getBytes(StandardCharsets.UTF_8));
    }
}
