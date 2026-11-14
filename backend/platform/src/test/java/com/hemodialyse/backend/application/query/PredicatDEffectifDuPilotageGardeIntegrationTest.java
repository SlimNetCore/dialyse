package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.characterization.patientcouverture.pc4.EffectifDeReference;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.EffectifDesPatientsJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.patientcouverture.EffectifsParLaFiche;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde de dérive de l'effectif (RG-PAT-032, revue du lot A du Pilotage direction, m2) : le prédicat d'effectif de la période de Patient et
 * couverture (copie nº 1, {@code EffectifDesPatientsJdbc} depuis LEC-PAT iii, ADR 0178 : le Pilotage le lit par {@code EffectifsParLaFiche}) et la référence des tests
 * PC4 / PC5 ({@link EffectifDeReference}, copie nº 2) doivent compter les mêmes patients sur un jeu de sorties, de séjours et de dates
 * limites. Un patient par centre : l'effectif de chaque centre vaut 0 ou 1. La copie nº 3 (Patient et couverture,
 * {@code CompteursDeCouvertureJdbc}) est comparée à la référence par PC4 / PC5. Convergence : langage publié du Patient (PD5).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PredicatDEffectifDuPilotageGardeIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99997700-0000-0000-0000-000000000001");
    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);
    private static final String[] ETATS = {null, "PERMANENT", "DECEDE", "GREFFE", "GUERRI", "TRANSFERE", "OCCASIONNEL",
            "VACANCIER_ETRANGER", "VACANCIER_LOCAL"};

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void poser() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-PDG", "Société garde effectif");
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM patients WHERE center_id IN (SELECT id FROM centers WHERE code LIKE 'ZT-PDG%')");
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-PDG%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-PDG%'");
    }

    private UUID unPatient(int rang, String etat, LocalDate evenement, LocalDate admission) {
        UUID centre = UUID.randomUUID();
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", centre, "ZT-PDG-" + rang,
                "Centre " + rang, SOCIETE);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_naissance, numero_assurance, "
                        + "date_admission, type_patient, etat_patient, date_evenement_etat, qualite_assure, sous_kt, epo_enabled, "
                        + "en_sommeil, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,FALSE,CURRENT_TIMESTAMP)",
                id, centre, "PDG-" + rang, "NOM", "Prenom", "M", Date.valueOf("1970-05-05"), "ASS-PDG-" + rang, Date.valueOf(admission),
                "NON_VACANCIER", etat, evenement == null ? null : Date.valueOf(evenement), "ASSURE", false);
        return centre;
    }

    @Test
    void le_predicat_du_pilotage_compte_les_memes_patients_que_la_reference_sur_les_sorties_les_sejours_et_les_dates_limites() {
        LocalDate[] evenements = {null, DEBUT.minusDays(1), DEBUT, DEBUT.plusDays(1), FIN, FIN.plusDays(1)};
        List<UUID> centres = new ArrayList<>();
        int rang = 0;
        for (String etat : ETATS) {
            for (LocalDate evenement : evenements) centres.add(unPatient(rang++, etat, evenement, DEBUT.minusDays(400)));
        }
        centres.add(unPatient(rang++, "PERMANENT", null, FIN));
        centres.add(unPatient(rang++, "PERMANENT", null, FIN.plusDays(1)));

        Map<UUID, Long> duPilotage = new HashMap<>();
        new EffectifsParLaFiche(new EffectifDesPatientsJdbc(jdbc), centre -> java.util.Map.of()).parCentre(centres, new Periode(DEBUT, FIN))
                .forEach((centre, effectif) -> duPilotage.put(centre, effectif.patients()));

        var reference = EffectifDeReference.presentSur("p", DEBUT, FIN);
        List<Object> arguments = new ArrayList<>(centres);
        arguments.addAll(reference.args());
        Map<UUID, Long> deLaReference = new HashMap<>();
        jdbc.query("SELECT p.center_id, COUNT(*) AS total FROM patients p WHERE p.center_id IN ("
                + String.join(",", java.util.Collections.nCopies(centres.size(), "?")) + ") AND " + reference.sql()
                + " GROUP BY p.center_id", rs -> {
            deLaReference.put(rs.getObject("center_id", UUID.class), rs.getLong("total"));
        }, arguments.toArray());

        assertThat(duPilotage).as("le prédicat du Pilotage direction a dérivé de la référence RG-PAT-032").isEqualTo(deLaReference);
        assertThat(deLaReference.size()).as("garde non vacueuse : des patients comptés").isBetween(10, centres.size() - 10);
    }
}
