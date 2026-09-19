package com.hemodialyse.backend.application.query;

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
import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.web.dto.response.AlerteSerologieKdigoResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EvaluationEgfrResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.EvaluationRisqueKdigoResponse;
import com.hemodialyse.backend.infrastructure.web.dto.response.KdigoGreffeResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assemble les évaluations KDIGO du bilan pré-greffe (risque immunologique, fonction rénale,
 * points d'attention sérologiques) à partir des données déjà saisies ailleurs dans le dossier —
 * aucune donnée dupliquée, tout est recalculé à la demande via {@link KdigoGreffeEvaluationPolicy}.
 */
@Service
public class KdigoGreffeQueryService {

    private final BilanPreGreffeRepositoryPort bilanRepository;
    private final PatientRepositoryPort patientRepository;
    private final ResultatAnalyseRepositoryPort resultatAnalyseRepository;
    private final SerologieRepositoryPort serologieRepository;

    public KdigoGreffeQueryService(BilanPreGreffeRepositoryPort bilanRepository,
                                   PatientRepositoryPort patientRepository,
                                   ResultatAnalyseRepositoryPort resultatAnalyseRepository,
                                   SerologieRepositoryPort serologieRepository) {
        this.bilanRepository = bilanRepository;
        this.patientRepository = patientRepository;
        this.resultatAnalyseRepository = resultatAnalyseRepository;
        this.serologieRepository = serologieRepository;
    }

    public KdigoGreffeResponse evaluer(CenterId centerId, UUID patientId) {
        BilanPreGreffe bilan = bilanRepository.findByPatientId(patientId, centerId).orElse(null);
        EvaluationRisqueKdigo risqueImmunologique = KdigoGreffeEvaluationPolicy.evaluerRisqueImmunologique(
                bilan == null ? null : bilan.getPraClasseI(), bilan == null ? null : bilan.getPraClasseII());

        Patient patient = patientRepository.findById(PatientId.of(patientId), centerId).orElse(null);
        BigDecimal creatinine = dernierCreatinine(centerId, patientId);
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

    private BigDecimal dernierCreatinine(CenterId centerId, UUID patientId) {
        return resultatAnalyseRepository.findByPatientId(patientId, centerId, null, null).stream()
                .filter(r -> r.getCreatinineMgDl() != null)
                .max(Comparator.comparing(ResultatAnalyse::getDatePrelevement))
                .map(ResultatAnalyse::getCreatinineMgDl)
                .orElse(null);
    }

    private Integer age(LocalDate dateNaissance) {
        return dateNaissance == null ? null : Period.between(dateNaissance, LocalDate.now()).getYears();
    }
}
