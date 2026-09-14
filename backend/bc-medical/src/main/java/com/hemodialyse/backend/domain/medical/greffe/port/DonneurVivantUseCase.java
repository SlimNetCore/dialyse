package com.hemodialyse.backend.domain.medical.greffe.port;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.DonneurVivant;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.LienParenteDonneur;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.ResultatCrossmatch;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanDonneur;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DonneurVivantUseCase {

    List<DonneurVivant> listByPatient(CenterId centerId, UUID patientId);

    DonneurVivant create(CenterId centerId, UUID patientId, String nom, String prenom, LocalDate dateNaissance,
                         LienParenteDonneur lienParente, String telephone, String groupeSanguin, String typageHla);

    DonneurVivant update(CenterId centerId, UUID patientId, UUID donneurId, String nom, String prenom,
                         LocalDate dateNaissance, LienParenteDonneur lienParente, String telephone,
                         String groupeSanguin, String typageHla, StatutBilanDonneur statutBilan,
                         ResultatCrossmatch crossmatchResultat, LocalDate dateCrossmatch, String bilanRealise,
                         String contreIndications, String decisionFinale, LocalDate dateDecision);

    void delete(CenterId centerId, UUID patientId, UUID donneurId);
}
