package com.hemodialyse.backend.contexts.stock.infrastructure.acl.centres;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.peremptions.CentresActifs;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.CentresFournis;

import java.util.List;

/**
 * Les centres actifs du Stock, tels que la plateforme les lui fournit ({@link CentresFournis}) : au sens strict, un état non renseigné
 * est exclu (O4-R3). Remplace {@code CentresActifsLegacy} (SQL direct).
 */
public class CentresActifsFournis implements CentresActifs {

    private final CentresFournis centres;

    public CentresActifsFournis(CentresFournis centres) {
        this.centres = centres;
    }

    @Override
    public List<CentreId> liste() {
        return centres.centresActifsAuSensStrict().stream().map(CentreId::new).toList();
    }
}
