package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;

public interface FacturationUseCase {
    FacturationPreviewResult preview(FacturationPreviewQuery query);

    FacturationPreviewResult excludeSeance(FacturationExcludeSeanceCommand command);

    FacturationPreviewResult updateSeanceForfait(FacturationUpdateSeanceForfaitCommand command);

    FacturationValidationResult validate(FacturationValidateCommand command);

    FacturationDashboardResult dashboard(FacturationDashboardQuery query);

    ParametresFacturation getSettings(FacturationSettingsQuery query);

    ParametresFacturation updateSettings(FacturationSettingsCommand command);
}

