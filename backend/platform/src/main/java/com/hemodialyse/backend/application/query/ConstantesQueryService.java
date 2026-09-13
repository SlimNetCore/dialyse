package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.SeanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.VoletParamedicalJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.SeanceJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.VoletParamedicalJpaRepository;
import com.hemodialyse.backend.infrastructure.web.dto.response.ConstanteSeanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Read-model — vue longitudinale des constantes du patient (poids, tension, débit, UF, durée),
 * agrégée à partir du volet paramédical déjà saisi par l'infirmier séance après séance.
 * <p>
 * Volontairement en lecture seule : le dossier médical ne duplique jamais une saisie qui existe
 * déjà côté cahier de dialyse (source unique de vérité — voir le plan Phase 4).
 */
@Service
public class ConstantesQueryService {

    private final SeanceJpaRepository seanceRepository;
    private final VoletParamedicalJpaRepository voletParamedicalRepository;

    public ConstantesQueryService(SeanceJpaRepository seanceRepository,
                                  VoletParamedicalJpaRepository voletParamedicalRepository) {
        this.seanceRepository = seanceRepository;
        this.voletParamedicalRepository = voletParamedicalRepository;
    }

    public PagedResult<ConstanteSeanceResponse> getConstantes(UUID centerId, UUID patientId, int page, int size) {
        Page<SeanceJpaEntity> seances = seanceRepository.findByCenterIdAndPatientIdOrderByDateSeanceDescCreatedAtDesc(
                centerId, patientId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateSeance")));

        var items = seances.getContent().stream()
                .map(seance -> toResponse(seance, centerId))
                .toList();

        return PagedResult.of(items, seances.getTotalElements(), page, size);
    }

    private ConstanteSeanceResponse toResponse(SeanceJpaEntity seance, UUID centerId) {
        VoletParamedicalJpaEntity volet = voletParamedicalRepository
                .findBySeanceIdAndCenterId(seance.getId(), centerId)
                .orElse(null);
        if (volet == null) {
            return new ConstanteSeanceResponse(seance.getId(), seance.getDateSeance(), null, null, null, null, null, null, null);
        }
        return new ConstanteSeanceResponse(seance.getId(), seance.getDateSeance(), volet.getPoidsAvantKg(),
                volet.getPoidsApresKg(), volet.getTaAvant(), volet.getTaApres(), volet.getDebitSangMlMin(),
                volet.getUltrafiltrationMl(), volet.getDureeMinutes());
    }
}
