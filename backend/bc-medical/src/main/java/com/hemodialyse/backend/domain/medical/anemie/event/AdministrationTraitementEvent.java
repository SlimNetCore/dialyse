package com.hemodialyse.backend.domain.medical.anemie.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface AdministrationTraitementEvent {

    UUID administrationId();

    OffsetDateTime at();

    record TraitementAnemieAdministre(UUID administrationId, UUID patientId, OffsetDateTime at)
            implements AdministrationTraitementEvent {
    }

    /**
     * L'administration n'a pas eu lieu comme prévu — signal de suivi clinique, pas une erreur technique.
     */
    record EcartPrescriptionAdministration(UUID administrationId, UUID patientId, OffsetDateTime at)
            implements AdministrationTraitementEvent {
    }
}
