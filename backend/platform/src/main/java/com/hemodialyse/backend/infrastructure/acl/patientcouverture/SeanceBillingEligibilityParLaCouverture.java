package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.domain.seance.port.SeanceBillingEligibilityPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL de la Séance vers le langage publié de Patient et couverture ({@code patientcouverture.v1}, PC5) : l'éligibilité d'une
 * séance à la facturation (PEC porteuse d'un forfait couvrant le jour, validée, et attestation couvrant le jour, même pour un
 * vacancier). Les trois étages et leur ordre sont ceux de l'ancien {@code SeanceBillingEligibilityJdbcAdapter} (supprimé), repris
 * à l'identique par le port publié. ARGENT, reproduit, À DÉCIDER AVEC LES MÉTIERS (KB-PAT-25, 46) : le port
 * {@link SeanceBillingEligibilityPort} reste la règle de production de la création de séance.
 */
@Component
public class SeanceBillingEligibilityParLaCouverture implements SeanceBillingEligibilityPort {

    private final CouvertureDesPatients couverture;

    public SeanceBillingEligibilityParLaCouverture(CouvertureDesPatients couverture) {
        this.couverture = couverture;
    }

    @Override
    public boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return causeNonFacturable(centerId, patientId, dateSeance).isEmpty();
    }

    @Override
    public Optional<CauseNonFacturable> causeNonFacturable(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return couverture.causeDeNonFacturation(centerId.value(), patientId, dateSeance).map(cause -> switch (cause) {
            case AUCUNE_PEC_COUVRANTE -> CauseNonFacturable.PRISE_EN_CHARGE;
            case PEC_NON_VALIDEE -> CauseNonFacturable.PRISE_EN_CHARGE_NON_VALIDEE;
            case ATTESTATION_ABSENTE -> CauseNonFacturable.ATTESTATION_DROITS;
        });
    }
}
