package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.SessionCourante;
import com.hemodialyse.backend.contexts.stock.application.inventaire.FeuilleDeComptage;
import com.hemodialyse.backend.contexts.stock.application.inventaire.GestionDesInventaires;
import com.hemodialyse.backend.contexts.stock.application.inventaire.ProcesVerbalDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.AjoutLigneRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.ComptageRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.EtatReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.ImportReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.InventaireReponse;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.OuvrirRequete;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.Page;
import com.hemodialyse.backend.contexts.stock.interfaces.http.InventairesJson.ResumeReponse;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Adapter entrant HTTP de l'inventaire de stock : {@code /api/v1/stock/inventaires} (slice S7). Ouverture (gel des mouvements du
 * centre), comptage, clôture (stock de départ) ou annulation, feuille de comptage (téléchargement puis import une fois remplie) et
 * procès-verbal. Consultation et comptage : {@code ADMIN}, {@code PHARMACIEN}, {@code INFIRMIER} ; ouverture, report, clôture,
 * annulation : {@code ADMIN}, {@code PHARMACIEN} ; le directeur administratif consulte sans pouvoir modifier (ADR 0045).
 */
@RestController
@RequestMapping("/api/v1/stock/inventaires")
public class InventairesControleur {

    private static final String READ = "hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')";
    private static final String MANAGE = "hasAnyRole('ADMIN','PHARMACIEN')";
    private static final String LECTURE_SEULE = "hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER','DIRECTEUR_ADMINISTRATIF')";
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SessionCourante session;
    private final GestionDesInventaires inventaires;
    private final FeuilleDeComptage feuille;
    private final ProcesVerbalDInventaire procesVerbal;

    public InventairesControleur(SessionCourante session, GestionDesInventaires inventaires, FeuilleDeComptage feuille,
                                 ProcesVerbalDInventaire procesVerbal) {
        this.session = session;
        this.inventaires = inventaires;
        this.feuille = feuille;
        this.procesVerbal = procesVerbal;
    }

    /**
     * Procès-verbal d'inventaire : modèle de document « INVENTAIRE_STOCK » du centre ; filigrane « PROVISOIRE » tant que l'inventaire
     * est en cours.
     */
    @PreAuthorize(LECTURE_SEULE)
    @GetMapping("/{id}/rapport")
    public ResponseEntity<byte[]> rapport(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        Inventaire inventaire = inventaires.consulter(session.centre(centerId), id);
        ProcesVerbalDInventaire.Document document = procesVerbal.imprimer(inventaire, session.nomUtilisateur());
        String base = "inventaire-" + inventaire.reference();
        return switch (document.format().toUpperCase(Locale.ROOT)) {
            case "EXCEL", "XLS", "XLSX" -> ResponseEntity.ok().contentType(XLSX)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(base + ".xlsx", StandardCharsets.UTF_8).build().toString())
                    .body(document.contenu());
            case "HTML" -> ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(document.contenu());
            default -> ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(base + ".pdf", StandardCharsets.UTF_8).build().toString())
                    .body(document.contenu());
        };
    }

    @PreAuthorize(LECTURE_SEULE)
    @GetMapping
    public Page<ResumeReponse> lister(@RequestParam(required = false) UUID centerId, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var resultat = inventaires.lister(session.centre(centerId), page, size);
        return new Page<>(resultat.elements().stream().map(ResumeReponse::depuis).toList(), resultat.total(), resultat.page(),
                resultat.taille());
    }

    /**
     * Situation du centre (bandeau « mouvements gelés », date de la dernière clôture).
     */
    @PreAuthorize(LECTURE_SEULE)
    @GetMapping("/etat")
    public EtatReponse etat(@RequestParam(required = false) UUID centerId) {
        return EtatReponse.depuis(inventaires.etat(session.centre(centerId)));
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public ResponseEntity<InventaireReponse> ouvrir(@RequestBody @Valid OuvrirRequete requete) {
        Inventaire ouvert = inventaires.ouvrir(session.centre(requete.centerId()), requete.dateInventaire(), requete.commentaire(),
                session.nomUtilisateur());
        return ResponseEntity.status(HttpStatus.CREATED).body(InventaireReponse.depuis(ouvert));
    }

    @PreAuthorize(LECTURE_SEULE)
    @GetMapping("/{id}")
    public InventaireReponse consulter(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return InventaireReponse.depuis(inventaires.consulter(session.centre(centerId), id));
    }

    @PreAuthorize(READ)
    @PutMapping("/{id}/lignes/{ligneId}")
    public InventaireReponse compter(@PathVariable UUID id, @PathVariable UUID ligneId, @RequestBody @Valid ComptageRequete requete) {
        return InventaireReponse.depuis(inventaires.compter(session.centre(requete.centerId()), id, ligneId, requete.quantite(),
                requete.motif(), session.nomUtilisateur()));
    }

    @PreAuthorize(READ)
    @PostMapping("/{id}/lignes")
    public InventaireReponse ajouterUneLigne(@PathVariable UUID id, @RequestBody @Valid AjoutLigneRequete requete) {
        return InventaireReponse.depuis(inventaires.ajouterUneLigne(session.centre(requete.centerId()), id, requete.articleId(),
                requete.numeroLot(), requete.datePeremption(), requete.quantite(), requete.motif(), session.nomUtilisateur()));
    }

    @PreAuthorize(READ)
    @DeleteMapping("/{id}/lignes/{ligneId}")
    public InventaireReponse retirerUneLigne(@PathVariable UUID id, @PathVariable UUID ligneId,
                                             @RequestParam(required = false) UUID centerId) {
        return InventaireReponse.depuis(inventaires.retirerUneLigne(session.centre(centerId), id, ligneId));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/reporter-theorique")
    public InventaireReponse reporterLeTheorique(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return InventaireReponse.depuis(inventaires.reporterLeTheorique(session.centre(centerId), id, session.nomUtilisateur()));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/cloturer")
    public InventaireReponse cloturer(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return InventaireReponse.depuis(inventaires.cloturer(session.centre(centerId), id, session.nomUtilisateur()));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/annuler")
    public InventaireReponse annuler(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return InventaireReponse.depuis(inventaires.annuler(session.centre(centerId), id, session.nomUtilisateur()));
    }

    /**
     * Feuille de comptage Excel. Par défaut « à l'aveugle » (sans quantité théorique) : bonne pratique qui évite de recopier le stock
     * informatique au lieu de compter. Une fois remplie, elle se réimporte (POST).
     */
    @PreAuthorize(LECTURE_SEULE)
    @GetMapping("/{id}/feuille-comptage")
    public ResponseEntity<byte[]> feuilleDeComptage(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                                    @RequestParam(defaultValue = "true") boolean aveugle) {
        Inventaire inventaire = inventaires.consulter(session.centre(centerId), id);
        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(XLSX);
        entetes.setContentDisposition(ContentDisposition.attachment()
                .filename("feuille-comptage-" + inventaire.reference() + ".xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(entetes).body(feuille.ecrire(inventaire, aveugle));
    }

    /**
     * Import de la feuille de comptage remplie : les quantités (et motifs) renseignés sont enregistrés ; les lignes vides sont
     * ignorées ; les lignes invalides sont listées dans le bilan sans bloquer les autres.
     */
    @PreAuthorize(READ)
    @PostMapping(value = "/{id}/feuille-comptage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReponse importerLaFeuille(@PathVariable UUID id, @RequestParam("file") MultipartFile fichier,
                                           @RequestParam(required = false) UUID centerId) {
        CentreId centre = session.centre(centerId);
        Inventaire inventaire = inventaires.consulter(centre, id);
        inventaire.exigerEnCours();
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new RefusDuReferentiel("IMPORT_UNREADABLE_FILE", "Le fichier n'a pas pu être lu.");
        }
        FeuilleDeComptage.Lecture lecture = feuille.lire(contenu, inventaire.reference());
        return ImportReponse.depuis(inventaires.importerUnComptage(centre, id, lecture.lignes(), session.nomUtilisateur()), lecture.anomalies());
    }
}

