package com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence;
import com.hemodialyse.backend.contexts.planification.application.PlanningDePresenceImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.TypesDeDocumentDeLaPlanification;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ACL de la Planification vers Documents (documents.v1, PLN-DOC, ADR 0167) : le planning de présence est rendu par le port
 * {@link RenduDeDocument} en « lecture sans écriture » (KB-PLN-51) : un centre sans modèle {@code PLANNING_PRESENCE_INFIRMIERS} utilise le
 * gabarit livré en mémoire, un modèle désactivé refuse (422 {@code DOCUMENT_MODEL_INACTIVE}), rien n'est provisionné. DOC5e (ADR 0197) : le
 * gabarit n'embarque plus de requête ; la Planification lui fournit les lignes du planning de la semaine ({@code LIGNES}, roulement,
 * absences et remplacements du centre pour les sept jours). Un modèle personnalisé stocké qui porte encore l'ancienne requête reçoit ces
 * mêmes lignes (la source de données prime). Les sept dates restent passées en paramètres. Un format que le contexte ne sert pas
 * n'est pas généré (le contexte le refuse en 400). Aucun accès à la table des modèles : elle appartient à Documents.
 */
public class PresenceImprimeeParDocuments implements ImpressionDeLaPresence {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PresenceImprimeeParDocuments.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final Set<String> SERVIS = Set.of("PDF", "HTML", "EXCEL", "XLS", "XLSX");
    /** Valeur de {@code JRParameter.REPORT_LOCALE} (le contexte ne dépend pas de Jasper). */
    static final String REPORT_LOCALE = "REPORT_LOCALE";

    private final RenduDeDocument documents;
    private final PlanningDePresenceImprimable planning;

    public PresenceImprimeeParDocuments(RenduDeDocument documents, PlanningDePresenceImprimable planning) {
        this.documents = documents;
        this.planning = planning;
    }

    /**
     * Les lignes du planning traduites en champs du gabarit, dans les types qu'il déclare ({@code java.sql.Date} pour le jour, texte
     * sinon ; la situation est imprimée par son libellé).
     */
    static List<Map<String, Object>> lignes(List<LigneDuPlanningDePresence> lignes) {
        return lignes.stream().map(l -> {
            Map<String, Object> champs = new HashMap<>();
            champs.put("DATE_JOUR", Date.valueOf(l.jour()));
            champs.put("SALLE", l.salle());
            champs.put("CRENEAU_CODE", l.creneauCode());
            champs.put("CRENEAU", l.creneau());
            champs.put("INFIRMIER", l.infirmier());
            champs.put("STATUT", l.situation().libelle());
            return champs;
        }).toList();
    }

    static Map<String, Object> parametres(LocalDate debut, List<Map<String, Object>> lignes) {
        Map<String, Object> p = new HashMap<>();
        p.put("LIGNES", lignes);
        p.put(REPORT_LOCALE, Locale.FRANCE);
        for (int i = 0; i < 7; i++) {
            p.put("DATE_" + i, Date.valueOf(debut.plusDays(i)));
        }
        p.put("SEMAINE_LABEL", "Semaine du " + JOUR.format(debut) + " au " + JOUR.format(debut.plusDays(6)));
        return p;
    }

    @Override
    public Document imprimer(UUID centre, LocalDate debutDeSemaine) {
        String type = TypesDeDocumentDeLaPlanification.PLANNING_PRESENCE_INFIRMIERS.code();
        String format;
        try {
            format = documents.formatDImpression(centre, type);
            if (!SERVIS.contains(format.trim().toUpperCase(Locale.ROOT))) {
                return new Document(new byte[0], format);
            }
            List<Map<String, Object>> lignes = lignes(planning.lignes(new CentreId(centre), debutDeSemaine));
            DocumentRendu rendu = documents.rendreSansProvisionner(centre, type, parametres(debutDeSemaine, lignes), format);
            return new Document(rendu.octets(), format);
        } catch (RenduRefuse e) {
            if (!"DOCUMENT_GENERATION_FAILED".equals(e.code())) throw new ImpressionRefusee(e.code(), e.getMessage());
            String libelle = TypesDeDocumentDeLaPlanification.PLANNING_PRESENCE_INFIRMIERS.libelle();
            log.warn("[PRESENCE] Génération de « {} » impossible pour le centre {} : {}", libelle, centre, e.getMessage(), e);
            throw new ImpressionRefusee("DOCUMENT_GENERATION_FAILED", "Impossible de générer « " + libelle
                    + " » : voir le journal du serveur");
        }
    }
}
