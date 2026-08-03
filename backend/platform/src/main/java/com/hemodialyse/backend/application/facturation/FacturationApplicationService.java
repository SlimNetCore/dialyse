package com.hemodialyse.backend.application.facturation;

import com.hemodialyse.backend.domain.facturation.port.*;
import com.hemodialyse.backend.domain.facturation.service.FacturationDomainService;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FacturationApplicationService implements FacturationUseCase {

    private final FacturationDomainService delegate;

    public FacturationApplicationService(SeanceFacturationPort seancePort,
                                         FactureRepositoryPort factureRepository,
                                         FacturationSettingsRepositoryPort settingsRepository) {
        this.delegate = new FacturationDomainService(seancePort, factureRepository, settingsRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public FacturationPreviewResult preview(FacturationPreviewQuery query) {
        return delegate.preview(query);
    }

    @Override
    public FacturationValidationResult validate(FacturationValidateCommand command) {
        return delegate.validate(command);
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

