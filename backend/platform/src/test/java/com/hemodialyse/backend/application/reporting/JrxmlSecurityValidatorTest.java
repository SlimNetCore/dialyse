package com.hemodialyse.backend.application.reporting;

import com.hemodialyse.backend.application.reporting.JrxmlSecurityValidator.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JrxmlSecurityValidatorTest {

    private final JrxmlSecurityValidator validator = new JrxmlSecurityValidator();

    private static String load(String name) throws IOException {
        try (var in = new ClassPathResource("reports/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> codes(List<Violation> violations) {
        return violations.stream().map(Violation::code).toList();
    }

    /**
     * Remplace le contenu de la première {@code textFieldExpression} du modèle.
     */
    private static String injectInFirstTextField(String xml, String expression) {
        int open = xml.indexOf("<textFieldExpression>");
        int close = xml.indexOf("</textFieldExpression>", open);
        assertThat(open).isPositive();
        return xml.substring(0, open) + "<textFieldExpression><![CDATA[" + expression + "]]>" + xml.substring(close);
    }

    @ParameterizedTest
    @ValueSource(strings = {"attestation.jrxml", "fiche_patient.jrxml", "liste_attestations.jrxml",
            "liste_patients.jrxml", "liste_pec.jrxml", "prise_en_charge.jrxml", "ordonnance.jrxml", "inventaire_stock.jrxml",
            "bon_intervention.jrxml"})
    void lesModelesLivresSontAcceptesContreEuxMemes(String template) throws IOException {
        String xml = load(template);
        assertThat(validator.validate(xml, xml)).isEmpty();
    }

    @Test
    void unChangementDeMiseEnPageEstAccepte() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = reference.replace("topMargin=\"40\"", "topMargin=\"38\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(validator.validate(candidate, reference)).isEmpty();
    }

    @Test
    void laRequeteSqlModifieeEstRefusee() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = reference.replace("ORDER BY a.date_fin DESC", "ORDER BY a.date_fin ASC");
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    @Test
    void uneRequeteSurUneAutreTableEstRefusee() throws IOException {
        String reference = load("attestation.jrxml");
        int start = reference.indexOf("<![CDATA[");
        int end = reference.indexOf("]]>") + 3;
        String candidate = reference.substring(0, start)
                + "<![CDATA[SELECT username AS nom, password_hash AS prenom FROM app_user]]>"
                + reference.substring(end);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    @Test
    void uneDeclarationDoctypeEstRefusee() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + reference.substring(reference.indexOf("<jasperReport"));
        assertThat(codes(validator.validate(candidate, reference))).containsExactly("XML_INVALID");
    }

    @Test
    void unXmlMalFormeEstRefuse() throws IOException {
        assertThat(codes(validator.validate("<jasperReport>", load("attestation.jrxml"))))
                .containsExactly("XML_INVALID");
    }

    @Test
    void unFichierTropVolumineuxEstRefuse() throws IOException {
        String big = "<jasperReport>" + "x".repeat(JrxmlSecurityValidator.MAX_BYTES) + "</jasperReport>";
        assertThat(codes(validator.validate(big, load("attestation.jrxml")))).containsExactly("TOO_LARGE");
    }

    @Test
    void unFichierVideEstRefuse() throws IOException {
        assertThat(codes(validator.validate("  ", load("attestation.jrxml")))).containsExactly("EMPTY");
    }

    @Test
    void uneExpressionExecutantDuCodeEstRefusee() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = injectInFirstTextField(reference,
                "Runtime.getRuntime().exec(\"calc\")");
        assertThat(codes(validator.validate(candidate, reference))).contains("EXPRESSION_FORBIDDEN");
    }

    @Test
    void unAppelDeMethodeNonAutoriseEstRefuse() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = injectInFirstTextField(reference, "$F{NOM}.getClass().getName()");
        assertThat(codes(validator.validate(candidate, reference))).contains("EXPRESSION_FORBIDDEN");
    }

    @Test
    void unParametreSystemeNonDeclareEstRefuse() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = injectInFirstTextField(reference, "$P{REPORT_CONNECTION}.toString()");
        assertThat(codes(validator.validate(candidate, reference))).contains("EXPRESSION_FORBIDDEN");
    }

    @Test
    void unChampInconnuEstRefuse() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = injectInFirstTextField(reference, "$F{PASSWORD_HASH}");
        assertThat(codes(validator.validate(candidate, reference))).contains("EXPRESSION_FORBIDDEN");
    }

    @Test
    void desExpressionsUsuellesRestentAutorisees() throws IOException {
        Set<String> fields = Set.of("NOM", "PRENOM", "SOUS_KT");
        Set<String> params = Set.of("CENTER_ID");
        Set<String> vars = Set.of("PAGE_NUMBER");
        for (String expr : List.of(
                "$F{NOM} + \" \" + $F{PRENOM}",
                "\"Page \" + $V{PAGE_NUMBER}",
                "$F{SOUS_KT} != null && $F{SOUS_KT} ? \"Oui\" : \"Non\"",
                "\"Imprimé le \" + new java.text.SimpleDateFormat(\"dd/MM/yyyy HH:mm\").format(new java.util.Date())",
                "$F{NOM}.trim().toUpperCase()",
                "\"Centre: \" + $P{CENTER_ID}")) {
            assertThat(JrxmlSecurityValidator.checkExpression(expr, fields, params, vars))
                    .as(expr).isNull();
        }
    }

    @Test
    void desConstructionsDangereusesSontRefusees() {
        Set<String> none = Set.of();
        for (String expr : List.of(
                "new java.io.File(\"/etc/passwd\")",
                "System.exit(0)",
                "Class.forName(\"x\")",
                "$F{NOM}.getClass()",
                "'a'",
                "\"non terminée",
                "$V{INCONNUE}")) {
            assertThat(JrxmlSecurityValidator.checkExpression(expr, Set.of("NOM"), none, none))
                    .as(expr).isNotNull();
        }
    }

    @Test
    void unScriptletUnSousRapportOuUneImageSontRefuses() throws IOException {
        String reference = load("attestation.jrxml");
        String withScriptlet = reference.replaceFirst("<jasperReport ", "<jasperReport scriptletClass=\"evil.Scriptlet\" ");
        assertThat(codes(validator.validate(withScriptlet, reference))).contains("ATTRIBUTE_FORBIDDEN");

        String withImage = reference.replaceFirst("<title>",
                "<title><band height=\"10\"><image><reportElement x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>"
                        + "<imageExpression>\"http://attacker/x.png\"</imageExpression></image></band>");
        assertThat(codes(validator.validate(withImage, reference))).contains("IMAGE_FORBIDDEN");

        String withSubreport = reference.replaceFirst("<title>",
                "<title><band height=\"10\"><subreport><reportElement x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>"
                        + "<subreportExpression>\"evil.jasper\"</subreportExpression></subreport></band>");
        assertThat(codes(validator.validate(withSubreport, reference))).contains("ELEMENT_FORBIDDEN");
    }

    @Test
    void seuleLImageDuLogoServeurEstAutorisee() throws IOException {
        String reference = load("attestation.jrxml");
        String logo = reference.replace("<title>",
                "<title><band height=\"10\"><image scaleImage=\"RetainShape\" onErrorType=\"Blank\">"
                        + "<reportElement x=\"0\" y=\"0\" width=\"10\" height=\"10\"/>"
                        + "<imageExpression>$P{SOCIETE_LOGO}</imageExpression></image></band>");
        assertThat(validator.validate(logo, reference)).isEmpty();

        String localFile = logo.replace("<imageExpression>$P{SOCIETE_LOGO}", "<imageExpression>\"C:/Windows/win.ini\"");
        assertThat(codes(validator.validate(localFile, reference))).contains("IMAGE_FORBIDDEN");

        String hyperlink = logo.replace("onErrorType=\"Blank\"", "onErrorType=\"Blank\" hyperlinkType=\"Reference\"");
        assertThat(codes(validator.validate(hyperlink, reference))).contains("ATTRIBUTE_FORBIDDEN");
    }

    @Test
    void lEnTeteDIdentiteNePeutPasEtreRetire() throws IOException {
        String reference = load("attestation.jrxml");
        assertThat(reference).contains("$P{SOCIETE_NOM}").contains("$P{CENTRE_NOM}");

        String sansSociete = reference.replace("$P{SOCIETE_NOM}", "\"Ma société\"");
        assertThat(codes(validator.validate(sansSociete, reference))).contains("IDENTITY_REMOVED");

        String sansCentre = reference.replace("$P{CENTRE_NOM}", "\"Mon centre\"");
        assertThat(codes(validator.validate(sansCentre, reference))).contains("IDENTITY_REMOVED");
    }

    @Test
    void unParametreAjouteEstRefuse() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = reference.replaceFirst("<parameter name=\"CENTER_ID\"",
                "<parameter name=\"EXTRA\" class=\"java.lang.String\"/>\n    <parameter name=\"CENTER_ID\"");
        assertThat(codes(validator.validate(candidate, reference))).contains("PARAMETER_FORBIDDEN");
    }

    @Test
    void unLangageDExpressionAutreQueJavaEstRefuse() throws IOException {
        String reference = load("attestation.jrxml");
        String candidate = reference.replaceFirst("<jasperReport ", "<jasperReport language=\"groovy\" ");
        assertThat(codes(validator.validate(candidate, reference))).contains("LANGUAGE_FORBIDDEN");
    }
}
