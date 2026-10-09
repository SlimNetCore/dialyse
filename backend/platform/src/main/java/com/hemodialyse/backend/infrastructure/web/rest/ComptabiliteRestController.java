package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase.Synchronisation;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.JournauxUseCase;
import com.hemodialyse.backend.domain.comptabilite.valueobject.*;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
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
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comptabilité d'un centre : écritures, export, clôture, paramétrage (comptes, journaux) et comptabilisation du stock.
 * Le centre de chaque requête est confronté à celui de la session ({@link CenterAccessGuard}) : un administrateur ne
 * lit ni ne paramètre jamais la comptabilité d'un autre centre.
 */
@RestController
@RequestMapping("/api/v1/comptabilite")
public class ComptabiliteRestController {

    /**
     * Plus longue période qu'une demande de comptabilisation du stock peut couvrir.
     */
    static final int JOURS_SYNCHRONISATION_MAX = 366;

    private final ComptabiliteUseCase useCase;
    private final JournauxUseCase journaux;
    private final ComptabiliteStockUseCase comptabiliteStock;
    private final CenterAccessGuard centerAccessGuard;
    private final JdbcTemplate jdbcTemplate;

    public ComptabiliteRestController(ComptabiliteUseCase useCase,
                                      JournauxUseCase journaux,
                                      ComptabiliteStockUseCase comptabiliteStock,
                                      CenterAccessGuard centerAccessGuard,
                                      JdbcTemplate jdbcTemplate) {
        this.useCase = useCase;
        this.journaux = journaux;
        this.comptabiliteStock = comptabiliteStock;
        this.centerAccessGuard = centerAccessGuard;
        this.jdbcTemplate = jdbcTemplate;
    }

    private static String ou(String valeur, String defaut) {
        return valeur == null || valeur.isBlank() ? defaut : valeur.trim();
    }

    // ─── Consultation écritures ───────────────────────────────────────────────

    private static OperationComptable operationDe(String code) {
        try {
            return OperationComptable.valueOf(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Opération comptable inconnue : " + code);
        }
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    /**
     * Centre de la session ; refuse un centre demandé qui n'est pas le sien.
     */
    private UUID centre(UUID demande) {
        return centerAccessGuard.requireCenter(demande).value();
    }

    // ─── Export ──────────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/ecritures")
    public ResponseEntity<?> search(
            @RequestParam(required = false) UUID centerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String journalCode,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        JournalCode jc = journalCode != null && !journalCode.isBlank() ? JournalCode.de(journalCode) : null;
        StatutEcriture st = statut != null ? StatutEcriture.valueOf(statut) : null;
        PagedResult<EcritureComptable> result = useCase.search(new ComptabiliteUseCase.SearchEcrituresQuery(
                centre(centerId), LocalDate.parse(from), LocalDate.parse(to), jc, st, page, size));
        return ResponseEntity.ok(Map.of(
                "items", result.items().stream().map(this::toResponse).toList(),
                "total", result.total(),
                "page", result.page(),
                "size", result.size()
        ));
    }

    // ─── Clôture de période ───────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/ecritures/{id}/valider")
    public ResponseEntity<?> valider(@PathVariable UUID id, @RequestParam(required = false) UUID centerId) {
        EcritureComptable ecriture = useCase.valider(
                new ComptabiliteUseCase.ValiderEcritureCommand(centre(centerId), id));
        return ResponseEntity.ok(toResponse(ecriture));
    }

    // ─── Paramétrage : comptes et journal de chaque opération ────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/ecritures/export")
    public ResponseEntity<byte[]> exporter(
            @RequestParam(required = false) UUID centerId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "VE") String journalCode,
            @RequestParam(defaultValue = "SAGE100") String format) {

        JournalCode journal = JournalCode.de(journalCode);
        byte[] content = useCase.exporter(new ComptabiliteUseCase.ExporterJournalQuery(
                centre(centerId), LocalDate.parse(from), LocalDate.parse(to), journal, format));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("journal-" + journal + "-" + from + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/periodes/cloturer")
    public ResponseEntity<?> cloturerPeriode(@RequestBody @Valid CloturerPeriodeRequest req) {
        useCase.cloturerPeriode(new ComptabiliteUseCase.CloturerPeriodeCommand(
                centre(req.centerId()), YearMonth.of(req.annee(), req.mois()), req.userId()));
        return ResponseEntity.ok(Map.of("closed", true, "periode", req.annee() + "-" + req.mois()));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/mapping")
    public ResponseEntity<?> getMapping(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(toMappingResponse(useCase.getMappingComptable(centre(centerId))));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/mapping")
    public ResponseEntity<?> saveMapping(@RequestBody @Valid UpdateMappingRequest req) {
        UUID centerId = centre(req.centerId());
        MappingComptable actuel = useCase.getMappingComptable(centerId);
        // un client qui ne connaît pas encore le stock ou les journaux ne les remet pas aux valeurs par défaut
        ComptesStock stock = new ComptesStock(
                ou(req.compteStock(), actuel.stock().stock()),
                ou(req.compteConsommation(), actuel.stock().consommation()),
                ou(req.compteFacturesNonParvenues(), actuel.stock().facturesNonParvenues()),
                ou(req.compteBoniInventaire(), actuel.stock().boniInventaire()),
                ou(req.compteMaliInventaire(), actuel.stock().maliInventaire()));
        Map<OperationComptable, JournalCode> choix = new EnumMap<>(actuel.journaux());
        if (req.journaux() != null) {
            req.journaux().forEach((operation, code) -> choix.put(operationDe(operation), JournalCode.de(code)));
        }
        MappingComptable saved = useCase.saveMappingComptable(new MappingComptable(
                centerId, req.compteVentes(), req.compteClientPatient(), req.compteClientDefaut(),
                req.compteBanque(), req.compteCaisse(), req.compteTVACollectee(), stock, choix));
        return ResponseEntity.ok(toMappingResponse(saved));
    }

    // ─── Paramétrage : journaux ──────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE')")
    @GetMapping("/journaux")
    public ResponseEntity<PagedResult<JournalResponse>> journaux(@RequestParam(required = false) UUID centerId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        // paramétrage de quelques lignes par centre : la page est découpée ici
        List<Journal> tous = journaux.lister(centre(centerId));
        int taille = Math.max(1, Math.min(size, 100));
        int debut = Math.min(Math.max(0, page) * taille, tous.size());
        List<JournalResponse> items = tous.subList(debut, Math.min(debut + taille, tous.size())).stream()
                .map(JournalResponse::de).toList();
        return ResponseEntity.ok(PagedResult.of(items, tous.size(), Math.max(0, page), taille));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PutMapping("/journaux/{code}")
    public ResponseEntity<JournalResponse> enregistrerJournal(@PathVariable String code,
                                                              @RequestParam(required = false) UUID centerId,
                                                              @RequestBody @Valid JournalRequest req) {
        Journal journal = journaux.enregistrer(centre(centerId),
                new Journal(JournalCode.de(code), req.libelle(), req.actif()));
        return ResponseEntity.ok(JournalResponse.de(journal));
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @DeleteMapping("/journaux/{code}")
    public ResponseEntity<Void> supprimerJournal(@PathVariable String code,
                                                 @RequestParam(required = false) UUID centerId) {
        journaux.supprimer(centre(centerId), JournalCode.de(code));
        return ResponseEntity.noContent().build();
    }

    // ─── Comptabilisation du stock ───────────────────────────────────────────

    /**
     * Met la comptabilité en accord avec le stock sur une période (rejouable sans doublon).
     */
    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/stock/synchroniser")
    public ResponseEntity<Synchronisation> synchroniserStock(@RequestBody @Valid RebuildEcrituresRequest req) {
        if (req.to().isBefore(req.from()) || req.from().plusDays(JOURS_SYNCHRONISATION_MAX).isBefore(req.to())) {
            throw new IllegalArgumentException("La période à comptabiliser doit couvrir de 1 à "
                    + JOURS_SYNCHRONISATION_MAX + " jours");
        }
        return ResponseEntity.ok(comptabiliteStock.synchroniser(centre(req.centerId()), req.from(), req.to(),
                LocalDate.now(ZoneOffset.UTC)));
    }

    // ─── Règles TVA ───────────────────────────────────────────────────────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("/regles-tva")
    public ResponseEntity<?> getRegles(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(useCase.getReglesTV(centre(centerId)).stream().map(this::toRegleResponse).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/regles-tva")
    public ResponseEntity<?> saveRegle(@RequestParam(required = false) UUID centerId,
                                       @RequestBody @Valid SaveRegleTVARequest req) {
        RegleTVA regle = new RegleTVA(req.typePrestation(), req.tauxApplique(), req.exonere(),
                req.dateDebutValidite(), req.dateFinValidite(), req.texteReference());
        useCase.saveRegleTVA(centre(centerId), regle);
        return ResponseEntity.ok(Map.of("saved", true));
    }

    // ─── Backfill écritures (factures + règlements déjà existants) ─────────

    @PreAuthorize("hasAnyRole('ADMIN')")
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild(@RequestBody @Valid RebuildEcrituresRequest req) {
        LocalDate from = req.from();
        LocalDate to = req.to();
        UUID centerId = centre(req.centerId());

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
                centerId, from, to
        );

        int generatedFactures = 0;
        for (FactureBackfillRow f : factures) {
            useCase.genererEcritureFacturation(new ComptabiliteUseCase.GenererEcritureFacturationCommand(
                    f.centerId(),
                    f.factureId(),
                    f.numeroFacture(),
                    f.patientId(),
                    f.centrePayeurId(),
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
                centerId, from, to
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
                    null
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
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("id", e.getId());
        reponse.put("centerId", e.getCenterId());
        reponse.put("journalCode", e.getJournalCode().valeur());
        reponse.put("dateEcriture", e.getDateEcriture().toString());
        reponse.put("datePiece", e.getDatePiece().toString());
        reponse.put("numeroPiece", e.getNumeroPiece());
        reponse.put("libelle", e.getLibelle());
        reponse.put("statut", e.getStatut().name());
        reponse.put("totalDebit", e.totalDebit());
        // pièce saisie à partir d'un modèle (elle seule peut être extournée à la main)
        reponse.put("saisie", e.estSaisie());
        reponse.put("lignes", e.getLignes().stream().map(this::toLigneResponse).toList());
        return reponse;
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

    private MappingResponse toMappingResponse(MappingComptable m) {
        Map<String, String> choix = new LinkedHashMap<>();
        for (OperationComptable operation : OperationComptable.values()) {
            choix.put(operation.name(), m.journalDe(operation).valeur());
        }
        return new MappingResponse(m.centerId(), m.compteVentes(), m.compteClientPatient(), m.compteClientDefaut(),
                m.compteBanque(), m.compteCaisse(), m.compteTVACollectee() != null ? m.compteTVACollectee() : "",
                m.stock().stock(), m.stock().consommation(), m.stock().facturesNonParvenues(),
                m.stock().boniInventaire(), m.stock().maliInventaire(), choix);
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
            UUID centerId,
            @Min(2000) @Max(3000) int annee,
            @Min(1) @Max(12) int mois,
            @NotBlank String userId) {
    }

    /**
     * @param journaux journal de chaque opération (clé = {@link OperationComptable}) ; une opération absente garde le sien
     */
    record UpdateMappingRequest(
            UUID centerId,
            @NotBlank @Size(max = 20) String compteVentes,
            @NotBlank @Size(max = 20) String compteClientPatient,
            @NotBlank @Size(max = 20) String compteClientDefaut,
            @NotBlank @Size(max = 20) String compteBanque,
            @NotBlank @Size(max = 20) String compteCaisse,
            @Size(max = 20) String compteTVACollectee,
            @Size(max = 20) String compteStock,
            @Size(max = 20) String compteConsommation,
            @Size(max = 20) String compteFacturesNonParvenues,
            @Size(max = 20) String compteBoniInventaire,
            @Size(max = 20) String compteMaliInventaire,
            Map<String, String> journaux) {
    }

    record MappingResponse(
            UUID centerId,
            String compteVentes,
            String compteClientPatient,
            String compteClientDefaut,
            String compteBanque,
            String compteCaisse,
            String compteTVACollectee,
            String compteStock,
            String compteConsommation,
            String compteFacturesNonParvenues,
            String compteBoniInventaire,
            String compteMaliInventaire,
            Map<String, String> journaux) {
    }

    record JournalRequest(@NotBlank @Size(max = Journal.LIBELLE_MAX) String libelle, boolean actif) {
    }

    record JournalResponse(String code, String libelle, boolean actif) {
        static JournalResponse de(Journal journal) {
            return new JournalResponse(journal.code().valeur(), journal.libelle(), journal.actif());
        }
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
            UUID centerId,
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



