package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.pilotagedirection.application.ImpressionDuRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Ligne;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers Documents (documents.v1, PL9, ADR 0148, ADR 0140 D3) : le rapport est rendu par le port {@link RenduDeDocument}, type
 * {@value #TYPE_DE_DOCUMENT}. Le gabarit ne lit aucune table : le Pilotage fournit les paramètres du titre et les lignes (champs
 * {@code SECTION}, {@code TITRE}, {@code GENRE}, {@code C1} à {@code C13}). Les refus du rendu (modèle désactivé, génération impossible)
 * remontent tels quels.
 */
public class RapportRenduParDocuments implements ImpressionDuRapport {

    public static final String TYPE_DE_DOCUMENT = "RAPPORT_DIRECTION";

    private final RenduDeDocument documents;

    public RapportRenduParDocuments(RenduDeDocument documents) {
        this.documents = documents;
    }

    @Override
    public Impression imprimer(UUID societe, UUID centre, PageDuRapport page) {
        DocumentRendu rendu = centre == null
                ? documents.rendrePourLaSociete(societe, TYPE_DE_DOCUMENT, parametres(page), "PDF")
                : documents.rendre(centre, TYPE_DE_DOCUMENT, parametres(page), "PDF");
        return new Impression(rendu.octets(), rendu.nomDeFichier());
    }

    /** Les paramètres du gabarit : titre, ligne de contexte, horodatage, note et les lignes ({@code LIGNES}). */
    public static Map<String, Object> parametres(PageDuRapport page) {
        Map<String, Object> parametres = new HashMap<>();
        parametres.put("RAPPORT_TITRE", page.titre());
        parametres.put("RAPPORT_SOCIETE", page.societe());
        parametres.put("RAPPORT_CONTEXTE", page.contexte());
        parametres.put("RAPPORT_HORODATAGE", page.horodatage());
        parametres.put("RAPPORT_NOTE", page.note());
        List<Map<String, Object>> lignes = new ArrayList<>();
        for (Ligne ligne : page.lignes()) {
            Map<String, Object> champs = new HashMap<>();
            champs.put("SECTION", ligne.section());
            champs.put("TITRE", ligne.titreDeSection());
            champs.put("GENRE", ligne.genre().name());
            for (int i = 0; i < PageDuRapport.CELLULES_MAX; i++) {
                champs.put("C" + (i + 1), i < ligne.cellules().size() ? ligne.cellules().get(i) : "");
            }
            lignes.add(champs);
        }
        parametres.put("LIGNES", lignes);
        return parametres;
    }
}
