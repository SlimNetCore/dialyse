package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PageDInterventions;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.NatureIntervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ConditionsDeValorisation;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Jalon;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutTache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat partagé du repository et du read model des interventions : exécuté contre le double in-memory et l'adapter JPA
 * sur H2 (même suite : le double est fidèle), puis sur PostgreSQL 16. Y sont verrouillés : l'aller-retour complet de
 * l'agrégat (lignes, rectifications, jalons absents), quantité arrondie à trois décimales et prix à deux (demi supérieur),
 * dépassements refusés par la base (KB-GMAO-44/45), lignes qui disparaissent, rectifications qui s'ajoutent, listes du plus
 * récent au plus ancien, suppression logique ignorée des listes mais lisible par identifiant et rétablie par une écriture
 * (KB-GMAO-54). Tâches (R21, KB-GMAO-55) : aller-retour dans l'ordre d'ajout avec leurs jalons, avancement mis à jour, 30 tâches.
 */
public abstract class InterventionContrat {

    protected static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    protected static final Instant DEBUT = Instant.parse("2026-03-10T08:00:00Z");
    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000b"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997400-0000-0000-0000-0000000000f1"));
    protected static final EquipementId EQUIPEMENT_1 = new EquipementId(UUID.fromString("99997400-0000-0000-0000-0000000000e1"));
    protected static final EquipementId EQUIPEMENT_2 = new EquipementId(UUID.fromString("99997400-0000-0000-0000-0000000000e2"));
    protected static final IntervenantId INTERVENANT = new IntervenantId(UUID.fromString("99997400-0000-0000-0000-0000000000b1"));

    private int sequence = 0;

    protected abstract InterventionRepository repository();

    protected abstract LectureInterventions lecture();

    /** La base sait poser {@code deleted_at} ; aucune commande du domaine ne le fait (KB-GMAO-54). */
    protected abstract void supprimerLogiquement(InterventionId id);

    private UUID prochainUuid() {
        return UUID.fromString(String.format("99997400-0000-0000-0000-%012d", ++sequence));
    }

    protected Intervention planifier(CentreId centre, EquipementId equipement, Instant creeLe) {
        return planifier(centre, equipement, creeLe, INTERVENANT);
    }

    protected Intervention planifier(CentreId centre, EquipementId equipement, Instant creeLe, IntervenantId intervenant) {
        Intervention intervention = Intervention.planifier(new InterventionId(prochainUuid()), equipement, centre,
                TypeIntervention.CURATIVE, DEBUT, "Fuite sur le circuit", intervenant, StatutEquipement.EN_MAINTENANCE,
                "Bruit anormal", PrioriteIntervention.HAUTE, DEBUT.plus(Duration.ofDays(2)), ADMIN, creeLe);
        intervention.tirerEvenements();
        return intervention;
    }

    private LigneDeCout ligne(TypeLigneCout type, String libelle, String quantite, String prix) {
        return LigneDeCout.saisir(new LigneDeCoutId(prochainUuid()), type, libelle, new BigDecimal(quantite),
                new BigDecimal(prix), UUID.fromString("99997400-0000-0000-0000-00000000a771"));
    }

    private Intervention terminee(CentreId centre, EquipementId equipement) {
        Intervention intervention = planifier(centre, equipement, T0);
        intervention.demarrer(ADMIN, T0.plusSeconds(60));
        intervention.ajouterLigneDeCout(ligne(TypeLigneCout.PIECE, "Joint", "2", "12.50"), null, ADMIN, T0.plusSeconds(90));
        intervention.terminer("Joint remplacé", StatutEquipement.EN_SERVICE, DEBUT.plus(Duration.ofMinutes(20)), "Usure",
                new ConditionsDeValorisation(NatureIntervenant.INTERNE, new BigDecimal("35.00")),
                new LigneDeCoutId(prochainUuid()), ADMIN, T0.plusSeconds(120));
        return intervention;
    }

    // ------------------------------------------------------------------ aller-retour

    @Test
    void should_store_a_planned_intervention_and_find_it_back_with_its_absent_values() {
        Intervention planifiee = planifier(CENTRE_A, EQUIPEMENT_1, T0, null);

        repository().enregistrer(planifiee);

        Intervention relue = repository().trouverParId(planifiee.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(relue.equipementId()).isEqualTo(EQUIPEMENT_1);
        assertThat(relue.centreId()).isEqualTo(CENTRE_A);
        assertThat(relue.type()).isEqualTo(TypeIntervention.CURATIVE);
        assertThat(relue.priorite()).isEqualTo(PrioriteIntervention.HAUTE);
        assertThat(relue.debut()).isEqualTo(DEBUT);
        assertThat(relue.echeance()).isEqualTo(DEBUT.plus(Duration.ofDays(2)));
        assertThat(relue.description()).isEqualTo("Fuite sur le circuit");
        assertThat(relue.symptome()).isEqualTo("Bruit anormal");
        assertThat(relue.etatAvant()).isEqualTo(StatutEquipement.EN_MAINTENANCE);
        assertThat(relue.creation()).isEqualTo(new Jalon(ADMIN, T0));
        assertThat(relue.intervenantId()).isNull();
        assertThat(relue.fin()).isNull();
        assertThat(relue.etatApres()).isNull();
        assertThat(relue.modification()).isNull();
        assertThat(relue.demarrage()).isNull();
        assertThat(relue.cloture()).isNull();
        assertThat(relue.annulation()).isNull();
        assertThat(relue.lignes()).isEmpty();
        assertThat(relue.rectifications()).isEmpty();
    }

    @Test
    void should_store_a_finished_intervention_with_its_lines_and_milestones() {
        Intervention terminee = terminee(CENTRE_A, EQUIPEMENT_1);

        repository().enregistrer(terminee);

        Intervention relue = repository().trouverParId(terminee.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutIntervention.TERMINEE);
        assertThat(relue.intervenantId()).isEqualTo(INTERVENANT);
        assertThat(relue.etatApres()).isEqualTo(StatutEquipement.EN_SERVICE);
        assertThat(relue.actions()).isEqualTo("Joint remplacé");
        assertThat(relue.cause()).isEqualTo("Usure");
        assertThat(relue.fin()).isEqualTo(DEBUT.plus(Duration.ofMinutes(20)));
        assertThat(relue.demarrage()).isEqualTo(new Jalon(ADMIN, T0.plusSeconds(60)));
        assertThat(relue.cloture()).isEqualTo(new Jalon(ADMIN, T0.plusSeconds(120)));
        assertThat(relue.modification()).isEqualTo(new Jalon(ADMIN, T0.plusSeconds(120)));
        assertThat(relue.lignes()).hasSize(2)
                .anySatisfy(l -> {
                    assertThat(l.type()).isEqualTo(TypeLigneCout.PIECE);
                    assertThat(l.libelle()).isEqualTo("Joint");
                    assertThat(l.articleStockId()).isEqualTo(UUID.fromString("99997400-0000-0000-0000-00000000a771"));
                    assertThat(l.estAutomatique()).isFalse();
                })
                .anySatisfy(l -> {
                    assertThat(l.type()).isEqualTo(TypeLigneCout.MAIN_OEUVRE);
                    assertThat(l.estAutomatique()).isTrue();
                    assertThat(l.quantite()).isEqualByComparingTo("0.33");
                    assertThat(l.articleStockId()).isNull();
                });
    }

    @Test
    void should_find_nothing_for_an_unknown_intervention() {
        assertThat(repository().trouverParId(new InterventionId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void should_load_for_modification_the_same_intervention_as_a_plain_lookup() {
        Intervention terminee = terminee(CENTRE_A, EQUIPEMENT_1);
        repository().enregistrer(terminee);

        assertThat(repository().chargerPourModification(terminee.id())).isPresent()
                .get().usingRecursiveComparison().isEqualTo(repository().trouverParId(terminee.id()).orElseThrow());
        assertThat(repository().chargerPourModification(new InterventionId(UUID.randomUUID()))).isEmpty();
    }

    // ------------------------------------------------------------------ montants et limites de la base

    @Test
    void should_round_the_quantity_to_three_decimals_and_the_price_to_two_decimals_half_up() {
        Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        intervention.ajouterLigneDeCout(ligne(TypeLigneCout.PIECE, "a", "0.0005", "10.105"), null, ADMIN, T0);
        intervention.ajouterLigneDeCout(ligne(TypeLigneCout.PIECE, "b", "1.2345", "10.104"), null, ADMIN, T0);
        repository().enregistrer(intervention);

        Intervention relue = repository().trouverParId(intervention.id()).orElseThrow();

        assertThat(relue.lignes()).extracting(LigneDeCout::quantite, LigneDeCout::prixUnitaire)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(new BigDecimal("0.001"), new BigDecimal("10.11")),
                        org.assertj.core.groups.Tuple.tuple(new BigDecimal("1.235"), new BigDecimal("10.10")));
        assertThat(relue.lignes()).extracting(LigneDeCout::montant)
                .containsExactlyInAnyOrder(new BigDecimal("0.01011"), new BigDecimal("12.47350"));
    }

    @Test
    void should_refuse_the_values_the_database_cannot_hold() {
        for (LigneDeCout trop : new LigneDeCout[]{
                ligne(TypeLigneCout.PIECE, "L".repeat(256), "1", "1"),
                ligne(TypeLigneCout.PIECE, "ok", "1000000000", "1"),
                ligne(TypeLigneCout.PIECE, "ok", "1", "10000000000")}) {
            Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
            intervention.ajouterLigneDeCout(trop, null, ADMIN, T0);
            assertThatThrownBy(() -> repository().enregistrer(intervention)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void should_accept_the_maximal_values() {
        Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        intervention.ajouterLigneDeCout(ligne(TypeLigneCout.AUTRE, "L".repeat(255), "999999999.999", "9999999999.99"), null,
                ADMIN, T0);

        repository().enregistrer(intervention);

        assertThat(repository().trouverParId(intervention.id()).orElseThrow().lignes()).hasSize(1);
    }

    // ------------------------------------------------------------------ lignes et rectifications

    @Test
    void should_drop_the_lines_that_disappeared_and_keep_the_others() {
        Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        LigneDeCout gardee = ligne(TypeLigneCout.PIECE, "gardée", "1", "5");
        LigneDeCout retiree = ligne(TypeLigneCout.AUTRE, "retirée", "1", "7");
        intervention.ajouterLigneDeCout(gardee, null, ADMIN, T0);
        intervention.ajouterLigneDeCout(retiree, null, ADMIN, T0);
        repository().enregistrer(intervention);

        Intervention relue = repository().trouverParId(intervention.id()).orElseThrow();
        relue.retirerLigneDeCout(retiree.id(), ADMIN, T0.plusSeconds(5));
        repository().enregistrer(relue);

        assertThat(repository().trouverParId(intervention.id()).orElseThrow().lignes())
                .extracting(LigneDeCout::id).containsExactly(gardee.id());
    }

    @Test
    void should_keep_the_rectifications_in_order_and_the_closing_they_cancelled() {
        Intervention intervention = terminee(CENTRE_A, EQUIPEMENT_1);
        repository().enregistrer(intervention);
        Intervention rouverte = repository().trouverParId(intervention.id()).orElseThrow();
        rouverte.rectifier("première", null, prochainUuid(), ADMIN, T0.plusSeconds(200));
        rouverte.terminer("a", StatutEquipement.EN_SERVICE, DEBUT.plus(Duration.ofHours(1)), null, null,
                new LigneDeCoutId(prochainUuid()), ADMIN, T0.plusSeconds(300));
        rouverte.rectifier("seconde", DEBUT.minusSeconds(60), prochainUuid(), ADMIN, T0.plusSeconds(400));
        repository().enregistrer(rouverte);

        Intervention relue = repository().trouverParId(intervention.id()).orElseThrow();

        assertThat(relue.statut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(relue.debut()).isEqualTo(DEBUT.minusSeconds(60));
        assertThat(relue.fin()).isNull();
        assertThat(relue.etatApres()).isNull();
        assertThat(relue.cloture()).isNull();
        assertThat(relue.rectifications()).extracting(r -> r.motif(), r -> r.clotureAnterieureLe()).containsExactly(
                org.assertj.core.groups.Tuple.tuple("première", T0.plusSeconds(120)),
                org.assertj.core.groups.Tuple.tuple("seconde", T0.plusSeconds(300)));
        assertThat(relue.lignes()).extracting(LigneDeCout::type).containsExactly(TypeLigneCout.PIECE);

        repository().enregistrer(relue);
        assertThat(repository().trouverParId(intervention.id()).orElseThrow().rectifications()).hasSize(2);
    }

    // ------------------------------------------------------------------ listes

    @Test
    void should_list_the_centre_most_recent_first_and_page() {
        Intervention ancienne = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        Intervention recente = planifier(CENTRE_A, EQUIPEMENT_2, T0.plusSeconds(60));
        Intervention autreCentre = planifier(CENTRE_B, EQUIPEMENT_1, T0.plusSeconds(120));
        for (Intervention i : new Intervention[]{ancienne, autreCentre, recente}) {
            repository().enregistrer(i);
        }

        PageDInterventions<Intervention> page = lecture().duCentre(CENTRE_A, null, 0, 20);

        assertThat(page.items()).extracting(Intervention::id).containsExactly(recente.id(), ancienne.id());
        assertThat(page.total()).isEqualTo(2);
        assertThat(lecture().duCentre(CENTRE_A, null, 1, 1).items()).extracting(Intervention::id).containsExactly(ancienne.id());
        assertThat(lecture().duCentre(CENTRE_A, null, 1, 1).total()).isEqualTo(2);
        assertThat(lecture().duCentre(CENTRE_A, null, 5, 20).items()).isEmpty();
    }

    @Test
    void should_filter_the_centre_by_the_exact_status_code() {
        Intervention planifiee = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        Intervention terminee = terminee(CENTRE_A, EQUIPEMENT_2);
        repository().enregistrer(planifiee);
        repository().enregistrer(terminee);

        assertThat(lecture().duCentre(CENTRE_A, "TERMINEE", 0, 20).items()).extracting(Intervention::id)
                .containsExactly(terminee.id());
        assertThat(lecture().duCentre(CENTRE_A, "terminee", 0, 20).items()).isEmpty();
        assertThat(lecture().duCentre(CENTRE_A, "ANNULEE", 0, 20).total()).isZero();
        assertThat(lecture().duCentre(CENTRE_A, "", 0, 20).items()).hasSize(2);
        assertThat(lecture().duCentre(CENTRE_A, " ", 0, 20).items()).hasSize(2);
    }

    @Test
    void should_list_the_interventions_of_an_equipment() {
        Intervention surUn = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        Intervention surDeux = planifier(CENTRE_A, EQUIPEMENT_2, T0);
        repository().enregistrer(surUn);
        repository().enregistrer(surDeux);

        PageDInterventions<Intervention> page = lecture().deLEquipement(EQUIPEMENT_2, 0, 20);

        assertThat(page.items()).extracting(Intervention::id).containsExactly(surDeux.id());
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    // ------------------------------------------------------------------ suppression logique (KB-GMAO-54)

    @Test
    void should_read_a_logically_deleted_intervention_by_identity_but_not_list_it() {
        Intervention supprimee = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        repository().enregistrer(supprimee);
        supprimerLogiquement(supprimee.id());

        assertThat(repository().trouverParId(supprimee.id())).isPresent();
        assertThat(lecture().duCentre(CENTRE_A, null, 0, 20).items()).isEmpty();
        assertThat(lecture().duCentre(CENTRE_A, "PLANIFIEE", 0, 20).items()).isEmpty();
        assertThat(lecture().deLEquipement(EQUIPEMENT_1, 0, 20).items()).isEmpty();
    }

    @Test
    void should_bring_a_logically_deleted_intervention_back_when_it_is_written_again() {
        Intervention supprimee = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        repository().enregistrer(supprimee);
        supprimerLogiquement(supprimee.id());

        Intervention relue = repository().trouverParId(supprimee.id()).orElseThrow();
        relue.annuler("doublon", ADMIN, T0.plusSeconds(30));
        repository().enregistrer(relue);

        assertThat(lecture().duCentre(CENTRE_A, null, 0, 20).items()).extracting(Intervention::id).containsExactly(supprimee.id());
    }

    // ------------------------------------------------------------------ tâches (R21)

    @Test
    void should_round_trip_the_tasks_in_the_order_they_were_added_with_their_milestones() {
        Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        intervention.demarrer(ADMIN, T0.plusSeconds(10));
        TacheId premiere = new TacheId(prochainUuid());
        TacheId deuxieme = new TacheId(prochainUuid());
        TacheId troisieme = new TacheId(prochainUuid());
        intervention.ajouterTache(premiere, "Couper l'eau", ADMIN, T0.plusSeconds(20));
        intervention.ajouterTache(deuxieme, "Changer le joint", ADMIN, T0.plusSeconds(20));
        intervention.ajouterTache(troisieme, "Tester", ADMIN, T0.plusSeconds(20));
        intervention.demarrerTache(premiere, ADMIN, T0.plusSeconds(30));
        intervention.acheverTache(premiere, ADMIN, T0.plusSeconds(40));
        intervention.demarrerTache(deuxieme, ADMIN, T0.plusSeconds(50));
        repository().enregistrer(intervention);

        Intervention relue = repository().trouverParId(intervention.id()).orElseThrow();

        assertThat(relue.taches()).extracting(t -> t.id()).containsExactly(premiere, deuxieme, troisieme);
        assertThat(relue.taches()).extracting(t -> t.statut())
                .containsExactly(StatutTache.COMPLETEE, StatutTache.EN_COURS, StatutTache.A_FAIRE);
        assertThat(relue.taches().get(0).description()).isEqualTo("Couper l'eau");
        assertThat(relue.taches().get(0).creation()).isEqualTo(new Jalon(ADMIN, T0.plusSeconds(20)));
        assertThat(relue.taches().get(0).demarrage()).isEqualTo(new Jalon(ADMIN, T0.plusSeconds(30)));
        assertThat(relue.taches().get(0).achevement()).isEqualTo(new Jalon(ADMIN, T0.plusSeconds(40)));
        assertThat(relue.taches().get(1).achevement()).isNull();
        assertThat(relue.taches().get(2).demarrage()).isNull();
    }

    @Test
    void should_update_the_progress_of_a_task_already_written_without_duplicating_it() {
        Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        intervention.demarrer(ADMIN, T0.plusSeconds(10));
        TacheId tache = new TacheId(prochainUuid());
        intervention.ajouterTache(tache, "Contrôler", ADMIN, T0.plusSeconds(20));
        repository().enregistrer(intervention);

        Intervention relue = repository().trouverParId(intervention.id()).orElseThrow();
        relue.demarrerTache(tache, ADMIN, T0.plusSeconds(30));
        repository().enregistrer(relue);

        Intervention finale = repository().trouverParId(intervention.id()).orElseThrow();
        assertThat(finale.taches()).hasSize(1);
        assertThat(finale.taches().getFirst().statut()).isEqualTo(StatutTache.EN_COURS);
    }

    @Test
    void should_keep_the_tasks_of_each_intervention_apart_and_none_for_an_old_intervention() {
        Intervention avec = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        avec.ajouterTache(new TacheId(prochainUuid()), "Contrôler", ADMIN, T0.plusSeconds(20));
        Intervention sans = planifier(CENTRE_A, EQUIPEMENT_1, T0.plusSeconds(5));
        repository().enregistrer(avec);
        repository().enregistrer(sans);

        assertThat(repository().trouverParId(avec.id()).orElseThrow().taches()).hasSize(1);
        assertThat(repository().trouverParId(sans.id()).orElseThrow().taches()).isEmpty();
        assertThat(lecture().duCentre(CENTRE_A, null, 0, 20).items()).extracting(i -> i.taches().size())
                .containsExactlyInAnyOrder(1, 0);
    }

    @Test
    void should_round_trip_thirty_tasks() {
        Intervention intervention = planifier(CENTRE_A, EQUIPEMENT_1, T0);
        for (int i = 1; i <= Intervention.LIMITE_DE_TACHES; i++) {
            intervention.ajouterTache(new TacheId(prochainUuid()), "Tâche " + i, ADMIN, T0.plusSeconds(20));
        }
        repository().enregistrer(intervention);

        assertThat(repository().trouverParId(intervention.id()).orElseThrow().taches())
                .extracting(t -> t.description())
                .containsExactlyElementsOf(intervention.taches().stream().map(t -> t.description()).toList());
    }
}
