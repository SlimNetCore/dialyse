package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.JrxmlSecurityValidator.Violation;
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
            "bon_intervention.jrxml", "planning_presence_infirmiers.jrxml", "cahier_dialyse.jrxml",
            "planning_optimise.jrxml"})
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

    /** Le gabarit de la fiche du patient (SQL embarqué, pas encore converti) sert de référence à la requête modifiée. */
    @Test
    void laRequeteSqlModifieeEstRefusee() throws IOException {
        String reference = load("fiche_patient.jrxml");
        assertThat(reference).contains("<queryString>");
        String candidate = reference.replace("FROM patients", "FROM patients WHERE 1 = 1");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5h (ADR 0200, KB-DOC5H-1) : l'attestation livrée n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void uneAttestationQuiPorteEncoreL_ancienneRequeteEstRefusee() throws IOException {
        String reference = load("attestation.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT p.nom, a.date_fin FROM attestation_droit a JOIN patients p ON p.id = a.patient_id "
                + "WHERE a.center_id = CAST($P{CENTER_ID} AS UUID) ORDER BY a.date_fin DESC LIMIT 1]]></queryString>";
        String candidate = reference.replace("<field name=\"NOM\"", ancienne + "<field name=\"NOM\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5h (ADR 0200, KB-DOC5H-1) : la prise en charge livrée n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void unePriseEnChargeQuiPorteEncoreL_ancienneRequeteEstRefusee() throws IOException {
        String reference = load("prise_en_charge.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT p.nom, pc.statut FROM prise_en_charge pc JOIN patients p ON p.id = pc.patient_id "
                + "WHERE pc.center_id = CAST($P{CENTER_ID} AS UUID) ORDER BY pc.date_fin_demande DESC LIMIT 1]]></queryString>";
        String candidate = reference.replace("<field name=\"NOM\"", ancienne + "<field name=\"NOM\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5b (ADR 0194, KB-DOC5B-1) : le bon livré n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void unBonDInterventionQuiPorteEncoreL_ancienneRequeteEstRefuse() throws IOException {
        String reference = load("bon_intervention.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT l.libelle AS ligne_libelle FROM gmao_interventions i "
                + "LEFT JOIN gmao_lignes_cout_intervention l ON l.intervention_id = i.id]]></queryString>";
        String candidate = reference.replace("<field name=\"LIGNE_TYPE\"", ancienne + "<field name=\"LIGNE_TYPE\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5c (ADR 0195, KB-DOC5C-1) : le planning proposé livré n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void unPlanningOptimiseQuiPorteEncoreL_ancienneRequeteEstRefuse() throws IOException {
        String reference = load("planning_optimise.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT semaine_debut, salle_nom FROM planification_calendrier_case "
                + "WHERE center_id = CAST($P{CENTER_ID} AS UUID)]]></queryString>";
        String candidate = reference.replace("<field name=\"SEMAINE_DEBUT\"", ancienne + "<field name=\"SEMAINE_DEBUT\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5d (ADR 0196, KB-DOC5D-1) : l'ordonnance livrée n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void uneOrdonnanceQuiPorteEncoreL_ancienneRequeteEstRefusee() throws IOException {
        String reference = load("ordonnance.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT p.nom FROM ordonnances o JOIN patients p ON p.id = o.patient_id "
                + "WHERE o.id = CAST($P{ordonnanceId} AS UUID)]]></queryString>";
        String candidate = reference.replace("<field name=\"NOM\"", ancienne + "<field name=\"NOM\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5e (ADR 0197, KB-DOC5E-1) : le planning de présence livré n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void unPlanningDePresenceQuiPorteEncoreL_ancienneRequeteEstRefuse() throws IOException {
        String reference = load("planning_presence_infirmiers.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT * FROM (SELECT sa.nom AS salle FROM infirmier_affectation a "
                + "JOIN salle sa ON sa.id = a.salle_id WHERE a.center_id = CAST($P{CENTER_ID} AS UUID)) planning]]></queryString>";
        String candidate = reference.replace("<field name=\"DATE_JOUR\"", ancienne + "<field name=\"DATE_JOUR\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5f (ADR 0198, KB-DOC5F-1) : la liste des patients livrée n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void uneListeDesPatientsQuiPorteEncoreL_ancienneRequeteEstRefusee() throws IOException {
        String reference = load("liste_patients.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT p.code_patient, p.nom, p.prenom FROM patients p "
                + "WHERE p.center_id = CAST($P{CENTER_ID} AS UUID) ORDER BY p.nom, p.prenom]]></queryString>";
        String candidate = reference.replace("<field name=\"CODE_PATIENT\"", ancienne + "<field name=\"CODE_PATIENT\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5g (ADR 0199, KB-DOC5G-1) : la liste des ouvertures de droit livrée n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void uneListeDesAttestationsQuiPorteEncoreL_ancienneRequeteEstRefusee() throws IOException {
        String reference = load("liste_attestations.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT p.code_patient, p.nom FROM attestation_droit a JOIN patients p ON p.id = a.patient_id "
                + "WHERE a.center_id = CAST($P{CENTER_ID} AS UUID) ORDER BY a.date_fin DESC]]></queryString>";
        String candidate = reference.replace("<field name=\"CODE_PATIENT\"", ancienne + "<field name=\"CODE_PATIENT\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    /** DOC5g (ADR 0199, KB-DOC5G-1) : la liste des prises en charge livrée n'a plus de requête ; un téléversement qui porte l'ancienne est refusé. */
    @Test
    void uneListeDesPrisesEnChargeQuiPorteEncoreL_ancienneRequeteEstRefusee() throws IOException {
        String reference = load("liste_pec.jrxml");
        assertThat(reference).doesNotContain("queryString");
        String ancienne = "<queryString><![CDATA[SELECT p.code_patient, p.nom FROM prise_en_charge pc JOIN patients p ON p.id = pc.patient_id "
                + "WHERE pc.center_id = CAST($P{CENTER_ID} AS UUID) ORDER BY pc.date_fin_demande DESC]]></queryString>";
        String candidate = reference.replace("<field name=\"CODE_PATIENT\"", ancienne + "<field name=\"CODE_PATIENT\"");
        assertThat(candidate).isNotEqualTo(reference);
        assertThat(codes(validator.validate(candidate, reference))).contains("QUERY_MODIFIED");
    }

    @Test
    void uneRequeteSurUneAutreTableEstRefusee() throws IOException {
        String reference = load("fiche_patient.jrxml");
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
