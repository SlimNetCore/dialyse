package com.hemodialyse.backend.domain.medical.greffe.service;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.DonneurVivant;
import com.hemodialyse.backend.domain.medical.greffe.port.DonneurVivantRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.DonneurVivantUseCase;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.LienParenteDonneur;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.ResultatCrossmatch;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanDonneur;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Candidats donneurs vivants. Classe pure du domaine (AGENTS.md §3), câblée en
 * {@code @Bean} dans {@code infrastructure/config/DomainServiceConfig}.
 */
public class DonneurVivantDomainService implements DonneurVivantUseCase {

    private final DonneurVivantRepositoryPort repository;

    public DonneurVivantDomainService(DonneurVivantRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<DonneurVivant> listByPatient(CenterId centerId, UUID patientId) {
        return repository.findByPatientId(patientId, centerId);
    }

    @Override
    public DonneurVivant create(CenterId centerId, UUID patientId, String nom, String prenom,
                                LocalDate dateNaissance, LienParenteDonneur lienParente, String telephone,
                                String groupeSanguin, String typageHla) {
        return repository.save(DonneurVivant.enregistrer(patientId, centerId.value(), nom, prenom, dateNaissance,
                lienParente, telephone, groupeSanguin, typageHla));
    }

    @Override
    public DonneurVivant update(CenterId centerId, UUID patientId, UUID donneurId, String nom, String prenom,
                                LocalDate dateNaissance, LienParenteDonneur lienParente, String telephone,
                                String groupeSanguin, String typageHla, StatutBilanDonneur statutBilan,
                                ResultatCrossmatch crossmatchResultat, LocalDate dateCrossmatch, String bilanRealise,
                                String contreIndications, String decisionFinale, LocalDate dateDecision) {
        DonneurVivant donneur = repository.findById(donneurId, patientId, centerId)
                .orElseThrow(() -> new IllegalArgumentException("Donneur vivant introuvable"));
        donneur.mettreAJour(nom, prenom, dateNaissance, lienParente, telephone, groupeSanguin, typageHla,
                statutBilan, crossmatchResultat, dateCrossmatch, bilanRealise, contreIndications, decisionFinale,
                dateDecision);
        return repository.save(donneur);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID donneurId) {
        repository.deleteById(donneurId, patientId, centerId);
    }
}
