package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ModelesEnMemoire;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.UUID;

/** Le double en mémoire passe le contrat. */
class ModelesDeDocumentEnMemoireContratTest extends ModelesDeDocumentContrat {

    private ModelesEnMemoire memoire;

    @BeforeEach
    void vider() {
        memoire = new ModelesEnMemoire();
    }

    @Override
    protected ModelesDeDocument modeles() {
        return memoire;
    }

    @Override
    protected void modele(UUID id, UUID centre, String type, String chemin, String format, boolean actif, Instant creeLe) {
        memoire.ajouter(centre, new ModeleDeDocument(id, chemin, format, type), actif, creeLe);
    }
}
