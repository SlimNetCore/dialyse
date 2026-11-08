package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.domain.planning.model.JourSemaine.LUNDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MARDI;
import static com.hemodialyse.backend.domain.planning.model.JourSemaine.MERCREDI;

/**
 * Filet de caractérisation P6 : lecture de la place par la Séance ({@code SeancePlaceJdbcAdapter}) au moment de la validation d'une
 * séance : le déplacement temporaire du jour prime sur la place habituelle du patient, la place est figée sur la séance, puis relue par
 * la semaine. Point d'observation : le cas d'usage {@code ValiderUneSeance} (SE2, ADR 0089 ; ex-{@code SeanceUseCase.validate}, retiré),
 * appelé sous une session d'administrateur, et l'état SQL des séances. Les goldens sont inchangés.
 */
class LecturesDePlaceConsommeesParLaSeanceCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Autowired
    private ValiderUneSeance validation;

    /** La validation de la séance (SE2, ADR 0089) lit la session : un administrateur du centre, qui régularise aussi les jours passés. */
    private void valider(UUID centre, UUID seance) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UserPrincipal.create("inf-01", centre.toString(), "inf-01", "x", List.of("ADMIN"), true), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            validation.executer(centre, seance, "inf-01", List.of());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    protected String theme() {
        return "p6-semaine/lecture-seance";
    }

    private int seancesCreees = 0;

    private UUID seanceACreer(UUID centre, UUID patient, java.time.LocalDate jour) {
        int rang = ++seancesCreees;
        UUID id = UUID.nameUUIDFromBytes(("planification-p6-seance-" + rang).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, hors_planning) "
                + "VALUES (?, ?, ?, ?, 'CREE', CURRENT_TIMESTAMP, FALSE)", id, patient, centre, java.sql.Date.valueOf(jour));
        aliasNommes.put(id.toString(), "<SEANCE_" + rang + ">");
        return id;
    }

    private void etatDesSeances(String nom) {
        sql(nom, "SELECT id, patient_id, date_seance, statut, salle_id, creneau_id, generateur_id FROM seances "
                + "WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY date_seance, patient_id, id");
    }

    @Test
    void la_validation_fige_la_place_habituelle_du_patient() throws Exception {
        decorDePlacement();
        nommerLaSemaine(-1);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        UUID s = seanceACreer(CENTRE_A, p, semaine(-1, LUNDI));
        valider(CENTRE_A, s);
        etatDesSeances("place_habituelle");
    }

    @Test
    void le_deplacement_temporaire_du_jour_prime_sur_la_place_habituelle() throws Exception {
        decorDePlacement();
        nommerLaSemaine(-1);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI, MARDI);
        deplacementTemporaire(CENTRE_A, p, semaine(-1, LUNDI), salle2, apresMidi, g2a);
        UUID lundi = seanceACreer(CENTRE_A, p, semaine(-1, LUNDI));
        UUID mardi = seanceACreer(CENTRE_A, p, semaine(-1, MARDI));
        valider(CENTRE_A, lundi);
        valider(CENTRE_A, mardi);
        etatDesSeances("deplacement_prioritaire");
    }

    @Test
    void un_patient_sans_salle_ou_sans_creneau_n_a_aucune_place_figee_sauf_deplacement_temporaire() throws Exception {
        decorDePlacement();
        nommerLaSemaine(-1);
        UUID sansSalle = place(CENTRE_A, "PAT_SANS_SALLE", salle1, matin, g1a, LUNDI);
        jdbc.update("UPDATE patients SET salle_id = NULL WHERE id = ?", sansSalle);
        UUID sansCreneau = place(CENTRE_A, "PAT_SANS_CRENEAU", salle1, matin, g1b, LUNDI);
        jdbc.update("UPDATE patients SET position_id = NULL WHERE id = ?", sansCreneau);
        UUID nonPlaceMaisDeplace = place(CENTRE_A, "PAT_DEPLACE", salle1, matin, null, LUNDI);
        jdbc.update("UPDATE patients SET salle_id = NULL, position_id = NULL WHERE id = ?", nonPlaceMaisDeplace);
        deplacementTemporaire(CENTRE_A, nonPlaceMaisDeplace, semaine(-1, LUNDI), salle2, matin, g2a);
        for (UUID p : List.of(sansSalle, sansCreneau, nonPlaceMaisDeplace)) {
            valider(CENTRE_A, seanceACreer(CENTRE_A, p, semaine(-1, LUNDI)));
        }
        etatDesSeances("sans_place");
    }

    @Test
    void un_deplacement_d_une_autre_date_ou_d_un_autre_centre_est_ignore() throws Exception {
        decorDePlacement();
        nommerLaSemaine(-1);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        deplacementTemporaire(CENTRE_A, p, semaine(-1, MARDI), salle2, apresMidi, g2a);
        deplacementTemporaire(CENTRE_B, p, semaine(-1, LUNDI), salleB, creneauB, giso);
        valider(CENTRE_A, seanceACreer(CENTRE_A, p, semaine(-1, LUNDI)));
        etatDesSeances("deplacement_ignore");
    }

    @Test
    void la_place_est_figee_sans_verifier_le_statut_du_generateur_ni_la_salle() throws Exception {
        decorDePlacement();
        nommerLaSemaine(-1);
        UUID hs = generateur(CENTRE_A, "G_HS", salle2, "HORS_SERVICE");
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, hs, LUNDI);
        valider(CENTRE_A, seanceACreer(CENTRE_A, p, semaine(-1, LUNDI)));
        etatDesSeances("generateur_hors_service");
    }

    @Test
    void une_seance_validee_reste_affichee_la_ou_elle_a_eu_lieu_quand_le_patient_est_ensuite_deplace_par_un_deplacement_temporaire() throws Exception {
        decorDePlacement();
        nommerLaSemaine(-1);
        UUID p = place(CENTRE_A, "PAT_1", salle1, matin, g1a, LUNDI);
        deplacementTemporaire(CENTRE_A, p, semaine(-1, LUNDI), salle2, apresMidi, g2a);
        valider(CENTRE_A, seanceACreer(CENTRE_A, p, semaine(-1, LUNDI)));
        jdbc.update("DELETE FROM deplacement_temporaire WHERE center_id = ?", CENTRE_A);
        jdbc.update("UPDATE patients SET jour_lundi = FALSE, jour_mardi = TRUE WHERE id = ?", p);
        arbre("seance_figee_relue_par_la_semaine", projectionDeLaSemaine(lireLaSemaine(-1)));
    }
}
