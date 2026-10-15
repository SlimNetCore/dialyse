package com.hemodialyse.backend.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ParametrageFiscal;
import com.hemodialyse.backend.domain.comptabilite.port.ParametrageFiscalPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.RegleTVA;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL temporaire (retirée avec la slice C7) : les règles de TVA sont tenues par le contexte {@code comptabilite} (C2) ; les
 * Absences ({@code ForfaitAbsenceJdbcAdapter}, inchangé) lisent la règle applicable par le langage publié, traduite dans
 * l'ancien modèle {@link RegleTVA}.
 */
@Component
public class ParametrageFiscalDuContexte implements ParametrageFiscalPort {

    private final ParametrageFiscal parametrage;

    public ParametrageFiscalDuContexte(ParametrageFiscal parametrage) {
        this.parametrage = parametrage;
    }

    @Override
    public Optional<RegleTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date) {
        return parametrage.regleApplicable(centerId, typePrestation, date)
                .map(r -> new RegleTVA(r.typePrestation(), r.taux(), r.exonere(), r.debutValidite(), r.finValidite(),
                        r.texteReference()));
    }
}
