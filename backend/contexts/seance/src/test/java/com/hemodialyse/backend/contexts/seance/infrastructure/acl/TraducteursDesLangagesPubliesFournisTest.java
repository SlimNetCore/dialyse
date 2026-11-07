package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.seance.contrat.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.DiffusionsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.HistoriqueParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PatientsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PlanningDesPatientsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.ValidationParLeLegacy;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DiffusionsFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlanningFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ValidationFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Traducteurs des langages publiés fournis par la plateforme vers les ports de la Séance (ADR 0088). */
class TraducteursDesLangagesPubliesFournisTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final UUID PATIENT = new UUID(1, 1);
    private static final UUID SEANCE = new UUID(2, 2);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @Test
    void la_situation_du_planning_est_traduite_champ_a_champ() {
        PlanningFourni fourni = (c, p, j) -> Optional.of(new PlanningFourni.Situation(true, false, true, "TRANSFERE", JOUR, JOUR.minusDays(9)));
        var situation = new PlanningDesPatientsParLeLegacy(fourni).situation(new CentreId(CENTRE), new PatientId(PATIENT), JOUR);
        assertThat(situation.centreFerme()).isTrue();
        assertThat(situation.jourDeDialyse()).isFalse();
        assertThat(situation.enSommeil()).isTrue();
        assertThat(situation.etatDuPatient()).isEqualTo("TRANSFERE");
        assertThat(situation.dateDeLEtat()).isEqualTo(JOUR);
        assertThat(situation.dateDAdmission()).isEqualTo(JOUR.minusDays(9));
    }

    @Test
    void un_patient_inconnu_du_planning_est_un_patient_introuvable() {
        PlanningFourni fourni = (c, p, j) -> Optional.empty();
        assertThatThrownBy(() -> new PlanningDesPatientsParLeLegacy(fourni).situation(new CentreId(CENTRE), new PatientId(PATIENT), JOUR))
                .isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void la_fiche_du_patient_et_ses_references_sont_traduites() {
        UUID generateur = new UUID(3, 3);
        var fiche = new FichesFournies.Fiche(PATIENT, "P-1", "Nom", "Prenom", "F", JOUR, "123", "A+", "0550", new UUID(4, 4), new UUID(5, 5),
                new UUID(6, 6), generateur, "G1", "Fresenius", "ACTIF");
        FichesFournies fourni = Mockito.mock(FichesFournies.class);
        Mockito.when(fourni.fiche(CENTRE, PATIENT)).thenReturn(Optional.of(fiche));
        Mockito.when(fourni.parCode(CENTRE, "P-1")).thenReturn(Optional.of(PATIENT));
        Mockito.when(fourni.tous(CENTRE)).thenReturn(List.of(new FichesFournies.References(PATIENT, "P-1", "123", "999")));
        var patients = new PatientsParLeLegacy(fourni);

        var traduite = patients.fiche(new CentreId(CENTRE), new PatientId(PATIENT)).orElseThrow();
        assertThat(traduite.id()).isEqualTo(new PatientId(PATIENT));
        assertThat(traduite.numeroDAssurance()).isEqualTo("123");
        assertThat(traduite.telephoneMobile()).isEqualTo("0550");
        assertThat(traduite.generateur()).isEqualTo(generateur);
        assertThat(traduite.generateurMarque()).isEqualTo("Fresenius");
        assertThat(patients.parCode(new CentreId(CENTRE), "P-1")).contains(new PatientId(PATIENT));
        assertThat(patients.parNumeroDAssurance(new CentreId(CENTRE), "000")).isEmpty();
        var references = patients.tous(new CentreId(CENTRE));
        assertThat(references).singleElement().satisfies(r -> {
            assertThat(r.patient()).isEqualTo(new PatientId(PATIENT));
            assertThat(r.numeroDAssuranceDeLAssure()).isEqualTo("999");
        });
    }

    @Test
    void une_ligne_de_statut_illisible_est_ignoree_de_l_historique() {
        HistoriqueFourni.Ligne lisible = ligne("VALIDEE", "URGENCE");
        HistoriqueFourni.Ligne illisible = ligne("INCONNU", null);
        HistoriqueFourni fourni = (c, du, au, statuts, texte, ordre, decroissant, deverrouillee, page, taille)
                -> new HistoriqueFourni.Page(List.of(illisible, lisible), 2);
        var page = new HistoriqueParLeLegacy(fourni).rechercher(new CentreId(CENTRE), RechercheDeSeances.toutes(), new Pagination(0, 20));
        assertThat(page.items()).singleElement().satisfies(l -> {
            assertThat(l.statut()).isEqualTo(StatutDeSeance.VALIDEE);
            assertThat(l.motifHorsPlanning()).hasToString("URGENCE");
            assertThat(l.nomDuPatient()).isEqualTo("Nom");
        });
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void la_recherche_est_transmise_en_noms_de_statut_et_de_tri() {
        var recu = new Object[3];
        HistoriqueFourni fourni = (c, du, au, statuts, texte, ordre, decroissant, deverrouillee, page, taille) -> {
            recu[0] = statuts;
            recu[1] = ordre;
            recu[2] = taille;
            return new HistoriqueFourni.Page(List.of(), 0);
        };
        var recherche = new RechercheDeSeances(null, null, RechercheDeSeances.lireLesStatuts("cree, validee"), "x",
                RechercheDeSeances.OrdreDeTri.lire("patientNom"), false, null);
        new HistoriqueParLeLegacy(fourni).rechercher(new CentreId(CENTRE), recherche, new Pagination(0, 9999));
        assertThat(recu[0]).isEqualTo(java.util.Set.of("CREE", "VALIDEE"));
        assertThat(recu[1]).isEqualTo("PATIENT");
        assertThat(recu[2]).isEqualTo(500);
    }

    @Test
    void la_validation_relit_la_seance_validee() {
        var seances = new SeancesEnMemoire();
        var id = new SeanceId(SEANCE);
        seances.semer(Seance.creer(id, new CentreId(CENTRE), new PatientId(PATIENT), JOUR, Instant.parse("2026-06-15T08:00:00Z")));
        var appels = new java.util.ArrayList<String>();
        ValidationFournie fourni = (c, s, auteur) -> appels.add(c + "|" + s + "|" + auteur);
        var validation = new ValidationParLeLegacy(fourni, seances);
        assertThat(validation.validerParLInfirmier(new CentreId(CENTRE), id, "marie").id()).isEqualTo(id);
        assertThat(appels).containsExactly(CENTRE + "|" + SEANCE + "|marie");
        assertThatThrownBy(() -> validation.validerParLInfirmier(new CentreId(CENTRE), new SeanceId(new UUID(9, 9)), "marie"))
                .isInstanceOf(SeanceIntrouvable.class);
    }

    @Test
    void les_diffusions_portent_l_identite_du_patient_et_le_jour_en_texte_iso() {
        DiffusionsFournies fourni = Mockito.mock(DiffusionsFournies.class);
        var diffusions = new DiffusionsParLeLegacy(fourni);
        var seance = Seance.creer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR, Instant.parse("2026-06-15T08:00:00Z"));
        var patient = new FicheDuPatient(new PatientId(PATIENT), "P-1", "Nom", "Prenom", null, null, null, null, null, null, null, null,
                null, null, null, null);

        diffusions.seanceCreee(new CentreId(CENTRE), seance, patient);
        diffusions.seanceValidee(new CentreId(CENTRE), seance, patient);
        diffusions.saisieDeLInfirmier(new CentreId(CENTRE), "SEANCE_CREEE", seance, patient, "marie");

        Mockito.verify(fourni).seanceCreee(CENTRE, SEANCE, PATIENT, "Nom", "Prenom", "2026-06-15");
        Mockito.verify(fourni).seanceValidee(CENTRE, SEANCE, PATIENT, "Nom", "Prenom", "2026-06-15");
        Mockito.verify(fourni).saisieDeLInfirmier(CENTRE, "SEANCE_CREEE", PATIENT, "Nom", "Prenom", "marie", "2026-06-15");
    }

    private static HistoriqueFourni.Ligne ligne(String statut, String motif) {
        return new HistoriqueFourni.Ligne(SEANCE, CENTRE, PATIENT, "P-1", "Nom", "Prenom", JOUR, statut, null, null, null, null, null,
                motif != null, motif);
    }
}
