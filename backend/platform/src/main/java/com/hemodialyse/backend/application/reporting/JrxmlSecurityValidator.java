package com.hemodialyse.backend.application.reporting;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valide qu'un modèle d'impression JRXML téléversé par un utilisateur est sans danger.
 * <p>
 * Un fichier JRXML n'est pas un simple document de mise en page : il embarque la <b>requête SQL</b> exécutée
 * sur la base de l'application et des <b>expressions Java</b> compilées puis exécutées sur le serveur. Accepter
 * un JRXML tel quel reviendrait à laisser un utilisateur lire n'importe quelle table (mots de passe, autres
 * centres) et exécuter du code arbitraire. Cette politique est donc une <b>liste blanche</b> :
 * <ul>
 *   <li>le XML est lu sans DOCTYPE ni entités externes (pas de XXE) et sa taille est bornée ;</li>
 *   <li>seuls les éléments de mise en page sont autorisés (pas de scriptlet, sous-rapport, image, graphique ajouté…) ;</li>
 *   <li>la requête SQL doit être <b>strictement identique</b> à celle du modèle d'origine livré avec l'application ;</li>
 *   <li>les paramètres et champs doivent être un sous-ensemble de ceux du modèle d'origine, avec les mêmes types ;</li>
 *   <li>l'en-tête d'identité (société + centre) ne peut pas être retiré, et la seule image autorisée est le
 *       logo de la société ({@code $P{SOCIETE_LOGO}}) : jamais de chemin ni d'URL (lecture de fichier, SSRF) ;</li>
 *   <li>chaque expression doit respecter une grammaire minimale (champs/paramètres/variables déclarés, chaînes,
 *       opérateurs simples, mise en forme de dates/nombres) — aucun appel de méthode arbitraire.</li>
 * </ul>
 * Classe pure : aucune dépendance Spring ni JPA.
 */
public final class JrxmlSecurityValidator {

    public static final int MAX_BYTES = 512 * 1024;
    private static final String IDENTITY_MARKER = "$P{SOCIETE_NOM}";
    private static final String CENTRE_MARKER = "$P{CENTRE_NOM}";
    private static final String LOGO_EXPRESSION = "$P{SOCIETE_LOGO}";
    private static final int MAX_ELEMENTS = 5_000;
    private static final int MAX_EXPRESSION_LENGTH = 2_000;
    private static final String JR_NS = "http://jasperreports.sourceforge.net/jasperreports";
    /**
     * Éléments de mise en page toujours autorisés (en plus de ceux du modèle d'origine).
     */
    private static final Set<String> BASE_ELEMENTS = Set.of(
            "jasperReport", "parameter", "field", "variable", "queryString", "defaultValueExpression",
            "variableExpression", "initialValueExpression", "background", "title", "pageHeader", "columnHeader",
            "detail", "columnFooter", "pageFooter", "lastPageFooter", "summary", "noData", "band",
            "staticText", "text", "textField", "textFieldExpression", "reportElement", "textElement", "font",
            "image", "imageExpression",
            "box", "pen", "line", "rectangle", "ellipse", "graphicElement", "printWhenExpression",
            "group", "groupExpression", "groupHeader", "groupFooter");
    private static final Set<String> COMMON_ATTRIBUTES = Set.of(
            "name", "class", "height", "width", "x", "y", "mode", "forecolor", "backcolor", "key", "uuid",
            "positionType", "stretchType", "isPrintRepeatedValues", "isRemoveLineWhenBlank",
            "isPrintInFirstWholeBand", "isPrintWhenDetailOverflows", "splitType", "isBold", "isItalic",
            "isUnderline", "isStrikeThrough", "size", "fontName", "pdfFontName", "pdfEncoding", "isPdfEmbedded",
            "textAlignment", "verticalAlignment", "rotation", "lineSpacing", "isStretchWithOverflow",
            "isBlankWhenNull", "pattern", "evaluationTime", "evaluationGroup", "calculation", "resetType",
            "resetGroup", "incrementType", "direction", "radius", "lineWidth", "lineStyle", "lineColor",
            "padding", "topPadding", "leftPadding", "bottomPadding", "rightPadding", "minHeightToStartNewPage",
            "isStartNewPage", "isStartNewColumn", "isResetPageNumber", "isReprintHeaderOnEachPage",
            "keepTogether", "orientation", "pageWidth", "pageHeight", "columnWidth", "columnCount",
            "columnSpacing", "leftMargin", "rightMargin", "topMargin", "bottomMargin", "whenNoDataType",
            "isTitleNewPage", "isSummaryNewPage", "isSummaryWithPageHeaderAndFooter", "isFloatColumnFooter",
            "printOrder", "columnDirection", "isIgnorePagination", "xmlns", "xmlns:xsi", "xsi:schemaLocation",
            // image du logo (l'expression est limitée à $P{SOCIETE_LOGO})
            "scaleImage", "hAlign", "vAlign", "onErrorType");
    private static final Set<String> ALLOWED_VARIABLE_CLASSES = Set.of(
            "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Float",
            "java.lang.Boolean", "java.lang.Number", "java.math.BigDecimal", "java.util.Date", "java.sql.Date",
            "java.sql.Timestamp");
    private static final Set<String> BUILTIN_VARIABLES = Set.of(
            "PAGE_NUMBER", "PAGE_COUNT", "COLUMN_NUMBER", "COLUMN_COUNT", "REPORT_COUNT",
            "MASTER_CURRENT_PAGE", "MASTER_TOTAL_PAGES");
    private static final Set<String> SAFE_METHODS = Set.of(
            "toString", "trim", "toUpperCase", "toLowerCase", "length", "isEmpty", "equals", "equalsIgnoreCase",
            "substring", "contains", "startsWith", "endsWith", "format", "intValue", "doubleValue", "longValue");
    private static final Pattern TOKEN = Pattern.compile(
            "\\s+"
                    + "|(?<ref>\\$(?<kind>[FPV])\\{(?<ident>[A-Za-z_][A-Za-z0-9_]*)\\})"
                    + "|(?<num>\\d+(?:\\.\\d+)?[LlDdFf]?)"
                    + "|(?<new>new\\s+java\\.(?:text\\.(?:SimpleDateFormat|DecimalFormat)|util\\.Date))"
                    + "|(?<call>\\.\\s*(?<method>[A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\())"
                    + "|(?<kw>null|true|false)"
                    + "|(?<lit>\\u0001)"
                    + "|(?<op>&&|\\|\\||==|!=|<=|>=|[+\\-*/%()?:<>!,])");

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setCoalescing(true);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new InputSource(new StringReader(xml)));
    }

    private static String localName(Node n) {
        return n.getLocalName() != null ? n.getLocalName() : n.getNodeName();
    }

    // ───────────────────────────── XML ─────────────────────────────

    private static int countElements(Element e) {
        int n = 1;
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element child) n += countElements(child);
        }
        return n;
    }

    private static void walk(Element e, java.util.function.Consumer<Element> visitor) {
        visitor.accept(e);
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element child) walk(child, visitor);
        }
    }

    private static String normalizeSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }

    /**
     * @return {@code null} si l'expression est acceptée, sinon une description de l'anomalie.
     */
    static String checkExpression(String raw, Set<String> fields, Set<String> params, Set<String> variables) {
        String expr = raw == null ? "" : raw.trim();
        if (expr.length() > MAX_EXPRESSION_LENGTH) return "expression trop longue";
        String masked;
        try {
            masked = maskStringLiterals(expr);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        Matcher m = TOKEN.matcher(masked);
        int pos = 0;
        while (pos < masked.length()) {
            m.region(pos, masked.length());
            if (!m.lookingAt()) {
                return "élément non autorisé près de « " + snippet(masked, pos) + " »";
            }
            if (m.group("ref") != null) {
                String kind = m.group("kind");
                String ident = m.group("ident");
                Set<String> allowed = switch (kind) {
                    case "F" -> fields;
                    case "P" -> params;
                    default -> variables;
                };
                if (!allowed.contains(ident)) return "$" + kind + "{" + ident + "} non déclaré";
            } else if (m.group("call") != null) {
                String method = m.group("method");
                if (!SAFE_METHODS.contains(method)) return "appel de méthode interdit : ." + method + "()";
            }
            pos = m.end();
        }
        return null;
    }

    /**
     * Remplace chaque littéral de chaîne par un jeton neutre ; refuse les apostrophes (littéraux char).
     */
    private static String maskStringLiterals(String expr) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '\'') throw new IllegalArgumentException("apostrophe interdite hors d'une chaîne");
            if (c == '"') {
                i++;
                boolean closed = false;
                while (i < expr.length()) {
                    char d = expr.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    if (d == '"') {
                        closed = true;
                        i++;
                        break;
                    }
                    i++;
                }
                if (!closed) throw new IllegalArgumentException("chaîne non terminée");
                sb.append('\u0001');
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static String snippet(String s, int pos) {
        return s.substring(pos, Math.min(s.length(), pos + 24)).replace('\u0001', '"');
    }

    // ───────────────────────────── Règles ─────────────────────────────

    /**
     * @param candidateXml contenu téléversé
     * @param referenceXml modèle d'origine livré avec l'application (référence de confiance)
     * @return la liste des anomalies ; vide si le modèle est accepté
     */
    public List<Violation> validate(String candidateXml, String referenceXml) {
        List<Violation> violations = new ArrayList<>();
        if (candidateXml == null || candidateXml.isBlank()) {
            violations.add(new Violation("EMPTY", "Le fichier est vide."));
            return violations;
        }
        if (candidateXml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add(new Violation("TOO_LARGE", "Taille maximale : " + MAX_BYTES / 1024 + " Ko."));
            return violations;
        }
        Document candidate;
        Document reference;
        try {
            candidate = parse(candidateXml);
        } catch (Exception e) {
            violations.add(new Violation("XML_INVALID",
                    "XML invalide ou contenant une déclaration DOCTYPE / entité externe interdite."));
            return violations;
        }
        try {
            reference = parse(referenceXml);
        } catch (Exception e) {
            violations.add(new Violation("REFERENCE_UNAVAILABLE", "Modèle d'origine illisible."));
            return violations;
        }

        Element root = candidate.getDocumentElement();
        if (!"jasperReport".equals(localName(root))) {
            violations.add(new Violation("ROOT_INVALID", "L'élément racine doit être <jasperReport>."));
            return violations;
        }
        if (root.getNamespaceURI() != null && !JR_NS.equals(root.getNamespaceURI())) {
            violations.add(new Violation("NAMESPACE_INVALID", "Espace de noms JasperReports attendu."));
        }
        if (countElements(candidate.getDocumentElement()) > MAX_ELEMENTS) {
            violations.add(new Violation("TOO_COMPLEX", "Trop d'éléments (max " + MAX_ELEMENTS + ")."));
            return violations;
        }

        Reference ref = Reference.of(reference);
        checkStructure(candidate, ref, violations);
        checkQuery(candidate, ref, violations);
        checkDeclarations(candidate, ref, violations);
        checkExpressions(candidate, ref, violations);
        return violations;
    }

    private void checkStructure(Document candidate, Reference ref, List<Violation> out) {
        Set<String> reported = new HashSet<>();
        walk(candidate.getDocumentElement(), (e) -> {
            String name = localName(e);
            boolean allowedElement = BASE_ELEMENTS.contains(name) || ref.elements().contains(name);
            if (!allowedElement) {
                if (reported.add("E:" + name)) out.add(new Violation("ELEMENT_FORBIDDEN", "<" + name + ">"));
                return;
            }
            NamedNodeMap attrs = e.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                String attr = attrs.item(i).getNodeName();
                boolean allowedAttr = COMMON_ATTRIBUTES.contains(attr)
                        || ref.attributes().getOrDefault(name, Set.of()).contains(attr);
                if (!allowedAttr && reported.add("A:" + name + "@" + attr)) {
                    out.add(new Violation("ATTRIBUTE_FORBIDDEN", "<" + name + " " + attr + "=…>"));
                }
            }
        });
        Element root = candidate.getDocumentElement();
        String lang = root.getAttribute("language");
        if (!lang.isEmpty() && !"java".equalsIgnoreCase(lang)) {
            out.add(new Violation("LANGUAGE_FORBIDDEN", "language=\"" + lang + "\""));
        }
    }

    private void checkQuery(Document candidate, Reference ref, List<Violation> out) {
        NodeList queries = candidate.getElementsByTagNameNS("*", "queryString");
        if (queries.getLength() > 1) {
            out.add(new Violation("QUERY_MODIFIED", "Plusieurs requêtes déclarées."));
            return;
        }
        String candidateQuery = queries.getLength() == 0 ? "" : normalizeSql(queries.item(0).getTextContent());
        if (queries.getLength() == 1) {
            String language = ((Element) queries.item(0)).getAttribute("language");
            if (!language.isEmpty() && !"sql".equalsIgnoreCase(language)) {
                out.add(new Violation("QUERY_MODIFIED", "language=\"" + language + "\""));
            }
        }
        if (!candidateQuery.equals(ref.query())) {
            out.add(new Violation("QUERY_MODIFIED",
                    "La requête de données ne peut pas être modifiée : seule la mise en page est personnalisable."));
        }
    }

    private void checkDeclarations(Document candidate, Reference ref, List<Violation> out) {
        walk(candidate.getDocumentElement(), (e) -> {
            String name = localName(e);
            switch (name) {
                case "parameter" -> {
                    String p = e.getAttribute("name");
                    String cls = ref.parameters().get(p);
                    if (cls == null || !cls.equals(e.getAttribute("class"))) {
                        out.add(new Violation("PARAMETER_FORBIDDEN", p));
                    }
                }
                case "field" -> {
                    String f = e.getAttribute("name");
                    String cls = ref.fields().get(f);
                    if (cls == null || !cls.equals(e.getAttribute("class"))) {
                        out.add(new Violation("FIELD_FORBIDDEN", f));
                    }
                }
                case "variable" -> {
                    if (!ALLOWED_VARIABLE_CLASSES.contains(e.getAttribute("class"))) {
                        out.add(new Violation("VARIABLE_CLASS_FORBIDDEN", e.getAttribute("name") + " : " + e.getAttribute("class")));
                    }
                }
                default -> {
                }
            }
        });
    }

    private void checkExpressions(Document candidate, Reference ref, List<Violation> out) {
        Set<String> variables = new HashSet<>(BUILTIN_VARIABLES);
        walk(candidate.getDocumentElement(), (e) -> {
            if ("variable".equals(localName(e))) variables.add(e.getAttribute("name"));
        });
        Set<String> fields = ref.fields().keySet();
        Set<String> params = ref.parameters().keySet();

        StringBuilder all = new StringBuilder();
        walk(candidate.getDocumentElement(), (e) -> {
            String name = localName(e);
            if (!name.endsWith("Expression")) return;
            all.append(e.getTextContent()).append('\n');
            if ("imageExpression".equals(name)) {
                // Seule image autorisée : le logo fourni par le serveur (jamais un chemin, une URL, un fichier).
                if (!LOGO_EXPRESSION.equals(e.getTextContent().trim())) {
                    out.add(new Violation("IMAGE_FORBIDDEN", "seule l'expression " + LOGO_EXPRESSION + " est autorisée"));
                }
                return;
            }
            String violation = checkExpression(e.getTextContent(), fields, params, variables);
            if (violation != null) out.add(new Violation("EXPRESSION_FORBIDDEN", violation));
        });
        if (ref.usesIdentity() && (all.indexOf(IDENTITY_MARKER) < 0 || all.indexOf(CENTRE_MARKER) < 0)) {
            out.add(new Violation("IDENTITY_REMOVED",
                    "l'en-tête doit conserver " + IDENTITY_MARKER + " et " + CENTRE_MARKER));
        }
    }

    /**
     * Une anomalie : un code stable (traduit côté interface) et un détail technique.
     */
    public record Violation(String code, String detail) {
    }

    /**
     * Éléments, attributs, paramètres, champs et requête du modèle d'origine (base de confiance).
     */
    private record Reference(Set<String> elements, Map<String, Set<String>> attributes,
                             Map<String, String> parameters, Map<String, String> fields, String query,
                             boolean usesIdentity) {
        static Reference of(Document doc) {
            Set<String> elements = new HashSet<>();
            Map<String, Set<String>> attributes = new HashMap<>();
            Map<String, String> params = new LinkedHashMap<>();
            Map<String, String> fields = new LinkedHashMap<>();
            String[] query = {""};
            StringBuilder expressions = new StringBuilder();
            walk(doc.getDocumentElement(), (e) -> {
                String name = localName(e);
                elements.add(name);
                NamedNodeMap attrs = e.getAttributes();
                for (int i = 0; i < attrs.getLength(); i++) {
                    attributes.computeIfAbsent(name, k -> new HashSet<>()).add(attrs.item(i).getNodeName());
                }
                if ("parameter".equals(name)) params.put(e.getAttribute("name"), e.getAttribute("class"));
                if ("field".equals(name)) fields.put(e.getAttribute("name"), e.getAttribute("class"));
                if ("queryString".equals(name)) query[0] = normalizeSql(e.getTextContent());
                if (name.endsWith("Expression")) expressions.append(e.getTextContent()).append('\n');
            });
            return new Reference(elements, attributes, params, fields, query[0],
                    expressions.indexOf(IDENTITY_MARKER) >= 0);
        }
    }
}
