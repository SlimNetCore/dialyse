package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Une analyse, champ par champ comme le legacy. */
public record AnalyseReponse(UUID id, UUID patientId, UUID centerId, LocalDate datePrelevement, BigDecimal hbGDl, BigDecimal htPct,
                             Integer plaquettes, BigDecimal ferritineNgMl, BigDecimal cstfPct, BigDecimal epoEndogeneMuiMl,
                             BigDecimal ureePreMgDl, BigDecimal ureePostMgDl, BigDecimal creatinineMgDl, BigDecimal ktVMensuel,
                             BigDecimal phosphoreMgDl, BigDecimal calciumMgDl, BigDecimal pthPgMl, BigDecimal albumineGDl,
                             BigDecimal proteinesGDl, BigDecimal crpMgL, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static AnalyseReponse de(Analyse a) {
        var m = a.mesures();
        return new AnalyseReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.datePrelevement(), m.hemoglobine(), m.hematocrite(),
                m.plaquettes(), m.ferritine(), m.coefficientDeSaturation(), m.epoEndogene(), m.ureeAvantSeance(), m.ureeApresSeance(),
                m.creatinine(), m.ktVMensuel(), m.phosphore(), m.calcium(), m.pth(), m.albumine(), m.proteines(), m.crp(),
                horodatage(a.creeLe()), horodatage(a.modifieLe()));
    }
}
