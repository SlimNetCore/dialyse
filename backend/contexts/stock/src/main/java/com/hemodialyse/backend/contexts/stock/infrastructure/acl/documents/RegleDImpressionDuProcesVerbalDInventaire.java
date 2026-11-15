package com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La règle d'impression du procès-verbal d'inventaire, publiée à Documents (documents.v1, DOC5j, ADR 0202) pour l'impression directe par
 * type ou par modèle ({@code POST /api/v1/documents/print}) : le type n'est PAS gardé (comportement reproduit) ; le gabarit n'embarque plus de
 * requête, le serveur pose les lignes ({@code LIGNES}) de l'inventaire désigné par le paramètre {@code INVENTAIRE_ID}, parmi ceux du centre
 * demandé. Un identifiant absent ne désigne rien (aucune ligne) ; présent mais vide ou illisible, c'est un échec d'impression (500) au
 * texte de l'ancienne erreur SQL ({@code CAST} sans {@code NULLIF}). Le procès-verbal du Stock ne passe pas par cette règle : il fournit
 * lui-même ses lignes.
 */
public final class RegleDImpressionDuProcesVerbalDInventaire implements RegleDImpressionDUnType {

    private final Inventaires inventaires;

    public RegleDImpressionDuProcesVerbalDInventaire(Inventaires inventaires) {
        this.inventaires = inventaires;
    }

    @Override
    public String type() {
        return ProcesVerbalRenduParDocuments.TYPE_DE_DOCUMENT;
    }

    @Override
    public void garder(UUID centreDemande) {
        // Aucune garde : le type n'en avait pas avant la conversion et n'en reçoit pas (comportement reproduit).
    }

    @Override
    public Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
        UUID inventaire = lire(parametres.get("INVENTAIRE_ID"));
        List<Map<String, Object>> lignes = inventaire == null ? List.of()
                : inventaires.parId(new CentreId(centre), inventaire)
                .map(i -> LignesDuProcesVerbal.de(i.lignes(), LocalDate.now())).orElse(List.of());
        return Map.of("LIGNES", lignes);
    }

    private static UUID lire(String brut) {
        if (brut == null) return null;
        try {
            return UUID.fromString(brut);
        } catch (IllegalArgumentException illisible) {
            throw new IllegalStateException("Error executing SQL statement for: inventaire_stock.", illisible);
        }
    }
}
