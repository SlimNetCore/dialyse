package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import com.hemodialyse.backend.infrastructure.reporting.FrenchFormatFactory;
import com.hemodialyse.backend.infrastructure.reporting.JasperReportService;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentCatalog;
import com.hemodialyse.backend.infrastructure.reporting.ModeleDocumentPrinter;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
 * Fournit au contexte Planification son port consommé {@link ImpressionDeLaPresence} (ACL vers les modèles de documents et le moteur
 * Jasper du legacy). Contrairement à {@link ModeleDocumentPrinter#print}, elle LIT sans écrire (KB-PLN-51, décision du 27/10/2026) :
 * un centre sans modèle « {@value ModeleDocumentCatalog#PLANNING_PRESENCE_INFIRMIERS} » utilise le modèle livré (PDF) en mémoire ;
 * un modèle désactivé refuse l'impression (422 {@code DOCUMENT_MODEL_INACTIVE}). Le modèle lit le roulement, les absences et les
 * remplacements du centre pour les sept dates passées en paramètres. Un format que le contexte ne sert pas n'est pas généré.
 */
@Component
public class ImpressionDeLaPresenceLegacy implements ImpressionDeLaPresence {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ImpressionDeLaPresenceLegacy.class);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final Set<String> SERVIS = Set.of("PDF", "HTML", "EXCEL", "XLS", "XLSX");

    private final JdbcTemplate jdbc;
    private final JasperReportService jasper;
    private final ModeleDocumentPrinter printer;
    private final PapierEnTeteDuCentre identite;

    public ImpressionDeLaPresenceLegacy(JdbcTemplate jdbc, JasperReportService jasper, ModeleDocumentPrinter printer,
                                        PapierEnTeteDuCentre identite) {
        this.jdbc = jdbc;
        this.jasper = jasper;
        this.printer = printer;
        this.identite = identite;
    }

    static Map<String, Object> parametres(LocalDate debut) {
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, Locale.FRANCE);
        for (int i = 0; i < 7; i++) {
            p.put("DATE_" + i, Date.valueOf(debut.plusDays(i)));
        }
        p.put("SEMAINE_LABEL", "Semaine du " + JOUR.format(debut) + " au " + JOUR.format(debut.plusDays(6)));
        return p;
    }

    @Override
    public Document imprimer(UUID centre, LocalDate debutDeSemaine) {
        ModeleDocumentCatalog.Entry entree = ModeleDocumentCatalog.find(ModeleDocumentCatalog.PLANNING_PRESENCE_INFIRMIERS).orElseThrow();
        List<Map<String, Object>> actifs = jdbc.queryForList(
                "SELECT id, chemin_jrxml, format_impression FROM modele_document "
                        + "WHERE center_id = ? AND UPPER(type_document) = ? AND active = TRUE ORDER BY created_at DESC",
                centre, entree.type());
        UUID modele = null;
        String chemin = entree.cheminJrxml();
        String format = "PDF";
        if (actifs.isEmpty()) {
            Integer existants = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM modele_document WHERE center_id = ? AND (UPPER(type_document) = ? OR UPPER(code) = ?)",
                    Integer.class, centre, entree.type(), entree.type());
            if (existants != null && existants > 0) {
                throw new ImpressionRefusee("DOCUMENT_MODEL_INACTIVE", "Le modèle de document « " + entree.libelle()
                        + " » est désactivé pour ce centre : réactivez-le dans Modèles de documents.");
            }
        } else {
            Map<String, Object> ligne = minuscules(actifs.getFirst());
            modele = UUID.fromString(String.valueOf(ligne.get("id")));
            if (ligne.get("chemin_jrxml") != null) chemin = ligne.get("chemin_jrxml").toString();
            if (ligne.get("format_impression") != null) format = ligne.get("format_impression").toString();
        }
        if (!SERVIS.contains(format.trim().toUpperCase(Locale.ROOT))) {
            return new Document(new byte[0], format);
        }
        try {
            Map<String, Object> params = new HashMap<>(parametres(debutDeSemaine));
            params.put("CENTER_ID", centre.toString());
            params.putIfAbsent(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
            params.putAll(identite.parametresDeLEnTete(centre));
            byte[] contenu = modele != null ? printer.render(modele, centre, chemin, params, format)
                    : jasper.generateReport(chemin, params, format);
            return new Document(contenu, format);
        } catch (Exception e) {
            log.warn("[PRESENCE] Génération de « {} » impossible pour le centre {} : {}", entree.libelle(), centre, cause(e), e);
            throw new ImpressionRefusee("DOCUMENT_GENERATION_FAILED", "Impossible de générer « " + entree.libelle()
                    + " » : voir le journal du serveur");
        }
    }

    private static Map<String, Object> minuscules(Map<String, Object> ligne) {
        Map<String, Object> sortie = new HashMap<>();
        ligne.forEach((k, v) -> sortie.put(k.toLowerCase(Locale.ROOT), v));
        return sortie;
    }

    private static String cause(Throwable t) {
        Throwable racine = t;
        while (racine.getCause() != null && racine.getCause() != racine) racine = racine.getCause();
        String message = racine.getMessage();
        return message == null ? racine.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }
}
