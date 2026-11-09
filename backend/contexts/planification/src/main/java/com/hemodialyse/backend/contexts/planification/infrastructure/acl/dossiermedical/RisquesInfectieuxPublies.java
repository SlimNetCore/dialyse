package com.hemodialyse.backend.contexts.planification.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL vers le Dossier médical (DM9, ADR 0101 ; remplace {@code legacydossiermedical/RisquesInfectieuxJdbc}) : le risque infectieux d'un patient
 * (RG-PLN-012) vient du langage publié du Dossier médical, fourni par la plateforme ({@link RisquesInfectieuxFournis}) : plus aucun SQL sur
 * {@code serologies_patient} dans la Planification. Même règle (dernière sérologie AgHBs, anti-VHC, ARN du VHC ou VIH positive).
 */
public class RisquesInfectieuxPublies implements RisquesInfectieux {

    private final RisquesInfectieuxFournis fournis;

    public RisquesInfectieuxPublies(RisquesInfectieuxFournis fournis) {
        this.fournis = fournis;
    }

    @Override
    public CategorieDIsolement de(CentreId centre, PatientId patient) {
        return CategorieDIsolement.deduite(fournis.patientsARisque(centre.valeur()).contains(patient.valeur()));
    }
}
