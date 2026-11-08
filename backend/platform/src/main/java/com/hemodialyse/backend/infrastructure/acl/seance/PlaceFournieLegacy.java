package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlaceFournie;
import com.hemodialyse.backend.domain.seance.port.SeancePlacePort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link PlaceFournie} (ACL temporaire, retrait avec SE7) : enveloppe la lecture de la place du
 * legacy ({@link SeancePlacePort} : déplacement temporaire du jour, puis place de la fiche lue chez Planification).
 */
@Component
public class PlaceFournieLegacy implements PlaceFournie {

    private final SeancePlacePort places;

    public PlaceFournieLegacy(SeancePlacePort places) {
        this.places = places;
    }

    @Override
    public Optional<Place> placeLe(UUID centre, UUID patient, LocalDate jour) {
        return places.placeLe(CenterId.of(centre), patient, jour).map(p -> new Place(p.salleId(), p.creneauId(), p.generateurId()));
    }
}
