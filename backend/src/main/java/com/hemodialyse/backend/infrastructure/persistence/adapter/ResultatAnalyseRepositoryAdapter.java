package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.ResultatAnalyseJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.ResultatAnalyseJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class ResultatAnalyseRepositoryAdapter implements ResultatAnalyseRepositoryPort {

    private final ResultatAnalyseJpaRepository jpa;

    public ResultatAnalyseRepositoryAdapter(ResultatAnalyseJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ResultatAnalyse> findByPatientId(UUID patientId, CenterId centerId, LocalDate from, LocalDate to) {
        List<ResultatAnalyseJpaEntity> rows;
        if (from != null && to != null) {
            rows = jpa.findByPatientIdAndCenterIdAndDatePrelevementBetweenOrderByDatePrelevementDesc(
                    patientId, centerId.value(), from, to);
        } else if (from != null) {
            rows = jpa.findByPatientIdAndCenterIdAndDatePrelevementGreaterThanEqualOrderByDatePrelevementDesc(
                    patientId, centerId.value(), from);
        } else if (to != null) {
            rows = jpa.findByPatientIdAndCenterIdAndDatePrelevementLessThanEqualOrderByDatePrelevementDesc(
                    patientId, centerId.value(), to);
        } else {
            rows = jpa.findByPatientIdAndCenterIdOrderByDatePrelevementDesc(patientId, centerId.value());
        }
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public ResultatAnalyse save(ResultatAnalyse resultat) {
        return toDomain(jpa.save(toJpa(resultat)));
    }

    @Override
    public void deleteById(UUID analyseId, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(analyseId, patientId, centerId.value());
    }

    private ResultatAnalyse toDomain(ResultatAnalyseJpaEntity e) {
        ResultatAnalyse r = new ResultatAnalyse();
        r.setId(e.getId());
        r.setPatientId(e.getPatientId());
        r.setCenterId(e.getCenterId());
        r.setDatePrelevement(e.getDatePrelevement());
        r.setHbGDl(e.getHbGDl());
        r.setHtPct(e.getHtPct());
        r.setPlaquettes(e.getPlaquettes());
        r.setFerritineNgMl(e.getFerritineNgMl());
        r.setCstfPct(e.getCstfPct());
        r.setEpoEndogeneMuiMl(e.getEpoEndogeneMuiMl());
        r.setUreePreMgDl(e.getUreePreMgDl());
        r.setUreePostMgDl(e.getUreePostMgDl());
        r.setCreatinineMgDl(e.getCreatinineMgDl());
        r.setKtVMensuel(e.getKtVMensuel());
        r.setPhosphoreMgDl(e.getPhosphoreMgDl());
        r.setCalciumMgDl(e.getCalciumMgDl());
        r.setPthPgMl(e.getPthPgMl());
        r.setAlbumineGDl(e.getAlbumineGDl());
        r.setProteinesGDl(e.getProteinesGDl());
        r.setCrpMgL(e.getCrpMgL());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedAt(e.getUpdatedAt());
        return r;
    }

    private ResultatAnalyseJpaEntity toJpa(ResultatAnalyse r) {
        ResultatAnalyseJpaEntity e = new ResultatAnalyseJpaEntity();
        e.setId(r.getId());
        e.setPatientId(r.getPatientId());
        e.setCenterId(r.getCenterId());
        e.setDatePrelevement(r.getDatePrelevement());
        e.setHbGDl(r.getHbGDl());
        e.setHtPct(r.getHtPct());
        e.setPlaquettes(r.getPlaquettes());
        e.setFerritineNgMl(r.getFerritineNgMl());
        e.setCstfPct(r.getCstfPct());
        e.setEpoEndogeneMuiMl(r.getEpoEndogeneMuiMl());
        e.setUreePreMgDl(r.getUreePreMgDl());
        e.setUreePostMgDl(r.getUreePostMgDl());
        e.setCreatinineMgDl(r.getCreatinineMgDl());
        e.setKtVMensuel(r.getKtVMensuel());
        e.setPhosphoreMgDl(r.getPhosphoreMgDl());
        e.setCalciumMgDl(r.getCalciumMgDl());
        e.setPthPgMl(r.getPthPgMl());
        e.setAlbumineGDl(r.getAlbumineGDl());
        e.setProteinesGDl(r.getProteinesGDl());
        e.setCrpMgL(r.getCrpMgL());
        e.setCreatedAt(r.getCreatedAt());
        e.setUpdatedAt(r.getUpdatedAt());
        return e;
    }
}

