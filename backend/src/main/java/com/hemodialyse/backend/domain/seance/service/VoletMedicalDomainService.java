package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.model.VoletMedical;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class VoletMedicalDomainService implements VoletMedicalUseCase {

    private final SeanceRepositoryPort seanceRepository;
    private final VoletMedicalRepositoryPort voletRepository;

    public VoletMedicalDomainService(SeanceRepositoryPort seanceRepository,
                                     VoletMedicalRepositoryPort voletRepository) {
        this.seanceRepository = seanceRepository;
        this.voletRepository = voletRepository;
    }

    @Override
    public VoletMedical save(CenterId centerId,
                             UUID seanceId,
                             String prescription,
                             String toleranceSeance,
                             String examenClinique,
                             String resultatsBiologiques,
                             String ajustementsTherapeutiques,
                             String conclusionMedicale) {
        var seance = seanceRepository.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));

        if (seance.getStatus() == SeanceStatus.CREE) {
            throw new IllegalStateException("Le volet medical n'est accessible qu'apres validation infirmiere");
        }

        VoletMedical volet = voletRepository.findBySeanceId(seanceId, centerId).orElseGet(VoletMedical::new);
        if (volet.getId() == null) {
            volet.setId(UUID.randomUUID());
            volet.setCreatedAt(OffsetDateTime.now());
        }
        volet.setSeanceId(seanceId);
        volet.setCenterId(centerId.value());
        volet.setPrescription(prescription);
        volet.setToleranceSeance(toleranceSeance);
        volet.setExamenClinique(examenClinique);
        volet.setResultatsBiologiques(resultatsBiologiques);
        volet.setAjustementsTherapeutiques(ajustementsTherapeutiques);
        volet.setConclusionMedicale(conclusionMedicale);
        volet.setUpdatedAt(OffsetDateTime.now());
        return voletRepository.save(volet);
    }
}

