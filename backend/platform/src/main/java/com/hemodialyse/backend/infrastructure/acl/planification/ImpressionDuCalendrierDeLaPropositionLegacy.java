package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentCatalog;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fournit au contexte Planification son port consommé {@link ImpressionDuCalendrierDeLaProposition} (ACL vers les modèles de
 * documents et le moteur Jasper du legacy) : le planning calendaire figé d'une proposition, modèle
 * {@value ModeleDocumentCatalog#PLANNING_OPTIMISE}, imprimé via {@link ModeleDocumentPrinter}. Un refus du moteur (modèle désactivé...)
 * est rendu en refus métier du contexte.
 */
@Component
public class ImpressionDuCalendrierDeLaPropositionLegacy implements ImpressionDuCalendrierDeLaProposition {

    private final ModeleDocumentPrinter printer;

    public ImpressionDuCalendrierDeLaPropositionLegacy(ModeleDocumentPrinter printer) {
        this.printer = printer;
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
            ModeleDocumentPrinter.Document doc = printer.print(centre, ModeleDocumentCatalog.PLANNING_OPTIMISE,
                    parametres(execution, titre, resume));
            return new Document(doc.content(), doc.format());
        } catch (BusinessException e) {
            throw new ImpressionRefusee(e.getCode(), e.getMessage());
        }
    }
}
