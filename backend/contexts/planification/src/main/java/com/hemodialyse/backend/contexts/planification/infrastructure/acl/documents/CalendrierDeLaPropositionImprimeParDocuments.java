package com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.TypesDeDocumentDeLaPlanification;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ACL de la Planification vers Documents (documents.v1, PLN-DOC, ADR 0167) : le planning calendaire figé d'une proposition, type
 * {@code PLANNING_OPTIMISE}, est rendu par {@link RenduDeDocument} dans le format du modèle du centre. Un refus du rendu (modèle désactivé,
 * génération impossible) est rendu en refus métier du contexte. Le type MIME rendu est traduit en format du contexte : tableur
 * {@code EXCEL}, page {@code HTML}, tout le reste {@code PDF} (comme le contrôleur servait déjà les formats inconnus).
 */
public class CalendrierDeLaPropositionImprimeParDocuments implements ImpressionDuCalendrierDeLaProposition {

    private static final String TABLEUR = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RenduDeDocument documents;

    public CalendrierDeLaPropositionImprimeParDocuments(RenduDeDocument documents) {
        this.documents = documents;
    }

    static Map<String, Object> parametres(UUID execution, String titre, String resume) {
        Map<String, Object> p = new HashMap<>();
        p.put(PresenceImprimeeParDocuments.REPORT_LOCALE, Locale.FRANCE);
        p.put("RUN_ID", execution.toString());
        p.put("TITRE_PROPOSITION", titre);
        p.put("RESUME_INDICATEURS", resume);
        return p;
    }

    static String format(String typeMime) {
        if (TABLEUR.equals(typeMime)) return "EXCEL";
        return "text/html".equals(typeMime) ? "HTML" : "PDF";
    }

    @Override
    public Document imprimer(UUID centre, UUID execution, String titre, String resume) {
        try {
            DocumentRendu rendu = documents.rendre(centre, TypesDeDocumentDeLaPlanification.PLANNING_OPTIMISE.code(),
                    parametres(execution, titre, resume), null);
            return new Document(rendu.octets(), format(rendu.typeMime()));
        } catch (RenduRefuse e) {
            throw new ImpressionRefusee(e.code(), e.getMessage());
        }
    }
}
