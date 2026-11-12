package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Impression d'un document par son modèle de document (règle applicable à tout rapport de l'application).
 * <ol>
 *   <li>le modèle du centre pour ce type est garanti ({@link ModeleDocumentProvisioner}) puis lu : il doit être
 *       actif ;</li>
 *   <li>l'identité société + centre (en-tête, pied de page, logo) est posée par le serveur et écrase tout
 *       paramètre homonyme ;</li>
 *   <li>la version personnalisée active du centre est utilisée si elle existe, sinon le modèle d'origine ;</li>
 *   <li>le document est exporté au format du modèle (PDF par défaut).</li>
 * </ol>
 * <p>
 * DOC1 (ADR 0142) : l'identité vient du papier en-tête du contexte Documents ({@link PapierEnTeteDuCentre}) et cette classe est le moteur
 * de rendu TRANSITOIRE que le contexte consomme par le port {@link MoteurDeRendu} (le moteur migre en DOC2).
 */
@Component
public class ModeleDocumentPrinter implements MoteurDeRendu {

    private final JdbcTemplate jdbc;
    private final JasperReportService jasper;
    private final ModeleDocumentTemplateService templates;
    private final CustomTemplateCompiler compiler;
    private final PapierEnTeteDuCentre papier;
    private final ModeleDocumentProvisioner provisioner;

    public ModeleDocumentPrinter(JdbcTemplate jdbc, JasperReportService jasper, ModeleDocumentTemplateService templates,
                                 CustomTemplateCompiler compiler, PapierEnTeteDuCentre papier,
                                 ModeleDocumentProvisioner provisioner) {
        this.jdbc = jdbc;
        this.jasper = jasper;
        this.templates = templates;
        this.compiler = compiler;
        this.papier = papier;
        this.provisioner = provisioner;
    }

    @Override
    public byte[] rendreParModele(UUID modele, UUID centre, String cheminDuGabarit, Map<String, Object> parametres, String format)
            throws Exception {
        return render(modele, centre, cheminDuGabarit, parametres, format);
    }

    @Override
    public Rendu rendreParType(UUID centre, String type, Map<String, Object> donnees, String formatDemande) {
        Document document = print(centre, type, donnees, formatDemande);
        return new Rendu(document.content(), document.format());
    }

    /**
     * PL9 : document d'une société SANS centre (rapport de la direction) : gabarit standard du catalogue, sans modèle de centre, avec
     * l'identité de la société seule ({@code SOCIETE_*}, jamais {@code CENTRE_*}).
     */
    @Override
    public Rendu rendreParTypePourLaSociete(UUID societe, String type, Map<String, Object> donnees, String formatDemande) {
        ModeleDocumentCatalog.Entry entry = ModeleDocumentCatalog.find(type).orElseThrow(() -> new BusinessException(
                "DOCUMENT_TYPE_UNKNOWN", "Type de document inconnu : " + type));
        String format = formatDemande != null ? formatDemande : "PDF";
        Map<String, Object> all = new HashMap<>(papier.ecarterLIdentiteDeLAppelant(donnees));
        all.putIfAbsent(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        papier.enTeteDeLaSociete(societe).ifPresent(e -> {
            all.put("SOCIETE_NOM", e.nom());
            all.put("SOCIETE_CONTACT", e.contact());
            all.put("SOCIETE_LEGAL", e.legal());
            all.put("SOCIETE_PIED_PAGE", e.piedDePage());
            if (e.logo() != null && e.logo().length >= 4) all.put("SOCIETE_LOGO", new java.io.ByteArrayInputStream(e.logo()));
        });
        try {
            return new Rendu(jasper.generateReport(entry.cheminJrxml(), avecSourceDeDonnees(all), format), format);
        } catch (Exception e) {
            throw new BusinessException("DOCUMENT_GENERATION_FAILED",
                    "Impossible de générer « " + entry.libelle() + " » : " + rootMessage(e));
        }
    }

    private static Map<String, Object> lower(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>();
        row.forEach((k, v) -> out.put(k.toLowerCase(), v));
        return out;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }

    /**
     * Imprime le document {@code type} du centre avec les paramètres métier fournis.
     *
     * @throws BusinessException modèle désactivé ou génération impossible (message lisible)
     */
    public Document print(UUID centerId, String type, Map<String, Object> params) {
        return print(centerId, type, params, null);
    }

    /**
     * Comme {@link #print(UUID, String, Map)} avec un format imposé par le demandeur ({@code null} = format du modèle).
     */
    public Document print(UUID centerId, String type, Map<String, Object> params, String formatDemande) {
        ModeleDocumentCatalog.Entry entry = ModeleDocumentCatalog.find(type).orElseThrow(() -> new BusinessException(
                "DOCUMENT_TYPE_UNKNOWN", "Type de document inconnu : " + type));
        provisioner.ensure(centerId, entry);

        List<Map<String, Object>> modeles = jdbc.queryForList(
                "SELECT id, chemin_jrxml, format_impression FROM modele_document "
                        + "WHERE center_id = ? AND UPPER(type_document) = ? AND active = TRUE ORDER BY created_at DESC",
                centerId, entry.type());
        if (modeles.isEmpty()) {
            throw new BusinessException("DOCUMENT_MODEL_INACTIVE", "Le modèle de document « " + entry.libelle()
                    + " » est désactivé pour ce centre : réactivez-le dans Modèles de documents.");
        }
        Map<String, Object> modele = lower(modeles.getFirst());
        UUID modeleId = UUID.fromString(String.valueOf(modele.get("id")));
        String chemin = modele.get("chemin_jrxml") == null ? entry.cheminJrxml() : modele.get("chemin_jrxml").toString();
        String format = formatDemande != null ? formatDemande
                : modele.get("format_impression") == null ? "PDF" : modele.get("format_impression").toString();

        try {
            return new Document(render(modeleId, centerId, chemin, withIdentity(centerId, params), format), format);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("DOCUMENT_GENERATION_FAILED",
                    "Impossible de générer « " + entry.libelle() + " » : " + rootMessage(e));
        }
    }

    /**
     * Génère un document d'un modèle donné : version personnalisée active du centre si elle existe, sinon le
     * modèle d'origine.
     */
    public byte[] render(UUID modeleId, UUID centerId, String cheminJrxml, Map<String, Object> params, String format)
            throws Exception {
        params = avecSourceDeDonnees(params);
        var custom = templates.activeCustom(centerId, modeleId);
        if (custom.isPresent()) {
            var report = compiler.compile(centerId, modeleId, custom.get().version(), custom.get().contenu());
            return jasper.generateFromCompiled(report, params, format);
        }
        return jasper.generateReport(cheminJrxml, params, format);
    }

    private Map<String, Object> withIdentity(UUID centerId, Map<String, Object> params) {
        Map<String, Object> all = new HashMap<>(papier.ecarterLIdentiteDeLAppelant(params));
        all.put("CENTER_ID", centerId.toString());
        all.putIfAbsent(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        all.putAll(papier.parametresDeLEnTete(centerId));
        return all;
    }

    /**
     * PL8 (ADR 0140 D3) : le contexte demandeur fournit ses lignes (clé {@code LIGNES} : liste de Map) ; le gabarit les lit comme source de
     * données et ne contient plus de requête. Appliqué à toute génération (impression par type et par identifiant de modèle).
     */
    private static Map<String, Object> avecSourceDeDonnees(Map<String, Object> params) {
        Map<String, Object> all = new HashMap<>(params);
        if (all.remove("LIGNES") instanceof Collection<?> lignes) {
            @SuppressWarnings("unchecked")
            Collection<Map<String, ?>> cartes = (Collection<Map<String, ?>>) lignes;
            all.put(JRParameter.REPORT_DATA_SOURCE, new JRMapCollectionDataSource(cartes));
        }
        return all;
    }

    /**
     * Document produit et son format ({@code PDF}, {@code EXCEL} ou {@code HTML}).
     */
    public record Document(byte[] content, String format) {
    }
}



