package com.hemodialyse.backend.infrastructure.persistence.adapter.referential;

import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Correspondance référentiel → table SQL (tables sans entité JPA, cf. {@code db/schema.sql}).
 * <p>
 * Tous les noms SQL proviennent de ces constantes : aucune valeur utilisateur n'est concaténée aux requêtes.
 */
final class ReferentialAdminTables {

    private static final Map<ReferentialKind, Table> TABLES = new HashMap<>();

    static {
        // Forfaits : retirés (RF2, ADR 0124) ; la table appartient à patient-couverture (catalogue de forfaits), les usages
        // (prises en charge, séances, lignes de facture) sont additionnés par ses propriétaires via UsageDuForfait.

        // Créneaux : retirés (PL11-2, ADR 0158) ; les créneaux sont gérés par l'Organisation (GestionDesCreneaux), qui publie leur
        // descripteur ; les usages sont additionnés par leurs propriétaires via UsageDuCreneau.

        TABLES.put(ReferentialKind.SALLE, new Table("salle",
                List.of(text("code", "code"), text("nom", "nom"), text("isolement", "isolement"),
                        new Column("capacite", "capacite", ColumnType.INTEGER)),
                List.of(), List.of("t.code", "t.nom"), "t.code",
                // Les générateurs vivent dans gmao_equipements (module GMAO v2) : l'usage d'une salle y est vérifié.
                List.of(new Usage("gmao_equipements", "salle_id"), new Usage("patients", "salle_id")),
                List.of("ref.salles", "ref.generateurs")));

        TABLES.put(ReferentialKind.MEDECIN, new Table("medecin",
                List.of(text("nom", "nom"), text("prenom", "prenom"), text("specialite", "specialite")),
                List.of(), List.of("t.nom", "t.prenom", "t.specialite"), "t.nom, t.prenom",
                List.of(new Usage("patients", "medecin_traitant_id"), new Usage("prescriptions_medicales", "medecin_id")),
                List.of("ref.medecins")));

        // Caisses, agences, centres payeurs : retirés (RF3a, ADR 0126) ; les tables caisse_assurance, agence et centre_payeur
        // appartiennent à patient-couverture (chaîne des payeurs), qui publie aussi leurs descripteurs (PL11-1, ADR 0157).

        // Transporteurs : retirés (RF1, ADR 0122) ; la table appartient à patient-couverture, qui publie le descripteur du
        // référentiel (champs, alias d'en-têtes, modèle d'import) depuis PL11-1 (ADR 0157).
    }

    private ReferentialAdminTables() {
    }

    static Table of(ReferentialKind kind) {
        Table table = TABLES.get(kind);
        if (table == null) throw new IllegalStateException("Référentiel géré par un contexte propriétaire : " + kind);
        return table;
    }

    private static Column text(String field, String column) {
        return new Column(field, column, ColumnType.TEXT);
    }

    enum ColumnType {TEXT, DECIMAL, INTEGER, UUID}

    /**
     * Champ de référentiel ↔ colonne.
     */
    record Column(String field, String column, ColumnType type) {
    }

    /**
     * Champ REFERENCE : table cible et expression affichée (ex. « S1 · Salle 1 »).
     */
    record Reference(String field, String table, String displayExpression) {
    }

    /**
     * Colonne qui pointe sur une ligne de ce référentiel (suppression refusée tant qu'elle est utilisée).
     */
    record Usage(String table, String column) {
    }

    record Table(String name, List<Column> columns, List<Reference> references, List<String> searchColumns,
                 String orderBy, List<Usage> usages, List<String> caches) {

        Column column(String field) {
            return columns.stream().filter(c -> c.field().equals(field)).findFirst().orElseThrow();
        }
    }
}

