package com.hemodialyse.backend.domain.seance.service;

import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseUseCase;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Domain Service — Résultats d'analyse. Pure domain class (hexagonal, AGENTS.md §3);
 * wired in {@code infrastructure/config/DomainServiceConfig}.
 */
public class ResultatAnalyseDomainService implements ResultatAnalyseUseCase {

    private final ResultatAnalyseRepositoryPort repository;

    public ResultatAnalyseDomainService(ResultatAnalyseRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<ResultatAnalyse> listByPatient(CenterId centerId, UUID patientId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La date from doit être <= à la date to");
        }
        return repository.findByPatientId(patientId, centerId, from, to);
    }

    @Override
    public ResultatAnalyse save(CenterId centerId,
                                UUID patientId,
                                UUID analyseId,
                                LocalDate datePrelevement,
                                BigDecimal hbGDl,
                                BigDecimal htPct,
                                Integer plaquettes,
                                BigDecimal ferritineNgMl,
                                BigDecimal cstfPct,
                                BigDecimal epoEndogeneMuiMl,
                                BigDecimal ureePreMgDl,
                                BigDecimal ureePostMgDl,
                                BigDecimal creatinineMgDl,
                                BigDecimal ktVMensuel,
                                BigDecimal phosphoreMgDl,
                                BigDecimal calciumMgDl,
                                BigDecimal pthPgMl,
                                BigDecimal albumineGDl,
                                BigDecimal proteinesGDl,
                                BigDecimal crpMgL) {
        ResultatAnalyse r = new ResultatAnalyse();
        r.setId(analyseId != null ? analyseId : UUID.randomUUID());
        r.setPatientId(patientId);
        r.setCenterId(centerId.value());
        r.setDatePrelevement(datePrelevement != null ? datePrelevement : LocalDate.now());
        r.setHbGDl(hbGDl);
        r.setHtPct(htPct);
        r.setPlaquettes(plaquettes);
        r.setFerritineNgMl(ferritineNgMl);
        r.setCstfPct(cstfPct);
        r.setEpoEndogeneMuiMl(epoEndogeneMuiMl);
        r.setUreePreMgDl(ureePreMgDl);
        r.setUreePostMgDl(ureePostMgDl);
        r.setCreatinineMgDl(creatinineMgDl);
        r.setKtVMensuel(ktVMensuel);
        r.setPhosphoreMgDl(phosphoreMgDl);
        r.setCalciumMgDl(calciumMgDl);
        r.setPthPgMl(pthPgMl);
        r.setAlbumineGDl(albumineGDl);
        r.setProteinesGDl(proteinesGDl);
        r.setCrpMgL(crpMgL);
        OffsetDateTime now = OffsetDateTime.now();
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return repository.save(r);
    }

    @Override
    public void delete(CenterId centerId, UUID patientId, UUID analyseId) {
        repository.deleteById(analyseId, patientId, centerId);
    }
}

