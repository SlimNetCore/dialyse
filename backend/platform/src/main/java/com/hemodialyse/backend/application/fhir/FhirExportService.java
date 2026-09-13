package com.hemodialyse.backend.application.fhir;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.medical.allergie.port.AllergieRepositoryPort;
import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort;
import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueRepositoryPort;
import com.hemodialyse.backend.domain.medical.ordonnance.aggregate.Ordonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.entity.LigneOrdonnance;
import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.seance.model.AbordVasculaire;
import com.hemodialyse.backend.domain.seance.model.ResultatAnalyse;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Export FHIR (R4) en lecture seule du dossier médical patient sous forme d'un {@code Bundle}
 * JSON — sérialisation directe, aucune dépendance FHIR (HAPI) ajoutée. La forme des données du
 * module (VO {@code ConceptCode}, alignement des agrégats sur des ressources FHIR) rend cette
 * projection terminale possible sans jamais contraindre le modèle interne.
 * <p>
 * Choix délibéré : les ressources sont construites via des {@code LinkedHashMap} plutôt que des
 * DTO typés — un {@code Bundle} contient des ressources hétérogènes dont la plupart des champs
 * sont optionnels (sémantique FHIR), un DTO figé forcerait soit des champs toujours présents
 * (bruit), soit une hiérarchie de types par ressource disproportionnée pour un export en lecture
 * seule sans consommateur FHIR certifié.
 * <p>
 * Portée actuelle : {@code Patient}, {@code Condition} (antécédents), {@code AllergyIntolerance}
 * (allergies), {@code Observation} (résultats d'analyses + observations LOINC), {@code
 * MedicationRequest} (ordonnances), {@code Procedure} (abords vasculaires). Les demandes
 * d'examen ({@code ServiceRequest}) et administrations d'anémie ({@code MedicationAdministration})
 * ne sont pas encore mappées — extension naturelle, la structure s'y prête déjà.
 */
@Service
public class FhirExportService {

    private static final Map<CodingSystem, String> FHIR_SYSTEM_URI = Map.of(
            CodingSystem.CIM10, "http://hl7.org/fhir/sid/icd-10",
            CodingSystem.LOINC, "http://loinc.org",
            CodingSystem.ATC, "http://www.whocc.no/atc",
            CodingSystem.LOCAL, "urn:hemodialyse:local-code"
    );

    private final PatientRepositoryPort patientRepository;
    private final AntecedentRepositoryPort antecedentRepository;
    private final AllergieRepositoryPort allergieRepository;
    private final ResultatAnalyseRepositoryPort resultatAnalyseRepository;
    private final ObservationBiologiqueRepositoryPort observationRepository;
    private final OrdonnanceRepositoryPort ordonnanceRepository;
    private final AbordVasculaireRepositoryPort abordVasculaireRepository;

    public FhirExportService(PatientRepositoryPort patientRepository,
                             AntecedentRepositoryPort antecedentRepository,
                             AllergieRepositoryPort allergieRepository,
                             ResultatAnalyseRepositoryPort resultatAnalyseRepository,
                             ObservationBiologiqueRepositoryPort observationRepository,
                             OrdonnanceRepositoryPort ordonnanceRepository,
                             AbordVasculaireRepositoryPort abordVasculaireRepository) {
        this.patientRepository = patientRepository;
        this.antecedentRepository = antecedentRepository;
        this.allergieRepository = allergieRepository;
        this.resultatAnalyseRepository = resultatAnalyseRepository;
        this.observationRepository = observationRepository;
        this.ordonnanceRepository = ordonnanceRepository;
        this.abordVasculaireRepository = abordVasculaireRepository;
    }

    public Map<String, Object> exportBundle(CenterId centerId, UUID patientId) {
        Patient patient = patientRepository.findById(PatientId.of(patientId), centerId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable"));

        List<Object> entries = new ArrayList<>();
        String patientReference = "Patient/" + patientId;

        entries.add(entry(patientResource(patient)));
        antecedentRepository.findByPatientId(patientId, centerId)
                .forEach(a -> entries.add(entry(conditionResource(a, patientReference))));
        allergieRepository.findByPatientId(patientId, centerId)
                .forEach(a -> entries.add(entry(allergyIntoleranceResource(a, patientReference))));
        resultatAnalyseRepository.findByPatientId(patientId, centerId, null, null)
                .forEach(r -> observationsFromResultatAnalyse(r, patientReference).forEach(o -> entries.add(entry(o))));
        observationRepository.findPagedByPatientId(patientId, centerId, null, null, null, 0, 500).items()
                .forEach(o -> entries.add(entry(observationResource(o, patientReference))));
        ordonnanceRepository.findPagedByPatientId(patientId, centerId, 0, 500).items()
                .forEach(o -> medicationRequestsFromOrdonnance(o, patientReference).forEach(mr -> entries.add(entry(mr))));
        abordVasculaireRepository.findByPatientId(patientId, centerId)
                .forEach(a -> entries.add(entry(procedureResource(a, patientReference))));

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "collection");
        bundle.put("timestamp", OffsetDateTime.now().toString());
        bundle.put("total", entries.size());
        bundle.put("entry", entries);
        return bundle;
    }

    private Map<String, Object> entry(Map<String, Object> resource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("resource", resource);
        return entry;
    }

    private Map<String, Object> patientResource(Patient p) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        resource.put("id", p.getId().value().toString());
        Map<String, Object> name = new LinkedHashMap<>();
        put(name, "family", p.getNom());
        put(name, "given", p.getPrenom() == null ? null : List.of(p.getPrenom()));
        resource.put("name", List.of(name));
        put(resource, "gender", mapSexe(p.getSexe()));
        put(resource, "birthDate", p.getDateNaissance());
        if (p.getNumeroAssurance() != null && p.getNumeroAssurance().value() != null) {
            Map<String, Object> identifier = new LinkedHashMap<>();
            identifier.put("system", "urn:hemodialyse:numero-assurance");
            identifier.put("value", p.getNumeroAssurance().value());
            resource.put("identifier", List.of(identifier));
        }
        return resource;
    }

    private String mapSexe(String sexe) {
        if (sexe == null) return null;
        return switch (sexe.trim().toUpperCase()) {
            case "M", "MASCULIN" -> "male";
            case "F", "FEMININ" -> "female";
            default -> "unknown";
        };
    }

    private Map<String, Object> conditionResource(Antecedent a, String patientReference) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Condition");
        resource.put("id", a.getId().toString());
        resource.put("subject", reference(patientReference));
        resource.put("code", codeableConcept(a.getDiagnostic(), a.getLibelleLibre()));
        put(resource, "clinicalStatus", statusCodeableConcept(
                a.getStatutClinique().name().equals("ACTIF") ? "active" : "resolved"));
        put(resource, "onsetDateTime", a.getPeriode().debut());
        put(resource, "abatementDateTime", a.getPeriode().fin());
        put(resource, "note", noteList(a.getNote()));
        put(resource, "severity", a.getSeverite() == null ? null : textCodeableConcept(a.getSeverite()));
        return resource;
    }

    private Map<String, Object> allergyIntoleranceResource(Allergie a, String patientReference) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "AllergyIntolerance");
        resource.put("id", a.getId().toString());
        resource.put("patient", reference(patientReference));
        resource.put("code", codeableConcept(a.getSubstance(), null));
        put(resource, "criticality", a.getCriticite().name().toLowerCase());
        put(resource, "clinicalStatus", statusCodeableConcept(a.getStatutVerification().name().toLowerCase()));
        put(resource, "recordedDate", a.getDateConstatation());
        if (a.getManifestations() != null && !a.getManifestations().isBlank()) {
            Map<String, Object> reaction = new LinkedHashMap<>();
            reaction.put("manifestation", List.of(textCodeableConcept(a.getManifestations())));
            resource.put("reaction", List.of(reaction));
        }
        return resource;
    }

    private List<Map<String, Object>> observationsFromResultatAnalyse(ResultatAnalyse r, String patientReference) {
        List<Map<String, Object>> observations = new ArrayList<>();
        addLocalObservation(observations, r, patientReference, "hbGDl", "Hémoglobine", r.getHbGDl(), "g/dL");
        addLocalObservation(observations, r, patientReference, "htPct", "Hématocrite", r.getHtPct(), "%");
        addLocalObservation(observations, r, patientReference, "ferritineNgMl", "Ferritine", r.getFerritineNgMl(), "ng/mL");
        addLocalObservation(observations, r, patientReference, "cstfPct", "Coefficient de saturation de la transferrine",
                r.getCstfPct(), "%");
        addLocalObservation(observations, r, patientReference, "ktVMensuel", "Kt/V", r.getKtVMensuel(), "");
        addLocalObservation(observations, r, patientReference, "phosphoreMgDl", "Phosphore", r.getPhosphoreMgDl(), "mg/dL");
        addLocalObservation(observations, r, patientReference, "calciumMgDl", "Calcium", r.getCalciumMgDl(), "mg/dL");
        addLocalObservation(observations, r, patientReference, "pthPgMl", "Parathormone", r.getPthPgMl(), "pg/mL");
        addLocalObservation(observations, r, patientReference, "albumineGDl", "Albumine", r.getAlbumineGDl(), "g/dL");
        addLocalObservation(observations, r, patientReference, "crpMgL", "CRP", r.getCrpMgL(), "mg/L");
        return observations;
    }

    private void addLocalObservation(List<Map<String, Object>> observations, ResultatAnalyse r, String patientReference,
                                     String code, String display, BigDecimal valeur, String unite) {
        if (valeur == null) return;
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Observation");
        resource.put("id", r.getId().toString() + "-" + code);
        resource.put("status", "final");
        resource.put("subject", reference(patientReference));
        resource.put("code", codeableConcept(ConceptCode.of(CodingSystem.LOCAL, code, display), display));
        put(resource, "effectiveDateTime", r.getDatePrelevement());
        Map<String, Object> quantity = new LinkedHashMap<>();
        quantity.put("value", valeur);
        put(quantity, "unit", unite.isBlank() ? null : unite);
        resource.put("valueQuantity", quantity);
        observations.add(resource);
    }

    private Map<String, Object> observationResource(ObservationBiologique o, String patientReference) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Observation");
        resource.put("id", o.getId().toString());
        resource.put("status", mapStatutObservation(o.getStatut().name()));
        resource.put("subject", reference(patientReference));
        resource.put("code", codeableConcept(o.getAnalyte(), null));
        put(resource, "effectiveDateTime", o.getDatePrelevement());
        o.getValeurNum().ifPresentOrElse(
                v -> {
                    Map<String, Object> quantity = new LinkedHashMap<>();
                    quantity.put("value", v.valeur());
                    quantity.put("unit", v.unite());
                    resource.put("valueQuantity", quantity);
                },
                () -> put(resource, "valueString", o.getValeurTexte())
        );
        return resource;
    }

    private String mapStatutObservation(String statut) {
        return switch (statut) {
            case "PRELIMINAIRE" -> "preliminary";
            case "CORRIGE" -> "corrected";
            default -> "final";
        };
    }

    private List<Map<String, Object>> medicationRequestsFromOrdonnance(Ordonnance o, String patientReference) {
        List<Map<String, Object>> requests = new ArrayList<>();
        String status = switch (o.getStatut()) {
            case BROUILLON -> "draft";
            case SIGNEE, IMPRIMEE -> "active";
            case ANNULEE -> "cancelled";
        };
        for (LigneOrdonnance ligne : o.getLignes()) {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("resourceType", "MedicationRequest");
            resource.put("id", ligne.getId().toString());
            resource.put("status", status);
            resource.put("intent", "order");
            resource.put("medicationCodeableConcept", codeableConcept(ligne.getMedicament(), ligne.getLibelle()));
            resource.put("subject", reference(patientReference));
            put(resource, "authoredOn", o.getDatePrescription());
            put(resource, "requester", o.getMedecinId() == null ? null : practitionerReference(o.getMedecinId()));

            Map<String, Object> dosage = new LinkedHashMap<>();
            put(dosage, "text", ligne.getPosologie());
            if (ligne.getVoie() != null) {
                dosage.put("route", textCodeableConcept(ligne.getVoie()));
            }
            if (ligne.getDureeJours() != null) {
                Map<String, Object> repeat = new LinkedHashMap<>();
                repeat.put("duration", ligne.getDureeJours());
                repeat.put("durationUnit", "d");
                dosage.put("timing", Map.of("repeat", repeat));
            }
            resource.put("dosageInstruction", List.of(dosage));

            if (ligne.getQuantite() != null) {
                Map<String, Object> quantity = new LinkedHashMap<>();
                quantity.put("value", ligne.getQuantite());
                resource.put("dispenseRequest", Map.of("quantity", quantity));
            }
            requests.add(resource);
        }
        return requests;
    }

    private Map<String, Object> procedureResource(AbordVasculaire a, String patientReference) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Procedure");
        resource.put("id", a.getId().toString());
        resource.put("status", Boolean.TRUE.equals(a.getActif()) ? "in-progress" : "completed");
        resource.put("subject", reference(patientReference));
        resource.put("code", textCodeableConcept(a.getTypeAbord()));
        String bodySite = (a.getCote() != null ? a.getCote() + " " : "") + (a.getLocalisation() != null ? a.getLocalisation() : "");
        if (!bodySite.isBlank()) {
            resource.put("bodySite", List.of(textCodeableConcept(bodySite.trim())));
        }
        Map<String, Object> period = new LinkedHashMap<>();
        put(period, "start", a.getDateCreation());
        put(period, "end", a.getDateFin());
        if (!period.isEmpty()) {
            resource.put("performedPeriod", period);
        }
        put(resource, "note", noteList(a.getComplications()));
        return resource;
    }

    private Map<String, Object> reference(String reference) {
        return Map.of("reference", reference);
    }

    private Map<String, Object> practitionerReference(String medecinId) {
        return Map.of("display", medecinId);
    }

    private Map<String, Object> codeableConcept(ConceptCode code, String fallbackText) {
        Map<String, Object> concept = new LinkedHashMap<>();
        if (code != null) {
            Map<String, Object> coding = new LinkedHashMap<>();
            coding.put("system", FHIR_SYSTEM_URI.get(code.system()));
            coding.put("code", code.code());
            put(coding, "display", code.display());
            concept.put("coding", List.of(coding));
            put(concept, "text", code.display() != null ? code.display() : fallbackText);
        } else {
            put(concept, "text", fallbackText);
        }
        return concept;
    }

    private Map<String, Object> textCodeableConcept(String text) {
        return Map.of("text", text);
    }

    private Map<String, Object> statusCodeableConcept(String code) {
        Map<String, Object> coding = new LinkedHashMap<>();
        coding.put("system", "http://terminology.hl7.org/CodeSystem/condition-clinical");
        coding.put("code", code);
        return Map.of("coding", List.of(coding));
    }

    private List<Map<String, Object>> noteList(String text) {
        if (text == null || text.isBlank()) return null;
        return List.of(Map.of("text", text));
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value instanceof LocalDate d ? d.toString() : value);
        }
    }
}
