package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.request.RegisterFacturePaymentRequest;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reglements")
public class ReglementRestController {

    private static final String[] CSV_HEADERS = {
            "N° Facture", "N° Assurance", "Nom", "Prénom", "Caisse", "Agence",
            "Centre payeur", "Date facturation", "Montant facturé", "Montant réglé",
            "Reste / trop-perçu", "Solde type", "État"
    };
    private final ReglementUseCase useCase;

    public ReglementRestController(ReglementUseCase useCase) {
        this.useCase = useCase;
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping
    public ResponseEntity<?> search(@RequestParam UUID centerId,
                                    @RequestParam int year,
                                    @RequestParam(required = false) Integer month,
                                    @RequestParam(required = false) UUID caisseId,
                                    @RequestParam(required = false) UUID agenceId,
                                    @RequestParam(required = false) UUID centrePayeurId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(useCase.search(new ReglementUseCase.ReglementSearchQuery(
                CenterId.of(centerId),
                year,
                month,
                caisseId,
                agenceId,
                centrePayeurId,
                page,
                size
        )));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam UUID centerId,
                                       @RequestParam int year,
                                       @RequestParam(required = false) Integer month,
                                       @RequestParam(required = false) UUID caisseId,
                                       @RequestParam(required = false) UUID agenceId,
                                       @RequestParam(required = false) UUID centrePayeurId) {
        return ResponseEntity.ok(useCase.dashboard(new ReglementUseCase.ReglementDashboardQuery(
                CenterId.of(centerId),
                year,
                month,
                caisseId,
                agenceId,
                centrePayeurId
        )));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @PostMapping("/{factureId}/paiements")
    public ResponseEntity<?> registerPayment(@PathVariable UUID factureId,
                                             @RequestBody @Valid RegisterFacturePaymentRequest request) {
        return ResponseEntity.ok(useCase.registerPayment(new ReglementUseCase.RegisterFacturePaymentCommand(
                CenterId.of(request.centerId()),
                factureId,
                request.montant(),
                request.dateReglement(),
                request.userId()
        )));
    }

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/{factureId}/paiements")
    public ResponseEntity<?> getPaymentHistory(@PathVariable UUID factureId,
                                               @RequestParam UUID centerId) {
        List<ReglementUseCase.FacturePaymentItem> history = useCase.getPaymentHistory(
                new ReglementUseCase.GetPaymentHistoryQuery(CenterId.of(centerId), factureId)
        );
        return ResponseEntity.ok(history);
    }

    // ─── Private helpers ────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam UUID centerId,
                                         @RequestParam int year,
                                         @RequestParam(required = false) Integer month,
                                         @RequestParam(required = false) UUID caisseId,
                                         @RequestParam(required = false) UUID agenceId,
                                         @RequestParam(required = false) UUID centrePayeurId,
                                         @RequestParam(defaultValue = "excel") String format) throws IOException {
        ReglementUseCase.ReglementSearchQuery query = new ReglementUseCase.ReglementSearchQuery(
                CenterId.of(centerId), year, month, caisseId, agenceId, centrePayeurId, 0, Integer.MAX_VALUE
        );
        List<ReglementUseCase.ReglementFactureListItem> items = useCase.exportList(query);

        if ("csv".equalsIgnoreCase(format)) {
            byte[] csv = buildCsv(items);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("reglements-" + year + ".csv").build().toString())
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .body(csv);
        }
        byte[] xlsx = buildExcel(items);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("reglements-" + year + ".xlsx").build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private byte[] buildCsv(List<ReglementUseCase.ReglementFactureListItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(";", CSV_HEADERS)).append('\n');
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (ReglementUseCase.ReglementFactureListItem item : items) {
            sb.append(item.numeroFacture()).append(';')
                    .append(item.numeroAssurance()).append(';')
                    .append(item.patientNom()).append(';')
                    .append(item.patientPrenom()).append(';')
                    .append(item.caisse()).append(';')
                    .append(item.agence()).append(';')
                    .append(item.centrePayeur()).append(';')
                    .append(item.dateFacturation() != null ? item.dateFacturation().format(fmt) : "").append(';')
                    .append(item.montantFacture()).append(';')
                    .append(item.montantRegle()).append(';')
                    .append(item.soldeType() == ReglementUseCase.FactureSoldeType.TROP_PERCU ? item.tropPercu().negate() : item.reste()).append(';')
                    .append(item.soldeType()).append(';')
                    .append(item.etat()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildExcel(List<ReglementUseCase.ReglementFactureListItem> items) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Règlements");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < CSV_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(CSV_HEADERS[i]);
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            int rowIdx = 1;
            for (ReglementUseCase.ReglementFactureListItem item : items) {
                Row row = sheet.createRow(rowIdx++);
                setCellStr(row, 0, item.numeroFacture());
                setCellStr(row, 1, item.numeroAssurance());
                setCellStr(row, 2, item.patientNom());
                setCellStr(row, 3, item.patientPrenom());
                setCellStr(row, 4, item.caisse());
                setCellStr(row, 5, item.agence());
                setCellStr(row, 6, item.centrePayeur());
                setCellStr(row, 7, item.dateFacturation() != null ? item.dateFacturation().format(fmt) : "");
                row.createCell(8).setCellValue(item.montantFacture() != null ? item.montantFacture().doubleValue() : 0d);
                row.createCell(9).setCellValue(item.montantRegle() != null ? item.montantRegle().doubleValue() : 0d);
                double solde = item.soldeType() == ReglementUseCase.FactureSoldeType.TROP_PERCU
                        ? (item.tropPercu() != null ? item.tropPercu().negate().doubleValue() : 0d)
                        : (item.reste() != null ? item.reste().doubleValue() : 0d);
                row.createCell(10).setCellValue(solde);
                setCellStr(row, 11, item.soldeType() != null ? item.soldeType().name() : "");
                setCellStr(row, 12, item.etat() != null ? item.etat().name() : "");
            }
            for (int i = 0; i < CSV_HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void setCellStr(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
    }
}
