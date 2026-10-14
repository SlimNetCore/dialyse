package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.application.equipement.ConstaterEtat;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipement;
import com.hemodialyse.backend.contexts.gmao.application.equipement.EnregistrerEquipementCommande;
import com.hemodialyse.backend.contexts.gmao.doubles.CapaciteDesSallesEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.gmao.doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.gmao.doubles.InterventionsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.ParcEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.PublicateurEnMemoire;
import com.hemodialyse.backend.contexts.gmao.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.gmao.doubles.IntervenantsEnMemoire;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.UniciteDuCodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.ChampInvalide;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LimiteDeTachesAtteinte;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIncompatible;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutTache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheAchevee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheAjoutee;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheDemarree;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheInconnue;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TransitionDeTacheInvalide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des tâches d'intervention (R21, KB-GMAO-55) sur adapters in-memory. */
class CasDUsageTachesTest {

    private static final Instant T0 = Instant.parse("2026-10-12T09:00:00Z");
    private static final Instant DEBUT = Instant.parse("2026-10-12T10:00:00Z");
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));
    private static final UtilisateurId ADMIN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

    private InterventionsEnMemoire interventions;
    private PublicateurEnMemoire publicateur;
    private HorlogeFixe horloge;
    private PlanifierIntervention planifier;
    private DemarrerIntervention demarrer;
    private AjouterTache ajouter;
    private DemarrerTache demarrerTache;
    private AcheverTache acheverTache;
    private ConsulterIntervention consulter;
    private EquipementId equipement;

    @BeforeEach
    void setUp() {
        ParcEnMemoire parc = new ParcEnMemoire();
        interventions = new InterventionsEnMemoire();
        publicateur = new PublicateurEnMemoire();
        UniteDeTravailDirecte unite = new UniteDeTravailDirecte();
        horloge = new HorlogeFixe(T0);
        IdentifiantsSequentiels identifiants = new IdentifiantsSequentiels();
        EnregistrerEquipement enregistrerEquipement = new EnregistrerEquipement(parc, new UniciteDuCodeEquipement(parc),
                new CapaciteDesSallesEnMemoire(), publicateur, horloge, identifiants, unite);
        equipement = new EquipementId(enregistrerEquipement.executer(new EnregistrerEquipementCommande(CENTRE, "G-1",
                "Désignation", "GENERATEUR_DIALYSE", null, null, null, Instant.parse("2024-03-01T08:00:00Z"), null, null,
                null, ADMIN)).id());
        ConstaterEtat etatEquipement = new ConstaterEtat(parc, publicateur, horloge, unite);
        planifier = new PlanifierIntervention(interventions, parc, publicateur, horloge, identifiants, unite);
        demarrer = new DemarrerIntervention(interventions, etatEquipement, publicateur, horloge, unite);
        ajouter = new AjouterTache(interventions, publicateur, horloge, identifiants, unite);
        demarrerTache = new DemarrerTache(interventions, publicateur, horloge, unite);
        acheverTache = new AcheverTache(interventions, publicateur, horloge, unite);
        consulter = new ConsulterIntervention(interventions, horloge);
        publicateur.publies.clear();
    }

    private InterventionId planifiee() {
        InterventionVue vue = planifier.executer(new PlanifierInterventionCommande(equipement, CENTRE, "CURATIVE", DEBUT,
                "Fuite", null, "EN_MAINTENANCE", null, null, null, ADMIN));
        return new InterventionId(vue.id());
    }

    private InterventionId enCours() {
        InterventionId id = planifiee();
        demarrer.executer(id, CENTRE, ADMIN);
        return id;
    }

    private InterventionVue.TacheVue ajouterUneTache(InterventionId id, String description) {
        return ajouter.executer(new AjouterTacheCommande(id, CENTRE, description, ADMIN)).taches().getLast();
    }

    @Test
    void should_add_a_task_to_do_published_and_visible_when_reading_the_intervention() {
        InterventionId id = planifiee();
        publicateur.publies.clear();

        InterventionVue vue = ajouter.executer(new AjouterTacheCommande(id, CENTRE, "Changer le joint", ADMIN));

        assertThat(vue.taches()).singleElement().satisfies(t -> {
            assertThat(t.description()).isEqualTo("Changer le joint");
            assertThat(t.statut()).isEqualTo(StatutTache.A_FAIRE);
            assertThat(t.creePar()).isEqualTo(ADMIN.valeur());
            assertThat(t.demarreLe()).isNull();
        });
        assertThat(publicateur.publies).singleElement().isInstanceOf(TacheAjoutee.class);
        assertThat(consulter.executer(id, CENTRE).orElseThrow().taches()).hasSize(1);
    }

    @Test
    void should_walk_a_task_to_completed_and_publish_each_step() {
        InterventionId id = enCours();
        InterventionVue.TacheVue tache = ajouterUneTache(id, "Changer le joint");
        publicateur.publies.clear();

        horloge.avancerA(T0.plusSeconds(60));
        demarrerTache.executer(id, CENTRE, new TacheId(tache.id()), ADMIN);
        horloge.avancerA(T0.plusSeconds(120));
        InterventionVue vue = acheverTache.executer(id, CENTRE, new TacheId(tache.id()), ADMIN);

        assertThat(vue.taches().getFirst().statut()).isEqualTo(StatutTache.COMPLETEE);
        assertThat(vue.taches().getFirst().demarreLe()).isEqualTo(T0.plusSeconds(60));
        assertThat(vue.taches().getFirst().acheveLe()).isEqualTo(T0.plusSeconds(120));
        assertThat(publicateur.publies).hasSize(2);
        assertThat(publicateur.publies.get(0)).isInstanceOf(TacheDemarree.class);
        assertThat(publicateur.publies.get(1)).isInstanceOf(TacheAchevee.class);
    }

    @Test
    void should_reject_an_invalid_transition_without_publishing_or_changing_the_task() {
        InterventionId id = enCours();
        InterventionVue.TacheVue tache = ajouterUneTache(id, "Changer le joint");
        publicateur.publies.clear();

        assertThatThrownBy(() -> acheverTache.executer(id, CENTRE, new TacheId(tache.id()), ADMIN))
                .isInstanceOf(TransitionDeTacheInvalide.class);

        assertThat(publicateur.publies).isEmpty();
        assertThat(consulter.executer(id, CENTRE).orElseThrow().taches().getFirst().statut()).isEqualTo(StatutTache.A_FAIRE);
    }

    @Test
    void should_reject_a_task_without_description_and_the_thirty_first_one() {
        InterventionId id = enCours();

        assertThatThrownBy(() -> ajouter.executer(new AjouterTacheCommande(id, CENTRE, " ", ADMIN)))
                .isInstanceOf(ChampInvalide.class);
        IntStream.rangeClosed(1, 30).forEach(i -> ajouterUneTache(id, "Tâche " + i));
        assertThatThrownBy(() -> ajouter.executer(new AjouterTacheCommande(id, CENTRE, "De trop", ADMIN)))
                .isInstanceOf(LimiteDeTachesAtteinte.class);
        assertThat(consulter.executer(id, CENTRE).orElseThrow().taches()).hasSize(30);
    }

    @Test
    void should_reject_working_a_task_of_an_intervention_not_in_progress() {
        InterventionId id = planifiee();
        InterventionVue.TacheVue tache = ajouterUneTache(id, "Changer le joint");

        assertThatThrownBy(() -> demarrerTache.executer(id, CENTRE, new TacheId(tache.id()), ADMIN))
                .isInstanceOf(StatutIncompatible.class);
    }

    @Test
    void should_treat_an_unknown_intervention_or_an_intervention_of_another_centre_alike() {
        InterventionId id = enCours();
        InterventionVue.TacheVue tache = ajouterUneTache(id, "Changer le joint");
        InterventionId inconnue = new InterventionId(UUID.randomUUID());

        assertThatThrownBy(() -> ajouter.executer(new AjouterTacheCommande(inconnue, CENTRE, "x", ADMIN)))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThatThrownBy(() -> ajouter.executer(new AjouterTacheCommande(id, AUTRE_CENTRE, "x", ADMIN)))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThatThrownBy(() -> demarrerTache.executer(id, AUTRE_CENTRE, new TacheId(tache.id()), ADMIN))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThatThrownBy(() -> acheverTache.executer(id, AUTRE_CENTRE, new TacheId(tache.id()), ADMIN))
                .isInstanceOf(InterventionIntrouvable.class);
        assertThat(consulter.executer(id, CENTRE).orElseThrow().taches()).hasSize(1);
    }

    @Test
    void should_reject_an_unknown_task() {
        InterventionId id = enCours();

        assertThatThrownBy(() -> demarrerTache.executer(id, CENTRE, new TacheId(UUID.randomUUID()), ADMIN))
                .isInstanceOf(TacheInconnue.class);
    }
}
