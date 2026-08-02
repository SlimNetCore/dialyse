package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.model.VoletParamedical;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalUseCase;
import com.hemodialyse.backend.domain.seance.vo.TensionArterielle;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.shared.vo.Poids;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain Service — Volet paramédical business rules.
 * <p>
 * Pure domain class (no Spring/JPA dependency — hexagonal architecture, AGENTS.md §3).
 * Wired as a bean in {@code infrastructure/config/DomainServiceConfig}.
 */
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
        var seance = seanceRepository.findById(seanceId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Seance introuvable"));
        if (seance.getStatus() == SeanceStatus.FACTUREE) {
            throw new IllegalStateException("La seance facturee ne peut plus etre modifiee");
        }

        VoletParamedical volet = voletRepository.findBySeanceId(seanceId, centerId).orElseGet(VoletParamedical::new);
        if (volet.getId() == null) {
            volet.setId(UUID.randomUUID());
            volet.setCreatedAt(OffsetDateTime.now());
        }
        // Value Object invariants (Shared Kernel / seance VO) — lenient on optional input,
        // so loading legacy data is unaffected and only user-supplied values are validated.
        if (poidsAvantKg != null) {
            Poids.ofKilogrammes(poidsAvantKg);
        }
        if (poidsApresKg != null) {
            Poids.ofKilogrammes(poidsApresKg);
        }
        String taAvantNormalise = TensionArterielle.tryParse(taAvant).map(TensionArterielle::format).orElse(taAvant);
        String taApresNormalise = TensionArterielle.tryParse(taApres).map(TensionArterielle::format).orElse(taApres);

        volet.setSeanceId(seanceId);
        volet.setCenterId(centerId.value());
        volet.setPoidsAvantKg(poidsAvantKg);
        volet.setPoidsApresKg(poidsApresKg);
        volet.setTaAvant(taAvantNormalise);
        volet.setTaApres(taApresNormalise);
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




