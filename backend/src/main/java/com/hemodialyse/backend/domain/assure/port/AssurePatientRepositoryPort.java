package com.hemodialyse.backend.domain.assure.port;

import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssurePatientRepositoryPort {
    AssurePatientAssignment save(AssurePatientAssignment assignment);
    void clearPrimary(CenterId centerId, UUID patientId);
    Optional<AssurePatientAssignment> findPrimary(CenterId centerId, UUID patientId);
    List<AssurePatientAssignment> findHistory(CenterId centerId, UUID patientId);
}
