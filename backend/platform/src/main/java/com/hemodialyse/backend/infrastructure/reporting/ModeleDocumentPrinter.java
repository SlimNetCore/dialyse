package com.hemodialyse.backend.infrastructure.reporting;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import net.sf.jasperreports.engine.JRParameter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
 */
@Component
public class ModeleDocumentPrinter {

    private final JdbcTemplate jdbc;
    private final JasperReportService jasper;
    private final ModeleDocumentTemplateService templates;
    private final CustomTemplateCompiler compiler;
    private final DocumentIdentityProvider identity;
    private final ModeleDocumentProvisioner provisioner;

    public ModeleDocumentPrinter(JdbcTemplate jdbc, JasperReportService jasper, ModeleDocumentTemplateService templates,
                                 CustomTemplateCompiler compiler, DocumentIdentityProvider identity,
                                 ModeleDocumentProvisioner provisioner) {
        this.jdbc = jdbc;
        this.jasper = jasper;
        this.templates = templates;
        this.compiler = compiler;
        this.identity = identity;
        this.provisioner = provisioner;
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
        String format = modele.get("format_impression") == null ? "PDF" : modele.get("format_impression").toString();

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
        var custom = templates.activeCustom(centerId, modeleId);
        if (custom.isPresent()) {
            var report = compiler.compile(centerId, modeleId, custom.get().version(), custom.get().contenu());
            return jasper.generateFromCompiled(report, params, format);
        }
        return jasper.generateReport(cheminJrxml, params, format);
    }

    private Map<String, Object> withIdentity(UUID centerId, Map<String, Object> params) {
        Map<String, Object> all = new HashMap<>();
        params.forEach((k, v) -> {
            String key = k == null ? "" : k.toUpperCase();
            if (!key.startsWith(DocumentIdentityProvider.RESERVED_PREFIX_SOCIETE)
                    && !key.startsWith(DocumentIdentityProvider.RESERVED_PREFIX_CENTRE)) {
                all.put(k, v);
            }
        });
        all.put("CENTER_ID", centerId.toString());
        all.putIfAbsent(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        all.putAll(identity.paramsFor(centerId));
        return all;
    }

    /**
     * Document produit et son format ({@code PDF}, {@code EXCEL} ou {@code HTML}).
     */
    public record Document(byte[] content, String format) {
    }
}



