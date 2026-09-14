package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.entity.DecisionRcp;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.AvisRcp;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.BilanPreGreffeJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.DecisionRcpJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.BilanPreGreffeJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.DecisionRcpJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'agrégat {@code BilanPreGreffe} n'a pas de relation JPA mappée vers ses décisions de RCP
 * (convention du dépôt — FK brute, jointure manuelle, cf. {@code DemandeExamenRepositoryAdapter}) :
 * cet adapter charge/persiste explicitement le parent et ses décisions dans la même transaction.
 */
@Component
public class BilanPreGreffeRepositoryAdapter implements BilanPreGreffeRepositoryPort {

    private final BilanPreGreffeJpaRepository bilanJpa;
    private final DecisionRcpJpaRepository decisionJpa;

    public BilanPreGreffeRepositoryAdapter(BilanPreGreffeJpaRepository bilanJpa, DecisionRcpJpaRepository decisionJpa) {
        this.bilanJpa = bilanJpa;
        this.decisionJpa = decisionJpa;
    }

    @Override
    public Optional<BilanPreGreffe> findByPatientId(UUID patientId, CenterId centerId) {
        return bilanJpa.findByPatientIdAndCenterId(patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public BilanPreGreffe save(BilanPreGreffe bilan) {
        BilanPreGreffeJpaEntity saved = bilanJpa.save(toJpa(bilan));
        decisionJpa.deleteByBilanId(saved.getId());
        for (DecisionRcp decision : bilan.getDecisionsRcp()) {
            decisionJpa.save(toDecisionJpa(saved.getId(), decision));
        }
        return toDomain(saved, bilan.getDecisionsRcp());
    }

    private BilanPreGreffe toDomain(BilanPreGreffeJpaEntity e) {
        List<DecisionRcp> decisions = decisionJpa.findByBilanIdOrderByDateReunionDesc(e.getId()).stream()
                .map(this::toDecisionDomain)
                .toList();
        return toDomain(e, decisions);
    }

    private BilanPreGreffe toDomain(BilanPreGreffeJpaEntity e, List<DecisionRcp> decisions) {
        return BilanPreGreffe.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(),
                StatutBilanGreffe.valueOf(e.getStatut()), e.getDateDebutBilan(), e.getDateInscriptionListeAttente(),
                e.getDateGreffe(), e.getGroupeSanguinConfirme(), e.getTypageHla(), e.getPraClasseI(),
                e.getPraClasseII(), e.getContreIndications(), e.getConclusionNephrologue(), decisions,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private DecisionRcp toDecisionDomain(DecisionRcpJpaEntity e) {
        return DecisionRcp.reconstituer(e.getId(), e.getDateReunion(), AvisRcp.valueOf(e.getAvis()),
                e.getCompteRendu(), e.getProchaineDateRevue());
    }

    private BilanPreGreffeJpaEntity toJpa(BilanPreGreffe b) {
        BilanPreGreffeJpaEntity e = new BilanPreGreffeJpaEntity();
        e.setId(b.getId());
        e.setPatientId(b.getPatientId());
        e.setCenterId(b.getCenterId());
        e.setStatut(b.getStatut().name());
        e.setDateDebutBilan(b.getDateDebutBilan());
        e.setDateInscriptionListeAttente(b.getDateInscriptionListeAttente());
        e.setDateGreffe(b.getDateGreffe());
        e.setGroupeSanguinConfirme(b.getGroupeSanguinConfirme());
        e.setTypageHla(b.getTypageHla());
        e.setPraClasseI(b.getPraClasseI());
        e.setPraClasseII(b.getPraClasseII());
        e.setContreIndications(b.getContreIndications());
        e.setConclusionNephrologue(b.getConclusionNephrologue());
        e.setCreatedAt(b.getCreatedAt());
        e.setUpdatedAt(b.getUpdatedAt());
        return e;
    }

    private DecisionRcpJpaEntity toDecisionJpa(UUID bilanId, DecisionRcp decision) {
        DecisionRcpJpaEntity e = new DecisionRcpJpaEntity();
        e.setId(decision.getId());
        e.setBilanId(bilanId);
        e.setDateReunion(decision.getDateReunion());
        e.setAvis(decision.getAvis().name());
        e.setCompteRendu(decision.getCompteRendu());
        e.setProchaineDateRevue(decision.getProchaineDateRevue());
        return e;
    }
}
