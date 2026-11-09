package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import com.hemodialyse.backend.domain.medical.greffe.aggregate.BilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.kdigo.service.KdigoGreffeEvaluationPolicy;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.AlerteSerologieKdigo;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationEgfr;
import com.hemodialyse.backend.domain.medical.kdigo.valueobject.EvaluationRisqueKdigo;
import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieRepositoryPort;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.response.AlerteSerologieKdigoResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EvaluationEgfrResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EvaluationRisqueKdigoResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.KdigoGreffeResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assemble les évaluations KDIGO du bilan pré-greffe (risque immunologique, fonction rénale,
 * points d'attention sérologiques) à partir des données déjà saisies ailleurs dans le dossier —
 * aucune donnée dupliquée, tout est recalculé à la demande via {@link KdigoGreffeEvaluationPolicy}.
 * <p>
 * SE7f (ADR 0100) : la dernière créatinine du patient au centre est lue par le langage publié du Dossier médical
 * ({@link DerniereCreatininePubliee}) au lieu du port {@code ResultatAnalyseRepositoryPort} de {@code bc-seance} (retiré) ; même règle
 * (KB-SEA-96 reproduit : ordre non défini à date égale, créatinine à 0 retenue).
 */
@Service
public class KdigoGreffeQueryService {

    private final BilanPreGreffeRepositoryPort bilanRepository;
    private final PatientRepositoryPort patientRepository;
    private final DerniereCreatininePubliee derniereCreatinine;
    private final SerologieRepositoryPort serologieRepository;

    public KdigoGreffeQueryService(BilanPreGreffeRepositoryPort bilanRepository,
                                   PatientRepositoryPort patientRepository,
                                   DerniereCreatininePubliee derniereCreatinine,
                                   SerologieRepositoryPort serologieRepository) {
        this.bilanRepository = bilanRepository;
        this.patientRepository = patientRepository;
        this.derniereCreatinine = derniereCreatinine;
        this.serologieRepository = serologieRepository;
    }

    public KdigoGreffeResponse evaluer(CenterId centerId, UUID patientId) {
        BilanPreGreffe bilan = bilanRepository.findByPatientId(patientId, centerId).orElse(null);
        EvaluationRisqueKdigo risqueImmunologique = KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(
                bilan == null ? null : bilan.getPraClasseI(), bilan == null ? null : bilan.getPraClasseII());

        Patient patient = patientRepository.findById(PatientId.of(patientId), centerId).orElse(null);
        BigDecimal creatinine = derniereCreatinine.duPatient(centerId.value(), patientId).orElse(null);
        Integer age = age(patient == null ? null : patient.getDateNaissance());
        String sexe = patient == null ? null : patient.getSexe();
        EvaluationEgfr fonctionRenale = KdigoGreffeEvaluationPolicy.evaluerFonctionRenale(creatinine, age, sexe);

        Map<String, String> marqueurResultats = new HashMap<>();
        for (Serologie s : serologieRepository.findByPatientId(patientId, centerId)) {
            marqueurResultats.put(s.getMarqueur().name(), s.getResultat().name());
        }
        List<AlerteSerologieKdigo> alertesSerologiques = KdigoGreffeEvaluationPolicy.evaluerSerologies(marqueurResultats);

        return new KdigoGreffeResponse(
                EvaluationRisqueKdigoResponse.from(risqueImmunologique),
                EvaluationEgfrResponse.from(fonctionRenale),
                alertesSerologiques.stream().map(AlerteSerologieKdigoResponse::from).toList());
    }

    private Integer age(LocalDate dateNaissance) {
        return dateNaissance == null ? null : Period.between(dateNaissance, LocalDate.now()).getYears();
    }
}
