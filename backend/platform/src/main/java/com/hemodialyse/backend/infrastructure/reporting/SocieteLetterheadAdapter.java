package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.direction.SocieteLetterheadPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Fournit l'en-tête et le pied de page d'une société à partir de la même identité que les autres documents
 * imprimés ({@link DocumentIdentityProvider}), pour que tous les documents d'une société se ressemblent.
 */
@Component
public class SocieteLetterheadAdapter implements SocieteLetterheadPort {

    private final DocumentIdentityProvider identity;

    public SocieteLetterheadAdapter(DocumentIdentityProvider identity) {
        this.identity = identity;
    }

    @Override
    public Optional<Letterhead> forSociete(UUID societeId) {
        return identity.societeIdentity(societeId)
                .map(i -> new Letterhead(i.nom(), i.contact(), i.legal(), i.piedPage(), i.logo()));
    }
}
