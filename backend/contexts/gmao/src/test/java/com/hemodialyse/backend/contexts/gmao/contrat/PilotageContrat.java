package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.PrixAcquisition;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé des lectures de consultation et de pilotage (G6) : historique des statuts, comptages, coûts de maintenance,
 * parc d'un centre et lecture d'une intervention par identifiant. Exécuté contre les doubles in-memory, l'adapter JPA sur H2
 * puis PostgreSQL 16 (même suite : les doubles sont fidèles). Y sont verrouillés : une intervention supprimée logiquement
 * n'est plus lisible par identifiant ni comptée (KB-GMAO-69 corrigé) ; le coût d'une période ne compte que les interventions
 * en cours ou terminées démarrées dans [du, au) ; les générateurs comptés sont ceux installés avant la date, ni réformés,
 * ni désactivés, ni supprimés ; l'historique d'un équipement va du plus ancien au plus récent.
 */
public abstract class PilotageContrat {

    protected static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    protected static final Instant INSTALLATION = Instant.parse("2024-03-01T08:00:00Z");
    protected static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997600-0000-0000-0000-00000000000a"));
    protected static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997600-0000-0000-0000-00000000000b"));
    protected static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("99997600-0000-0000-0000-0000000000f1"));

    private int sequence = 0;

    protected abstract EquipementRepository equipements();

    protected abstract LectureEquipements lectureEquipements();

    protected abstract InterventionRepository interventions();

    protected abstract LectureInterventions lectureInterventions();

    protected abstract HistoriqueDesStatuts historique();

    protected abstract LectureDesComptages comptages();

    protected abstract LectureDesCouts couts();

    protected abstract GenerateursDuCentre generateursDuCentre();

    /** La base sait poser {@code deleted_at} ; aucune commande du domaine ne le fait. */
    protected abstract void supprimerLogiquementEquipement(EquipementId id);

    protected abstract void supprimerLogiquementIntervention(InterventionId id);

    private UUID prochainUuid() {
        return UUID.fromString(String.format("99997600-0000-0000-0000-%012d", ++sequence));
    }

    private Equipement equipement(CentreId centre, String code, TypeEquipement type, Instant installation) {
        Equipement equipement = Equipement.enregistrer(new EquipementId(prochainUuid()), centre, new CodeEquipement(code),
                "Désignation " + code, type, "Fresenius", "5008S", "SN-1", installation, "Salle 1", null,
                PrixAcquisition.ouAucun(new BigDecimal("1000.00")), ADMIN, T0);
        equipement.tirerEvenements();
        equipements().enregistrer(equipement);
        return equipement;
    }

    private Equipement generateur(CentreId centre, String code) {
        return equipement(centre, code, TypeEquipement.GENERATEUR_DIALYSE, INSTALLATION);
    }

    private void reconstituerAuStatut(Equipement e, StatutEquipement statut) {
        equipements().enregistrer(Equipement.reconstituer(e.id(), e.centreId(), e.code(), e.designation(), e.type(), e.fabricant(),
                e.modele(), e.numeroSerie(), e.dateInstallation(), statut, e.localisation(), e.observations(), e.salleId(),
                e.prixAcquisition(), e.creeLe(), e.modifieLe(), e.creePar(), e.modifiePar()));
    }

    private Intervention planifiee(CentreId centre, EquipementId equipement, Instant debut) {
        Intervention intervention = Intervention.planifier(new InterventionId(prochainUuid()), equipement, centre,
                TypeIntervention.CURATIVE, debut, "Fuite sur le circuit", null, StatutEquipement.EN_MAINTENANCE, "Bruit anormal",
                PrioriteIntervention.HAUTE, debut.plus(Duration.ofDays(2)), ADMIN, T0);
        intervention.tirerEvenements();
        return intervention;
    }

    private Intervention enCours(CentreId centre, EquipementId equipement, Instant debut, String quantite, String prix) {
        Intervention intervention = planifiee(centre, equipement, debut);
        intervention.demarrer(ADMIN, debut);
        intervention.ajouterLigneDeCout(LigneDeCout.saisir(new LigneDeCoutId(prochainUuid()), TypeLigneCout.PIECE, "Joint",
                new BigDecimal(quantite), new BigDecimal(prix), UUID.fromString("99997600-0000-0000-0000-00000000a771")), null,
                ADMIN, debut.plusSeconds(60));
        intervention.tirerEvenements();
        interventions().enregistrer(intervention);
        return intervention;
    }

    private Intervention annulee(CentreId centre, EquipementId equipement, Instant debut) {
        Intervention intervention = planifiee(centre, equipement, debut);
        intervention.ajouterLigneDeCout(LigneDeCout.saisir(new LigneDeCoutId(prochainUuid()), TypeLigneCout.PIECE, "Joint",
                new BigDecimal("1"), new BigDecimal("999.00"), UUID.fromString("99997600-0000-0000-0000-00000000a771")), null,
                ADMIN, debut.plusSeconds(60));
        intervention.annuler("Doublon", ADMIN, debut.plusSeconds(120));
        intervention.tirerEvenements();
        interventions().enregistrer(intervention);
        return intervention;
    }

    // ------------------------------------------------------------------ historique des statuts

    @Test
    void should_read_the_status_history_of_an_equipment_from_the_oldest_change_to_the_latest() {
        Equipement e = generateur(CENTRE_A, "G-01");
        e.mettreHorsService("Fuite", ADMIN, T0.plusSeconds(3600));
        equipements().enregistrer(e);
        e.remettreEnService(ADMIN, T0.plusSeconds(7200));
        equipements().enregistrer(e);

        List<ChangementDeStatut> historique = historique().deLEquipement(e.id());

        assertThat(historique).extracting(ChangementDeStatut::nouveau).containsExactly(StatutEquipement.EN_SERVICE,
                StatutEquipement.HORS_SERVICE, StatutEquipement.EN_SERVICE);
        assertThat(historique).extracting(ChangementDeStatut::le).isSorted();
        assertThat(historique.get(0).precedent()).isNull();
        assertThat(historique().deLEquipement(new EquipementId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void should_read_the_history_of_every_equipment_of_a_centre_without_the_deleted_or_foreign_ones() {
        Equipement a = generateur(CENTRE_A, "G-A");
        Equipement supprime = generateur(CENTRE_A, "G-SUP");
        Equipement b = generateur(CENTRE_B, "G-B");
        supprimerLogiquementEquipement(supprime.id());

        Map<EquipementId, List<ChangementDeStatut>> historiques = historique().desEquipementsDuCentre(CENTRE_A);

        assertThat(historiques).containsOnlyKeys(a.id());
        assertThat(historiques.get(a.id())).hasSize(1);
        assertThat(historiques).doesNotContainKey(b.id());
    }

    // ------------------------------------------------------------------ parc

    @Test
    void should_read_the_whole_park_of_a_centre_without_the_deleted_and_foreign_equipments() {
        Equipement a1 = generateur(CENTRE_A, "G-01");
        Equipement a2 = generateur(CENTRE_A, "G-02");
        Equipement supprime = generateur(CENTRE_A, "G-SUP");
        generateur(CENTRE_B, "G-B");
        supprimerLogiquementEquipement(supprime.id());

        assertThat(lectureEquipements().parcDuCentre(CENTRE_A)).extracting(e -> new EquipementId(e.id()))
                .containsExactlyInAnyOrder(a1.id(), a2.id());
        assertThat(lectureEquipements().parcDuCentre(new CentreId(UUID.randomUUID()))).isEmpty();
    }

    // ------------------------------------------------------------------ interventions par identifiant

    @Test
    void should_not_read_a_logically_deleted_intervention_by_identifier() {
        Equipement e = generateur(CENTRE_A, "G-01");
        Intervention vivante = enCours(CENTRE_A, e.id(), T0, "1", "10.00");
        Intervention supprimee = enCours(CENTRE_A, e.id(), T0.plusSeconds(10), "1", "10.00");
        supprimerLogiquementIntervention(supprimee.id());

        assertThat(lectureInterventions().parId(vivante.id())).isPresent();
        assertThat(lectureInterventions().parId(supprimee.id())).as("KB-GMAO-69 corrigé").isEmpty();
        assertThat(lectureInterventions().parId(new InterventionId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void should_find_the_last_intervention_of_an_equipment_by_start_date_without_the_deleted_ones() {
        Equipement e = generateur(CENTRE_A, "G-01");
        enCours(CENTRE_A, e.id(), T0, "1", "10.00");
        Intervention recente = enCours(CENTRE_A, e.id(), T0.plus(Duration.ofDays(2)), "1", "10.00");
        Intervention plusRecente = enCours(CENTRE_A, e.id(), T0.plus(Duration.ofDays(3)), "1", "10.00");

        assertThat(lectureInterventions().derniereDeLEquipement(e.id()).orElseThrow().id()).isEqualTo(plusRecente.id());
        supprimerLogiquementIntervention(plusRecente.id());
        assertThat(lectureInterventions().derniereDeLEquipement(e.id()).orElseThrow().id()).isEqualTo(recente.id());
        assertThat(lectureInterventions().derniereDeLEquipement(new EquipementId(UUID.randomUUID()))).isEmpty();
    }

    // ------------------------------------------------------------------ comptages

    @Test
    void should_count_the_equipments_and_the_interventions_of_a_centre_by_status_without_the_deleted_ones() {
        Equipement a1 = generateur(CENTRE_A, "G-01");
        Equipement a2 = generateur(CENTRE_A, "G-02");
        Equipement supprime = generateur(CENTRE_A, "G-SUP");
        generateur(CENTRE_B, "G-B");
        a2.mettreHorsService("Fuite", ADMIN, T0.plusSeconds(60));
        equipements().enregistrer(a2);
        supprimerLogiquementEquipement(supprime.id());
        enCours(CENTRE_A, a1.id(), T0, "1", "10.00");
        Intervention supprimee = enCours(CENTRE_A, a1.id(), T0.plusSeconds(10), "1", "10.00");
        interventions().enregistrer(planifiee(CENTRE_A, a1.id(), T0));
        supprimerLogiquementIntervention(supprimee.id());
        enCours(CENTRE_B, generateur(CENTRE_B, "G-B2").id(), T0, "1", "10.00");

        assertThat(comptages().equipements(CENTRE_A)).isEqualTo(2);
        assertThat(comptages().equipementsAuStatut(CENTRE_A, StatutEquipement.HORS_SERVICE)).isEqualTo(1);
        assertThat(comptages().equipementsAuStatut(CENTRE_A, StatutEquipement.EN_SERVICE)).isEqualTo(1);
        assertThat(comptages().equipementsAuStatut(CENTRE_A, StatutEquipement.REFORME)).isZero();
        assertThat(comptages().interventions(CENTRE_A)).isEqualTo(2);
        assertThat(comptages().interventionsAuStatut(CENTRE_A, StatutIntervention.EN_COURS)).isEqualTo(1);
        assertThat(comptages().interventionsAuStatut(CENTRE_A, StatutIntervention.PLANIFIEE)).isEqualTo(1);
        assertThat(comptages().interventionsDeLEquipement(a1.id())).isEqualTo(2);
    }

    @Test
    void should_count_the_installed_generators_before_a_date_without_the_reformed_deactivated_and_deleted_ones() {
        generateur(CENTRE_A, "G-01");
        generateur(CENTRE_A, "G-02");
        equipement(CENTRE_A, "OSMOSEUR", TypeEquipement.AUTRE, INSTALLATION);
        equipement(CENTRE_A, "G-RECENT", TypeEquipement.GENERATEUR_DIALYSE, Instant.parse("2026-02-01T00:00:00Z"));
        Equipement reforme = generateur(CENTRE_A, "G-RF");
        reconstituerAuStatut(reforme, StatutEquipement.REFORME);
        Equipement desactive = generateur(CENTRE_A, "G-DES");
        reconstituerAuStatut(desactive, StatutEquipement.DESACTIF);
        Equipement horsService = generateur(CENTRE_A, "G-HS");
        horsService.mettreHorsService("Fuite", ADMIN, T0.plusSeconds(60));
        equipements().enregistrer(horsService);
        supprimerLogiquementEquipement(generateur(CENTRE_A, "G-SUP").id());
        generateur(CENTRE_B, "G-B");
        CentreId sansGenerateur = new CentreId(UUID.randomUUID());

        Map<UUID, Long> parCentre = comptages().generateursInstallesAvant(
                List.of(CENTRE_A.valeur(), CENTRE_B.valeur(), sansGenerateur.valeur()), Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(parCentre).containsOnlyKeys(CENTRE_A.valeur(), CENTRE_B.valeur());
        assertThat(parCentre.get(CENTRE_A.valeur())).as("G-01, G-02 et G-HS").isEqualTo(3);
        assertThat(parCentre.get(CENTRE_B.valeur())).isEqualTo(1);
        assertThat(comptages().generateursInstallesAvant(List.of(CENTRE_A.valeur()), INSTALLATION)).isEmpty();
    }

    @Test
    void should_list_the_generators_of_a_centre_whatever_their_status_without_the_deleted_ones_by_code() {
        UUID salle = UUID.fromString("99997600-0000-0000-0000-0000000005a1");
        Equipement g2 = generateur(CENTRE_A, "G-02");
        equipements().enregistrer(Equipement.reconstituer(g2.id(), g2.centreId(), g2.code(), g2.designation(), g2.type(),
                g2.fabricant(), g2.modele(), g2.numeroSerie(), g2.dateInstallation(), StatutEquipement.EN_MAINTENANCE,
                g2.localisation(), g2.observations(), new SalleId(salle), g2.prixAcquisition(), g2.creeLe(), g2.modifieLe(),
                g2.creePar(), g2.modifiePar()));
        Equipement g1 = generateur(CENTRE_A, "G-01");
        Equipement reforme = generateur(CENTRE_A, "G-03");
        reconstituerAuStatut(reforme, StatutEquipement.REFORME);
        equipement(CENTRE_A, "OSMOSEUR", TypeEquipement.AUTRE, INSTALLATION);
        supprimerLogiquementEquipement(generateur(CENTRE_A, "G-SUP").id());
        generateur(CENTRE_B, "G-B");

        List<GenerateursDuCentre.Generateur> generateurs = generateursDuCentre().duCentre(CENTRE_A.valeur());

        assertThat(generateurs).extracting(GenerateursDuCentre.Generateur::code).containsExactly("G-01", "G-02", "G-03");
        assertThat(generateurs).extracting(GenerateursDuCentre.Generateur::statut)
                .containsExactly("EN_SERVICE", "EN_MAINTENANCE", "REFORME");
        assertThat(generateurs.get(0).id()).isEqualTo(g1.id().valeur());
        assertThat(generateurs.get(0).salleId()).isNull();
        assertThat(generateurs.get(1).salleId()).isEqualTo(salle);
        assertThat(generateurs).allMatch(g -> g.centreId().equals(CENTRE_A.valeur()));
        assertThat(generateursDuCentre().duCentre(UUID.randomUUID())).isEmpty();
    }

    // ------------------------------------------------------------------ coûts de maintenance

    @Test
    void should_sum_the_cost_lines_of_the_interventions_started_in_the_period_only() {
        Equipement e = generateur(CENTRE_A, "G-01");
        enCours(CENTRE_A, e.id(), T0, "2", "12.50");
        enCours(CENTRE_A, e.id(), T0.plus(Duration.ofDays(1)), "0.335", "3.00");
        enCours(CENTRE_A, e.id(), T0.minus(Duration.ofDays(40)), "1", "100.00");
        Instant du = T0.minus(Duration.ofDays(10));
        Instant au = T0.plus(Duration.ofDays(10));

        assertThat(couts().deLEquipement(e.id(), du, au)).isEqualByComparingTo("26.005");
        assertThat(couts().cumuleDeLEquipement(e.id())).isEqualByComparingTo("126.005");
        assertThat(couts().duCentre(CENTRE_A, du, au)).isEqualByComparingTo("26.005");
        assertThat(couts().duCentre(CENTRE_B, du, au)).isEqualByComparingTo("0");
        assertThat(couts().deLEquipement(e.id(), T0, T0)).as("la borne haute est exclue").isEqualByComparingTo("0");
        assertThat(couts().deLEquipement(e.id(), T0, T0.plusSeconds(1))).as("la borne basse est incluse").isEqualByComparingTo("25.00");
    }

    @Test
    void should_ignore_the_planned_cancelled_and_deleted_interventions_in_the_costs() {
        Equipement e = generateur(CENTRE_A, "G-01");
        enCours(CENTRE_A, e.id(), T0, "1", "10.00");
        annulee(CENTRE_A, e.id(), T0);
        Intervention supprimee = enCours(CENTRE_A, e.id(), T0, "1", "500.00");
        supprimerLogiquementIntervention(supprimee.id());
        Instant du = T0.minus(Duration.ofDays(1));
        Instant au = T0.plus(Duration.ofDays(1));

        assertThat(couts().deLEquipement(e.id(), du, au)).isEqualByComparingTo("10.00");
        assertThat(couts().cumuleDeLEquipement(e.id())).isEqualByComparingTo("10.00");
    }

    @Test
    void should_sum_the_costs_by_equipment_and_leave_out_the_equipments_without_cost() {
        Equipement g1 = generateur(CENTRE_A, "G-01");
        Equipement g2 = generateur(CENTRE_A, "G-02");
        generateur(CENTRE_A, "G-03");
        Equipement autre = generateur(CENTRE_B, "G-B");
        enCours(CENTRE_A, g1.id(), T0, "1", "10.00");
        enCours(CENTRE_A, g1.id(), T0.plusSeconds(5), "2", "5.00");
        enCours(CENTRE_A, g2.id(), T0.minus(Duration.ofDays(60)), "1", "7.00");
        enCours(CENTRE_B, autre.id(), T0, "1", "99.00");
        Instant du = T0.minus(Duration.ofDays(1));
        Instant au = T0.plus(Duration.ofDays(1));

        Map<EquipementId, BigDecimal> periode = couts().parEquipement(CENTRE_A, du, au);
        Map<EquipementId, BigDecimal> cumule = couts().cumuleParEquipement(CENTRE_A);

        assertThat(periode).containsOnlyKeys(g1.id());
        assertThat(periode.get(g1.id())).isEqualByComparingTo("20.00");
        assertThat(cumule).containsOnlyKeys(g1.id(), g2.id());
        assertThat(cumule.get(g2.id())).isEqualByComparingTo("7.00");
    }

    @Test
    void should_count_the_interventions_to_follow_up() {
        Equipement e = generateur(CENTRE_A, "G-01");
        Instant maintenant = T0.plus(Duration.ofDays(10));
        interventions().enregistrer(planifiee(CENTRE_A, e.id(), T0));                       // début passé
        interventions().enregistrer(planifiee(CENTRE_A, e.id(), maintenant.plusSeconds(10)));  // à venir, échéance à venir
        enCours(CENTRE_A, e.id(), T0.minus(Duration.ofDays(5)), "1", "1.00");               // en cours, échéance dépassée
        enCours(CENTRE_A, e.id(), maintenant.minusSeconds(10), "1", "1.00");                // en cours, échéance à venir

        assertThat(comptages().interventionsARelancer(CENTRE_A, maintenant)).isEqualTo(2);
        assertThat(comptages().interventionsARelancer(CENTRE_B, maintenant)).isZero();
    }
}
