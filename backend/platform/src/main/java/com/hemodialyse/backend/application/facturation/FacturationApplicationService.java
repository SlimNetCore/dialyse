package com.hemodialyse.backend.application.facturation;

import com.hemodialyse.backend.domain.facturation.event.FacturationValideeEvent;
import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.facturation.service.FacturationDomainService;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@Transactional
public class FacturationApplicationService implements FacturationUseCase {

    private final FacturationDomainService delegate;
    private final ApplicationEventPublisher eventPublisher;

    public FacturationApplicationService(SeanceFacturationPort seancePort,
                                         FactureRepositoryPort factureRepository,
                                         FacturationSettingsRepositoryPort settingsRepository,
                                         TypeTvaRepositoryPort typeTvaRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.delegate = new FacturationDomainService(seancePort, factureRepository, settingsRepository, typeTvaRepository);
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public FacturationPreviewResult preview(FacturationPreviewQuery query) {
        return delegate.preview(query);
    }

    @Override
    public FacturationPreviewResult excludeSeance(FacturationExcludeSeanceCommand command) {
        return delegate.excludeSeance(command);
    }

    @Override
    public FacturationPreviewResult updateSeanceForfait(FacturationUpdateSeanceForfaitCommand command) {
        return delegate.updateSeanceForfait(command);
    }

    @Override
    public FacturationValidationResult validate(FacturationValidateCommand command) {
        FacturationValidationResult result = delegate.validate(command);
        LocalDate periodStart = command.periodStart() != null
                ? command.periodStart()
                : (command.month() != null ? command.month().atDay(1) : LocalDate.now().withDayOfMonth(1));
        LocalDate periodEnd = command.periodEnd() != null
                ? command.periodEnd()
                : (command.month() != null ? command.month().atEndOfMonth() : LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        // Publie l'événement de domaine après la validation
        eventPublisher.publishEvent(new FacturationValideeEvent(
                command.centerId().value(),
                periodStart,
                periodEnd,
                result.createdInvoices(),
                result.billedSeances(),
                java.math.BigDecimal.ZERO, // totalTtc calculé par les listeners via la DB
                OffsetDateTime.now()
        ));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public FacturationDashboardResult dashboard(FacturationDashboardQuery query) {
        return delegate.dashboard(query);
    }

    @Override
    @Transactional(readOnly = true)
    public ParametresFacturation getSettings(FacturationSettingsQuery query) {
        return delegate.getSettings(query);
    }

    @Override
    public ParametresFacturation updateSettings(FacturationSettingsCommand command) {
        return delegate.updateSettings(command);
    }
}
