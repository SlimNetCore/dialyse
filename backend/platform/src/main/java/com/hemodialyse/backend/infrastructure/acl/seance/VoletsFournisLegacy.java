package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.VoletsFournis;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Fournit à la Séance le langage publié {@link VoletsFournis} (lecture seule des volets paramédical et médical legacy). */
@Component
public class VoletsFournisLegacy implements VoletsFournis {

    private final VoletParamedicalRepositoryPort paramedical;
    private final VoletMedicalRepositoryPort medical;

    public VoletsFournisLegacy(VoletParamedicalRepositoryPort paramedical, VoletMedicalRepositoryPort medical) {
        this.paramedical = paramedical;
        this.medical = medical;
    }

    @Override
    public Optional<Paramedical> paramedical(UUID centre, UUID seance) {
        return paramedical.findBySeanceId(seance, CenterId.of(centre))
                .map(v -> new Paramedical(v.getPoidsAvantKg(), v.getPoidsApresKg(), v.getTaAvant(), v.getTaApres(), v.getDureeMinutes(),
                        v.getDebitSangMlMin(), v.getUltrafiltrationMl(), v.getAnticoagulant(), v.getTypeDialysat(), v.getIncidents()));
    }

    @Override
    public Optional<Medical> medical(UUID centre, UUID seance) {
        return medical.findBySeanceId(seance, CenterId.of(centre))
                .map(v -> new Medical(v.getPrescription(), v.getToleranceSeance(), v.getExamenClinique(), v.getResultatsBiologiques(),
                        v.getAjustementsTherapeutiques(), v.getConclusionMedicale()));
    }
}
