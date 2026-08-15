package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.*;
import com.hemodialyse.backend.domain.shared.PagedResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/comptabilite")
public class ComptabiliteRestController {

    private final ComptabiliteUseCase useCase;
    private final JdbcTemplate jdbcTemplate;

    public ComptabiliteRestController(ComptabiliteUseCase useCase,
                                      JdbcTemplate jdbcTemplate) {
        this.useCase = useCase;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── Consultation écritures ───────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/ecritures")
    public ResponseEntity<?> search(
            @RequestParam UUID centerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String journalCode,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        JournalCode jc = journalCode != null ? JournalCode.valueOf(journalCode) : null;
        StatutEcriture st = statut != null ? StatutEcriture.valueOf(statut) : null;
        PagedResult<EcritureComptable> result = useCase.search(new ComptabiliteUseCase.SearchEcrituresQuery(
                centerId, LocalDate.parse(from), LocalDate.parse(to), jc, st, page, size));
        return ResponseEntity.ok(Map.of(
                "items", result.items().stream().map(this::toResponse).toList(),
                "total", result.total(),
                "page", result.page(),
                "size", result.size()
        ));
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/ecritures/{id}/valider")
    public ResponseEntity<?> valider(@PathVariable UUID id, @RequestParam UUID centerId) {
        EcritureComptable ecriture = useCase.valider(new ComptabiliteUseCase.ValiderEcritureCommand(centerId, id));
        return ResponseEntity.ok(toResponse(ecriture));
    }

    // ─── Export ──────────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/ecritures/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam UUID centerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "VE") String journalCode,
            @RequestParam(defaultValue = "SAGE100") String format) {

        byte[] content = useCase.exporter(new ComptabiliteUseCase.ExporterJournalQuery(
                centerId, LocalDate.parse(from), LocalDate.parse(to),
                JournalCode.valueOf(journalCode), format));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("journal-" + journalCode + "-" + from + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }

    // ─── Clôture de période ───────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/periodes/cloturer")
    public ResponseEntity<?> cloturerPeriode(@RequestBody @Valid CloturerPeriodeRequest req) {
        useCase.cloturerPeriode(new ComptabiliteUseCase.CloturerPeriodeCommand(
                req.centerId(), YearMonth.of(req.annee(), req.mois()), req.userId()));
        return ResponseEntity.ok(Map.of("closed", true, "periode", req.annee() + "-" + req.mois()));
    }

    // ─── Mapping comptable ────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/mapping")
    public ResponseEntity<?> getMapping(@RequestParam UUID centerId) {
        return ResponseEntity.ok(toMappingResponse(useCase.getMappingComptable(centerId)));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/mapping")
    public ResponseEntity<?> saveMapping(@RequestBody @Valid UpdateMappingRequest req) {
        MappingComptable mapping = new MappingComptable(
                req.centerId(), req.compteVentes(), req.compteClientPatient(),
                req.compteClientCnas(), req.compteClientCasnos(), req.compteClientMutuelle(),
                req.compteClientAutre(), req.compteBanque(), req.compteCaisse(), req.compteTVACollectee());
        MappingComptable saved = useCase.saveMappingComptable(mapping);
        return ResponseEntity.ok(toMappingResponse(saved));
    }

    // ─── Règles TVA ───────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/regles-tva")
    public ResponseEntity<?> getRegles(@RequestParam UUID centerId) {
        return ResponseEntity.ok(useCase.getReglesTV(centerId).stream().map(this::toRegleResponse).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/regles-tva")
    public ResponseEntity<?> saveRegle(@RequestParam UUID centerId,
                                       @RequestBody @Valid SaveRegleTVARequest req) {
        RegleTVA regle = new RegleTVA(req.typePrestation(), req.tauxApplique(), req.exonere(),
                req.dateDebutValidite(), req.dateFinValidite(), req.texteReference());
        useCase.saveRegleTVA(centerId, regle);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    // ─── Backfill écritures (factures + règlements déjà existants) ─────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild(@RequestBody @Valid RebuildEcrituresRequest req) {
        LocalDate from = req.from();
        LocalDate to = req.to();

        List<FactureBackfillRow> factures = jdbcTemplate.query(
                """
                        SELECT f.id, f.center_id, f.numero_facture, f.patient_id,
                               f.centre_payeur_id_snapshot, f.total_ht, f.total_tva, f.total_ttc,
                               f.date_facturation, COALESCE(f.patient_full_name, 'Facture') AS libelle
                        FROM factures f
                        WHERE f.center_id = ?
                          AND f.date_facturation BETWEEN ? AND ?
                        ORDER BY f.date_facturation, f.numero_facture
                        """,
                (rs, rowNum) -> new FactureBackfillRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("center_id", UUID.class),
                        rs.getString("numero_facture"),
                        rs.getObject("patient_id", UUID.class),
                        rs.getObject("centre_payeur_id_snapshot", UUID.class),
                        rs.getBigDecimal("total_ht"),
                        rs.getBigDecimal("total_tva"),
                        rs.getBigDecimal("total_ttc"),
                        rs.getObject("date_facturation", LocalDate.class),
                        rs.getString("libelle")
                ),
                req.centerId(), from, to
        );

        int generatedFactures = 0;
        for (FactureBackfillRow f : factures) {
            useCase.genererEcritureFacturation(new ComptabiliteUseCase.GenererEcritureFacturationCommand(
                    f.centerId(),
                    f.factureId(),
                    f.numeroFacture(),
                    f.patientId(),
                    f.centrePayeurId(),
                    "AUTRE",
                    f.totalHt() != null ? f.totalHt() : BigDecimal.ZERO,
                    f.totalTva() != null ? f.totalTva() : BigDecimal.ZERO,
                    f.totalTtc() != null ? f.totalTtc() : BigDecimal.ZERO,
                    f.dateFacturation(),
                    f.libelle()
            ));
            generatedFactures++;
        }

        List<ReglementBackfillRow> reglements = jdbcTemplate.query(
                """
                        SELECT fr.id, fr.facture_id, fr.center_id, fr.montant, fr.date_reglement,
                               COALESCE(fr.saisi_par, 'system') AS saisi_par
                        FROM facture_reglements fr
                        WHERE fr.center_id = ?
                          AND fr.date_reglement BETWEEN ? AND ?
                        ORDER BY fr.date_reglement, fr.id
                        """,
                (rs, rowNum) -> new ReglementBackfillRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("facture_id", UUID.class),
                        rs.getObject("center_id", UUID.class),
                        rs.getBigDecimal("montant"),
                        rs.getObject("date_reglement", LocalDate.class),
                        rs.getString("saisi_par")
                ),
                req.centerId(), from, to
        );

        int generatedReglements = 0;
        for (ReglementBackfillRow r : reglements) {
            useCase.genererEcritureReglement(new ComptabiliteUseCase.GenererEcritureReglementCommand(
                    r.centerId(),
                    r.factureId(),
                    r.paiementId(),
                    r.montant(),
                    r.dateReglement(),
                    "BANQUE",
                    "Règlement facture",
                    null,
                    "AUTRE"
            ));
            generatedReglements++;
        }

        return ResponseEntity.ok(Map.of(
                "facturesProcessed", generatedFactures,
                "reglementsProcessed", generatedReglements,
                "from", from,
                "to", to
        ));
    }

    // ─── Mappers DTO ─────────────────────────────────────────────────────────

    private Map<String, Object> toResponse(EcritureComptable e) {
        return Map.of(
                "id", e.getId(),
                "centerId", e.getCenterId(),
                "journalCode", e.getJournalCode().name(),
                "dateEcriture", e.getDateEcriture().toString(),
                "datePiece", e.getDatePiece().toString(),
                "numeroPiece", e.getNumeroPiece(),
                "libelle", e.getLibelle(),
                "statut", e.getStatut().name(),
                "totalDebit", e.totalDebit(),
                "lignes", e.getLignes().stream().map(this::toLigneResponse).toList()
        );
    }

    private Map<String, Object> toLigneResponse(LigneEcriture l) {
        return Map.of(
                "id", l.getId(),
                "compteSCF", l.getCompteSCF(),
                "libelleLigne", l.getLibelleLigne(),
                "montantDebit", l.getMontantDebit(),
                "montantCredit", l.getMontantCredit()
        );
    }

    private Map<String, Object> toMappingResponse(MappingComptable m) {
        return Map.of(
                "centerId", m.centerId(),
                "compteVentes", m.compteVentes(),
                "compteClientPatient", m.compteClientPatient(),
                "compteClientCnas", m.compteClientCnas(),
                "compteClientCasnos", m.compteClientCasnos(),
                "compteClientMutuelle", m.compteClientMutuelle(),
                "compteClientAutre", m.compteClientAutre(),
                "compteBanque", m.compteBanque(),
                "compteCaisse", m.compteCaisse(),
                "compteTVACollectee", m.compteTVACollectee() != null ? m.compteTVACollectee() : ""
        );
    }

    private Map<String, Object> toRegleResponse(RegleTVA r) {
        return Map.of(
                "typePrestation", r.typePrestation(),
                "tauxApplique", r.tauxApplique(),
                "exonere", r.exonere(),
                "dateDebutValidite", r.dateDebutValidite().toString(),
                "dateFinValidite", r.dateFinValidite() != null ? r.dateFinValidite().toString() : ""
        );
    }

    // ─── Request records ─────────────────────────────────────────────────────

    record CloturerPeriodeRequest(
            @NotNull UUID centerId,
            @Min(2000) @Max(3000) int annee,
            @Min(1) @Max(12) int mois,
            @NotBlank String userId) {
    }

    record UpdateMappingRequest(
            @NotNull UUID centerId,
            @NotBlank String compteVentes,
            @NotBlank String compteClientPatient,
            @NotBlank String compteClientCnas,
            @NotBlank String compteClientCasnos,
            @NotBlank String compteClientMutuelle,
            @NotBlank String compteClientAutre,
            @NotBlank String compteBanque,
            @NotBlank String compteCaisse,
            String compteTVACollectee) {
    }

    record SaveRegleTVARequest(
            @NotBlank String typePrestation,
            @NotNull @DecimalMin("0.00") BigDecimal tauxApplique,
            boolean exonere,
            @NotNull LocalDate dateDebutValidite,
            LocalDate dateFinValidite,
            String texteReference) {
    }

    record RebuildEcrituresRequest(
            @NotNull UUID centerId,
            @NotNull LocalDate from,
            @NotNull LocalDate to) {
    }

    private record FactureBackfillRow(
            UUID factureId,
            UUID centerId,
            String numeroFacture,
            UUID patientId,
            UUID centrePayeurId,
            BigDecimal totalHt,
            BigDecimal totalTva,
            BigDecimal totalTtc,
            LocalDate dateFacturation,
            String libelle) {
    }

    private record ReglementBackfillRow(
            UUID paiementId,
            UUID factureId,
            UUID centerId,
            BigDecimal montant,
            LocalDate dateReglement,
            String saisiPar) {
    }
}



