package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.TypesDeDocumentDeLaPlanification;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link ImpressionDuCalendrierDeLaProposition} (ACL vers les modèles de
 * documents et le moteur Jasper du legacy) : le planning calendaire figé d'une proposition, modèle
 * {@code PLANNING_OPTIMISE} ({@link TypesDeDocumentDeLaPlanification}), imprimé via {@link MoteurDeRendu}. Un refus du moteur (modèle désactivé...)
 * est rendu en refus métier du contexte.
 */
@Component
public class ImpressionDuCalendrierDeLaPropositionLegacy implements ImpressionDuCalendrierDeLaProposition {

    private final MoteurDeRendu moteur;

    public ImpressionDuCalendrierDeLaPropositionLegacy(MoteurDeRendu moteur) {
        this.moteur = moteur;
    }

    static Map<String, Object> parametres(UUID execution, String titre, String resume) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        p.put("RUN_ID", execution.toString());
        p.put("TITRE_PROPOSITION", titre);
        p.put("RESUME_INDICATEURS", resume);
        return p;
    }

    @Override
    public Document imprimer(UUID centre, UUID execution, String titre, String resume) {
        try {
            MoteurDeRendu.Rendu doc = moteur.rendreParType(centre, TypesDeDocumentDeLaPlanification.PLANNING_OPTIMISE.code(),
                    parametres(execution, titre, resume), null);
            return new Document(doc.octets(), doc.format());
        } catch (RenduRefuse e) {
            throw new ImpressionRefusee(e.code(), e.getMessage());
        }
    }
}
