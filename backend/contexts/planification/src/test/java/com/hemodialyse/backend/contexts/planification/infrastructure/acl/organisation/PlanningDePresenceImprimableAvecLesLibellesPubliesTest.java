package com.hemodialyse.backend.contexts.planification.infrastructure.acl.organisation;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence;
import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence.Situation;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine.LigneDuRoulement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Traducteur du planning de présence imprimé vers organisation.v1 (DOC5e, ADR 0197) : libellés, lignes écartées, ordre. */
class PlanningDePresenceImprimableAvecLesLibellesPubliesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("5e000000-0000-0000-0000-00000000000a"));
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 11, 8);
    private static final UUID SALLE_1 = UUID.randomUUID();
    private static final UUID SALLE_10 = UUID.randomUUID();
    private static final UUID SALLE_2 = UUID.randomUUID();
    private static final UUID SALLE_ETOILE = UUID.randomUUID();
    private static final UUID SALLE_INCONNUE = UUID.randomUUID();
    private static final UUID MATIN = UUID.randomUUID();
    private static final UUID SOIR = UUID.randomUUID();
    private static final UUID CRENEAU_INCONNU = UUID.randomUUID();

    /** Organisation factice : n'implémente que les deux lectures de libellés utilisées ; garde les appels. */
    static class OrganisationFactice implements LibellesDeLOrganisation {
        final Map<UUID, String> salles = new HashMap<>();
        final Map<UUID, LibelleDeCreneau> creneaux = new HashMap<>();
        final List<String> appels = new ArrayList<>();

        @Override
        public Map<UUID, String> nomsDesSalles(UUID centre, Collection<UUID> demandees) {
            appels.add("salles " + centre + " " + demandees.size());
            Map<UUID, String> rendu = new HashMap<>();
            demandees.forEach(s -> {
                if (salles.containsKey(s)) rendu.put(s, salles.get(s));
            });
            return rendu;
        }

        @Override
        public Map<UUID, LibelleDeCreneau> creneauxDuCentre(UUID centre, Collection<UUID> demandes) {
            appels.add("creneaux " + centre + " " + demandes.size());
            Map<UUID, LibelleDeCreneau> rendu = new HashMap<>();
            demandes.forEach(c -> {
                if (creneaux.containsKey(c)) rendu.put(c, creneaux.get(c));
            });
            return rendu;
        }

        @Override
        public Map<UUID, String> codesDesSalles(UUID centre, Collection<UUID> salles) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<UUID, String> libellesDesCreneaux(UUID centre, Collection<UUID> creneaux) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<UUID, String> nomsDesMedecins(UUID centre, Collection<UUID> medecins) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> medecinsCorrespondant(UUID centre, String terme) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<UUID> creneauxCorrespondant(UUID centre, String terme) {
            throw new UnsupportedOperationException();
        }
    }

    private final OrganisationFactice organisation = new OrganisationFactice();

    private OrganisationFactice avecLesLibelles() {
        organisation.salles.put(SALLE_1, "Salle 1");
        organisation.salles.put(SALLE_10, "Salle 10");
        organisation.salles.put(SALLE_2, "Salle 2");
        organisation.salles.put(SALLE_ETOILE, "Salle Étoile");
        organisation.creneaux.put(MATIN, new LibellesDeLOrganisation.LibelleDeCreneau("P1", "Matin"));
        organisation.creneaux.put(SOIR, new LibellesDeLOrganisation.LibelleDeCreneau("P2", "Soir"));
        return organisation;
    }

    private static LigneDuRoulement ligne(int rang, UUID salle, UUID creneau, String infirmier, Situation situation) {
        return new LigneDuRoulement(DIMANCHE.plusDays(rang), salle, creneau, infirmier, situation);
    }

    private List<LigneDuPlanningDePresence> imprimer(List<LigneDuRoulement> lignes) {
        return new PlanningDePresenceImprimableAvecLesLibellesPublies((centre, debut) -> lignes, avecLesLibelles()).lignes(CENTRE, DIMANCHE);
    }

    @Test
    void chaque_ligne_recoit_le_nom_de_la_salle_et_le_code_et_le_libelle_du_creneau_lus_pour_le_centre() {
        List<LigneDuPlanningDePresence> lignes = imprimer(List.of(ligne(1, SALLE_1, MATIN, "Sara Amrani", Situation.ABSENT)));

        assertThat(lignes).containsExactly(new LigneDuPlanningDePresence(DIMANCHE.plusDays(1), "Salle 1", "P1", "Matin", "Sara Amrani", Situation.ABSENT));
        assertThat(organisation.appels).containsExactly("salles " + CENTRE.valeur() + " 1", "creneaux " + CENTRE.valeur() + " 1");
    }

    @Test
    void une_ligne_dont_la_salle_ou_le_creneau_n_a_pas_de_libelle_pour_le_centre_n_est_pas_imprimee() {
        List<LigneDuPlanningDePresence> lignes = imprimer(List.of(
                ligne(1, SALLE_INCONNUE, MATIN, "Salle perdue", Situation.PREVU),
                ligne(1, SALLE_1, CRENEAU_INCONNU, "Creneau perdu", Situation.PREVU),
                ligne(1, SALLE_1, MATIN, "Garde", Situation.PREVU)));

        assertThat(lignes).extracting(LigneDuPlanningDePresence::infirmier).containsExactly("Garde");
    }

    @Test
    void l_ordre_est_jour_code_du_creneau_salle_situation_puis_infirmier_par_points_de_code() {
        List<LigneDuPlanningDePresence> lignes = imprimer(List.of(
                ligne(2, SALLE_1, MATIN, "Jour deux", Situation.PREVU),
                ligne(1, SALLE_ETOILE, MATIN, "Etoile", Situation.PREVU),
                ligne(1, SALLE_2, MATIN, "Deux", Situation.PREVU),
                ligne(1, SALLE_10, MATIN, "Dix", Situation.PREVU),
                ligne(1, SALLE_1, SOIR, "Soir", Situation.PREVU),
                ligne(1, SALLE_1, MATIN, "Remplacant", Situation.REMPLACANT),
                ligne(1, SALLE_1, MATIN, "Zed", Situation.PREVU),
                ligne(1, SALLE_1, MATIN, "Anne", Situation.PREVU),
                ligne(1, SALLE_1, MATIN, "Absent", Situation.ABSENT)));

        assertThat(lignes).extracting(l -> l.jour().getDayOfMonth() + " " + l.creneauCode() + " " + l.salle() + " " + l.situation().libelle() + " " + l.infirmier())
                .containsExactly(
                        "9 P1 Salle 1 Absent Absent", "9 P1 Salle 1 Prévu Anne", "9 P1 Salle 1 Prévu Zed", "9 P1 Salle 1 Remplaçant Remplacant",
                        "9 P1 Salle 10 Prévu Dix", "9 P1 Salle 2 Prévu Deux", "9 P1 Salle Étoile Prévu Etoile", "9 P2 Salle 1 Prévu Soir",
                        "10 P1 Salle 1 Prévu Jour deux");
    }

    @Test
    void un_roulement_vide_n_interroge_pas_organisation() {
        assertThat(imprimer(List.of())).isEmpty();
        assertThat(organisation.appels).isEmpty();
    }
}
