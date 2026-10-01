package com.hemodialyse.backend.infrastructure.persistence.adapter.referential;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Correspondance référentiel → table SQL (tables sans entité JPA, cf. {@code db/schema.sql}).
 * <p>
 * Tous les noms SQL proviennent de ces constantes : aucune valeur utilisateur n'est concaténée aux requêtes.
 */
final class ReferentialAdminTables {

    private static final Map<ReferentialKind, Table> TABLES = new EnumMap<>(ReferentialKind.class);

    static {
        TABLES.put(ReferentialKind.FORFAIT, new Table("forfait",
                List.of(text("code", "code"), text("libelle", "libelle"), new Column("prix", "prix", ColumnType.DECIMAL)),
                List.of(), List.of("t.code", "t.libelle"), "t.code",
                List.of(new Usage("prise_en_charge", "forfait_demande_id"), new Usage("prise_en_charge", "forfait_effectif_id"),
                        new Usage("seances", "forfait_override_id"), new Usage("facture_lignes", "forfait_id")),
                List.of("ref.forfaits")));

        TABLES.put(ReferentialKind.CRENEAU, new Table("position_creneau",
                List.of(text("code", "code"), text("libelle", "libelle")),
                List.of(), List.of("t.code", "t.libelle"), "t.code",
                List.of(new Usage("patients", "position_id")),
                List.of("ref.positions")));

        TABLES.put(ReferentialKind.SALLE, new Table("salle",
                List.of(text("code", "code"), text("nom", "nom")),
                List.of(), List.of("t.code", "t.nom"), "t.code",
                // Les générateurs vivent désormais dans gmao_equipements (module GMAO v2), plus dans
                // l'ancien référentiel plat "generateur" — l'usage reste vérifié génériquement.
                List.of(new Usage("gmao_equipements", "salle_id"), new Usage("patients", "salle_id")),
                List.of("ref.salles", "ref.generateurs")));

        TABLES.put(ReferentialKind.MEDECIN, new Table("medecin",
                List.of(text("nom", "nom"), text("prenom", "prenom"), text("specialite", "specialite")),
                List.of(), List.of("t.nom", "t.prenom", "t.specialite"), "t.nom, t.prenom",
                List.of(new Usage("patients", "medecin_traitant_id"), new Usage("prescriptions_medicales", "medecin_id")),
                List.of("ref.medecins")));

        TABLES.put(ReferentialKind.CAISSE, new Table("caisse_assurance",
                List.of(text("code", "code"), text("nom", "nom"), text("typeCaisse", "type_caisse")),
                List.of(), List.of("t.code", "t.nom"), "t.code",
                List.of(new Usage("agence", "caisse_id")),
                List.of("ref.caisses", "ref.centresPayeursDetails")));

        TABLES.put(ReferentialKind.AGENCE, new Table("agence",
                List.of(text("code", "code"), text("nom", "nom"), new Column("caisse", "caisse_id", ColumnType.UUID)),
                List.of(new Reference("caisse", "caisse_assurance", "CONCAT(r_caisse.code, ' · ', r_caisse.nom)")),
                List.of("t.code", "t.nom", "r_caisse.code", "r_caisse.nom"), "t.code",
                List.of(new Usage("centre_payeur", "agence_id")),
                List.of("ref.agences", "ref.centresPayeursDetails")));

        TABLES.put(ReferentialKind.CENTRE_PAYEUR, new Table("centre_payeur",
                List.of(text("code", "code"), text("nom", "nom"), text("adresse", "adresse"),
                        new Column("agence", "agence_id", ColumnType.UUID)),
                List.of(new Reference("agence", "agence", "CONCAT(r_agence.code, ' · ', r_agence.nom)")),
                List.of("t.code", "t.nom", "t.adresse", "r_agence.code", "r_agence.nom"), "t.code",
                List.of(new Usage("patients", "centre_payeur_id")),
                List.of("ref.centresPayeurs", "ref.centresPayeursDetails")));

        TABLES.put(ReferentialKind.TRANSPORTEUR, new Table("transporteur",
                List.of(text("nom", "nom"), text("telephone", "telephone")),
                List.of(), List.of("t.nom", "t.telephone"), "t.nom",
                List.of(new Usage("patients", "transporteur_aller_id"), new Usage("patients", "transporteur_retour_id")),
                List.of("ref.transporteurs")));
    }

    private ReferentialAdminTables() {
    }

    static Table of(ReferentialKind kind) {
        return TABLES.get(kind);
    }

    private static Column text(String field, String column) {
        return new Column(field, column, ColumnType.TEXT);
    }

    enum ColumnType {TEXT, DECIMAL, UUID}

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

