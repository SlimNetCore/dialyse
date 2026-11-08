package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.DiffusionsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DiffusionsFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/** ACL des diffusions : traduit en langage publié {@link DiffusionsFournies} (identifiants, noms, date ISO). */
public class DiffusionsParLeLegacy implements DiffusionsDeSeance {

    private final DiffusionsFournies diffusions;

    public DiffusionsParLeLegacy(DiffusionsFournies diffusions) {
        this.diffusions = diffusions;
    }

    @Override
    public void seanceCreee(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffusions.seanceCreee(centre.valeur(), seance.id().valeur(), seance.patient().valeur(), patient.nom(), patient.prenom(),
                jour(seance));
    }

    @Override
    public void seanceValidee(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffusions.seanceValidee(centre.valeur(), seance.id().valeur(), seance.patient().valeur(), patient.nom(), patient.prenom(),
                jour(seance));
    }

    @Override
    public void saisieDeLInfirmier(CentreId centre, String type, Seance seance, FicheDuPatient patient, String auteur) {
        diffusions.saisieDeLInfirmier(centre.valeur(), type, seance.patient().valeur(), patient.nom(), patient.prenom(), auteur,
                jour(seance));
    }

    @Override
    public void seanceDeverrouillee(CentreId centre, Seance seance, FicheDuPatient patient) {
        diffusions.seanceDeverrouillee(centre.valeur(), seance.id().valeur(), patient.nom(), patient.prenom(), jour(seance));
    }

    @Override
    public void consommablesModifies(CentreId centre, Seance seance) {
        diffusions.consommablesModifies(centre.valeur(), seance.id().valeur());
    }

    private static String jour(Seance seance) {
        return seance.jour() == null ? null : seance.jour().toString();
    }
}
