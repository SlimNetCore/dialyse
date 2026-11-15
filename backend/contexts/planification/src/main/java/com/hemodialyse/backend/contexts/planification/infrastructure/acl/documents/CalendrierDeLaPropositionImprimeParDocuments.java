package com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.application.CaseImprimableDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.TypesDeDocumentDeLaPlanification;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ACL de la Planification vers Documents (documents.v1, PLN-DOC, ADR 0167) : le planning calendaire figé d'une proposition, type
 * {@code PLANNING_OPTIMISE}, est rendu par {@link RenduDeDocument} dans le format du modèle du centre. Un refus du rendu (modèle désactivé,
 * génération impossible) est rendu en refus métier du contexte. Le type MIME rendu est traduit en format du contexte : tableur
 * {@code EXCEL}, page {@code HTML}, tout le reste {@code PDF} (comme le contrôleur servait déjà les formats inconnus).
 * <p>DOC5c (ADR 0195) : le gabarit n'embarque plus de requête ; la Planification lui fournit les lignes du calendrier figé ({@code LIGNES},
 * {@link #lignes}, une par salle et créneau de chaque semaine, dans l'ordre semaine, créneau, salle). Un modèle personnalisé stocké qui
 * porte encore l'ancienne requête reçoit ces mêmes lignes (la source de données prime) : mêmes noms de champs.
 */
public class CalendrierDeLaPropositionImprimeParDocuments implements ImpressionDuCalendrierDeLaProposition {

    private static final String TABLEUR = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RenduDeDocument documents;
    private final CalendriersDesPropositions calendriers;

    public CalendrierDeLaPropositionImprimeParDocuments(RenduDeDocument documents, CalendriersDesPropositions calendriers) {
        this.documents = documents;
        this.calendriers = calendriers;
    }

    static Map<String, Object> parametres(UUID execution, String titre, String resume, List<Map<String, Object>> lignes) {
        Map<String, Object> p = new HashMap<>();
        p.put(PresenceImprimeeParDocuments.REPORT_LOCALE, Locale.FRANCE);
        p.put("RUN_ID", execution.toString());
        p.put("TITRE_PROPOSITION", titre);
        p.put("RESUME_INDICATEURS", resume);
        p.put("LIGNES", lignes);
        return p;
    }

    /**
     * Les lignes du calendrier traduites en champs du gabarit, dans les types qu'il déclare ({@code java.sql.Date} pour le début de semaine,
     * entier pour l'ordre du créneau, texte sinon ; un jour absent de la semaine reste nul).
     */
    static List<Map<String, Object>> lignes(List<CaseImprimableDuCalendrier> cases) {
        return cases.stream().map(c -> {
            Map<String, Object> champs = new HashMap<>();
            champs.put("SEMAINE_DEBUT", Date.valueOf(c.semaineDebut()));
            champs.put("SALLE_NOM", c.salleNom());
            champs.put("CRENEAU_LIBELLE", c.creneauLibelle());
            champs.put("CRENEAU_ORDRE", c.creneauOrdre());
            for (int i = 0; i < CaseImprimableDuCalendrier.NB_JOURS; i++) {
                champs.put("ENTETE_" + i, c.entetes().get(i));
                champs.put("CELL_" + i, c.cellules().get(i));
            }
            return champs;
        }).toList();
    }

    static String format(String typeMime) {
        if (TABLEUR.equals(typeMime)) return "EXCEL";
        return "text/html".equals(typeMime) ? "HTML" : "PDF";
    }

    @Override
    public Document imprimer(UUID centre, UUID execution, String titre, String resume) {
        try {
            List<Map<String, Object>> lignes = lignes(calendriers.imprimables(new CentreId(centre), new ExecutionId(execution)));
            DocumentRendu rendu = documents.rendre(centre, TypesDeDocumentDeLaPlanification.PLANNING_OPTIMISE.code(),
                    parametres(execution, titre, resume, lignes), null);
            return new Document(rendu.octets(), format(rendu.typeMime()));
        } catch (RenduRefuse e) {
            throw new ImpressionRefusee(e.code(), e.getMessage());
        }
    }
}
