package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.medical.kdigo.service.KdigoEvaluationPolicy;
import com.hemodialyse.backend.domain.seance.model.PrescriptionMedicale;
import com.hemodialyse.backend.domain.seance.model.UniteFrequence;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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
    private final ArticleRepositoryPort articleRepository;

    public SuiviAnemieQueryService(ResultatAnalyseJpaRepository resultatAnalyseRepository,
                                   PrescriptionMedicaleJpaRepository prescriptionRepository,
                                   AdministrationTraitementJpaRepository administrationRepository,
                                   ArticleRepositoryPort articleRepository) {
        this.resultatAnalyseRepository = resultatAnalyseRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.administrationRepository = administrationRepository;
        this.articleRepository = articleRepository;
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
                .map(e -> toPrescriptionResponse(e, centerId))
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
                e.getMotifNonAdministration(), e.getArticleId(), e.getQuantiteArticle(), e.getCreatedAt());
        return AdministrationTraitementResponse.from(administration);
    }

    private PrescriptionMedicaleResponse toPrescriptionResponse(PrescriptionMedicaleJpaEntity e, UUID centerId) {
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
        p.setEpoArticleId(e.getEpoArticleId());
        p.setEpoDoseUi(e.getEpoDoseUi());
        p.setEpoVoie(e.getEpoVoie());
        p.setEpoFrequenceValeur(e.getEpoFrequenceValeur());
        p.setEpoFrequenceUnite(e.getEpoFrequenceUnite() != null ? UniteFrequence.valueOf(e.getEpoFrequenceUnite()) : null);
        p.setFerArticleId(e.getFerArticleId());
        p.setFerDoseMg(e.getFerDoseMg());
        p.setFerVoie(e.getFerVoie());
        p.setFerFrequenceValeur(e.getFerFrequenceValeur());
        p.setFerFrequenceUnite(e.getFerFrequenceUnite() != null ? UniteFrequence.valueOf(e.getFerFrequenceUnite()) : null);
        p.setCreatedAt(e.getCreatedAt());
        p.setUpdatedAt(e.getUpdatedAt());

        CenterId center = new CenterId(centerId);
        String epoCode = null;
        String epoLibelle = null;
        if (p.getEpoArticleId() != null) {
            Article article = articleRepository.findById(p.getEpoArticleId(), center).orElse(null);
            if (article != null) {
                epoCode = article.getCode();
                epoLibelle = article.getLibelle();
            }
        }
        String ferCode = null;
        String ferLibelle = null;
        if (p.getFerArticleId() != null) {
            Article article = articleRepository.findById(p.getFerArticleId(), center).orElse(null);
            if (article != null) {
                ferCode = article.getCode();
                ferLibelle = article.getLibelle();
            }
        }
        return PrescriptionMedicaleResponse.from(p, epoCode, epoLibelle, ferCode, ferLibelle);
    }
}
