package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Transactional
public class VoletParamedicalDomainService implements VoletParamedicalUseCase {

    private final SeanceRepositoryPort seanceRepository;
    private final VoletParamedicalRepositoryPort voletRepository;

    public VoletParamedicalDomainService(SeanceRepositoryPort seanceRepository,
                                         VoletParamedicalRepositoryPort voletRepository) {
        this.seanceRepository = seanceRepository;
        this.voletRepository = voletRepository;
    }

    @Override
    public VoletParamedical save(CenterId centerId,
                                 UUID seanceId,
                                 BigDecimal poidsAvantKg,
                                 BigDecimal poidsApresKg,
                                 String taAvant,
                                 String taApres,
                                 Integer dureeMinutes,
                                 Integer debitSangMlMin,
                                 BigDecimal ultrafiltrationMl,
                                 String anticoagulant,
                                 String typeDialysat,
                                 String incidents) {
        seanceRepository.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));

        VoletParamedical volet = voletRepository.findBySeanceId(seanceId, centerId).orElseGet(VoletParamedical::new);
        if (volet.getId() == null) {
            volet.setId(UUID.randomUUID());
            volet.setCreatedAt(OffsetDateTime.now());
        }
        volet.setSeanceId(seanceId);
        volet.setCenterId(centerId.value());
        volet.setPoidsAvantKg(poidsAvantKg);
        volet.setPoidsApresKg(poidsApresKg);
        volet.setTaAvant(taAvant);
        volet.setTaApres(taApres);
        volet.setDureeMinutes(dureeMinutes);
        volet.setDebitSangMlMin(debitSangMlMin);
        volet.setUltrafiltrationMl(ultrafiltrationMl);
        volet.setAnticoagulant(anticoagulant);
        volet.setTypeDialysat(typeDialysat);
        volet.setIncidents(incidents);
        volet.setUpdatedAt(OffsetDateTime.now());
        return voletRepository.save(volet);
    }
}

