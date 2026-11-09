package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.ConstatsDuTableauDeBord.Lecture;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Changement;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le suivi du temps réel avec des doubles en mémoire : abonnés, signaux de centre, constat de référence, diffusion, plafond, panne. */
class SuivreLeTempsReelTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant MAINTENANT = Instant.parse("2026-11-09T08:00:00Z");

    private record Diffusion(UUID societeId, Instant a, List<Changement> changements) {
    }

    private final Map<UUID, Lecture> lectures = new HashMap<>();
    private final List<UUID> societesLues = new ArrayList<>();
    private final List<Diffusion> diffusions = new ArrayList<>();
    private final HistoriqueDesAlertesEnMemoire historique = new HistoriqueDesAlertesEnMemoire();
    private boolean panne;
    private int interrogationsDuCentre;

    private final SuivreLeTempsReel tempsReel = new SuivreLeTempsReel(
            societeId -> {
                societesLues.add(societeId);
                if (panne) throw new IllegalStateException("lecture impossible");
                return lectures.get(societeId);
            },
            new EnregistrerLesAlertes(historique, new HorlogeDuPilotage(Clock.fixed(MAINTENANT, ZoneOffset.UTC))),
            centreId -> {
                interrogationsDuCentre++;
                return CENTRE.equals(centreId) ? Optional.of(SOCIETE) : Optional.empty();
            },
            (societeId, a, changements) -> diffusions.add(new Diffusion(societeId, a, changements)),
            new HorlogeDuPilotage(Clock.fixed(MAINTENANT, ZoneOffset.UTC)));

    private static Lecture lecture(int seances, AlerteLevee... alertes) {
        return new Lecture(new ConstatDesIndicateurs().retenir(CENTRE, "Centre", ConstatDesIndicateurs.SEANCES, "seances", seances),
                List.of(alertes));
    }

    @Test
    void le_premier_recalcul_etablit_le_constat_de_reference_sans_rien_diffuser() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.abonneAjoute(SOCIETE);
        assertTrue(tempsReel.rafraichir(SOCIETE).isEmpty());
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void un_changement_est_diffuse_avec_l_heure_du_pilotage_puis_plus_rien_si_rien_ne_change() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.rafraichir(SOCIETE);
        lectures.put(SOCIETE, lecture(11));

        assertEquals(1, tempsReel.rafraichir(SOCIETE).size());
        assertEquals(1, diffusions.size());
        assertEquals(MAINTENANT, diffusions.get(0).a());
        assertEquals(SOCIETE, diffusions.get(0).societeId());
        assertEquals(new BigDecimal("10"), diffusions.get(0).changements().get(0).avant());

        assertTrue(tempsReel.rafraichir(SOCIETE).isEmpty());
        assertEquals(1, diffusions.size());
    }

    @Test
    void chaque_recalcul_alimente_l_historique_des_alertes() {
        AlerteLevee stock = new AlerteLevee(CENTRE, "Centre", "STOCK_SOUS_SEUIL", GraviteDAlerte.CRITICAL, BigDecimal.ONE);
        lectures.put(SOCIETE, lecture(10, stock));
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.rafraichir(SOCIETE);
        assertEquals(1, historique.ouvertes(SOCIETE).size());
        lectures.put(SOCIETE, lecture(10));
        tempsReel.rafraichir(SOCIETE);
        assertTrue(historique.ouvertes(SOCIETE).isEmpty());
        assertEquals(1, historique.nombreDeLignes());
    }

    @Test
    void sans_abonne_un_signal_de_centre_ne_recalcule_rien_et_n_interroge_meme_pas_la_base() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.unCentreAChange(CENTRE);
        tempsReel.traiterLesSignalements();
        assertTrue(societesLues.isEmpty());
        assertEquals(0, interrogationsDuCentre);
        assertEquals(0, historique.nombreDeLignes());
    }

    @Test
    void un_signal_de_centre_recalcule_sa_societe_et_la_societe_du_centre_est_memorisee() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.traiterLesSignalements(); // référence posée à l'abonnement
        lectures.put(SOCIETE, lecture(12));
        tempsReel.unCentreAChange(CENTRE);
        tempsReel.unCentreAChange(CENTRE);
        tempsReel.traiterLesSignalements();

        assertEquals(1, diffusions.size());
        assertEquals(1, interrogationsDuCentre, "la société d'un centre connu est mémorisée");
    }

    @Test
    void un_centre_inconnu_ou_nul_est_ignore_et_son_absence_n_est_pas_memorisee() {
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.unCentreAChange(null);
        tempsReel.unCentreAChange(UUID.randomUUID());
        assertEquals(1, interrogationsDuCentre);
        tempsReel.unCentreAChange(UUID.fromString("00000000-0000-0000-0000-0000000000ff"));
        assertEquals(2, interrogationsDuCentre);
    }

    @Test
    void le_dernier_abonne_parti_la_societe_n_est_plus_suivie_et_son_constat_est_oublie() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.rafraichir(SOCIETE);
        tempsReel.abonneRetire(SOCIETE);
        assertTrue(tempsReel.estSuivie(SOCIETE));
        tempsReel.abonneRetire(SOCIETE);
        assertFalse(tempsReel.estSuivie(SOCIETE));

        // un nouvel abonné repart d'un constat de référence neuf : rien n'est diffusé
        lectures.put(SOCIETE, lecture(99));
        tempsReel.abonneAjoute(SOCIETE);
        assertTrue(tempsReel.rafraichir(SOCIETE).isEmpty());
        assertTrue(diffusions.isEmpty());
    }

    @Test
    void le_balayage_recalcule_toutes_les_societes_suivies() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.balayer();
        assertEquals(List.of(SOCIETE), societesLues);
    }

    @Test
    void une_diffusion_est_plafonnee_a_soixante_changements() {
        ConstatDesIndicateurs avant = new ConstatDesIndicateurs();
        ConstatDesIndicateurs apres = new ConstatDesIndicateurs();
        for (int i = 0; i < 100; i++) {
            avant.retenir(CENTRE, "Centre", ConstatDesIndicateurs.FINANCE, "i" + i, 1);
            apres.retenir(CENTRE, "Centre", ConstatDesIndicateurs.FINANCE, "i" + i, 2);
        }
        lectures.put(SOCIETE, new Lecture(avant, List.of()));
        tempsReel.abonneAjoute(SOCIETE);
        tempsReel.rafraichir(SOCIETE);
        lectures.put(SOCIETE, new Lecture(apres, List.of()));

        assertEquals(100, tempsReel.rafraichir(SOCIETE).size(), "les changements rendus ne sont pas plafonnés");
        assertEquals(SuivreLeTempsReel.CHANGEMENTS_MAX, diffusions.get(0).changements().size());
        assertEquals(60, SuivreLeTempsReel.CHANGEMENTS_MAX);
    }

    @Test
    void une_lecture_en_panne_est_avalee_et_n_ecrit_ni_historique_ni_constat() {
        lectures.put(SOCIETE, lecture(10));
        tempsReel.abonneAjoute(SOCIETE);
        panne = true;
        assertTrue(tempsReel.rafraichir(SOCIETE).isEmpty());
        assertEquals(0, historique.nombreDeLignes());
        panne = false;
        assertTrue(tempsReel.rafraichir(SOCIETE).isEmpty(), "le premier calcul réussi est encore la référence");
        assertTrue(diffusions.isEmpty());
    }
}
