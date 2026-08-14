package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.UUID;

public interface SeanceBillingEligibilityPort {
    boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance);
}

