package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import com.hemodialyse.backend.domain.stock.port.InventaireUseCase;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import com.hemodialyse.backend.infrastructure.web.dto.response.PagedResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.AjoutLigneRequest;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.ComptageRequest;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.EtatResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.InventaireResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.InventaireResumeResponse;
import com.hemodialyse.backend.infrastructure.web.dto.stock.InventaireDtos.OuvrirRequest;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Inventaire de stock : ouverture (gel des mouvements du centre), comptage, clôture (stock de départ) ou
 * annulation, feuille de comptage imprimable.
 * <p>
 * Consultation et comptage : ADMIN, PHARMACIEN, INFIRMIER. Ouverture, clôture, annulation : ADMIN, PHARMACIEN.
 */
@RestController
@RequestMapping("/api/v1/stock/inventaires")
public class InventaireRestController {

    private static final String READ = "hasAnyRole('ADMIN','PHARMACIEN','INFIRMIER')";
    private static final String MANAGE = "hasAnyRole('ADMIN','PHARMACIEN')";
    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InventaireUseCase useCase;
    private final CenterAccessGuard centerAccessGuard;

    public InventaireRestController(InventaireUseCase useCase, CenterAccessGuard centerAccessGuard) {
        this.useCase = useCase;
        this.centerAccessGuard = centerAccessGuard;
    }

    private static byte[] countSheet(Inventaire inv, boolean aveugle) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Sheet sheet = wb.createSheet("Comptage");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Inventaire " + inv.getReference() + " du " + inv.getDateInventaire().format(FR));
            title.getCell(0).setCellStyle(bold);
            String[] headers = aveugle
                    ? new String[]{"Code", "Article", "Unité", "N° de lot", "Péremption", "Quantité comptée", "Compté par"}
                    : new String[]{"Code", "Article", "Unité", "N° de lot", "Péremption", "Théorique", "Quantité comptée", "Compté par"};
            Row header = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                header.getCell(i).setCellStyle(bold);
                sheet.setColumnWidth(i, (i == 1 ? 40 : 16) * 256);
            }
            int r = 3;
            for (LigneInventaire l : inv.getLignes()) {
                Row row = sheet.createRow(r++);
                int c = 0;
                row.createCell(c++).setCellValue(nz(l.getArticleCode()));
                row.createCell(c++).setCellValue(nz(l.getArticleLibelle()));
                row.createCell(c++).setCellValue(nz(l.getUnite()));
                row.createCell(c++).setCellValue(nz(l.getNumeroLot()));
                row.createCell(c++).setCellValue(l.getDatePeremption() != null ? l.getDatePeremption().format(FR) : "");
                if (!aveugle) row.createCell(c++).setCellValue(l.getQuantiteTheorique().doubleValue());
                row.createCell(c++).setCellValue("");
                row.createCell(c).setCellValue("");
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nz(String value) {
        return value != null ? value : "";
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
     * de recopier le stock informatique au lieu de compter.
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
        return ResponseEntity.ok().headers(headers).body(countSheet(inv, aveugle));
    }

    private CenterId center(UUID requested) {
        return centerAccessGuard.requireCenter(requested);
    }
}

