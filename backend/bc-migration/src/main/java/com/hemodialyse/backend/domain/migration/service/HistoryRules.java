package com.hemodialyse.backend.domain.migration.service;

import com.hemodialyse.backend.domain.migration.model.MigrationEntity;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.domain.referential.admin.port.ReferentialAdminRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.domain.migration.service.EntityMigrator.issue;
import static com.hemodialyse.backend.domain.migration.service.RowValues.bool;
import static com.hemodialyse.backend.domain.migration.service.RowValues.date;
import static com.hemodialyse.backend.domain.migration.service.RowValues.decimal;
import static com.hemodialyse.backend.domain.migration.service.RowValues.integer;
import static com.hemodialyse.backend.domain.migration.service.RowValues.map;

/**
 * Règles de reprise des données historiques rattachées à un patient.
 */
public final class HistoryRules {

    private HistoryRules() {
    }

    /**
     * Toutes les règles, pour le câblage.
     */
    public static List<RecordRules> all(ReferentialAdminRepositoryPort referentials) {
        return List.of(new Attestations(), new PrisesEnCharge(referentials), new DossiersMedicaux(), new Antecedents(),
                new Serologies(), new AbordsVasculaires(), new Analyses(), new Seances());
    }

    /**
     * La fin ne peut précéder le début.
     */
    static void checkRange(EntityMigrator.Row row, String startKey, String endKey, List<ValidationIssue> errors) {
        LocalDate start = date(row, startKey);
        LocalDate end = date(row, endKey);
        if (start != null && end != null && end.isBefore(start)) {
            errors.add(issue(row.line(), endKey, "INCONSISTENT_DATES", "La date de fin précède la date de début.", Map.of()));
        }
    }

    static void checkNotFuture(EntityMigrator.Row row, String key, List<ValidationIssue> errors) {
        LocalDate value = date(row, key);
        if (value != null && value.isAfter(LocalDate.now())) {
            errors.add(issue(row.line(), key, "DATE_IN_FUTURE", "La date est dans le futur.", Map.of()));
        }
    }

    // ---- Attestations d'ouverture de droit -------------------------------------------------------------------

    public static final class Attestations implements RecordRules {
        public MigrationEntity entity() {
            return MigrationEntity.ATTESTATIONS;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("dateDebut", date(row, "dateDebut"));
        }

        /**
         * Une attestation expirée avant la période reprise est inutile.
         */
        public String periodField() {
            return "dateFin";
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkRange(row, "dateDebut", "dateFin", errors);
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            return map("dateDebut", date(row, "dateDebut"), "dateFin", date(row, "dateFin"));
        }
    }

    // ---- Prises en charge ------------------------------------------------------------------------------------

    public static final class PrisesEnCharge implements RecordRules {
        private final ReferentialAdminRepositoryPort referentials;
        /**
         * Codes forfait du centre, chargés par fichier ; propres au thread (la règle est partagée).
         */
        private final ThreadLocal<Map<String, UUID>> forfaits = ThreadLocal.withInitial(HashMap::new);

        public PrisesEnCharge(ReferentialAdminRepositoryPort referentials) {
            this.referentials = referentials;
        }

        public MigrationEntity entity() {
            return MigrationEntity.PRISES_EN_CHARGE;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("dateDebutDemande", date(row, "dateDebutDemande"));
        }

        public String periodField() {
            return "dateFinDemande";
        }

        public void begin(CenterId centerId) {
            Map<String, UUID> codes = forfaits.get();
            codes.clear();
            for (ReferentialEntry e : referentials.findAllForMatching(centerId, ReferentialKind.FORFAIT)) {
                String code = e.values().get("code");
                if (code != null) codes.putIfAbsent(code.trim().toUpperCase(Locale.ROOT), e.id());
            }
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkRange(row, "dateDebutDemande", "dateFinDemande", errors);
            checkRange(row, "dateDebutEffectif", "dateFinEffectif", errors);
            for (String field : List.of("forfaitDemande", "forfaitEffectif")) {
                String code = row.get(field);
                if (code != null && forfait(code) == null) {
                    errors.add(issue(row.line(), field, "REFERENCE_NOT_FOUND",
                            "Forfait « " + code + " » introuvable : créez-le d'abord (Administration → Référentiels).",
                            Map.of("value", code, "target", "forfaits")));
                }
            }
        }

        /**
         * Une PEC accordée sans dates / forfait effectifs reprend ceux de la demande.
         */
        public Map<String, Object> values(EntityMigrator.Row row) {
            boolean accordee = !"CREE".equals(row.get("statut"));
            UUID demande = forfait(row.get("forfaitDemande"));
            UUID effectif = row.get("forfaitEffectif") != null ? forfait(row.get("forfaitEffectif")) : accordee ? demande : null;
            LocalDate debutEffectif = date(row, "dateDebutEffectif");
            LocalDate finEffectif = date(row, "dateFinEffectif");
            return map("dateDebutDemande", date(row, "dateDebutDemande"), "dateFinDemande", date(row, "dateFinDemande"),
                    "forfaitDemandeId", demande, "statut", row.get("statut"),
                    "dateDebutEffectif", debutEffectif == null && accordee ? date(row, "dateDebutDemande") : debutEffectif,
                    "dateFinEffectif", finEffectif == null && accordee ? date(row, "dateFinDemande") : finEffectif,
                    "forfaitEffectifId", effectif);
        }

        private UUID forfait(String code) {
            return code == null ? null : forfaits.get().get(code.trim().toUpperCase(Locale.ROOT));
        }
    }

    // ---- Dossier médical (un par patient) --------------------------------------------------------------------

    public static final class DossiersMedicaux implements RecordRules {
        public MigrationEntity entity() {
            return MigrationEntity.DOSSIERS_MEDICAUX;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return Map.of();
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkNotFuture(row, "dateMiseEnDialyse", errors);
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            return map("dateMiseEnDialyse", date(row, "dateMiseEnDialyse"),
                    "nephropathieInitiale", row.get("nephropathieInitiale"),
                    "hepatiteBStatut", row.get("hepatiteBStatut"), "hepatiteCStatut", row.get("hepatiteCStatut"),
                    "observationGlobale", row.get("observationGlobale"), "conclusionMedicale", row.get("conclusionMedicale"));
        }
    }

    // ---- Antécédents -----------------------------------------------------------------------------------------

    public static final class Antecedents implements RecordRules {
        public MigrationEntity entity() {
            return MigrationEntity.ANTECEDENTS;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("typeAntecedent", row.get("typeAntecedent"), "libelleLibre", row.get("libelle"),
                    "dateDebut", date(row, "dateDebut"));
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkRange(row, "dateDebut", "dateFin", errors);
            checkNotFuture(row, "dateDebut", errors);
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            String code = row.get("codeCim10");
            return map("typeAntecedent", row.get("typeAntecedent"), "libelleLibre", row.get("libelle"),
                    "diagnosticCodeSystem", code == null ? null : "CIM10", "diagnosticCode", code,
                    "dateDebut", date(row, "dateDebut"), "dateFin", date(row, "dateFin"),
                    "statutClinique", row.get("statutClinique"), "severite", row.get("severite"), "note", row.get("note"));
        }
    }

    // ---- Sérologies ------------------------------------------------------------------------------------------

    public static final class Serologies implements RecordRules {
        public MigrationEntity entity() {
            return MigrationEntity.SEROLOGIES;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("marqueur", row.get("marqueur"), "datePrelevement", date(row, "datePrelevement"));
        }

        public String periodField() {
            return "datePrelevement";
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkNotFuture(row, "datePrelevement", errors);
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            return map("marqueur", row.get("marqueur"), "resultat", row.get("resultat"),
                    "datePrelevement", date(row, "datePrelevement"), "titre", decimal(row, "titre"),
                    "unite", row.get("unite"), "laboratoire", row.get("laboratoire"));
        }
    }

    // ---- Abords vasculaires ----------------------------------------------------------------------------------

    public static final class AbordsVasculaires implements RecordRules {
        public MigrationEntity entity() {
            return MigrationEntity.ABORDS_VASCULAIRES;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("typeAbord", row.get("typeAbord"), "dateCreation", date(row, "dateCreation"));
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkRange(row, "dateCreation", "dateFin", errors);
            if (Boolean.TRUE.equals(bool(row, "actif")) && date(row, "dateFin") != null
                    && date(row, "dateFin").isBefore(LocalDate.now())) {
                warnings.add(issue(row.line(), "actif", "ACTIVE_BUT_ENDED",
                        "Abord déclaré actif avec une date de fin passée.", Map.of()));
            }
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            return map("typeAbord", row.get("typeAbord"), "cote", row.get("cote"), "localisation", row.get("localisation"),
                    "dateCreation", date(row, "dateCreation"), "dateFin", date(row, "dateFin"),
                    "actif", bool(row, "actif"), "complications", row.get("complications"));
        }
    }

    // ---- Résultats d'analyses --------------------------------------------------------------------------------

    public static final class Analyses implements RecordRules {
        static final List<String> DECIMALS = List.of("hbGDl", "htPct", "ferritineNgMl", "cstfPct", "ureePreMgDl",
                "ureePostMgDl", "creatinineMgDl", "ktVMensuel", "phosphoreMgDl", "calciumMgDl", "pthPgMl", "albumineGDl",
                "proteinesGDl", "crpMgL");

        public MigrationEntity entity() {
            return MigrationEntity.ANALYSES;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("datePrelevement", date(row, "datePrelevement"));
        }

        public String periodField() {
            return "datePrelevement";
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkNotFuture(row, "datePrelevement", errors);
            if (row.get("plaquettes") == null && DECIMALS.stream().allMatch(k -> row.get(k) == null)) {
                errors.add(issue(row.line(), "datePrelevement", "NO_RESULT", "Aucun résultat renseigné sur cette ligne.", Map.of()));
            }
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            Map<String, Object> values = map("datePrelevement", date(row, "datePrelevement"),
                    "plaquettes", integer(row, "plaquettes"));
            DECIMALS.forEach(k -> values.put(k, decimal(row, k)));
            return values;
        }
    }

    // ---- Séances ---------------------------------------------------------------------------------------------

    /**
     * Séance historique, reprise dans son état final. Facturée dans l'ancien logiciel → FACTUREE (jamais refacturée) ;
     * sinon → SIGNEE, donc facturable dans la plateforme. Une séance déjà présente n'est jamais modifiée.
     */
    public static final class Seances implements RecordRules {
        public MigrationEntity entity() {
            return MigrationEntity.SEANCES;
        }

        public Map<String, Object> key(EntityMigrator.Row row) {
            return map("dateSeance", date(row, "dateSeance"));
        }

        public String periodField() {
            return "dateSeance";
        }

        public boolean updatable() {
            return false;
        }

        public void check(EntityMigrator.Row row, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
            checkNotFuture(row, "dateSeance", errors);
        }

        public Map<String, Object> values(EntityMigrator.Row row) {
            boolean facturee = Boolean.TRUE.equals(bool(row, "facturee"));
            return map("dateSeance", date(row, "dateSeance"), "statut", facturee ? "FACTUREE" : "SIGNEE");
        }
    }
}




