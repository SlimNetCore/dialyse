package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.port.InventaireUseCase;
import com.hemodialyse.backend.infrastructure.importer.FeuilleComptageExcel;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.AjoutLigneRequest;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.ComptageRequest;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.EtatResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.ImportComptageResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.InventaireResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.InventaireResumeResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.OuvrirRequest;
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
import java.security.Principal;
import java.util.UUID;

/**
 * Inventaire de stock : ouverture (gel des mouvements du centre), comptage, clôture (stock de départ) ou
 * annulation, feuille de comptage (téléchargement puis import une fois remplie).
 * <p>
 * Consultation et comptage : ADMIN, PHARMACIEN, INFIRMIER. Ouverture, clôture, annulation : ADMIN, PHARMACIEN.
 */
@RestController
@RequestMapping("/api/v1/stock/inventaires")
public class InventaireRestController {

    private static final String READ = "hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')";
    private static final String MANAGE = "hasAnyRole('ADMIN','PHARMACIEN')";

    private final InventaireUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;
    private final FeuilleComptageExcel feuille;

    public InventaireRestController(InventaireUseCase useCase, CenterAccessGuard centerAccessGuard,
                                    FeuilleComptageExcel feuille) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
        this.feuille = feuille;
    }

    private static String user(Principal principal) {
        return principal != null ? principal.getName() : null;
    }

    @PreAuthorize(READ)
    @GetMapping
    public PagedResponse<InventaireResumeResponse> list(@RequestParam(required = false) UUID centerId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.from(useCase.list(center(centerId), page, size), InventaireResumeResponse::from);
    }

    /**
     * Situation du centre (bandeau « mouvements gelés », date de la dernière clôture).
     */
    @PreAuthorize(READ)
    @GetMapping("/etat")
    public EtatResponse etat(@RequestParam(required = false) UUID centerId) {
        return EtatResponse.from(useCase.etat(center(centerId)));
    }

    @PreAuthorize(MANAGE)
    @PostMapping
    public ResponseEntity<InventaireResponse> ouvrir(@RequestBody @Valid OuvrirRequest request, Principal principal) {
        Inventaire inv = useCase.ouvrir(center(request.centerId()), request.dateInventaire(), request.commentaire(), user(principal));
        return ResponseEntity.status(HttpStatus.CREATED).body(InventaireResponse.from(inv));
    }

    @PreAuthorize(READ)
    @GetMapping("/{id}")
    public InventaireResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        return InventaireResponse.from(useCase.get(center(centerId), id));
    }

    @PreAuthorize(READ)
    @PutMapping("/{id}/lignes/{ligneId}")
    public InventaireResponse compter(@PathVariable UUID id, @PathVariable UUID ligneId,
                                      @RequestBody @Valid ComptageRequest request, Principal principal) {
        return InventaireResponse.from(useCase.compter(center(request.centerId()), id, ligneId, request.quantite(),
                request.motif(), user(principal)));
    }

    @PreAuthorize(READ)
    @PostMapping("/{id}/lignes")
    public InventaireResponse ajouterLigne(@PathVariable UUID id, @RequestBody @Valid AjoutLigneRequest request,
                                           Principal principal) {
        return InventaireResponse.from(useCase.ajouterLigne(center(request.centerId()), id, request.articleId(),
                request.numeroLot(), request.datePeremption(), request.quantite(), request.motif(), user(principal)));
    }

    @PreAuthorize(READ)
    @DeleteMapping("/{id}/lignes/{ligneId}")
    public InventaireResponse retirerLigne(@PathVariable UUID id, @PathVariable UUID ligneId,
                                           @RequestParam(required = false) UUID centerId) {
        return InventaireResponse.from(useCase.retirerLigne(center(centerId), id, ligneId));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/reporter-theorique")
    public InventaireResponse reporterTheorique(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                                Principal principal) {
        return InventaireResponse.from(useCase.reporterTheorique(center(centerId), id, user(principal)));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/cloturer")
    public InventaireResponse cloturer(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                       Principal principal) {
        return InventaireResponse.from(useCase.cloturer(center(centerId), id, user(principal)));
    }

    @PreAuthorize(MANAGE)
    @PostMapping("/{id}/annuler")
    public InventaireResponse annuler(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                      Principal principal) {
        return InventaireResponse.from(useCase.annuler(center(centerId), id, user(principal)));
    }

    /**
     * Feuille de comptage Excel. Par défaut « à l'aveugle » (sans quantité théorique) : bonne pratique qui évite
     * de recopier le stock informatique au lieu de compter. Une fois remplie, elle se réimporte (POST).
     */
    @PreAuthorize(READ)
    @GetMapping("/{id}/feuille-comptage")
    public ResponseEntity<byte[]> feuilleComptage(@PathVariable UUID id, @RequestParam(required = false) UUID centerId,
                                                  @RequestParam(defaultValue = "true") boolean aveugle) {
        Inventaire inv = useCase.get(center(centerId), id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("feuille-comptage-" + inv.getReference() + ".xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(feuille.ecrire(inv, aveugle));
    }

    /**
     * Import de la feuille de comptage remplie : les quantités (et motifs) renseignés sont enregistrés ; les
     * lignes vides sont ignorées ; les lignes invalides sont listées dans le bilan sans bloquer les autres.
     */
    @PreAuthorize(READ)
    @PostMapping(value = "/{id}/feuille-comptage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportComptageResponse importerFeuilleComptage(@PathVariable UUID id,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(required = false) UUID centerId,
                                                          Principal principal) {
        CenterId center = center(centerId);
        Inventaire inv = useCase.get(center, id);
        inv.ensureEnCours();
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("IMPORT_UNREADABLE_FILE", "Le fichier n'a pas pu être lu.");
        }
        FeuilleComptageExcel.Lecture lecture = feuille.lire(content, inv.getReference());
        return ImportComptageResponse.from(useCase.importerComptage(center, id, lecture.lignes(), user(principal)),
                lecture.anomalies());
    }

    private CenterId center(UUID requested) {
        return centerAccessGuard.requireCenter(requested);
    }
}
