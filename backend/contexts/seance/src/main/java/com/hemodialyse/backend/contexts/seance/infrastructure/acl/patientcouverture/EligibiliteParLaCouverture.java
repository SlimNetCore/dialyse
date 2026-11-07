package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.seance.application.EligibiliteALaFacturation;
import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ACL vers le langage publié de Patient et couverture ({@code patientcouverture.v1}) : l'éligibilité d'une séance à la facturation (prise
 * en charge porteuse d'un forfait couvrant le jour, validée, puis attestation couvrant le jour). Les trois étages et leur ordre sont ceux
 * du port publié, repris à l'identique. ARGENT, reproduit, À DÉCIDER AVEC LES MÉTIERS (KB-PAT-25 et 46, KB-SEA-10).
 */
public class EligibiliteParLaCouverture implements EligibiliteALaFacturation {

    private final CouvertureDesPatients couverture;

    public EligibiliteParLaCouverture(CouvertureDesPatients couverture) {
        this.couverture = couverture;
    }

    @Override
    public Optional<CauseDeNonFacturation> cause(CentreId centre, PatientId patient, LocalDate jour) {
        return couverture.causeDeNonFacturation(centre.valeur(), patient.valeur(), jour).map(EligibiliteParLaCouverture::traduire);
    }

    static CauseDeNonFacturation traduire(CouvertureDesPatients.CauseDeNonFacturation cause) {
        return switch (cause) {
            case AUCUNE_PEC_COUVRANTE -> CauseDeNonFacturation.PRISE_EN_CHARGE;
            case PEC_NON_VALIDEE -> CauseDeNonFacturation.PRISE_EN_CHARGE_NON_VALIDEE;
            case ATTESTATION_ABSENTE -> CauseDeNonFacturation.ATTESTATION_DE_DROITS;
        };
    }
}
