package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.FrenchFormatFactory;
import com.hemodialyse.backend.contexts.documents.application.CatalogueDesTypesLivres;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.MoteurDeRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.PapierEnTeteDuCentre;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le moteur de rendu des documents (DOC2, ADR 0162), repris à l'identique de l'ancien {@code ModeleDocumentPrinter} de la plateforme.
 * Impression d'un document par son modèle de document (règle applicable à tout rapport de l'application).
 * <ol>
 *   <li>le modèle du centre pour ce type est garanti ({@link ModeleDocumentProvisioner}) puis lu : il doit être actif ;</li>
 *   <li>l'identité société + centre (en-tête, pied de page, logo) est posée par le serveur et écrase tout paramètre homonyme ;</li>
 *   <li>la version personnalisée active du centre est utilisée si elle existe, sinon le modèle d'origine ;</li>
 *   <li>le document est exporté au format du modèle (PDF par défaut).</li>
 * </ol>
 * Les refus sont des {@link RenduRefuse} (422, code stable).
 */
public class MoteurDeRenduJasper implements MoteurDeRendu {

    private final JdbcTemplate jdbc;
    private final JasperReportService jasper;
    private final GabaritPersonnaliseDuCentre gabarits;
    private final CustomTemplateCompiler compiler;
    private final PapierEnTeteDuCentre papier;
    private final ModeleDocumentProvisioner provisioner;
    private final CatalogueDesTypesLivres catalogue;

    public MoteurDeRenduJasper(JdbcTemplate jdbc, JasperReportService jasper, GabaritPersonnaliseDuCentre gabarits,
                               CustomTemplateCompiler compiler, PapierEnTeteDuCentre papier, ModeleDocumentProvisioner provisioner,
                               CatalogueDesTypesLivres catalogue) {
        this.catalogue = catalogue;
        this.jdbc = jdbc;
        this.jasper = jasper;
        this.gabarits = gabarits;
        this.compiler = compiler;
        this.papier = papier;
        this.provisioner = provisioner;
    }

    @Override
    public byte[] rendreParModele(UUID modele, UUID centre, String cheminDuGabarit, Map<String, Object> parametres, String format)
            throws Exception {
        parametres = avecSourceDeDonnees(parametres);
        var personnalise = gabarits.actif(centre, modele);
        if (personnalise.isPresent()) {
            var report = compiler.compile(centre, modele, personnalise.get().version(), personnalise.get().contenu());
            return jasper.generateFromCompiled(report, parametres, format);
        }
        return jasper.generateReport(cheminDuGabarit, parametres, format);
    }

    @Override
    public byte[] rendreSansModele(String cheminDuGabarit, Map<String, Object> parametres, String format) throws Exception {
        // DOC5 (correction DOC5f) : le gabarit sans requête reçoit ses lignes comme source de données aussi quand le centre n'a aucun modèle
        return jasper.generateReport(cheminDuGabarit, avecSourceDeDonnees(parametres), format);
    }

    @Override
    public Rendu rendreParType(UUID centre, String type, Map<String, Object> donnees, String formatDemande) {
        TypeDeDocumentDeclare entry = catalogue.trouver(type).orElseThrow(() -> new RenduRefuse(
                "DOCUMENT_TYPE_UNKNOWN", "Type de document inconnu : " + type));
        provisioner.ensure(centre, entry);

        List<Map<String, Object>> modeles = jdbc.queryForList(
                "SELECT id, chemin_jrxml, format_impression FROM modele_document "
                        + "WHERE center_id = ? AND UPPER(type_document) = ? AND active = TRUE ORDER BY created_at DESC",
                centre, entry.code());
        if (modeles.isEmpty()) {
            throw new RenduRefuse("DOCUMENT_MODEL_INACTIVE", "Le modèle de document « " + entry.libelle()
                    + " » est désactivé pour ce centre : réactivez-le dans Modèles de documents.");
        }
        Map<String, Object> modele = lower(modeles.getFirst());
        UUID modeleId = UUID.fromString(String.valueOf(modele.get("id")));
        String chemin = modele.get("chemin_jrxml") == null ? entry.gabaritParDefaut() : modele.get("chemin_jrxml").toString();
        String format = formatDemande != null ? formatDemande
                : modele.get("format_impression") == null ? "PDF" : modele.get("format_impression").toString();

        try {
            return new Rendu(rendreParModele(modeleId, centre, chemin, avecIdentite(centre, donnees), format), format);
        } catch (RenduRefuse e) {
            throw e;
        } catch (Exception e) {
            throw new RenduRefuse("DOCUMENT_GENERATION_FAILED",
                    "Impossible de générer « " + entry.libelle() + " » : " + rootMessage(e));
        }
    }

    /**
     * PLN-DOC (KB-PLN-51) : lecture sans écriture. Le modèle actif du centre s'il existe ; un centre sans aucun modèle de ce type (par type
     * ou par code) utilise le gabarit livré en mémoire ; un modèle présent mais désactivé refuse. Rien n'est provisionné.
     */
    @Override
    public String formatDImpression(UUID centre, String type) {
        Optional<Map<String, Object>> modele = modeleActifSansProvisionner(centre, entree(type));
        return modele.isPresent() && modele.get().get("format_impression") != null ? modele.get().get("format_impression").toString() : "PDF";
    }

    @Override
    public Rendu rendreParTypeSansProvisionner(UUID centre, String type, Map<String, Object> donnees, String formatDemande) {
        TypeDeDocumentDeclare entry = entree(type);
        Optional<Map<String, Object>> modele = modeleActifSansProvisionner(centre, entry);
        String format = formatDemande != null ? formatDemande
                : modele.isPresent() && modele.get().get("format_impression") != null ? modele.get().get("format_impression").toString() : "PDF";
        try {
            Map<String, Object> parametres = avecIdentite(centre, donnees);
            if (modele.isEmpty()) {
                return new Rendu(rendreSansModele(entry.gabaritParDefaut(), parametres, format), format);
            }
            UUID modeleId = UUID.fromString(String.valueOf(modele.get().get("id")));
            String chemin = modele.get().get("chemin_jrxml") == null ? entry.gabaritParDefaut() : modele.get().get("chemin_jrxml").toString();
            return new Rendu(rendreParModele(modeleId, centre, chemin, parametres, format), format);
        } catch (Exception e) {
            throw new RenduRefuse("DOCUMENT_GENERATION_FAILED",
                    "Impossible de générer « " + entry.libelle() + " » : " + rootMessage(e));
        }
    }

    private TypeDeDocumentDeclare entree(String type) {
        return catalogue.trouver(type).orElseThrow(() -> new RenduRefuse("DOCUMENT_TYPE_UNKNOWN", "Type de document inconnu : " + type));
    }

    /** Le modèle actif le plus récent ; vide si le centre n'a aucun modèle du type ; refus si tous sont désactivés. */
    private Optional<Map<String, Object>> modeleActifSansProvisionner(UUID centre, TypeDeDocumentDeclare entry) {
        List<Map<String, Object>> actifs = jdbc.queryForList(
                "SELECT id, chemin_jrxml, format_impression FROM modele_document "
                        + "WHERE center_id = ? AND UPPER(type_document) = ? AND active = TRUE ORDER BY created_at DESC",
                centre, entry.code());
        if (!actifs.isEmpty()) return Optional.of(lower(actifs.getFirst()));
        Integer existants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM modele_document WHERE center_id = ? AND (UPPER(type_document) = ? OR UPPER(code) = ?)",
                Integer.class, centre, entry.code(), entry.code());
        if (existants != null && existants > 0) {
            throw new RenduRefuse("DOCUMENT_MODEL_INACTIVE", "Le modèle de document « " + entry.libelle()
                    + " » est désactivé pour ce centre : réactivez-le dans Modèles de documents.");
        }
        return Optional.empty();
    }

    /**
     * PL9 : document d'une société SANS centre (rapport de la direction) : gabarit standard du catalogue, sans modèle de centre, avec
     * l'identité de la société seule ({@code SOCIETE_*}, jamais {@code CENTRE_*}).
     */
    @Override
    public Rendu rendreParTypePourLaSociete(UUID societe, String type, Map<String, Object> donnees, String formatDemande) {
        TypeDeDocumentDeclare entry = catalogue.trouver(type).orElseThrow(() -> new RenduRefuse(
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
            return new Rendu(jasper.generateReport(entry.gabaritParDefaut(), avecSourceDeDonnees(all), format), format);
        } catch (Exception e) {
            throw new RenduRefuse("DOCUMENT_GENERATION_FAILED",
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

    private Map<String, Object> avecIdentite(UUID centre, Map<String, Object> params) {
        Map<String, Object> all = new HashMap<>(papier.ecarterLIdentiteDeLAppelant(params));
        all.put("CENTER_ID", centre.toString());
        all.putIfAbsent(JRParameter.REPORT_FORMAT_FACTORY, new FrenchFormatFactory());
        all.putAll(papier.parametresDeLEnTete(centre));
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
}
