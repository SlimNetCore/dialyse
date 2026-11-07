package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Corps de l'enregistrement ou du remplacement d'une analyse (mêmes champs que le legacy). */
public record AnalyseDemande(UUID centerId, LocalDate datePrelevement, BigDecimal hbGDl, BigDecimal htPct, Integer plaquettes,
                             BigDecimal ferritineNgMl, BigDecimal cstfPct, BigDecimal epoEndogeneMuiMl, BigDecimal ureePreMgDl,
                             BigDecimal ureePostMgDl, BigDecimal creatinineMgDl, BigDecimal ktVMensuel, BigDecimal phosphoreMgDl,
                             BigDecimal calciumMgDl, BigDecimal pthPgMl, BigDecimal albumineGDl, BigDecimal proteinesGDl, BigDecimal crpMgL) {
    public MesuresDuBilan mesures() {
        return new MesuresDuBilan(hbGDl, htPct, plaquettes, ferritineNgMl, cstfPct, epoEndogeneMuiMl, ureePreMgDl, ureePostMgDl, creatinineMgDl,
                ktVMensuel, phosphoreMgDl, calciumMgDl, pthPgMl, albumineGDl, proteinesGDl, crpMgL);
    }
}
