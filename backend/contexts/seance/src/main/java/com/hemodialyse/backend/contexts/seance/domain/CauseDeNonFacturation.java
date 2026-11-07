package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Pourquoi un patient n'est pas facturable à la date d'une séance (les trois étages de la couverture, dans cet ordre : prise en
 * charge, validation de la prise en charge, attestation de droits). ARGENT, reproduit à l'identique (KB-SEA-10, à décider avec les
 * métiers).
 */
public enum CauseDeNonFacturation {
    PRISE_EN_CHARGE, PRISE_EN_CHARGE_NON_VALIDEE, ATTESTATION_DE_DROITS;

    /** Refuse la création si le patient n'est pas facturable le jour de la séance ; messages de l'API observés. */
    public static void exigerFacturable(Optional<CauseDeNonFacturation> cause, LocalDate jour) {
        cause.ifPresent(c -> {
            throw switch (c) {
                case PRISE_EN_CHARGE -> new SeanceRefusee(MotifDeRefus.PRISE_EN_CHARGE_ABSENTE,
                        "Le patient doit avoir une prise en charge valide pour être facturé");
                case PRISE_EN_CHARGE_NON_VALIDEE -> new SeanceRefusee(MotifDeRefus.PRISE_EN_CHARGE_NON_VALIDEE,
                        "La prise en charge du patient couvre le " + jour + " mais n'est pas validée : validez-la pour pouvoir le facturer");
                case ATTESTATION_DE_DROITS -> new SeanceRefusee(MotifDeRefus.ATTESTATION_DE_DROITS_ABSENTE,
                        "La prise en charge du patient est valide mais aucune attestation de droits ne couvre le " + jour
                                + " : renouvelez l'attestation pour pouvoir le facturer");
            };
        });
    }
}
