package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.FormatDeSortie;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;

import java.util.Map;
import java.util.UUID;

/** Cas d'usage du port publié {@code RenduDeDocument} : rend le document d'un type pour un centre via le moteur transitoire. */
public class RendreUnDocument {

    private final MoteurDeRendu moteur;

    public RendreUnDocument(MoteurDeRendu moteur) {
        this.moteur = moteur;
    }

    public DocumentRendu rendrePourLaSociete(UUID societe, String typeDeDocument, Map<String, Object> donnees, String format) {
        MoteurDeRendu.Rendu rendu = moteur.rendreParTypePourLaSociete(societe, typeDeDocument, donnees, format);
        FormatDeSortie sortie = FormatDeSortie.de(rendu.format());
        return new DocumentRendu(rendu.octets(), sortie.typeMime(), sortie.nomDeFichier(typeDeDocument.trim()));
    }

    public DocumentRendu rendre(UUID centre, String typeDeDocument, Map<String, Object> donnees, String format) {
        MoteurDeRendu.Rendu rendu = moteur.rendreParType(centre, typeDeDocument, donnees, format);
        FormatDeSortie sortie = FormatDeSortie.de(rendu.format());
        return new DocumentRendu(rendu.octets(), sortie.typeMime(), sortie.nomDeFichier(typeDeDocument.trim()));
    }
}
