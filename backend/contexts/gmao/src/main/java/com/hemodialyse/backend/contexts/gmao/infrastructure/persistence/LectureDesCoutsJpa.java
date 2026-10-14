package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Adapter JPA du read model {@link LectureDesCouts} : somme calculée par la base, sur les lignes de coût des interventions. */
public class LectureDesCoutsJpa implements LectureDesCouts {

    private static final Instant DEPUIS_TOUJOURS = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant SANS_LIMITE = Instant.parse("9999-01-01T00:00:00Z");

    private final LigneDeCoutSpringData lignes;

    public LectureDesCoutsJpa(LigneDeCoutSpringData lignes) {
        this.lignes = lignes;
    }

    @Override
    public BigDecimal deLEquipement(EquipementId equipement, Instant du, Instant au) {
        BigDecimal somme = lignes.sommeDeLEquipement(equipement.valeur(), du, au);
        return somme == null ? BigDecimal.ZERO : somme;
    }

    @Override
    public BigDecimal cumuleDeLEquipement(EquipementId equipement) {
        return deLEquipement(equipement, DEPUIS_TOUJOURS, SANS_LIMITE);
    }

    @Override
    public BigDecimal duCentre(CentreId centre, Instant du, Instant au) {
        BigDecimal somme = lignes.sommeDuCentre(centre.valeur(), du, au);
        return somme == null ? BigDecimal.ZERO : somme;
    }

    @Override
    public Map<EquipementId, BigDecimal> parEquipement(CentreId centre, Instant du, Instant au) {
        Map<EquipementId, BigDecimal> sommes = new HashMap<>();
        for (Object[] ligne : lignes.sommesParEquipement(centre.valeur(), du, au)) {
            sommes.put(new EquipementId((java.util.UUID) ligne[0]), (BigDecimal) ligne[1]);
        }
        return sommes;
    }

    @Override
    public Map<EquipementId, BigDecimal> cumuleParEquipement(CentreId centre) {
        return parEquipement(centre, DEPUIS_TOUJOURS, SANS_LIMITE);
    }
}
