package com.hemodialyse.backend.characterization.comptabilite.referentiel;

import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteComptableJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.ComptePayeurJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.JournalComptableJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.MappingComptableJpa;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Les entités legacy du référentiel comptable (journaux) sont retirées (C1) : il n'y a plus de second modèle à comparer. Le
 * modèle de persistance du module est comparé à la description figée des tables historiques ({@code comptes_comptables} et
 * {@code comptes_payeurs} de la migration V33, {@code journaux_comptables} de V32 : mêmes longueurs, nullités, définitions et
 * contraintes d'unicité), pour qu'il ne dérive pas : la base refuse les valeurs trop longues comme avant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ModelesDePersistanceAlignesComptabiliteIntegrationTest {

    @Autowired
    private EntityManagerFactory emf;
    @Autowired
    private JdbcTemplate jdbc;

    private static Set<String> colonnesAttendues(String... colonnes) {
        return new TreeSet<>(Arrays.asList(colonnes));
    }

    private static String decrire(SelectableMapping colonne) {
        // la longueur n'a de sens que pour une chaîne (255 par défaut sur les autres types, nulle selon le type Java)
        Long longueur = colonne.getJdbcMapping().getJdbcType().isString() ? colonne.getLength() : null;
        return colonne.getSelectableName() + "[longueur=" + longueur + ", precision=" + colonne.getPrecision()
                + ", echelle=" + colonne.getScale() + ", nullable=" + colonne.isNullable()
                + ", definition=" + colonne.getColumnDefinition() + "]";
    }

    @Test
    void comptes_comptables() {
        verifier(CompteComptableJpa.class, "comptes_comptables", colonnesAttendues(
                        "actif[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "libelle[longueur=150, precision=null, echelle=null, nullable=false, definition=null]",
                        "numero[longueur=20, precision=null, echelle=null, nullable=false, definition=null]"),
                Set.of("uq_compte_comptable_centre_numero[center_id, numero]"));
    }

    @Test
    void journaux_comptables() {
        verifier(JournalComptableJpa.class, "journaux_comptables", colonnesAttendues(
                        "actif[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "code[longueur=10, precision=null, echelle=null, nullable=false, definition=null]",
                        "id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "libelle[longueur=100, precision=null, echelle=null, nullable=false, definition=null]"),
                Set.of("uq_journal_comptable_centre_code[center_id, code]"));
    }

    @Test
    void comptes_payeurs() {
        verifier(ComptePayeurJpa.class, "comptes_payeurs", colonnesAttendues(
                        "center_id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "compte[longueur=20, precision=null, echelle=null, nullable=false, definition=null]",
                        "payeur_id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]"),
                Set.of());
    }

    /**
     * Slice C2 : le modèle de persistance du mapping (qui remplace l'entité legacy {@code MappingComptableJpaEntity}) garde
     * les colonnes de la table historique : mêmes longueurs, nullités et contrainte {@code uq_mapping_center}.
     */
    @Test
    void mapping_comptable() {
        String compteObligatoire = "[longueur=20, precision=null, echelle=null, nullable=false, definition=null]";
        String compteFacultatif = "[longueur=20, precision=null, echelle=null, nullable=true, definition=null]";
        String journal = "[longueur=10, precision=null, echelle=null, nullable=true, definition=null]";
        verifier(MappingComptableJpa.class, "mapping_comptable", colonnesAttendues(
                        "id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "center_id[longueur=null, precision=null, echelle=null, nullable=false, definition=uuid]",
                        "compte_ventes" + compteObligatoire,
                        "compte_client_patient" + compteObligatoire,
                        "compte_client_autre" + compteObligatoire,
                        "compte_banque" + compteObligatoire,
                        "compte_caisse" + compteObligatoire,
                        "compte_tva_collectee" + compteFacultatif,
                        "compte_stock" + compteFacultatif,
                        "compte_consommation" + compteFacultatif,
                        "compte_factures_non_parvenues" + compteFacultatif,
                        "compte_boni_inventaire" + compteFacultatif,
                        "compte_mali_inventaire" + compteFacultatif,
                        "journal_vente" + journal,
                        "journal_reglement_banque" + journal,
                        "journal_reglement_caisse" + journal,
                        "journal_stock_reception" + journal,
                        "journal_stock_sortie" + journal,
                        "journal_stock_inventaire" + journal,
                        "updated_at[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                        "updated_by[longueur=120, precision=null, echelle=null, nullable=true, definition=null]"),
                Set.of("uq_mapping_center[center_id]"));
    }

    /**
     * Slice C2 : la table {@code tva_types}, partagée avec la Facturation, est lue et écrite en SQL direct (pas de modèle de
     * persistance). Les colonnes que ce SQL utilise sont comparées à la description figée de la migration V1 : mêmes longueurs,
     * précision du taux {@code numeric(5,2)} (qui arrondit à deux décimales et refuse 1000 et plus) et nullités.
     */
    @Test
    void tva_types() {
        Set<String> colonnes = new TreeSet<>(jdbc.query(
                "SELECT column_name, character_maximum_length, numeric_precision, numeric_scale, is_nullable "
                        + "FROM information_schema.columns WHERE LOWER(table_name) = 'tva_types'",
                (rs, i) -> rs.getString("column_name").toLowerCase() + "[longueur=" + rs.getObject("character_maximum_length")
                        + ", precision=" + rs.getObject("numeric_precision") + ", echelle=" + rs.getObject("numeric_scale")
                        + ", nullable=" + rs.getString("is_nullable") + "]"));
        assertEquals(colonnesAttendues(
                "id[longueur=null, precision=null, echelle=null, nullable=NO]",
                "center_id[longueur=null, precision=null, echelle=null, nullable=NO]",
                "libelle[longueur=255, precision=null, echelle=null, nullable=NO]",
                "taux[longueur=null, precision=5, echelle=2, nullable=NO]",
                "type_prestation[longueur=120, precision=null, echelle=null, nullable=NO]",
                "exonere[longueur=null, precision=null, echelle=null, nullable=NO]",
                "date_debut_validite[longueur=null, precision=null, echelle=null, nullable=NO]",
                "date_fin_validite[longueur=null, precision=null, echelle=null, nullable=YES]",
                "texte_reference[longueur=500, precision=null, echelle=null, nullable=YES]",
                "actif[longueur=null, precision=null, echelle=null, nullable=NO]",
                "created_at[longueur=null, precision=null, echelle=null, nullable=NO]",
                "created_by[longueur=120, precision=null, echelle=null, nullable=YES]"), colonnes, "colonnes de tva_types");
    }

    private void verifier(Class<?> module, String table, Set<String> colonnes, Set<String> unicites) {
        assertEquals(table, table(module), "table");
        assertEquals(colonnes, colonnes(module), "colonnes");
        assertEquals(new TreeSet<>(unicites), unicites(module), "contraintes d'unicité");
    }

    private AbstractEntityPersister persister(Class<?> entite) {
        return (AbstractEntityPersister) emf.unwrap(SessionFactoryImplementor.class)
                .getMappingMetamodel().getEntityDescriptor(entite);
    }

    private String table(Class<?> entite) {
        return persister(entite).getTableName();
    }

    /**
     * Chaque colonne (identifiant compris) avec sa longueur, sa précision, son échelle, son caractère nul et sa définition.
     */
    private Set<String> colonnes(Class<?> entite) {
        Set<String> noms = new TreeSet<>();
        AbstractEntityPersister persister = persister(entite);
        persister.getIdentifierMapping().forEachSelectable((i, colonne) -> noms.add(decrire(colonne)));
        persister.forEachAttributeMapping(attribut ->
                attribut.forEachSelectable((i, colonne) -> noms.add(decrire(colonne))));
        return noms;
    }

    private Set<String> unicites(Class<?> entite) {
        Set<String> contraintes = new TreeSet<>();
        for (UniqueConstraint u : entite.getAnnotation(Table.class).uniqueConstraints()) {
            contraintes.add(u.name() + Arrays.toString(u.columnNames()));
        }
        return contraintes;
    }
}
