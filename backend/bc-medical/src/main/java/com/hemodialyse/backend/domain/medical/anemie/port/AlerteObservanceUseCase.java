package com.hemodialyse.backend.domain.medical.anemie.port;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeAlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AlerteObservanceUseCase {

    List<AlerteObservance> listByPatient(CenterId centerId, UUID patientId);

    AlerteObservance resoudre(CenterId centerId, UUID patientId, UUID alerteId);

    /**
     * Appelé par {@code ObservancePrescriptionScheduler} : ouvre une nouvelle alerte non résolue si
     * aucune n'est déjà ouverte pour ce patient/type/nature, sinon ne fait rien (pas de doublon).
     */
    void signalerNonConformite(CenterId centerId, UUID patientId, TypeTraitementAnemie typeTraitement,
                               TypeAlerteObservance type, LocalDate periodeDebut, LocalDate periodeFin,
                               int dosesAttendues, int dosesAdministrees, String message);

    /**
     * Appelé par le job planifié quand l'observance redevient conforme : résout silencieusement
     * l'alerte ouverte pour ce patient/type/nature, s'il y en a une. N'est utilisé que pour
     * {@link TypeAlerteObservance#RAPPEL_ECHEANCE} — un {@code RETARD_CONSTATE} porte sur une
     * période close et ne peut pas redevenir conforme après coup ; il attend une résolution
     * manuelle du médecin.
     */
    void resoudreSiConforme(CenterId centerId, UUID patientId, TypeTraitementAnemie typeTraitement,
                            TypeAlerteObservance type);
}
