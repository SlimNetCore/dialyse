package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.VoletsDeLaSeance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.VoletsFournis;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** ACL des volets : traduit le langage publié {@link VoletsFournis}. */
public class VoletsParLeLegacy implements VoletsDeLaSeance {

    private final VoletsFournis volets;

    public VoletsParLeLegacy(VoletsFournis volets) {
        this.volets = volets;
    }

    @Override
    public Optional<VoletParamedical> paramedical(CentreId centre, SeanceId seance) {
        return volets.paramedical(centre.valeur(), seance.valeur())
                .map(v -> new VoletParamedical(v.poidsAvantKg(), v.poidsApresKg(), v.tensionAvant(), v.tensionApres(), v.dureeMinutes(),
                        v.debitSangMlMin(), v.ultrafiltrationMl(), v.anticoagulant(), v.typeDeDialysat(), v.incidents()));
    }

    @Override
    public Optional<VoletMedical> medical(CentreId centre, SeanceId seance) {
        return volets.medical(centre.valeur(), seance.valeur())
                .map(v -> new VoletMedical(v.prescription(), v.toleranceDeLaSeance(), v.examenClinique(), v.resultatsBiologiques(),
                        v.ajustementsTherapeutiques(), v.conclusionMedicale()));
    }
}
