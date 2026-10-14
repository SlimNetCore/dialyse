package com.hemodialyse.backend.contexts.gmao.application.pilotage;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Port sortant de lecture : le coût de maintenance, somme des lignes de coût (quantité fois prix, sans arrondi) des
 * interventions en cours ou terminées, non supprimées, <b>démarrées</b> sur la période [du, au). Le coût est nul sans ligne.
 * La lecture par équipement ne regarde pas le centre de l'intervention (KB-GMAO-71).
 */
public interface LectureDesCouts {

    BigDecimal deLEquipement(EquipementId equipement, Instant du, Instant au);

    /** Depuis l'installation : sans borne de période. */
    BigDecimal cumuleDeLEquipement(EquipementId equipement);

    BigDecimal duCentre(CentreId centre, Instant du, Instant au);

    /** Par équipement du centre ; les équipements sans coût sont absents. */
    Map<EquipementId, BigDecimal> parEquipement(CentreId centre, Instant du, Instant au);

    Map<EquipementId, BigDecimal> cumuleParEquipement(CentreId centre);
}
