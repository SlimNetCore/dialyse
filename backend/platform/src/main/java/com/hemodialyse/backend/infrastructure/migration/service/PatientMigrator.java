package com.hemodialyse.backend.infrastructure.migration.service;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures.AssureConnu;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients.PatientDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients.ReportDuPatient;
import com.hemodialyse.backend.infrastructure.migration.port.GenerateursDeReprise;
import com.hemodialyse.backend.infrastructure.migration.port.GenerateursDeReprise.GenerateurRepris;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.acl.patientcouverture.ReferentielsDePatientCouverture;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.infrastructure.migration.service.EntityMigrator.issue;

/**
 * Reprise des patients et de leur affectation (salle, créneau, générateur, transporteurs, médecin, jours).
 * <p>
 * Un patient déjà présent dans le centre avec le même n° d'assurance est rapproché (mis à jour) plutôt que
 * dupliqué. Les références aux référentiels se font par code (ou par nom pour médecins et transporteurs) :
 * les référentiels doivent donc être saisis ou importés avant les patients.
 */
public class PatientMigrator implements EntityMigrator {

    private static final Set<String> ETATS_AVEC_DATE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");

    private final RepriseDesPatients patients;
    private final RepriseDesAssures assures;
    private final ReferentialAdminRepositoryPort referentials;
    private final GenerateursDeReprise generateurs;

    public PatientMigrator(RepriseDesPatients patients, RepriseDesAssures assures,
                           ReferentialAdminRepositoryPort referentials, GenerateursDeReprise generateurs) {
        this.patients = patients;
        this.assures = assures;
        this.referentials = referentials;
        this.generateurs = generateurs;
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

    /**
     * Valeurs du fichier à reporter : seules celles renseignées seront reprises, une reprise n'efface jamais une donnée.
     */
    private static ReportDuPatient report(Row row, Refs refs) {
        String enfants = row.get("nombreEnfants");
        String jours = row.get("joursDialyse");
        return new ReportDuPatient(row.get("numeroAssurance"), row.get("nom"), row.get("prenom"), row.get("sexe"),
                date(row.get("dateNaissance")), date(row.get("dateAdmission")), "VACANCIER".equals(row.get("typePatient")),
                row.get("etatPatient"), row.get("qualiteAssure"), row.get("codePatient"), row.get("civilite"),
                row.get("lieuNaissance"), row.get("situationFamiliale"), enfants == null ? null : Integer.parseInt(enfants),
                row.get("profession"), row.get("adresse"), row.get("telPersonnel"), row.get("telMobile"), row.get("telBureau"),
                row.get("email"), row.get("groupeSanguin"), date(row.get("dateEvenementEtat")), row.get("observation"),
                refs.centrePayeur(), refs.medecin(), refs.salle(), refs.creneau(), refs.generateur(), refs.transporteurAller(),
                refs.transporteurRetour(), jours == null ? null : jours(jours), row.get("assureNumeroAssurance"));
    }

    static Set<DayOfWeek> jours(String codes) {
        List<String> days = List.of(codes.split(","));
        Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
        if (days.contains("DIM")) jours.add(DayOfWeek.SUNDAY);
        if (days.contains("LUN")) jours.add(DayOfWeek.MONDAY);
        if (days.contains("MAR")) jours.add(DayOfWeek.TUESDAY);
        if (days.contains("MER")) jours.add(DayOfWeek.WEDNESDAY);
        if (days.contains("JEU")) jours.add(DayOfWeek.THURSDAY);
        if (days.contains("VEN")) jours.add(DayOfWeek.FRIDAY);
        if (days.contains("SAM")) jours.add(DayOfWeek.SATURDAY);
        return jours;
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
        CentreId center =ctx.centerId();
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, String> mapped = ctx.ids().findAll(center, entity());
        Lookups lookups = new Lookups(center);

        Map<String, PatientDuCentre> byNumero = new HashMap<>();
        Map<String, PatientDuCentre> byCode = new HashMap<>();
        Map<String, PatientDuCentre> byIdentity = new HashMap<>();
        Set<UUID> existingIds = new java.util.HashSet<>();
        for (PatientDuCentre p : patients.duCentre(center.valeur())) {
            existingIds.add(p.id());
            if (p.numeroAssurance() != null) byNumero.putIfAbsent(key(p.numeroAssurance()), p);
            if (p.code() != null) byCode.putIfAbsent(key(p.code()), p);
            byIdentity.putIfAbsent(identity(p.nom(), p.prenom(), p.dateNaissance()), p);
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
            // Une correspondance vers un patient supprimé depuis est ignorée : le patient est recréé sous le même identifiant
            // (KB-S6a-3 corrigé : ses affectations déjà écrites ne restent pas orphelines, ADR 0134).
            UUID targetId = mapped.containsKey(legacy) ? UUID.fromString(mapped.get(legacy)) : null;
            UUID idARetrouver = null;
            if (targetId != null && !existingIds.contains(targetId)) {
                idARetrouver = targetId;
                targetId = null;
            }
            PatientDuCentre sameNumero = byNumero.get(key(numero));
            if (targetId != null && sameNumero != null && !sameNumero.id().equals(targetId)) {
                errors.add(issue(row.line(), "numeroAssurance", "NUMERO_USED_BY_OTHER",
                        "Le n° d'assurance « " + numero + " » appartient déjà à un autre patient du centre ("
                                + sameNumero.nom() + " " + sameNumero.prenom() + ").", Map.of("value", numero)));
            }
            IdMapping.Operation operation = IdMapping.Operation.CREATED;
            if (targetId != null) {
                operation = IdMapping.Operation.UPDATED;
            } else if (sameNumero != null) {
                targetId = sameNumero.id();
                idARetrouver = null;
                operation = IdMapping.Operation.UPDATED;
                warnings.add(issue(row.line(), "numeroAssurance", "MATCHED_EXISTING",
                        "Patient déjà présent (" + sameNumero.nom() + " " + sameNumero.prenom()
                                + ") : il sera mis à jour et rapproché.", Map.of("value", numero)));
            }

            String code = row.get("codePatient");
            if (code != null) {
                PatientDuCentre owner = byCode.get(key(code));
                if (owner != null && !owner.id().equals(targetId)) {
                    errors.add(issue(row.line(), "codePatient", "CODE_ALREADY_USED",
                            "Le code patient « " + code + " » est déjà attribué à un autre patient.", Map.of("value", code)));
                }
            }

            checkDates(row, errors, warnings);
            checkAssure(ctx, row, errors);
            Refs refs = lookups.resolve(row, errors, warnings);

            PatientDuCentre twin = byIdentity.get(identity(row.get("nom"), row.get("prenom"), date(row.get("dateNaissance"))));
            if (twin != null && !twin.id().equals(targetId)) {
                warnings.add(issue(row.line(), "nom", "POSSIBLE_DUPLICATE",
                        "Doublon possible : un patient du centre porte les mêmes nom, prénom et date de naissance (n° "
                                + (twin.numeroAssurance() != null ? twin.numeroAssurance() : "?") + ").",
                        Map.of()));
            }
            if (errors.size() > errorsBefore) continue;
            plans.add(new Plan(row, legacy, targetId, idARetrouver, refs, operation));
        }

        int created = (int) plans.stream().filter(p -> p.existingId() == null).count();
        int updated = plans.size() - created;
        if (!errors.isEmpty() || !write) return new Outcome(created, updated, errors, warnings);

        for (Plan plan : plans) {
            UUID id = patients.reprendre(center.valeur(), plan.existingId() != null ? plan.existingId() : plan.idARetrouver(),
                    report(plan.row(), plan.refs()));
            ctx.ids().save(center, ctx.batch().getId(), new IdMapping(entity(), plan.legacyId(), id.toString(), plan.operation()));
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
        Optional<AssureConnu> assure = assures.trouver(numeroAssure);
        if (assure.isEmpty() || !ctx.centerId().valeur().equals(assure.get().centre())) {
            errors.add(issue(row.line(), "assureNumeroAssurance", "ASSURE_NOT_FOUND",
                    "Assuré « " + numeroAssure + " » introuvable dans le centre : importez d'abord le fichier des assurés.",
                    Map.of("value", numeroAssure)));
        }
    }

    /**
     * Références résolues d'une ligne.
     */
    private record Refs(UUID centrePayeur, UUID medecin, UUID salle, UUID creneau, UUID generateur,
                        UUID transporteurAller, UUID transporteurRetour) {
    }

    /**
     * @param existingId  patient du centre à mettre à jour (correspondance ou rapprochement), nul pour une création
     * @param idARetrouver identifiant d'une correspondance vers un patient supprimé depuis (la fiche est recréée sous cet identifiant)
     */
    private record Plan(Row row, String legacyId, UUID existingId, UUID idARetrouver, Refs refs, IdMapping.Operation operation) {
    }

    // ---- Références aux référentiels ---------------------------------------------------------------------------

    /**
     * Index des référentiels du centre, chargés une fois par fichier.
     */
    private final class Lookups {
        private final CentreId center;
        private final Map<ReferentialKind, Map<String, ReferentialEntry>> indexes = new HashMap<>();
        private final Map<CentreId, Map<String, GenerateurRepris>> generateurIndex = new HashMap<>();

        Lookups(CentreId center) {
            this.center = center;
        }

        private static String nz(String value) {
            return value == null ? "" : value;
        }

        Refs resolve(Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            UUID salle = find(row, "salle", ReferentialKind.SALLE, errors);
            GenerateurRepris generateurEquipement = findGenerateur(row, errors);
            UUID generateur = generateurEquipement == null ? null : generateurEquipement.id();
            if (generateurEquipement != null) {
                UUID salleDuGenerateur = generateurEquipement.salleId();
                if (salle == null && salleDuGenerateur != null) {
                    salle = salleDuGenerateur;
                } else if (salle != null && salleDuGenerateur != null && !salle.equals(salleDuGenerateur)) {
                    warnings.add(issue(row.line(), "generateur", "GENERATEUR_OTHER_ROOM",
                            "Le générateur « " + row.get("generateur") + " » est installé dans une autre salle que « "
                                    + row.get("salle") + " ».", Map.of()));
                }
            }
            return new Refs(
                    find(row, "centrePayeur", ReferentielsDePatientCouverture.CENTRE_PAYEUR, errors),
                    find(row, "medecinTraitant", com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.MEDECIN, errors),
                    salle,
                    find(row, "creneau", com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.CRENEAU, errors),
                    generateur,
                    find(row, "transporteurAller", ReferentielsDePatientCouverture.TRANSPORTEUR, errors),
                    find(row, "transporteurRetour", ReferentielsDePatientCouverture.TRANSPORTEUR, errors));
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
        private GenerateurRepris findGenerateur(Row row, List<ValidationIssue> errors) {
            String value = row.get("generateur");
            if (value == null) return null;
            GenerateurRepris entry = generateurIndex().get(key(LegacyValueParser.toCode(value)));
            if (entry == null) {
                errors.add(issue(row.line(), "generateur", "REFERENCE_NOT_FOUND",
                        "« " + value + " » est introuvable parmi les équipements GMAO (générateurs) de ce centre : "
                                + "créez-le d'abord (GMAO → Équipements).",
                        Map.of("value", value, "target", "gmao/equipements")));
                return null;
            }
            return entry;
        }

        private Map<String, GenerateurRepris> generateurIndex() {
            return generateurIndex.computeIfAbsent(center, c -> {
                Map<String, GenerateurRepris> index = new HashMap<>();
                for (GenerateurRepris e : generateurs.duCentre(c.valeur())) {
                    index.putIfAbsent(key(LegacyValueParser.toCode(nz(e.code()))), e);
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
                    List<String> keys = k.equals(com.hemodialyse.backend.infrastructure.acl.organisation.ReferentielsDeLOrganisation.MEDECIN)
                            ? List.of(v.get("nom") + " " + nz(v.get("prenom")), nz(v.get("prenom")) + " " + v.get("nom"))
                            : k.equals(ReferentielsDePatientCouverture.TRANSPORTEUR) ? List.of(nz(v.get("nom")))
                            : List.of(nz(v.get("code")));
                    keys.forEach(raw -> index.putIfAbsent(key(LegacyValueParser.toCode(raw)), e));
                }
                return index;
            });
        }
    }
}



