package com.hemodialyse.backend.domain.seance.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SeanceBillingEligibilityPort {

    /**
     * @return la cause de non-facturabilité, ou vide si le patient est facturable à la date donnée
     */
    default Optional<CauseNonFacturable> causeNonFacturable(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return isPatientBillableAt(centerId, patientId, dateSeance)
                ? Optional.empty() : Optional.of(CauseNonFacturable.PRISE_EN_CHARGE);
    }

    boolean isPatientBillableAt(CenterId centerId, UUID patientId, LocalDate dateSeance);

    /**
     * Pourquoi un patient n'est pas facturable à une date : permet d'indiquer à l'utilisateur ce qui manque
     * réellement (prise en charge ou attestation de droits) plutôt qu'un message générique.
     */
    enum CauseNonFacturable {
        /**
         * Aucune prise en charge, avec forfait, ne couvre la date (période effective ou demandée).
         */
        PRISE_EN_CHARGE,
        /**
         * Une prise en charge couvre la date mais n'est pas au statut « validée ».
         */
        PRISE_EN_CHARGE_NON_VALIDEE,
        /**
         * Une prise en charge valide existe mais aucune attestation de droits ne couvre la date.
         */
        ATTESTATION_DROITS
    }
}
