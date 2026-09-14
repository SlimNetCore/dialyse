package com.hemodialyse.backend.domain.medical.greffe.service;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeUseCase;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutEtapeGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Domain Service — Checklist du bilan pré-greffe rénale (receveur). Classe pure du domaine
 * (AGENTS.md §3), câblée en {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class EtapeBilanPreGreffeDomainService implements EtapeBilanPreGreffeUseCase {

    private final EtapeBilanPreGreffeRepositoryPort repository;

    public EtapeBilanPreGreffeDomainService(EtapeBilanPreGreffeRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<EtapeBilanPreGreffe> listByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
    }

    @Override
    public EtapeBilanPreGreffe create(CenterId centerId, UUID patientId, CategorieEtapeGreffe categorie,
                                      String libelle) {
        return repository.save(EtapeBilanPreGreffe.creer(patientId, centerId.value(), categorie, libelle));
    }

    @Override
    public EtapeBilanPreGreffe update(CenterId centerId, UUID patientId, UUID etapeId, StatutEtapeGreffe statut,
                                      LocalDate dateRealisation, String resultat, LocalDate dateExpiration,
                                      UUID demandeExamenId, UUID serologieId) {
        EtapeBilanPreGreffe etape = repository.findById(etapeId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Étape de bilan pré-greffe introuvable"));
        etape.mettreAJour(statut, dateRealisation, resultat, dateExpiration, demandeExamenId, serologieId);
        return repository.save(etape);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID etapeId) {
        repository.deleteById(etapeId, patientId, centerId);
    }

    @Override
    public List<EtapeBilanPreGreffe> genererEtapesStandard(CenterId centerId, UUID patientId) {
        List<EtapeBilanPreGreffe> existantes = repository.findByPatientId(patientId, centerId);
        Set<String> dejaPresentes = existantes.stream()
                .map(e -> e.getCategorie().name() + "|" + e.getLibelle())
                .collect(java.util.stream.Collectors.toSet());

        for (EtapeBilanPreGreffeTemplate.EtapeStandard standard : EtapeBilanPreGreffeTemplate.ETAPES_STANDARD) {
            String cle = standard.categorie().name() + "|" + standard.libelle();
            if (!dejaPresentes.contains(cle)) {
                repository.save(EtapeBilanPreGreffe.creer(patientId, centerId.value(), standard.categorie(),
                        standard.libelle()));
            }
        }
        return repository.findByPatientId(patientId, centerId);
    }
}
