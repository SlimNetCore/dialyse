package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.facturation.infrastructure.classeur.SyntheseEnClasseur;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FormatDImpression;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ImpressionDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ModeleDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;

/**
 * ACL vers Documents (documents.v1, PL8, ADR 0140 D3) : la synthèse est rendue par le port {@link RenduDeDocument}, type
 * {@value #TYPE_DE_DOCUMENT}, avec les données de la Facturation (le gabarit ne lit plus aucune table). Le classeur Excel est produit par la
 * Facturation elle-même (KB-PL8-4 corrigé), sans moteur de mise en page. Les refus du rendu (modèle désactivé, génération impossible)
 * remontent tels quels.
 */
public class SyntheseRendueParDocuments implements ImpressionDeLaSynthese {

    public static final String TYPE_DE_DOCUMENT = "SYNTHESE_FACTURATION_MENSUELLE";

    private final RenduDeDocument documents;

    public SyntheseRendueParDocuments(RenduDeDocument documents) {
        this.documents = documents;
    }

    @Override
    public byte[] imprimer(SyntheseMensuelle synthese, FormatDImpression format) {
        if (format.estUnClasseur()) {
            return SyntheseEnClasseur.classeur(synthese);
        }
        DocumentRendu rendu = documents.rendre(synthese.centre(), TYPE_DE_DOCUMENT, ModeleDeLaSynthese.parametres(synthese),
                format == FormatDImpression.HTML ? "HTML" : "PDF");
        return rendu.octets();
    }
}
