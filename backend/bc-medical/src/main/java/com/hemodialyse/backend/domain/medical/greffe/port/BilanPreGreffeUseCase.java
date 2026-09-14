package com.hemodialyse.backend.domain.medical.greffe.port;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.AvisRcp;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface BilanPreGreffeUseCase {

    /**
     * Récupère le bilan du patient, ou en ouvre un nouveau (statut {@code NON_DEBUTE}) s'il
     * n'existe pas encore — patron get-or-create, comme {@code DossierMedicalPatient}.
     */
    BilanPreGreffe getOrCreate(CenterId centerId, UUID patientId);

    BilanPreGreffe changerStatut(CenterId centerId, UUID patientId, StatutBilanGreffe statut);

    BilanPreGreffe mettreAJourBilanImmunologique(CenterId centerId, UUID patientId, String groupeSanguinConfirme,
                                                 String typageHla, BigDecimal praClasseI, BigDecimal praClasseII);

    BilanPreGreffe mettreAJourNotes(CenterId centerId, UUID patientId, String contreIndications,
                                    String conclusionNephrologue);

    BilanPreGreffe ajouterDecisionRcp(CenterId centerId, UUID patientId, LocalDate dateReunion, AvisRcp avis,
                                      String compteRendu, LocalDate prochaineDateRevue);
}
