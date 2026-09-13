package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.medical.kdigo.service.KdigoEvaluationPolicy;
import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.infrastructure.persistence.entity.AdministrationTraitementJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.PrescriptionMedicaleJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.ResultatAnalyseJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AdministrationTraitementJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.PrescriptionMedicaleJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.ResultatAnalyseJpaRepository;
import com.hemodialyse.backend.infrastructure.web.dto.response.AdministrationTraitementResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EvaluationCibleResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PointBiologiqueResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.PrescriptionMedicaleResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.SuiviAnemieResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Read-model agrégeant, pour le suivi de l'anémie d'un patient hémodialysé, les dernières
 * valeurs biologiques (Hb, ferritine, CST, albumine), leur évaluation face aux cibles KDIGO, la
 * prescription EPO/fer en cours et l'historique des administrations réellement effectuées.
 * <p>
 * Lit directement les bilans à colonnes fixes {@code resultats_analyses} (pas encore les
 * observations LOINC génériques — voir la Phase 3/4 du plan pour leur unification à venir) :
 * c'est aujourd'hui la seule source alimentée en pratique.
 */
@Service
public class SuiviAnemieQueryService {

    private final ResultatAnalyseJpaRepository resultatAnalyseRepository;
    private final PrescriptionMedicaleJpaRepository prescriptionRepository;
    private final AdministrationTraitementJpaRepository administrationRepository;

    public SuiviAnemieQueryService(ResultatAnalyseJpaRepository resultatAnalyseRepository,
                                   PrescriptionMedicaleJpaRepository prescriptionRepository,
                                   AdministrationTraitementJpaRepository administrationRepository) {
        this.resultatAnalyseRepository = resultatAnalyseRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.administrationRepository = administrationRepository;
    }

    public SuiviAnemieResponse getSuiviAnemie(UUID centerId, UUID patientId) {
        List<ResultatAnalyseJpaEntity> bilans =
                resultatAnalyseRepository.findTop10ByPatientIdAndCenterIdOrderByDatePrelevementDesc(patientId, centerId);

        List<PointBiologiqueResponse> courbe = bilans.stream()
                .map(b -> new PointBiologiqueResponse(b.getDatePrelevement(), b.getHbGDl(), b.getFerritineNgMl(),
                        b.getCstfPct(), b.getAlbumineGDl()))
                .toList();

        ResultatAnalyseJpaEntity dernierBilan = bilans.isEmpty() ? null : bilans.get(0);

        List<EvaluationCibleResponse> evaluations = dernierBilan == null ? List.of() : List.of(
                EvaluationCibleResponse.from(KdigoEvaluationPolicy.evaluerHemoglobine(dernierBilan.getHbGDl())),
                EvaluationCibleResponse.from(KdigoEvaluationPolicy.evaluerFerritine(dernierBilan.getFerritineNgMl())),
                EvaluationCibleResponse.from(KdigoEvaluationPolicy.evaluerCoefficientSaturationTransferrine(
                        dernierBilan.getCstfPct())),
                EvaluationCibleResponse.from(KdigoEvaluationPolicy.evaluerAlbumine(dernierBilan.getAlbumineGDl())),
                EvaluationCibleResponse.from(KdigoEvaluationPolicy.evaluerKtV(dernierBilan.getKtVMensuel()))
        );

        PrescriptionMedicaleResponse prescriptionActive = prescriptionRepository
                .findTopByPatientIdAndCenterIdOrderByDatePrescriptionDesc(patientId, centerId)
                .map(this::toPrescriptionResponse)
                .orElse(null);

        List<AdministrationTraitementResponse> administrationsRecentes = administrationRepository
                .findByPatientIdAndCenterId(patientId, centerId, PageRequest.of(0, 10))
                .getContent().stream()
                .map(this::toAdministrationResponse)
                .toList();

        return new SuiviAnemieResponse(
                dernierBilan == null ? null : dernierBilan.getDatePrelevement(),
                evaluations, courbe, prescriptionActive, administrationsRecentes);
    }

    private AdministrationTraitementResponse toAdministrationResponse(AdministrationTraitementJpaEntity e) {
        DoseAdministree dose = e.getDose() == null ? null : new DoseAdministree(e.getDose(), e.getUniteDose());
        AdministrationTraitement administration = AdministrationTraitement.reconstituer(
                e.getId(), e.getPatientId(), e.getCenterId(), e.getPrescriptionMedicaleId(),
                TypeTraitementAnemie.valueOf(e.getTypeTraitement()), e.getMolecule(), dose, e.getVoie(),
                e.getDateAdministration(), e.getSeanceId(), e.getAdministrePar(), e.isAdministree(),
                e.getMotifNonAdministration(), e.getCreatedAt());
        return AdministrationTraitementResponse.from(administration);
    }

    private PrescriptionMedicaleResponse toPrescriptionResponse(PrescriptionMedicaleJpaEntity e) {
        PrescriptionMedicale p = new PrescriptionMedicale();
        p.setId(e.getId());
        p.setPatientId(e.getPatientId());
        p.setCenterId(e.getCenterId());
        p.setDatePrescription(e.getDatePrescription());
        p.setMedecinId(e.getMedecinId());
        p.setQbCible(e.getQbCible());
        p.setQdCible(e.getQdCible());
        p.setUfMaxMl(e.getUfMaxMl());
        p.setDureeCibleMin(e.getDureeCibleMin());
        p.setTypeDialyseurPrescrit(e.getTypeDialyseurPrescrit());
        p.setAnticoagTypePrescrit(e.getAnticoagTypePrescrit());
        p.setEpoMolecule(e.getEpoMolecule());
        p.setEpoDoseUi(e.getEpoDoseUi());
        p.setEpoVoie(e.getEpoVoie());
        p.setEpoFrequence(e.getEpoFrequence());
        p.setFerMolecule(e.getFerMolecule());
        p.setFerDoseMg(e.getFerDoseMg());
        p.setFerVoie(e.getFerVoie());
        p.setFerFrequence(e.getFerFrequence());
        p.setCreatedAt(e.getCreatedAt());
        p.setUpdatedAt(e.getUpdatedAt());
        return PrescriptionMedicaleResponse.from(p);
    }
}
