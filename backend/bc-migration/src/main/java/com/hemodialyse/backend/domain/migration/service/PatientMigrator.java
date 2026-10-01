package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.assure.model.Assure;
import com.hemodialyse.backend.domain.assure.model.AssurePatientAssignment;
import com.hemodialyse.backend.domain.assure.port.AssurePatientRepositoryPort;
import com.hemodialyse.backend.domain.assure.port.AssureRepositoryPort;
import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.migration.model.IdMapping;
import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.model.PatientType;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.AssureInfo;
import com.hemodialyse.backend.domain.patient.vo.JoursDialyse;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.domain.migration.service.EntityMigrator.issue;

/**
 * Reprise des patients et de leur affectation (salle, créneau, générateur, transporteurs, médecin, jours).
 * <p>
 * Un patient déjà présent dans le centre avec le même n° d'assurance est rapproché (mis à jour) plutôt que
 * dupliqué. Les références aux référentiels se font par code (ou par nom pour médecins et transporteurs) :
 * les référentiels doivent donc être saisis ou importés avant les patients.
 */
public class PatientMigrator implements EntityMigrator {

    private static final Set<String> ETATS_AVEC_DATE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");

    private final PatientRepositoryPort patients;
    private final AssureRepositoryPort assures;
    private final AssurePatientRepositoryPort assignments;
    private final ReferentialAdminRepositoryPort referentials;
    private final EquipementRepositoryPort equipements;

    public PatientMigrator(PatientRepositoryPort patients, AssureRepositoryPort assures,
                           AssurePatientRepositoryPort assignments, ReferentialAdminRepositoryPort referentials,
                           EquipementRepositoryPort equipements) {
        this.patients = patients;
        this.assures = assures;
        this.assignments = assignments;
        this.referentials = referentials;
        this.equipements = equipements;
    }

    private static void checkDates(Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        LocalDate naissance = date(row.get("dateNaissance"));
        LocalDate admission = date(row.get("dateAdmission"));
        LocalDate evenement = date(row.get("dateEvenementEtat"));
        LocalDate today = LocalDate.now();
        if (naissance != null && naissance.isAfter(today)) {
            errors.add(issue(row.line(), "dateNaissance", "DATE_IN_FUTURE", "La date de naissance est dans le futur.", Map.of()));
        }
        if (naissance != null && admission != null && admission.isBefore(naissance)) {
            errors.add(issue(row.line(), "dateAdmission", "INCONSISTENT_DATES",
                    "La date d'admission est antérieure à la date de naissance.", Map.of()));
        }
        if (evenement != null && admission != null && evenement.isBefore(admission)) {
            errors.add(issue(row.line(), "dateEvenementEtat", "INCONSISTENT_DATES",
                    "La date de l'événement est antérieure à la date d'admission.", Map.of()));
        }
        if (evenement == null && ETATS_AVEC_DATE.contains(row.get("etatPatient"))) {
            warnings.add(issue(row.line(), "dateEvenementEtat", "EVENT_DATE_MISSING",
                    "État « " + row.get("etatPatient") + " » sans date d'événement.", Map.of()));
        }
    }

    private static Patient newPatient(CenterId center, Row row) {
        Patient patient = Patient.creer(center, row.get("nom"), row.get("prenom"), row.get("sexe"),
                date(row.get("dateAdmission")), date(row.get("dateNaissance")),
                new NumeroAssurance(row.get("numeroAssurance")), PatientType.valueOf(row.get("typePatient")));
        patient.setCreatedAt(OffsetDateTime.now());
        return patient;
    }

    /**
     * Seules les valeurs renseignées dans le fichier sont reportées : une reprise n'efface jamais une donnée.
     */
    private static void apply(Patient p, Row row, Refs refs) {
        p.setNom(row.get("nom"));
        p.setPrenom(row.get("prenom"));
        p.setSexe(row.get("sexe"));
        p.setDateNaissance(date(row.get("dateNaissance")));
        p.setDateAdmission(date(row.get("dateAdmission")));
        p.setNumeroAssurance(new NumeroAssurance(row.get("numeroAssurance")));
        p.setTypePatient(PatientType.valueOf(row.get("typePatient")));
        p.setEtatPatient(row.get("etatPatient"));
        p.setQualiteAssure(row.get("qualiteAssure"));
        if (row.get("codePatient") != null) p.setCodePatient(row.get("codePatient"));
        if (row.get("civilite") != null) p.setCivilite(row.get("civilite"));
        if (row.get("lieuNaissance") != null) p.setLieuNaissance(row.get("lieuNaissance"));
        if (row.get("situationFamiliale") != null) p.setSituationFamiliale(row.get("situationFamiliale"));
        if (row.get("nombreEnfants") != null) p.setNombreEnfants(Integer.parseInt(row.get("nombreEnfants")));
        if (row.get("profession") != null) p.setProfession(row.get("profession"));
        if (row.get("adresse") != null) p.setAdresse(row.get("adresse"));
        if (row.get("telPersonnel") != null) p.setTelPersonnel(row.get("telPersonnel"));
        if (row.get("telMobile") != null) p.setTelMobile(row.get("telMobile"));
        if (row.get("telBureau") != null) p.setTelBureau(row.get("telBureau"));
        if (row.get("email") != null) p.setEmail(row.get("email"));
        if (row.get("groupeSanguin") != null) p.setGroupeSanguin(row.get("groupeSanguin"));
        if (row.get("dateEvenementEtat") != null) p.setDateEvenementEtat(date(row.get("dateEvenementEtat")));
        if (row.get("observation") != null) p.setObservation(row.get("observation"));
        if (refs.centrePayeur() != null) p.setCentrePayeurId(refs.centrePayeur());
        if (refs.medecin() != null) p.setMedecinTraitantId(refs.medecin());
        if (refs.salle() != null) p.setSalleId(refs.salle());
        if (refs.creneau() != null) p.setPositionId(refs.creneau());
        if (refs.generateur() != null) p.setGenerateurId(refs.generateur());
        if (refs.transporteurAller() != null) p.setTransporteurAllerId(refs.transporteurAller());
        if (refs.transporteurRetour() != null) p.setTransporteurRetourId(refs.transporteurRetour());
        if (row.get("joursDialyse") != null) p.setJoursDialyse(jours(row.get("joursDialyse")));
    }

    static JoursDialyse jours(String codes) {
        List<String> days = List.of(codes.split(","));
        return new JoursDialyse(days.contains("DIM"), days.contains("LUN"), days.contains("MAR"), days.contains("MER"),
                days.contains("JEU"), days.contains("VEN"), days.contains("SAM"));
    }

    // ---- Contrôles -------------------------------------------------------------------------------------------

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ---- Écriture --------------------------------------------------------------------------------------------

    private static String identity(String nom, String prenom, LocalDate naissance) {
        return key(LegacyValueParser.toCode(nom)) + "|" + key(LegacyValueParser.toCode(prenom)) + "|" + naissance;
    }

    @Override
    public MigrationEntity entity() {
        return MigrationEntity.PATIENTS;
    }

    @Override
    public Outcome migrate(Context ctx, List<Row> rows, boolean write) {
        CenterId center = ctx.centerId();
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, String> mapped = ctx.ids().findAll(center, entity());
        Lookups lookups = new Lookups(center);

        List<Patient> existingPatients = patients.findAllByCenter(center);
        Map<String, Patient> byNumero = new HashMap<>();
        Map<String, Patient> byCode = new HashMap<>();
        Map<String, Patient> byIdentity = new HashMap<>();
        Set<UUID> existingIds = new java.util.HashSet<>();
        for (Patient p : existingPatients) {
            existingIds.add(p.getId().value());
            if (p.getNumeroAssurance() != null) byNumero.putIfAbsent(key(p.getNumeroAssurance().value()), p);
            if (p.getCodePatient() != null) byCode.putIfAbsent(key(p.getCodePatient()), p);
            byIdentity.putIfAbsent(identity(p.getNom(), p.getPrenom(), p.getDateNaissance()), p);
        }

        Map<String, Integer> seenLegacy = new HashMap<>();
        Map<String, Integer> seenNumero = new HashMap<>();
        Map<String, Integer> seenCode = new HashMap<>();
        List<Plan> plans = new ArrayList<>();

        for (Row row : rows) {
            int errorsBefore = errors.size();
            String legacy = row.get("legacyId");
            String numero = row.get("numeroAssurance");
            AssureMigrator.duplicate(seenLegacy, legacy, row, "legacyId", "identifiant d'origine", errors);
            AssureMigrator.duplicate(seenNumero, key(numero), row, "numeroAssurance", "n° d'assurance", errors);
            if (row.get("codePatient") != null) {
                AssureMigrator.duplicate(seenCode, key(row.get("codePatient")), row, "codePatient", "code patient", errors);
            }

            // Cible : déjà reprise (identifiant d'origine), sinon rapprochée par n° d'assurance, sinon nouvelle.
            // Une correspondance vers un patient supprimé depuis est ignorée (le patient sera recréé).
            UUID targetId = mapped.containsKey(legacy) ? UUID.fromString(mapped.get(legacy)) : null;
            if (targetId != null && !existingIds.contains(targetId)) targetId = null;
            Patient sameNumero = byNumero.get(key(numero));
            if (targetId != null && sameNumero != null && !sameNumero.getId().value().equals(targetId)) {
                errors.add(issue(row.line(), "numeroAssurance", "NUMERO_USED_BY_OTHER",
                        "Le n° d'assurance « " + numero + " » appartient déjà à un autre patient du centre ("
                                + sameNumero.getNom() + " " + sameNumero.getPrenom() + ").", Map.of("value", numero)));
            }
            IdMapping.Operation operation = IdMapping.Operation.CREATED;
            if (targetId != null) {
                operation = IdMapping.Operation.UPDATED;
            } else if (sameNumero != null) {
                targetId = sameNumero.getId().value();
                operation = IdMapping.Operation.UPDATED;
                warnings.add(issue(row.line(), "numeroAssurance", "MATCHED_EXISTING",
                        "Patient déjà présent (" + sameNumero.getNom() + " " + sameNumero.getPrenom()
                                + ") : il sera mis à jour et rapproché.", Map.of("value", numero)));
            }

            String code = row.get("codePatient");
            if (code != null) {
                Patient owner = byCode.get(key(code));
                if (owner != null && !owner.getId().value().equals(targetId)) {
                    errors.add(issue(row.line(), "codePatient", "CODE_ALREADY_USED",
                            "Le code patient « " + code + " » est déjà attribué à un autre patient.", Map.of("value", code)));
                }
            }

            checkDates(row, errors, warnings);
            checkAssure(ctx, row, errors);
            Refs refs = lookups.resolve(row, errors, warnings);

            Patient twin = byIdentity.get(identity(row.get("nom"), row.get("prenom"), date(row.get("dateNaissance"))));
            if (twin != null && !twin.getId().value().equals(targetId)) {
                warnings.add(issue(row.line(), "nom", "POSSIBLE_DUPLICATE",
                        "Doublon possible : un patient du centre porte les mêmes nom, prénom et date de naissance (n° "
                                + (twin.getNumeroAssurance() != null ? twin.getNumeroAssurance().value() : "?") + ").",
                        Map.of()));
            }
            if (errors.size() > errorsBefore) continue;
            plans.add(new Plan(row, legacy, targetId, refs, operation));
        }

        int created = (int) plans.stream().filter(p -> p.existingId() == null).count();
        int updated = plans.size() - created;
        if (!errors.isEmpty() || !write) return new Outcome(created, updated, errors, warnings);

        for (Plan plan : plans) {
            Patient patient = plan.existingId() == null ? newPatient(center, plan.row())
                    : patients.findById(PatientId.of(plan.existingId()), center).orElseThrow();
            apply(patient, plan.row(), plan.refs());
            linkAssure(center, patient, plan.row());
            Patient saved = patients.save(patient);
            ctx.ids().save(center, ctx.batch().getId(),
                    new IdMapping(entity(), plan.legacyId(), saved.getId().value().toString(), plan.operation()));
        }
        return new Outcome(created, updated, errors, warnings);
    }

    private void checkAssure(Context ctx, Row row, List<ValidationIssue> errors) {
        if ("ASSURE_LUI_MEME".equals(row.get("qualiteAssure"))) return;
        String numeroAssure = row.get("assureNumeroAssurance");
        if (numeroAssure == null) {
            errors.add(issue(row.line(), "assureNumeroAssurance", "REQUIRED",
                    "« N° d'assurance de l'assuré » est obligatoire quand le patient n'est pas l'assuré lui-même.", Map.of()));
            return;
        }
        Optional<Assure> assure = assures.findByNumeroAssurance(numeroAssure);
        if (assure.isEmpty() || !ctx.centerId().value().equals(assure.get().getCenterId())) {
            errors.add(issue(row.line(), "assureNumeroAssurance", "ASSURE_NOT_FOUND",
                    "Assuré « " + numeroAssure + " » introuvable dans le centre : importez d'abord le fichier des assurés.",
                    Map.of("value", numeroAssure)));
        }
    }

    /**
     * Même règle que la création d'un patient à l'écran : l'assuré principal est l'unique affectation principale.
     */
    private void linkAssure(CenterId center, Patient patient, Row row) {
        UUID patientId = patient.getId().value();
        if ("ASSURE_LUI_MEME".equals(row.get("qualiteAssure"))) {
            assignments.clearPrimary(center, patientId);
            patient.setAssureNumeroAssurance(row.get("numeroAssurance"));
            patient.setAssureInfo(new AssureInfo(patient.getSexe(), patient.getNom(), patient.getPrenom(),
                    patient.getDateNaissance() != null ? patient.getDateNaissance().toString() : null,
                    patient.getTelPersonnel(), patient.getAdresse(), patient.getGroupeSanguin(),
                    patient.getTelMobile(), patient.getTelBureau()));
            return;
        }
        String numeroAssure = row.get("assureNumeroAssurance");
        Optional<AssurePatientAssignment> primary = assignments.findPrimary(center, patientId);
        if (primary.isEmpty() || !numeroAssure.equalsIgnoreCase(primary.get().getNumeroAssurance())) {
            assignments.clearPrimary(center, patientId);
            AssurePatientAssignment assignment = new AssurePatientAssignment();
            assignment.setPatientId(patientId);
            assignment.setNumeroAssurance(numeroAssure);
            assignment.setCenterId(center.value());
            assignment.setPrimary(true);
            assignment.setDateAffectation(OffsetDateTime.now());
            assignment.setDateDebutAffectation(patient.getDateAdmission());
            assignments.save(assignment);
        }
        patient.setAssureNumeroAssurance(numeroAssure);
    }

    /**
     * Références résolues d'une ligne.
     */
    private record Refs(UUID centrePayeur, UUID medecin, UUID salle, UUID creneau, UUID generateur,
                        UUID transporteurAller, UUID transporteurRetour) {
    }

    private record Plan(Row row, String legacyId, UUID existingId, Refs refs, IdMapping.Operation operation) {
    }

    // ---- Références aux référentiels ---------------------------------------------------------------------------

    /**
     * Index des référentiels du centre, chargés une fois par fichier.
     */
    private final class Lookups {
        private final CenterId center;
        private final Map<ReferentialKind, Map<String, ReferentialEntry>> indexes = new EnumMap<>(ReferentialKind.class);
        private final Map<CenterId, Map<String, Equipement>> generateurIndex = new HashMap<>();

        Lookups(CenterId center) {
            this.center = center;
        }

        private static String nz(String value) {
            return value == null ? "" : value;
        }

        Refs resolve(Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            UUID salle = find(row, "salle", ReferentialKind.SALLE, errors);
            Equipement generateurEquipement = findGenerateur(row, errors);
            UUID generateur = generateurEquipement == null ? null : generateurEquipement.getId();
            if (generateurEquipement != null) {
                UUID salleDuGenerateur = generateurEquipement.getSalleId();
                if (salle == null && salleDuGenerateur != null) {
                    salle = salleDuGenerateur;
                } else if (salle != null && salleDuGenerateur != null && !salle.equals(salleDuGenerateur)) {
                    warnings.add(issue(row.line(), "generateur", "GENERATEUR_OTHER_ROOM",
                            "Le générateur « " + row.get("generateur") + " » est installé dans une autre salle que « "
                                    + row.get("salle") + " ».", Map.of()));
                }
            }
            return new Refs(
                    find(row, "centrePayeur", ReferentialKind.CENTRE_PAYEUR, errors),
                    find(row, "medecinTraitant", ReferentialKind.MEDECIN, errors),
                    salle,
                    find(row, "creneau", ReferentialKind.CRENEAU, errors),
                    generateur,
                    find(row, "transporteurAller", ReferentialKind.TRANSPORTEUR, errors),
                    find(row, "transporteurRetour", ReferentialKind.TRANSPORTEUR, errors));
        }

        private UUID find(Row row, String field, ReferentialKind kind, List<ValidationIssue> errors) {
            String value = row.get(field);
            if (value == null) return null;
            ReferentialEntry entry = index(kind).get(key(LegacyValueParser.toCode(value)));
            if (entry == null) {
                errors.add(issue(row.line(), field, "REFERENCE_NOT_FOUND",
                        "« " + value + " » est introuvable dans « " + kind.label()
                                + " » : créez-le ou importez-le d'abord (Administration → Référentiels).",
                        Map.of("value", value, "target", kind.slug())));
                return null;
            }
            return entry.id();
        }

        /**
         * Les générateurs de dialyse ne sont plus un référentiel administrable génériquement (module GMAO
         * v2) : ils sont résolus directement via l'agrégat GMAO Equipement (type GENERATEUR_DIALYSE),
         * géré via /gmao/equipements — pas de {@link ReferentialKind} dédié.
         */
        private Equipement findGenerateur(Row row, List<ValidationIssue> errors) {
            String value = row.get("generateur");
            if (value == null) return null;
            Equipement entry = generateurIndex().get(key(LegacyValueParser.toCode(value)));
            if (entry == null) {
                errors.add(issue(row.line(), "generateur", "REFERENCE_NOT_FOUND",
                        "« " + value + " » est introuvable parmi les équipements GMAO (générateurs) de ce centre : "
                                + "créez-le d'abord (GMAO → Équipements).",
                        Map.of("value", value, "target", "gmao/equipements")));
                return null;
            }
            return entry;
        }

        private Map<String, Equipement> generateurIndex() {
            return generateurIndex.computeIfAbsent(center, c -> {
                Map<String, Equipement> index = new HashMap<>();
                for (Equipement e : equipements.findByCentreIdAndType(c.value(), "GENERATEUR_DIALYSE")) {
                    index.putIfAbsent(key(LegacyValueParser.toCode(nz(e.getCode()))), e);
                }
                return index;
            });
        }

        /**
         * Clés de recherche : code (ou numéro), nom ; médecins par « nom prénom » et « prénom nom ».
         */
        private Map<String, ReferentialEntry> index(ReferentialKind kind) {
            return indexes.computeIfAbsent(kind, k -> {
                Map<String, ReferentialEntry> index = new HashMap<>();
                for (ReferentialEntry e : referentials.findAllForMatching(center, k)) {
                    Map<String, String> v = e.values();
                    List<String> keys = switch (k) {
                        case MEDECIN ->
                                List.of(v.get("nom") + " " + nz(v.get("prenom")), nz(v.get("prenom")) + " " + v.get("nom"));
                        case TRANSPORTEUR -> List.of(nz(v.get("nom")));
                        default -> List.of(nz(v.get("code")));
                    };
                    keys.forEach(raw -> index.putIfAbsent(key(LegacyValueParser.toCode(raw)), e));
                }
                return index;
            });
        }
    }
}



