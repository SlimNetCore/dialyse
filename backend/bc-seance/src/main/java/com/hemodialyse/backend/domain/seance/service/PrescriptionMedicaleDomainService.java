package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.model.UniteFrequence;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.PrescriptionMedicaleUseCase;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Prescription médicale. Pure domain class (hexagonal, AGENTS.md §3);
 * wired in {@code infrastructure/config/DomainServiceConfig}.
 */
public class PrescriptionMedicaleDomainService implements PrescriptionMedicaleUseCase {

    /**
     * Taille de page par défaut lorsque l'appelant n'en fournit pas (AGENTS.md §9).
     */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /**
     * Garde-fou : empêche un client de contourner la pagination en réclamant une page géante.
     */
    private static final int MAX_PAGE_SIZE = 200;

    private final PrescriptionMedicaleRepositoryPort repository;

    public PrescriptionMedicaleDomainService(PrescriptionMedicaleRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<PrescriptionMedicale> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La date from doit être <= à la date to");
        }
        return repository.findByPatientId(patientId, centerId, from, to);
    }

    @Override
    public PagedResult<PrescriptionMedicale> listPagedByPatient(CenterId centerId,
                                                                UUID patientId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                int page,
                                                                int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La date from doit être <= à la date to");
        }
        return repository.findPagedByPatientId(patientId, centerId, from, to, normalizePage(page), normalizeSize(size));
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Poids sec cible facultatif ; s'il est renseigné il doit rester dans des bornes cliniques
     * plausibles. Arrondi au centième (colonne NUMERIC(5,2)).
     */
    static BigDecimal normalizePoidsSec(BigDecimal poidsSecCibleKg) {
        if (poidsSecCibleKg == null) {
            return null;
        }
        if (poidsSecCibleKg.compareTo(PrescriptionMedicale.POIDS_SEC_MIN_KG) < 0
                || poidsSecCibleKg.compareTo(PrescriptionMedicale.POIDS_SEC_MAX_KG) > 0) {
            throw new IllegalArgumentException("Le poids sec cible doit être compris entre "
                    + PrescriptionMedicale.POIDS_SEC_MIN_KG + " et " + PrescriptionMedicale.POIDS_SEC_MAX_KG + " kg");
        }
        return poidsSecCibleKg.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public PrescriptionMedicale save(CenterId centerId,
                                     UUID patientId,
                                     UUID prescriptionId,
                                     LocalDate datePrescription,
                                     UUID medecinId,
                                     Integer qbCible,
                                     Integer qdCible,
                                     Integer ufMaxMl,
                                     Integer dureeCibleMin,
                                     BigDecimal poidsSecCibleKg,
                                     String typeDialyseurPrescrit,
                                     String anticoagTypePrescrit,
                                     UUID epoArticleId,
                                     Integer epoDoseUi,
                                     String epoVoie,
                                     Integer epoFrequenceValeur,
                                     UniteFrequence epoFrequenceUnite,
                                     UUID ferArticleId,
                                     Integer ferDoseMg,
                                     String ferVoie,
                                     Integer ferFrequenceValeur,
                                     UniteFrequence ferFrequenceUnite) {
        PrescriptionMedicale prescription = new PrescriptionMedicale();
        prescription.setId(prescriptionId != null ? prescriptionId : UUID.randomUUID());
        prescription.setPatientId(patientId);
        prescription.setCenterId(centerId.value());
        prescription.setDatePrescription(datePrescription != null ? datePrescription : LocalDate.now());
        prescription.setMedecinId(medecinId);
        prescription.setQbCible(qbCible);
        prescription.setQdCible(qdCible);
        prescription.setUfMaxMl(ufMaxMl);
        prescription.setDureeCibleMin(dureeCibleMin);
        prescription.setPoidsSecCibleKg(normalizePoidsSec(poidsSecCibleKg));
        prescription.setTypeDialyseurPrescrit(typeDialyseurPrescrit);
        prescription.setAnticoagTypePrescrit(anticoagTypePrescrit);
        prescription.setEpoArticleId(epoArticleId);
        prescription.setEpoDoseUi(epoDoseUi);
        prescription.setEpoVoie(epoVoie);
        prescription.setEpoFrequenceValeur(epoFrequenceValeur);
        prescription.setEpoFrequenceUnite(epoFrequenceUnite);
        prescription.setFerArticleId(ferArticleId);
        prescription.setFerDoseMg(ferDoseMg);
        prescription.setFerVoie(ferVoie);
        prescription.setFerFrequenceValeur(ferFrequenceValeur);
        prescription.setFerFrequenceUnite(ferFrequenceUnite);
        OffsetDateTime now = OffsetDateTime.now();
        prescription.setCreatedAt(now);
        prescription.setUpdatedAt(now);
        return repository.save(prescription);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID prescriptionId) {
        repository.deleteById(prescriptionId, patientId, centerId);
    }
}
