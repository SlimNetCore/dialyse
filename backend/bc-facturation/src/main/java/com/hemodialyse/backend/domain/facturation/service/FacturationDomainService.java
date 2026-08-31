package com.hemodialyse.backend.domain.facturation.service;

import com.hemodialyse.backend.domain.facturation.aggregate.FactureAggregate;
import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.facturation.entity.LigneFacture;
import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.facturation.specification.SeanceEligibleForFacturationSpecification;
import com.hemodialyse.backend.domain.facturation.valueobject.FactureNumberTemplate;
import com.hemodialyse.backend.domain.facturation.valueobject.FacturationPeriod;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.*;

public class FacturationDomainService implements FacturationUseCase {

    /**
     * Type de prestation par défaut pour la résolution TVA en hémodialyse.
     */
    private static final String TYPE_PRESTATION_HEMODIALYSE = "HEMODIALYSE";

    private final SeanceFacturationPort seancePort;
    private final FactureRepositoryPort factureRepository;
    private final FacturationSettingsRepositoryPort settingsRepository;
    private final TypeTvaRepositoryPort typeTvaRepository;
    private final SeanceEligibleForFacturationSpecification eligibleSpec = new SeanceEligibleForFacturationSpecification();

    public FacturationDomainService(SeanceFacturationPort seancePort,
                                    FactureRepositoryPort factureRepository,
                                    FacturationSettingsRepositoryPort settingsRepository,
                                    TypeTvaRepositoryPort typeTvaRepository) {
        this.seancePort = seancePort;
        this.factureRepository = factureRepository;
        this.settingsRepository = settingsRepository;
        this.typeTvaRepository = typeTvaRepository;
    }

    @Override
    public FacturationPreviewResult preview(FacturationPreviewQuery query) {
        FacturationPeriod period = toPeriod(query.month(), query.periodStart(), query.periodEnd());
        List<SeanceFacturationCandidate> candidates = seancePort.findEligibleSeances(query.centerId(), period).stream()
                .filter(eligibleSpec::isSatisfiedBy)
                .toList();

        ParametresFacturation settings = settingsRepository.findByCenterId(query.centerId());
        LocalDate billingDate = LocalDate.now();
        int currentSequence = factureRepository.currentInvoiceSequence(query.centerId(), billingDate);

        List<FacturationPreviewInvoice> invoices = buildPreviewInvoices(candidates,
                query.centerId(), billingDate, query.regroupementMultiForfait(), settings.codeFormat(), currentSequence);
        BigDecimal totalHt = invoices.stream().map(FacturationPreviewInvoice::totalHt)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTva = invoices.stream().map(FacturationPreviewInvoice::totalTva)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalTtc = invoices.stream().map(FacturationPreviewInvoice::totalTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        return new FacturationPreviewResult(
                query.centerId().value(),
                OffsetDateTime.now(),
                period.startDate(),
                period.endDate(),
                invoices.size(),
                totalHt,
                totalTva,
                totalTtc,
                invoices
        );
    }

    @Override
    public FacturationPreviewResult excludeSeance(FacturationExcludeSeanceCommand command) {
        seancePort.markAsAbsent(command.centerId(), command.seanceId(), command.userId());
        return preview(new FacturationPreviewQuery(
                command.centerId(),
                command.month(),
                command.periodStart(),
                command.periodEnd(),
                command.regroupementMultiForfait()
        ));
    }

    @Override
    public FacturationPreviewResult updateSeanceForfait(FacturationUpdateSeanceForfaitCommand command) {
        seancePort.overrideForfait(command.centerId(), command.seanceId(), command.forfaitId(), command.userId());
        return preview(new FacturationPreviewQuery(
                command.centerId(),
                command.month(),
                command.periodStart(),
                command.periodEnd(),
                command.regroupementMultiForfait()
        ));
    }

    @Override
    public FacturationValidationResult validate(FacturationValidateCommand command) {
        FacturationPreviewResult preview = preview(new FacturationPreviewQuery(
                command.centerId(),
                command.month(),
                command.periodStart(),
                command.periodEnd(),
                command.regroupementMultiForfait()
        ));
        if (preview.invoices().isEmpty()) {
            return new FacturationValidationResult(0, 0);
        }

        ParametresFacturation settings = settingsRepository.findByCenterId(command.centerId());
        BigDecimal tvaRateForInvoice = resoudreTauxTva(command.centerId().value(), LocalDate.now());
        List<FactureAggregate> factures = new ArrayList<>();
        Map<UUID, UUID> seanceToFactureId = new LinkedHashMap<>();

        for (FacturationPreviewInvoice invoice : preview.invoices()) {
            UUID factureId = UUID.randomUUID();
            String numero = factureRepository.nextInvoiceNumber(command.centerId(), settings.codeFormat(), LocalDate.now());
            List<LigneFacture> lignes = invoice.lines().stream()
                    .map(line -> new LigneFacture(line.forfaitId(), line.forfaitLabel(), line.unitPriceHt(), line.seanceCount()))
                    .toList();

            SeanceFacturationCandidate anchor = findAnchorCandidate(invoice, preview, command);
            FactureAggregate facture = new FactureAggregate(
                    factureId,
                    command.centerId().value(),
                    invoice.patientId(),
                    numero,
                    invoice.patientCode(),
                    invoice.patientFullName(),
                    invoice.patientStatusSnapshot(),
                    anchor.numeroImmatriculation(),
                    anchor.centrePayeurId(),
                    anchor.agenceId(),
                    new FacturationPeriod(preview.periodStart(), preview.periodEnd()),
                    LocalDate.now(),
                    tvaRateForInvoice,
                    lignes
            );
            factures.add(facture);
            for (UUID seanceId : invoice.seanceIds()) {
                seanceToFactureId.put(seanceId, factureId);
            }
        }

        factureRepository.saveAll(factures);
        seancePort.markAsBilled(command.centerId(), seanceToFactureId);

        return new FacturationValidationResult(factures.size(), seanceToFactureId.size());
    }

    @Override
    public FacturationDashboardResult dashboard(FacturationDashboardQuery query) {
        if (query.month() == null) {
            throw new IllegalArgumentException("Le mois est obligatoire");
        }
        return seancePort.loadDashboard(query.centerId(), query.month());
    }

    @Override
    public ParametresFacturation getSettings(FacturationSettingsQuery query) {
        return settingsRepository.findByCenterId(query.centerId());
    }

    @Override
    public ParametresFacturation updateSettings(FacturationSettingsCommand command) {
        ParametresFacturation updated = new ParametresFacturation(
                command.codeFormat(),
                command.regroupementMultiForfait(),
                OffsetDateTime.now()
        );
        return settingsRepository.save(command.centerId(), command.userId(), updated);
    }

    /**
     * Résout le taux TVA à appliquer à la date donnée pour un centre.
     * Source unique : TypeTva actif en base. Retourne 0 si aucun TypeTva actif configuré.
     */
    private BigDecimal resoudreTauxTva(UUID centerId, LocalDate date) {
        return typeTvaRepository
                .findActiveAt(centerId, TYPE_PRESTATION_HEMODIALYSE, date)
                .map(TypeTVA::taux)
                .orElse(BigDecimal.ZERO);
    }

    private List<FacturationPreviewInvoice> buildPreviewInvoices(List<SeanceFacturationCandidate> candidates,
                                                                 CenterId centerId,
                                                                 LocalDate dateFacturation,
                                                                 boolean regroupementMultiForfait,
                                                                 String codeFormat,
                                                                 int currentSequence) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Résoudre le taux TVA actif une seule fois pour toute la campagne
        BigDecimal tvaRate = resoudreTauxTva(centerId.value(), dateFacturation);
        BigDecimal tvaRatio = tvaRate.movePointLeft(2).setScale(6, RoundingMode.HALF_UP);
        FactureNumberTemplate factureNumberTemplate = new FactureNumberTemplate(codeFormat);
        int simulatedSequence = currentSequence;

        Map<String, List<SeanceFacturationCandidate>> grouped = new LinkedHashMap<>();
        for (SeanceFacturationCandidate c : candidates) {
            String key = regroupementMultiForfait
                    ? c.patientId().toString()
                    : c.patientId() + "::" + (c.forfaitId() == null ? "NO_FORFAIT" : c.forfaitId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        List<FacturationPreviewInvoice> invoices = new ArrayList<>();
        for (Map.Entry<String, List<SeanceFacturationCandidate>> entry : grouped.entrySet()) {
            List<SeanceFacturationCandidate> rows = entry.getValue();
            SeanceFacturationCandidate head = rows.getFirst();

            Map<String, List<SeanceFacturationCandidate>> byForfait = new LinkedHashMap<>();
            for (SeanceFacturationCandidate row : rows) {
                String key = (row.forfaitId() == null ? "NO_FORFAIT" : row.forfaitId().toString()) + "::" + row.forfaitLabel();
                byForfait.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }

            List<FacturationPreviewLine> lines = new ArrayList<>();
            for (List<SeanceFacturationCandidate> lineRows : byForfait.values()) {
                SeanceFacturationCandidate first = lineRows.getFirst();
                BigDecimal unitPrice = first.forfaitPrixHt() == null ? BigDecimal.ZERO : first.forfaitPrixHt();
                int count = lineRows.size();
                BigDecimal lineHt = unitPrice.multiply(BigDecimal.valueOf(count)).setScale(2, RoundingMode.HALF_UP);
                lines.add(new FacturationPreviewLine(first.forfaitId(), first.forfaitLabel(), unitPrice, count, lineHt));
            }

            BigDecimal totalHt = lines.stream().map(FacturationPreviewLine::lineHt)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalTva = totalHt.multiply(tvaRatio).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalTtc = totalHt.add(totalTva).setScale(2, RoundingMode.HALF_UP);
            simulatedSequence++;
            String numeroFacture = factureNumberTemplate.format(centerId, dateFacturation, simulatedSequence);

            invoices.add(new FacturationPreviewInvoice(
                    entry.getKey(),
                    numeroFacture,
                    head.patientId(),
                    nullToDash(head.patientCode()),
                    (nullToEmpty(head.patientNom()) + " " + nullToEmpty(head.patientPrenom())).trim(),
                    nullToDash(head.patientStatus()),
                    totalHt,
                    totalTva,
                    totalTtc,
                    lines,
                    rows.stream().map(SeanceFacturationCandidate::seanceId).toList(),
                    rows.stream()
                            .map(row -> new FacturationPreviewSeance(
                                    row.seanceId(),
                                    row.seanceDate(),
                                    nullToDash(row.seanceStatus()),
                                    row.forfaitId(),
                                    nullToDash(row.forfaitLabel()),
                                    row.forfaitPrixHt() == null ? BigDecimal.ZERO : row.forfaitPrixHt().setScale(2, RoundingMode.HALF_UP)
                            ))
                            .toList()
            ));
        }

        return invoices;
    }

    private SeanceFacturationCandidate findAnchorCandidate(FacturationPreviewInvoice invoice,
                                                           FacturationPreviewResult preview,
                                                           FacturationValidateCommand command) {
        FacturationPeriod period = new FacturationPeriod(preview.periodStart(), preview.periodEnd());
        return seancePort.findEligibleSeances(command.centerId(), period).stream()
                .filter(eligibleSpec::isSatisfiedBy)
                .filter(c -> c.patientId().equals(invoice.patientId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune seance eligibile pour le patient " + invoice.patientId()));
    }

    private FacturationPeriod toPeriod(YearMonth month, LocalDate start, LocalDate end) {
        if (month != null) {
            return FacturationPeriod.ofMonth(month);
        }
        return new FacturationPeriod(start, end);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
