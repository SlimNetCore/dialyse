package com.hemodialyse.backend.characterization.gmao;

import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.IntervenantJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LigneDeCoutJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.RectificationJpa;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Les entités legacy GMAO sont retirées (G6 temps 2) : il n'y a plus de second modèle à comparer. Le modèle de persistance du
 * module est comparé à la description figée de la table historique (celle des anciennes entités, avec les mêmes longueurs,
 * précisions, nullités, définitions et contraintes d'unicité), pour qu'il ne dérive pas : la base refuse les valeurs trop
 * longues et arrondit les montants comme avant. Ce test échoue si la table, une colonne mappée ou une contrainte d'unicité
 * diverge de la description.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ModelesDePersistanceAlignesIntegrationTest {

    @Autowired
    private EntityManagerFactory emf;

    @Test
    void equipements() {
        verifier(EquipementJpa.class, "gmao_equipements", colonnesAttendues(
                "centre_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "code[longueur=50, precision=null, echelle=null, nullable=false, definition=null]",
                "cree_par[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "date_creation[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "date_installation[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "date_modification[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "deleted_at[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "designation[longueur=255, precision=null, echelle=null, nullable=false, definition=null]",
                "fabricant[longueur=100, precision=null, echelle=null, nullable=true, definition=null]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "localisation[longueur=255, precision=null, echelle=null, nullable=true, definition=null]",
                "modele[longueur=100, precision=null, echelle=null, nullable=true, definition=null]",
                "modifie_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "numero_serie[longueur=100, precision=null, echelle=null, nullable=true, definition=null]",
                "observations[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]",
                "prix_acquisition[longueur=null, precision=14, echelle=2, nullable=true, definition=null]",
                "salle_id[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "statut[longueur=50, precision=null, echelle=null, nullable=false, definition=null]",
                "type[longueur=50, precision=null, echelle=null, nullable=false, definition=null]"),
                Set.of("uk_gmao_equipement_centre_code[centreId, code]"));
    }

    @Test
    void plans_de_maintenance() {
        verifier(PlanMaintenanceJpa.class, "gmao_plans_maintenance", colonnesAttendues(
                "centre_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "cree_par[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "date_creation[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "date_modification[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "deleted_at[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "derniere_date_execution[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "description[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]",
                "designation[longueur=255, precision=null, echelle=null, nullable=false, definition=null]",
                "equipement_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "frequence[longueur=50, precision=null, echelle=null, nullable=false, definition=null]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "modifie_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "nombre_executions[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "prochaine_date_prevue[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "statut[longueur=50, precision=null, echelle=null, nullable=false, definition=null]",
                "tachesaeffectuer[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]"),
                Set.of());
    }

    @Test
    void intervenants() {
        verifier(IntervenantJpa.class, "gmao_intervenants", colonnesAttendues(
                "actif[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "centre_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "email[longueur=255, precision=null, echelle=null, nullable=true, definition=null]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "nom[longueur=255, precision=null, echelle=null, nullable=false, definition=null]",
                "tarif_horaire_defaut[longueur=null, precision=10, echelle=2, nullable=true, definition=null]",
                "telephone[longueur=50, precision=null, echelle=null, nullable=true, definition=null]",
                "type[longueur=20, precision=null, echelle=null, nullable=false, definition=null]"),
                Set.of());
    }

    @Test
    void interventions() {
        verifier(InterventionJpa.class, "gmao_interventions", colonnesAttendues(
                "actions[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]",
                "annule_le[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "annule_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "cause[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]",
                "centre_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "cloture_le[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "cloture_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "cree_par[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "date_creation[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "date_debut[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "date_fin[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "date_modification[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "deleted_at[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "demarre_le[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "demarre_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "description[longueur=255, precision=null, echelle=null, nullable=false, definition=TEXT]",
                "echeance[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "equipement_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "etat_equipement_apres[longueur=50, precision=null, echelle=null, nullable=true, definition=null]",
                "etat_equipement_avant[longueur=50, precision=null, echelle=null, nullable=true, definition=null]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "intervenant_id[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "modifie_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "observations[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]",
                "piece_remplacee[longueur=255, precision=null, echelle=null, nullable=true, definition=null]",
                "priorite[longueur=20, precision=null, echelle=null, nullable=true, definition=null]",
                "statut[longueur=50, precision=null, echelle=null, nullable=false, definition=null]",
                "symptome[longueur=255, precision=null, echelle=null, nullable=true, definition=TEXT]",
                "type[longueur=50, precision=null, echelle=null, nullable=false, definition=null]"),
                Set.of());
    }

    @Test
    void lignes_de_cout_d_intervention() {
        verifier(LigneDeCoutJpa.class, "gmao_lignes_cout_intervention", colonnesAttendues(
                "article_stock_id[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "automatique[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "intervention_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "libelle[longueur=255, precision=null, echelle=null, nullable=false, definition=null]",
                "prix_unitaire[longueur=null, precision=12, echelle=2, nullable=false, definition=null]",
                "quantite[longueur=null, precision=12, echelle=3, nullable=false, definition=null]",
                "type[longueur=30, precision=null, echelle=null, nullable=false, definition=null]"),
                Set.of());
    }

    @Test
    void rectifications_d_intervention() {
        verifier(RectificationJpa.class, "gmao_rectifications_intervention", colonnesAttendues(
                "cloture_anterieure_le[longueur=null, precision=null, echelle=null, nullable=true, definition=TIMESTAMP WITH TIME ZONE]",
                "cloture_anterieure_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "intervention_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "le[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "motif[longueur=255, precision=null, echelle=null, nullable=false, definition=TEXT]",
                "par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]"),
                Set.of());
    }

    /**
     * G5 : l'ancienne entité {@code DocumentInterventionEntity} est retirée (aucune autre classe ne lisait la table), il n'y a
     * donc plus de second modèle à comparer. Le modèle du module est comparé à la description figée de la table historique
     * {@code gmao_documents_intervention} (V7, avec les longueurs, nullités et définitions de l'ancienne entité), pour qu'il ne
     * dérive pas : la base refuse les valeurs trop longues comme avant, les octets restent un {@code BYTEA}.
     */
    @Test
    void pieces_jointes_d_intervention() {
        assertEquals("gmao_documents_intervention", table(PieceJointeJpa.class), "table");
        assertEquals(new TreeSet<>(Arrays.asList(
                "ajoute_le[longueur=null, precision=null, echelle=null, nullable=false, definition=TIMESTAMP WITH TIME ZONE]",
                "ajoute_par[longueur=null, precision=null, echelle=null, nullable=true, definition=null]",
                "centre_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "content_type[longueur=50, precision=null, echelle=null, nullable=false, definition=null]",
                "contenu[longueur=null, precision=null, echelle=null, nullable=false, definition=BYTEA]",
                "id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "intervention_id[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "nom[longueur=150, precision=null, echelle=null, nullable=false, definition=null]",
                "taille[longueur=null, precision=null, echelle=null, nullable=false, definition=null]",
                "type[longueur=30, precision=null, echelle=null, nullable=false, definition=null]")),
                colonnes(PieceJointeJpa.class), "colonnes");
    }

    private static Set<String> colonnesAttendues(String... colonnes) {
        return new TreeSet<>(Arrays.asList(colonnes));
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

    /** Chaque colonne (identifiant compris) avec sa longueur, sa précision, son échelle, son caractère nul et sa définition. */
    private Set<String> colonnes(Class<?> entite) {
        Set<String> noms = new TreeSet<>();
        AbstractEntityPersister persister = persister(entite);
        persister.getIdentifierMapping().forEachSelectable((i, colonne) -> noms.add(decrire(colonne)));
        persister.forEachAttributeMapping(attribut ->
                attribut.forEachSelectable((i, colonne) -> noms.add(decrire(colonne))));
        return noms;
    }

    private static String decrire(SelectableMapping colonne) {
        // la longueur n'a de sens que pour une chaîne (255 par défaut sur les autres types, nulle selon le type Java)
        Long longueur = colonne.getJdbcMapping().getJdbcType().isString() ? colonne.getLength() : null;
        return colonne.getSelectableName() + "[longueur=" + longueur + ", precision=" + colonne.getPrecision()
                + ", echelle=" + colonne.getScale() + ", nullable=" + colonne.isNullable()
                + ", definition=" + colonne.getColumnDefinition() + "]";
    }

    private Set<String> unicites(Class<?> entite) {
        Set<String> contraintes = new TreeSet<>();
        for (UniqueConstraint u : entite.getAnnotation(Table.class).uniqueConstraints()) {
            contraintes.add(u.name() + Arrays.toString(u.columnNames()));
        }
        return contraintes;
    }
}
