package com.hemodialyse.backend.contexts.documents.interfaces.publie;

import com.hemodialyse.backend.contexts.documents.application.RendreUnDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;

import java.util.Map;
import java.util.UUID;

/** Adapter entrant du langage publié {@link RenduDeDocument}. */
public class RenduDeDocumentPublie implements RenduDeDocument {

    private final RendreUnDocument rendre;

    public RenduDeDocumentPublie(RendreUnDocument rendre) {
        this.rendre = rendre;
    }

    @Override
    public DocumentRendu rendre(UUID centre, String typeDeDocument, Map<String, Object> donnees, String format) {
        return rendre.rendre(centre, typeDeDocument, donnees, format);
    }
}
