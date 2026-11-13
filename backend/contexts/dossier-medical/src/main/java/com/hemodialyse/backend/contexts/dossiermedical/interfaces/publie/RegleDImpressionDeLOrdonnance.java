package com.hemodialyse.backend.contexts.dossiermedical.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.application.GarderLImpressionDeLOrdonnance;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * La règle d'impression de l'ordonnance, publiée à Documents (documents.v1, DOC4c, ADR 0166) : profil de lecture des ordonnances et centre du
 * corps, avant tout accès aux données, sur la voie par type. Sur la voie par modèle l'ordonnance n'est pas gardée par profil (comportement
 * historique reproduit, question ouverte de l'ADR 0142). Le refus est celui de la plateforme (403 {@code ACCESS_DENIED}, même corps).
 */
public final class RegleDImpressionDeLOrdonnance implements RegleDImpressionDUnType {

    public static final String TYPE = "ORDONNANCE";

    private final GarderLImpressionDeLOrdonnance garde;

    public RegleDImpressionDeLOrdonnance(GarderLImpressionDeLOrdonnance garde) {
        this.garde = garde;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void garder(UUID centreDemande) {
        try {
            garde.executer(centreDemande);
        } catch (AccesRefuse refus) {
            throw new AccessDeniedException(refus.getMessage(), refus);
        }
    }
}
