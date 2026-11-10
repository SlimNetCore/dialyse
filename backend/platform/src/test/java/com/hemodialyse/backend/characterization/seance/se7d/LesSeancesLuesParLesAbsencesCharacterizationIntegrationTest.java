package com.hemodialyse.backend.characterization.seance.se7d;

import com.hemodialyse.backend.contexts.absencespatients.application.ExclusionsDeSeances;
import com.hemodialyse.backend.contexts.absencespatients.application.PatientsAttendus;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SE7d lot d1 : les trois lecteurs SQL de {@code seances} du contexte Absences patients ({@code SeancesRealiseesJdbc} : séances
 * réalisées du centre, jours d'un patient, séance réalisée un jour ; {@code ExclusionsDeSeancesJdbc} : absence issue d'une exclusion ;
 * {@code PatientsAttendusJdbc} : patients écartés car déjà réalisés). Complète A1 à A4 sur les cas que leurs goldens ne posent pas :
 * égalité de date entre patients, casse et blancs du statut, lien d'exclusion porté par une séance d'un autre statut ou d'un autre centre.
 */
class LesSeancesLuesParLesAbsencesCharacterizationIntegrationTest extends Se7dCharacterizationSupport {

    @Autowired
    private SeancesRealisees seancesRealiseesDesAbsences;
    @Autowired
    private PatientsAttendus patientsAttendus;
    @Autowired
    private ExclusionsDeSeances exclusionsDeSeances;
    @Autowired
    private com.hemodialyse.backend.contexts.planification.application.SeancesRealisees seancesRealiseesDeLaPlanification;

    @Override
    protected String dossier() {
        return "absences";
    }

    @AfterEach
    void retirerLesCentres() {
        retirerLeReferentielDesCentres();
    }

    /**
     * Statuts posés tels quels en base (la colonne accepte toute chaîne) : la requête compare {@code IN ('VALIDEE','SIGNEE','FACTUREE')},
     * sensible à la casse et aux blancs.
     */
    private static final List<String> STATUTS = List.of("VALIDEE", "SIGNEE", "FACTUREE", "validee", "Signee", "facturee", " VALIDEE",
            "VALIDEE ", "CREE", "ABSENT", "ANNULEE", "BROUILLON", "EN_COURS", "PLANIFIEE");

    @Test
    void la_semaine_des_absences_rend_une_ligne_par_patient_et_par_jour_triee_par_date_seulement() throws Exception {
        LocalDate dimanche = semaineObservee();
        UUID p1 = patientSe1(CENTRE_A, "P1");
        UUID p2 = patientSe1(CENTRE_A, "P2");
        UUID p3 = patientSe1(CENTRE_A, "P3");
        UUID pb = patientSe1(CENTRE_B, "PB");
        // le mardi : trois patients ; P1 deux fois (validée et signée) ; posés dans l'ordre inverse des alias
        seanceTb(CENTRE_A, p3, jourObserve(2), "FACTUREE");
        seanceTb(CENTRE_A, p2, jourObserve(2), "VALIDEE");
        seanceEnBase(CENTRE_A, p1, "P1-mardi-a", jourObserve(2), "VALIDEE");
        seanceEnBase(CENTRE_A, p1, "P1-mardi-b", jourObserve(2), "SIGNEE");
        // le lundi : un seul patient, posé après ceux du mardi ; bornes : samedi inclus, dimanche suivant et samedi précédent exclus
        seanceTb(CENTRE_A, p2, jourObserve(1), "SIGNEE");
        seanceTb(CENTRE_A, p1, jourObserve(6), "VALIDEE");
        seanceTb(CENTRE_A, p1, jourObserve(7), "VALIDEE");
        seanceTb(CENTRE_A, p1, fixe(DIMANCHE_OBSERVE - 1), "VALIDEE");
        seanceTb(CENTRE_B, pb, jourObserve(2), "VALIDEE");

        Resp r = semaineDesAbsences(jourObserve(3), as(CENTRE_A, "INFIRMIER"));
        JsonNode lignes = mapper.readTree(r.raw()).path("seancesRealisees");
        List<String> datesDansLOrdreServi = new ArrayList<>();
        List<String> lignesTriees = new ArrayList<>();
        for (JsonNode l : lignes) {
            datesDansLOrdreServi.add(l.path("dateSeance").asString());
            lignesTriees.add(l.path("dateSeance").asString() + " " + aliasNommes.getOrDefault(l.path("patientId").asString(), "?"));
        }
        List<String> datesTriees = new ArrayList<>(datesDansLOrdreServi);
        datesTriees.sort(Comparator.naturalOrder());
        assertEquals(datesTriees, datesDansLOrdreServi, "ORDER BY s.date_seance : les dates sortent croissantes");
        lignesTriees.sort(Comparator.naturalOrder());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        m.put("semaine demandee le mercredi, debut", dimanche.toString());
        m.put("dates dans l'ordre servi (ordre entre patients d'un meme jour NON verrouille : KB-SEA-77)", datesDansLOrdreServi);
        m.put("lignes (date puis patient, triees par le test)", lignesTriees);
        arbre("semaine_une_ligne_par_patient_et_par_jour", m);
    }

    @Test
    void les_seances_de_rattrapage_ignorent_la_casse_differente_et_dedoublonnent_le_jour() throws Exception {
        UUID p1 = patientSe1(CENTRE_A, "P1");
        UUID absence = absenceComplete(CENTRE_A, "ABSENCE_1", p1, fixe(10));
        seanceTb(CENTRE_A, p1, fixe(9), "VALIDEE");
        seanceEnBase(CENTRE_A, p1, "P1-j11-a", fixe(11), "VALIDEE");
        seanceEnBase(CENTRE_A, p1, "P1-j11-b", fixe(11), "FACTUREE");
        seanceTb(CENTRE_A, p1, fixe(12), "validee");
        seanceTb(CENTRE_A, p1, fixe(13), "SIGNEE ");
        seanceTb(CENTRE_A, p1, fixe(14), "SIGNEE");
        seanceTb(CENTRE_A, p1, fixe(15), "ABSENT");
        UUID autreCentre = patientSe1(CENTRE_B, "P1B");
        seanceTb(CENTRE_B, autreCentre, fixe(16), "VALIDEE");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", reponse(seancesDeRattrapage(absence, as(CENTRE_A, "SECRETAIRE"))));
        m.put("absence lue depuis le centre B", reponse(seancesDeRattrapage(absence, as(CENTRE_B, "SECRETAIRE"))));
        arbre("rattrapage_casse_et_doublons", m);
    }

    @Test
    void l_annulation_ne_regarde_que_le_lien_d_exclusion_porte_par_une_seance_du_centre_reellement_exclue() throws Exception {
        referentielDesCentres();
        UUID p1 = patientSe1(CENTRE_A, "P1");
        UUID p2 = patientSe1(CENTRE_A, "P2");
        UUID p3 = patientSe1(CENTRE_A, "P3");
        UUID p4 = patientSe1(CENTRE_A, "P4");
        UUID pb = patientSe1(CENTRE_B, "PB");
        UUID sansLien = absenceComplete(CENTRE_A, "SANS_LIEN", p1, fixe(20));
        UUID lienAbsent = absenceComplete(CENTRE_A, "LIEN_SEANCE_ABSENT", p2, fixe(20));
        UUID lienValidee = absenceComplete(CENTRE_A, "LIEN_SEANCE_VALIDEE", p3, fixe(20));
        UUID lienAutreCentre = absenceComplete(CENTRE_A, "LIEN_SEANCE_CENTRE_B", p4, fixe(20));
        lienDExclusion(seanceTb(CENTRE_A, p2, fixe(20), "ABSENT"), lienAbsent);
        lienDExclusion(seanceTb(CENTRE_A, p3, fixe(21), "VALIDEE"), lienValidee);
        lienDExclusion(seanceTb(CENTRE_B, pb, fixe(20), "ABSENT"), lienAutreCentre);

        Map<String, Object> port = new LinkedHashMap<>();
        for (Map.Entry<String, UUID> e : Map.of("SANS_LIEN", sansLien, "LIEN_SEANCE_ABSENT", lienAbsent, "LIEN_SEANCE_VALIDEE", lienValidee,
                "LIEN_SEANCE_CENTRE_B", lienAutreCentre).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            port.put(e.getKey(), exclusionsDeSeances.absenceIssueDUneExclusion(new CentreId(CENTRE_A), new AbsenceId(e.getValue())));
        }
        Map<String, Object> http = new LinkedHashMap<>();
        for (Map.Entry<String, UUID> e : List.of(Map.entry("SANS_LIEN", sansLien), Map.entry("LIEN_SEANCE_ABSENT", lienAbsent),
                Map.entry("LIEN_SEANCE_VALIDEE", lienValidee), Map.entry("LIEN_SEANCE_CENTRE_B", lienAutreCentre))) {
            Resp r = annulerLAbsence(e.getValue(), adminA());
            http.put(e.getKey(), refus(r) + " -> statut en base " + statutDeLAbsence(e.getValue()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port ExclusionsDeSeances (centre A)", port);
        m.put("PUT /annulation ADMIN du centre A", http);
        // L5a (2026-11-10, ADR 0117, KB-SEA-81) : seule une séance réellement ABSENT bloque ; ancien golden archivé sous _ecarts-legacy
        arbre("annulation_et_lien_d_exclusion_nouveau_code", m);
    }

    @Test
    void seuls_les_trois_statuts_realises_en_majuscules_exactes_comptent_pour_les_quatre_lectures() {
        UUID patient = patientSe1(CENTRE_A, "P1");
        CentreId centre = new CentreId(CENTRE_A);
        PatientId id = new PatientId(patient);
        for (int i = 0; i < STATUTS.size(); i++) {
            seanceEnBase(CENTRE_A, patient, "S" + i, fixe(30 + i), STATUTS.get(i));
        }
        LocalDate du = fixe(30);
        LocalDate au = fixe(30 + STATUTS.size() - 1);
        List<LocalDate> semaine = seancesRealiseesDesAbsences.duCentre(centre, du, au).stream()
                .map(com.hemodialyse.backend.contexts.absencespatients.domain.SeanceRealisee::date).toList();
        List<LocalDate> jours = seancesRealiseesDesAbsences.joursDuPatient(centre, id, du, au);
        List<LocalDate> planification = seancesRealiseesDeLaPlanification.duCentre(centre, du, au).stream()
                .map(s -> s.date()).toList();

        Map<String, Object> matrice = new LinkedHashMap<>();
        for (int i = 0; i < STATUTS.size(); i++) {
            LocalDate jour = fixe(30 + i);
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("absences.duCentre", semaine.contains(jour));
            l.put("absences.joursDuPatient", jours.contains(jour));
            l.put("absences.realisee", seancesRealiseesDesAbsences.realisee(centre, id, jour));
            l.put("detection : patient encore attendu", patientsAttendus.sansSeanceRealisee(centre, jour).contains(id));
            l.put("planification.duCentre", planification.contains(jour));
            matrice.put("[" + STATUTS.get(i) + "]", l);
        }
        assertTrue(seancesRealiseesDesAbsences.realisee(centre, id, fixe(30)), "VALIDEE est réalisée");
        arbre("matrice_des_statuts_et_de_la_casse", matrice);
    }

    @Test
    void les_bornes_inversees_et_un_patient_d_un_autre_centre_ne_rendent_rien() {
        UUID patient = patientSe1(CENTRE_A, "P1");
        UUID pb = patientSe1(CENTRE_B, "PB");
        seanceTb(CENTRE_A, patient, fixe(40), "VALIDEE");
        seanceTb(CENTRE_B, pb, fixe(40), "VALIDEE");
        CentreId a = new CentreId(CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("duCentre bornes inversees", seancesRealiseesDesAbsences.duCentre(a, fixe(41), fixe(39)).size());
        m.put("duCentre borne unique", seancesRealiseesDesAbsences.duCentre(a, fixe(40), fixe(40)).size());
        m.put("joursDuPatient du patient du centre B lu par le centre A",
                seancesRealiseesDesAbsences.joursDuPatient(a, new PatientId(pb), fixe(39), fixe(41)).size());
        m.put("realisee du patient du centre B lue par le centre A", seancesRealiseesDesAbsences.realisee(a, new PatientId(pb), fixe(40)));
        m.put("planification bornes inversees", seancesRealiseesDeLaPlanification.duCentre(a, fixe(41), fixe(39)).size());
        arbre("bornes_et_centre", m);
    }
}
