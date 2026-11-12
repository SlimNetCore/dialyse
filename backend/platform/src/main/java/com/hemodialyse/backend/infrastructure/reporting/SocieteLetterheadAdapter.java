package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.direction.SocieteLetterheadPort;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fournit l'en-tête et le pied de page d'une société à partir du papier en-tête du contexte Documents
 * ({@link PapierEnTeteDuCentre}, DOC1), pour que tous les documents d'une société se ressemblent.
 */
@Component
public class SocieteLetterheadAdapter implements SocieteLetterheadPort {

    private final PapierEnTeteDuCentre papier;

    public SocieteLetterheadAdapter(PapierEnTeteDuCentre papier) {
        this.papier = papier;
    }

    @Override
    public Optional<Letterhead> forSociete(UUID societeId) {
        return papier.enTeteDeLaSociete(societeId)
                .map(e -> new Letterhead(e.nom(), e.contact(), e.legal(), e.piedDePage(), e.logo()));
    }
}
