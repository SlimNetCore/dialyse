package com.hemodialyse.backend.contexts.dossiermedical.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.dossiermedical.application.AccesRefuse;
import com.hemodialyse.backend.contexts.dossiermedical.application.ComposerLesLignesDeLOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.application.GarderLImpressionDeLOrdonnance;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.UUID;

/**
 * La règle d'impression de l'ordonnance, publiée à Documents (documents.v1, DOC4c, ADR 0166) : profil de lecture des ordonnances et centre du
 * corps, avant tout accès aux données, sur la voie par type. Sur la voie par modèle l'ordonnance n'est pas gardée par profil (comportement
 * historique reproduit, question ouverte de l'ADR 0142). Le refus est celui de la plateforme (403 {@code ACCESS_DENIED}, même corps).
 *
 * <p>DOC5d (ADR 0196) : le gabarit n'embarque plus de requête, le serveur pose les lignes de l'état ({@code LIGNES}) composées par le
 * Dossier médical, sur les deux voies. Un identifiant absent donne un état vide ; un identifiant présent mais illisible (voie par modèle
 * seulement : la voie par type le refuse en 400 avant) reste un échec d'impression (500) au même texte que l'ancienne erreur SQL.
 */
public final class RegleDImpressionDeLOrdonnance implements RegleDImpressionDUnType {

    public static final String TYPE = "ORDONNANCE";

    /** Texte historique du corps du 500 (l'ancienne requête échouait sur le {@code CAST} de l'identifiant), reproduit (KB-DOC5D-2). */
    static final String ECHEC_HISTORIQUE = "Error executing SQL statement for: ordonnance.";

    private final GarderLImpressionDeLOrdonnance garde;
    private final ComposerLesLignesDeLOrdonnance lignes;

    public RegleDImpressionDeLOrdonnance(GarderLImpressionDeLOrdonnance garde, ComposerLesLignesDeLOrdonnance lignes) {
        this.garde = garde;
        this.lignes = lignes;
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

    @Override
    public Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
        String brut = parametres.get("ordonnanceId");
        UUID ordonnance = null;
        if (brut != null) {
            try {
                ordonnance = UUID.fromString(brut);
            } catch (IllegalArgumentException illisible) {
                throw new IllegalStateException(ECHEC_HISTORIQUE, illisible);
            }
        }
        return Map.of("LIGNES", ChampsDeLOrdonnanceImprimee.champs(lignes.executer(centre, ordonnance)));
    }
}
