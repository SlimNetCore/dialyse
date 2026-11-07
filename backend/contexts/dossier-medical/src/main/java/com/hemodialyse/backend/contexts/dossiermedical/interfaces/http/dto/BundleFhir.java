package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.application.ExporterLeDossierMedical.DossierAExporter;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduction du dossier médical en {@code Bundle} FHIR R4 (sérialisation directe, aucune dépendance FHIR) : MÊMES ressources, MÊMES
 * champs et MÊME ordre que le legacy (patient, antécédents, allergies, analyses, observations, ordonnances, abords). Les ressources sont
 * des {@code LinkedHashMap} : un bundle contient des ressources hétérogènes aux champs facultatifs. Les codes FHIR sont REPRODUITS tels
 * quels (KB-MED-70 et 102, à confirmer avec le référentiel de terminologie) : {@code Condition} INACTIF exporté « resolved »,
 * allergie : statut de vérification en minuscules dans le système {@code condition-clinical}, {@code Procedure} « in-progress » si actif.
 */
public final class BundleFhir {
    private static final Map<String, String> SYSTEMES = Map.of(
            "CIM10", "http://hl7.org/fhir/sid/icd-10",
            "LOINC", "http://loinc.org",
            "ATC", "http://www.whocc.no/atc",
            "LOCAL", "urn:hemodialyse:local-code");

    private BundleFhir() {
    }

    public static Map<String, Object> de(DossierAExporter dossier) {
        List<Object> entrees = new ArrayList<>();
        String reference = "Patient/" + dossier.patient();
        entrees.add(entree(patient(dossier)));
        dossier.antecedents().forEach(a -> entrees.add(entree(condition(a, reference))));
        dossier.allergies().forEach(a -> entrees.add(entree(allergie(a, reference))));
        dossier.analyses().forEach(a -> observationsDeLAnalyse(a, reference).forEach(o -> entrees.add(entree(o))));
        dossier.observations().forEach(o -> entrees.add(entree(observation(o, reference))));
        dossier.ordonnances().forEach(o -> demandesDeMedicament(o, reference).forEach(m -> entrees.add(entree(m))));
        dossier.abords().forEach(a -> entrees.add(entree(procedure(a, reference))));

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "collection");
        bundle.put("timestamp", OffsetDateTime.ofInstant(dossier.exporteLe(), ZoneOffset.UTC).toString());
        bundle.put("total", entrees.size());
        bundle.put("entry", entrees);
        return bundle;
    }

    private static Map<String, Object> entree(Map<String, Object> ressource) {
        Map<String, Object> entree = new LinkedHashMap<>();
        entree.put("resource", ressource);
        return entree;
    }

    private static Map<String, Object> patient(DossierAExporter dossier) {
        var identite = dossier.identite();
        Map<String, Object> ressource = new LinkedHashMap<>();
        ressource.put("resourceType", "Patient");
        ressource.put("id", dossier.patient().toString());
        Map<String, Object> nom = new LinkedHashMap<>();
        mettre(nom, "family", identite.nom());
        mettre(nom, "given", identite.prenom() == null ? null : List.of(identite.prenom()));
        ressource.put("name", List.of(nom));
        mettre(ressource, "gender", sexe(identite.sexe()));
        mettre(ressource, "birthDate", identite.dateNaissance());
        if (identite.numeroAssurance() != null) {
            Map<String, Object> identifiant = new LinkedHashMap<>();
            identifiant.put("system", "urn:hemodialyse:numero-assurance");
            identifiant.put("value", identite.numeroAssurance());
            ressource.put("identifier", List.of(identifiant));
        }
        return ressource;
    }

    private static String sexe(String sexe) {
        if (sexe == null) return null;
        return switch (sexe.trim().toUpperCase()) {
            case "M", "MASCULIN" -> "male";
            case "F", "FEMININ" -> "female";
            default -> "unknown";
        };
    }

    private static Map<String, Object> condition(Antecedent a, String reference) {
        Map<String, Object> ressource = new LinkedHashMap<>();
        ressource.put("resourceType", "Condition");
        ressource.put("id", a.id().toString());
        ressource.put("subject", Map.of("reference", reference));
        ressource.put("code", conceptCode(a.diagnostic(), a.libelleLibre()));
        mettre(ressource, "clinicalStatus", statut(a.statut() == StatutClinique.ACTIF ? "active" : "resolved"));
        mettre(ressource, "onsetDateTime", a.debut());
        mettre(ressource, "abatementDateTime", a.fin());
        mettre(ressource, "note", notes(a.note()));
        mettre(ressource, "severity", a.severite() == null ? null : Map.of("text", a.severite()));
        return ressource;
    }

    private static Map<String, Object> allergie(Allergie a, String reference) {
        Map<String, Object> ressource = new LinkedHashMap<>();
        ressource.put("resourceType", "AllergyIntolerance");
        ressource.put("id", a.id().toString());
        ressource.put("patient", Map.of("reference", reference));
        ressource.put("code", conceptCode(a.substance(), null));
        mettre(ressource, "criticality", a.criticite().name().toLowerCase());
        mettre(ressource, "clinicalStatus", a.statut() == null ? null : statut(a.statut().name().toLowerCase()));
        mettre(ressource, "recordedDate", a.dateDeConstatation());
        if (a.manifestations() != null && !a.manifestations().isBlank()) {
            Map<String, Object> reaction = new LinkedHashMap<>();
            reaction.put("manifestation", List.of(Map.of("text", a.manifestations())));
            ressource.put("reaction", List.of(reaction));
        }
        return ressource;
    }

    private static List<Map<String, Object>> observationsDeLAnalyse(Analyse a, String reference) {
        List<Map<String, Object>> observations = new ArrayList<>();
        MesuresDuBilan m = a.mesures();
        mesure(observations, a, reference, "hbGDl", "Hémoglobine", m.hemoglobine(), "g/dL");
        mesure(observations, a, reference, "htPct", "Hématocrite", m.hematocrite(), "%");
        mesure(observations, a, reference, "ferritineNgMl", "Ferritine", m.ferritine(), "ng/mL");
        mesure(observations, a, reference, "cstfPct", "Coefficient de saturation de la transferrine", m.coefficientDeSaturation(), "%");
        mesure(observations, a, reference, "ktVMensuel", "Kt/V", m.ktVMensuel(), "");
        mesure(observations, a, reference, "phosphoreMgDl", "Phosphore", m.phosphore(), "mg/dL");
        mesure(observations, a, reference, "calciumMgDl", "Calcium", m.calcium(), "mg/dL");
        mesure(observations, a, reference, "pthPgMl", "Parathormone", m.pth(), "pg/mL");
        mesure(observations, a, reference, "albumineGDl", "Albumine", m.albumine(), "g/dL");
        mesure(observations, a, reference, "crpMgL", "CRP", m.crp(), "mg/L");
        return observations;
    }

    private static void mesure(List<Map<String, Object>> observations, Analyse a, String reference, String code, String libelle,
                               BigDecimal valeur, String unite) {
        if (valeur == null) return;
        Map<String, Object> ressource = new LinkedHashMap<>();
        ressource.put("resourceType", "Observation");
        ressource.put("id", a.id() + "-" + code);
        ressource.put("status", "final");
        ressource.put("subject", Map.of("reference", reference));
        ressource.put("code", conceptCode(CodeClinique.reconstituer(com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage.LOCAL,
                code, libelle), libelle));
        mettre(ressource, "effectiveDateTime", a.datePrelevement());
        Map<String, Object> quantite = new LinkedHashMap<>();
        quantite.put("value", valeur);
        mettre(quantite, "unit", unite.isBlank() ? null : unite);
        ressource.put("valueQuantity", quantite);
        observations.add(ressource);
    }

    private static Map<String, Object> observation(ObservationBiologique o, String reference) {
        Map<String, Object> ressource = new LinkedHashMap<>();
        ressource.put("resourceType", "Observation");
        ressource.put("id", o.id().toString());
        ressource.put("status", switch (o.statut()) {
            case PRELIMINAIRE -> "preliminary";
            case CORRIGE -> "corrected";
            default -> "final";
        });
        ressource.put("subject", Map.of("reference", reference));
        ressource.put("code", conceptCode(o.analyte(), null));
        mettre(ressource, "effectiveDateTime", o.datePrelevement());
        if (o.valeurNumerique() != null) {
            Map<String, Object> quantite = new LinkedHashMap<>();
            quantite.put("value", o.valeurNumerique().valeur());
            quantite.put("unit", o.valeurNumerique().unite());
            ressource.put("valueQuantity", quantite);
        } else {
            mettre(ressource, "valueString", o.valeurTexte());
        }
        return ressource;
    }

    private static List<Map<String, Object>> demandesDeMedicament(Ordonnance o, String reference) {
        List<Map<String, Object>> demandes = new ArrayList<>();
        String statut = switch (o.statut()) {
            case BROUILLON -> "draft";
            case SIGNEE, IMPRIMEE -> "active";
            case ANNULEE -> "cancelled";
        };
        for (LigneDOrdonnance ligne : o.lignes()) {
            Map<String, Object> ressource = new LinkedHashMap<>();
            ressource.put("resourceType", "MedicationRequest");
            ressource.put("id", ligne.id().toString());
            ressource.put("status", statut);
            ressource.put("intent", "order");
            ressource.put("medicationCodeableConcept", medicament(ligne));
            ressource.put("subject", Map.of("reference", reference));
            mettre(ressource, "authoredOn", o.datePrescription());
            mettre(ressource, "requester", o.medecin() == null ? null : Map.of("display", o.medecin()));

            Map<String, Object> posologie = new LinkedHashMap<>();
            mettre(posologie, "text", ligne.posologie());
            if (ligne.voie() != null) {
                posologie.put("route", Map.of("text", ligne.voie()));
            }
            if (ligne.dureeJours() != null) {
                Map<String, Object> repetition = new LinkedHashMap<>();
                repetition.put("duration", ligne.dureeJours());
                repetition.put("durationUnit", "d");
                posologie.put("timing", Map.of("repeat", repetition));
            }
            ressource.put("dosageInstruction", List.of(posologie));

            if (ligne.quantite() != null) {
                Map<String, Object> quantite = new LinkedHashMap<>();
                quantite.put("value", ligne.quantite());
                ressource.put("dispenseRequest", Map.of("quantity", quantite));
            }
            demandes.add(ressource);
        }
        return demandes;
    }

    private static Map<String, Object> medicament(LigneDOrdonnance ligne) {
        Map<String, Object> concept = new LinkedHashMap<>();
        if (ligne.medicament() != null) {
            Map<String, Object> codage = new LinkedHashMap<>();
            codage.put("system", SYSTEMES.get(ligne.medicament().nomenclature()));
            codage.put("code", ligne.medicament().code());
            mettre(codage, "display", ligne.medicament().libelle());
            concept.put("coding", List.of(codage));
            mettre(concept, "text", ligne.medicament().libelle() != null ? ligne.medicament().libelle() : ligne.libelle());
        } else {
            mettre(concept, "text", ligne.libelle());
        }
        return concept;
    }

    private static Map<String, Object> procedure(AbordVasculaire a, String reference) {
        Map<String, Object> ressource = new LinkedHashMap<>();
        ressource.put("resourceType", "Procedure");
        ressource.put("id", a.id().toString());
        ressource.put("status", a.actif() ? "in-progress" : "completed");
        ressource.put("subject", Map.of("reference", reference));
        ressource.put("code", Map.of("text", a.type()));
        String site = (a.cote() != null ? a.cote() + " " : "") + (a.localisation() != null ? a.localisation() : "");
        if (!site.isBlank()) {
            ressource.put("bodySite", List.of(Map.of("text", site.trim())));
        }
        Map<String, Object> periode = new LinkedHashMap<>();
        mettre(periode, "start", a.creation());
        mettre(periode, "end", a.fin());
        if (!periode.isEmpty()) {
            ressource.put("performedPeriod", periode);
        }
        mettre(ressource, "note", notes(a.complications()));
        return ressource;
    }

    private static Map<String, Object> conceptCode(CodeClinique code, String texteDeRepli) {
        Map<String, Object> concept = new LinkedHashMap<>();
        if (code != null) {
            Map<String, Object> codage = new LinkedHashMap<>();
            codage.put("system", SYSTEMES.get(code.systeme().name()));
            codage.put("code", code.code());
            mettre(codage, "display", code.affichage());
            concept.put("coding", List.of(codage));
            mettre(concept, "text", code.affichage() != null ? code.affichage() : texteDeRepli);
        } else {
            mettre(concept, "text", texteDeRepli);
        }
        return concept;
    }

    private static Map<String, Object> statut(String code) {
        Map<String, Object> codage = new LinkedHashMap<>();
        codage.put("system", "http://terminology.hl7.org/CodeSystem/condition-clinical");
        codage.put("code", code);
        return Map.of("coding", List.of(codage));
    }

    private static List<Map<String, Object>> notes(String texte) {
        if (texte == null || texte.isBlank()) return null;
        return List.of(Map.of("text", texte));
    }

    private static void mettre(Map<String, Object> map, String cle, Object valeur) {
        if (valeur != null) {
            map.put(cle, valeur instanceof LocalDate d ? d.toString() : valeur);
        }
    }
}
