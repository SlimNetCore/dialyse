package com.hemodialyse.backend.characterization.planification.optimisation;

import com.hemodialyse.backend.characterization.planification.OptimisationCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.port.DeplacementTemporairePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filet de caractérisation P8, lot 4 : autorisations des routes d'optimisation (onze profils), lecture des données à optimiser par
 * l'adaptateur JDBC (patients à placer, indisponibilités GMAO, déplacements temporaires, profils) et écriture des déplacements
 * temporaires (un par patient et par date).
 */
class DonneesEtAutorisationsDeLOptimisationCharacterizationIntegrationTest extends OptimisationCharacterizationSupport {

    @Autowired
    private OptimisationDonneesPort donnees;
    @Autowired
    private DeplacementTemporairePort temporaires;

    @Override
    protected String theme() {
        return "p8-optimisation/donnees";
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void les_onze_profils_et_l_anonyme_face_aux_huit_routes_de_l_optimisation() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        UUID inconnu = UUID.randomUUID();
        String base = OPTIMISATIONS + "/" + inconnu;
        Map<String, Map<String, String>> matrice = new LinkedHashMap<>();
        Map<String, RequestPostProcessor> profils = profils();
        List<String> noms = new ArrayList<>(profils.keySet());
        noms.add("anonyme");
        for (String nom : noms) {
            RequestPostProcessor who = profils.get(nom);
            Map<String, String> lignes = new LinkedHashMap<>();
            lignes.put("GET historique", statutEtCode(appel("GET", OPTIMISATIONS, null, who)));
            lignes.put("GET execution", statutEtCode(appel("GET", base, null, who)));
            lignes.put("GET calendrier", statutEtCode(appel("GET", base + "/calendrier", null, who)));
            lignes.put("GET impression", statutEtCode(appel("GET", base + "/impression", null, who)));
            lignes.put("POST lancer (corps vide {})", statutEtCode(appel("POST", OPTIMISATIONS, "{}", who)));
            lignes.put("POST lancer (corps valide, calcul vide)", statutEtCode(appel("POST", OPTIMISATIONS,
                    lancement("ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1), who)));
            attendreLaFinDeTousLesCalculs();
            lignes.put("POST arret", statutEtCode(appel("POST", base + "/arret", "", who)));
            lignes.put("POST application", statutEtCode(appel("POST", base + "/application", "", who)));
            lignes.put("DELETE execution", statutEtCode(appel("DELETE", base, null, who)));
            matrice.put(nom, lignes);
        }
        arbre("routes_par_profil", matrice);
    }

    // ------------------------------------------------------------------ lecture des données à optimiser

    @Test
    void l_adaptateur_lit_les_patients_a_placer_les_indisponibilites_les_deplacements_temporaires_et_les_profils() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        LocalDate du = semaine(1, JourSemaine.DIMANCHE);
        LocalDate au = semaine(1, JourSemaine.SAMEDI);
        UUID place = place(CENTRE_A, "PLACE", salle1, matin, g1a, JourSemaine.LUNDI);
        UUID transporteur = UUID.nameUUIDFromBytes("transporteur".getBytes());
        aliasNommes.put(transporteur.toString(), "<TRANSPORTEUR>");
        jdbc.update("UPDATE patients SET transporteur_aller_id = ?, transporteur_retour_id = ? WHERE id = ?", transporteur, transporteur, place);
        serologie(CENTRE_A, place, "AG_HBS", "POSITIF", "2026-02-01");
        patient(CENTRE_A, "ATTENTE_JOURS", "Attente", "Jours", "PERMANENT", matin, "mardi");
        patient(CENTRE_A, "ATTENTE_SANS_JOURS", "Attente", "SansJours", "PERMANENT", matin);
        UUID aChoisir = patient(CENTRE_A, "A_CHOISIR", "Attente", "AChoisir", "PERMANENT", null);
        jdbc.update("INSERT INTO planning_preference_patient (center_id, patient_id, creneau_prefere_id, seances_par_semaine, jours_a_choisir) "
                + "VALUES (?, ?, ?, 3, TRUE)", CENTRE_A, aChoisir, matin);
        UUID sortiAvant = patient(CENTRE_A, "SORTI_AVANT", "Sorti", "Avant", "PERMANENT", matin, "mardi");
        etat(sortiAvant, "TRANSFERE", semaine(-2, JourSemaine.LUNDI));
        UUID sortiApres = patient(CENTRE_A, "SORTI_APRES", "Sorti", "Apres", "PERMANENT", matin, "jeudi");
        etat(sortiApres, "TRANSFERE", semaine(3, JourSemaine.LUNDI));
        UUID enfant = patient(CENTRE_A, "ENFANT", "Enfant", "Eve", "PERMANENT", matin, "mercredi");
        jdbc.update("UPDATE patients SET date_naissance = DATE '2015-06-01' WHERE id = ?", enfant);
        UUID cathetre = patient(CENTRE_A, "CATHETER", "Cathe", "Ter", "PERMANENT", matin, "vendredi");
        jdbc.update("INSERT INTO abords_vasculaires (id, actif, center_id, patient_id, type_abord) VALUES (?, TRUE, ?, ?, 'KT_TUNNELISE')",
                UUID.randomUUID(), CENTRE_A, cathetre);
        UUID fistule = patient(CENTRE_A, "FISTULE", "Fistule", "Fred", "PERMANENT", matin, "samedi");
        jdbc.update("INSERT INTO abords_vasculaires (id, actif, center_id, patient_id, type_abord) VALUES (?, TRUE, ?, ?, 'FAV')",
                UUID.randomUUID(), CENTRE_A, fistule);
        UUID ktTermine = patient(CENTRE_A, "KT_RETIRE", "Retire", "Ray", "PERMANENT", matin, "dimanche");
        jdbc.update("INSERT INTO abords_vasculaires (id, actif, center_id, patient_id, type_abord, date_fin) VALUES (?, TRUE, ?, ?, "
                + "'KT_AIGU', ?)", UUID.randomUUID(), CENTRE_A, ktTermine, java.sql.Date.valueOf(semaine(0, JourSemaine.MERCREDI)));
        patient(CENTRE_B, "AUTRE_CENTRE", "Autre", "Centre", "PERMANENT", creneauB, "lundi");

        UUID g1b = generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        UUID pompe = equipement(CENTRE_A, "POMPE", "POMPE", salle1, "EN_SERVICE");
        intervention(CENTRE_A, g1a, "PLANIFIEE", semaine(1, JourSemaine.MERCREDI), null);
        intervention(CENTRE_A, g1b, "EN_COURS", semaine(0, JourSemaine.LUNDI), null);
        intervention(CENTRE_A, g1a, "PLANIFIEE", semaine(2, JourSemaine.LUNDI), semaine(2, JourSemaine.MERCREDI));
        intervention(CENTRE_A, g1a, "PLANIFIEE", semaine(0, JourSemaine.LUNDI), semaine(0, JourSemaine.MERCREDI));
        intervention(CENTRE_A, g1a, "TERMINEE", semaine(1, JourSemaine.MARDI), semaine(1, JourSemaine.MARDI));
        intervention(CENTRE_A, g1a, "PLANIFIEE", semaine(1, JourSemaine.JEUDI), semaine(1, JourSemaine.VENDREDI));
        jdbc.update("UPDATE gmao_interventions SET deleted_at = CURRENT_TIMESTAMP WHERE date_fin IS NOT NULL AND date_debut = ?",
                semaine(1, JourSemaine.JEUDI).atTime(8, 0).atOffset(java.time.ZoneOffset.UTC));
        intervention(CENTRE_A, pompe, "PLANIFIEE", semaine(1, JourSemaine.MERCREDI), null);
        deplacementTemporaire(CENTRE_A, place, semaine(1, JourSemaine.MARDI), salle1, matin, g1b);
        deplacementTemporaire(CENTRE_A, place, semaine(2, JourSemaine.MARDI), salle1, matin, g1b);
        UUID infirmier = inf(CENTRE_A, "INF_A", "Alpha", "Anne", false);
        jdbc.update("INSERT INTO infirmier_profil_planning (center_id, infirmier_id, taux_activite, competences) VALUES (?, ?, 50, "
                + "'PEDIATRIE')", CENTRE_A, infirmier);
        inf(CENTRE_A, "INF_SANS_PROFIL", "Beta", "Bob", false);

        DonneesOptimisation d = donnees.charger(CENTRE_A, du, au);
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> patients = new ArrayList<>();
        d.patients().forEach(p -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("nom", p.nom());
            x.put("jours", p.jours().stream().map(Enum::name).sorted().collect(Collectors.toList()));
            x.put("aRisque", p.aRisque());
            x.put("actuelle", p.actuelle() == null ? null : Map.of("salle", String.valueOf(p.actuelle().salleId()), "creneau",
                    String.valueOf(p.actuelle().creneauId()), "generateur", String.valueOf(p.actuelle().generateurId()),
                    "code", String.valueOf(p.actuelle().generateurCode())));
            x.put("premierJour", p.premierJour() == null ? null : p.premierJour().equals(LocalDate.of(2026, 1, 1)) ? "<ADMISSION>"
                    : p.premierJour().toString());
            x.put("dernierJour", p.dernierJour() == null ? null : p.dernierJour().toString());
            x.put("creneauPrefere", p.creneauPrefereId() == null ? null : p.creneauPrefereId().toString());
            x.put("seancesAChoisir", p.seancesAChoisir());
            x.put("transporteurs", p.transporteurs().stream().map(UUID::toString).sorted().collect(Collectors.toList()));
            x.put("competences", p.competencesRequises().stream().map(Enum::name).sorted().collect(Collectors.toList()));
            patients.add(x);
        });
        m.put("patients", patients);
        m.put("indisponibilites", d.indisponibilites().stream().map(i -> i.generateurId() + " " + i.debut() + " -> " + i.fin() + " "
                + i.motif()).sorted().collect(Collectors.toList()));
        m.put("temporaires", d.temporaires().stream().map(t -> t.patientId() + " " + t.date() + " " + t.generateurId() + " " + t.motif())
                .collect(Collectors.toList()));
        m.put("profils", d.profils().entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).sorted()
                .collect(Collectors.toList()));
        m.put("infirmiers", d.presence().infirmiers().size());
        arbre("donnees_lues", m);
        arbre("donnees_lues_par_un_autre_centre", donnees.charger(CENTRE_B, du, au).patients().stream().map(p -> p.nom()).toList());
    }

    // ------------------------------------------------------------------ déplacements temporaires

    @Test
    void un_deplacement_temporaire_par_patient_et_par_date_le_dernier_enregistre_remplace_le_precedent() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        UUID g1b = generateur(CENTRE_A, "G1B", salle1, "EN_SERVICE");
        UUID p1 = patient(CENTRE_A, "P1", "Un", "Patient", "PERMANENT", matin, "lundi");
        UUID p2 = patient(CENTRE_A, "P2", "Deux", "Patient", "PERMANENT", matin, "lundi");
        LocalDate lundi = semaine(1, JourSemaine.LUNDI);
        LocalDate mardi = semaine(1, JourSemaine.MARDI);
        Map<String, Object> m = new LinkedHashMap<>();
        temporaires.enregistrer(List.of(DeplacementTemporaire.creer(CENTRE_A, p1, lundi, salle1, matin, g1a, "PREMIER")));
        temporaires.enregistrer(List.of(DeplacementTemporaire.creer(CENTRE_A, p1, lundi, salle1, matin, g1b, "SECOND"),
                DeplacementTemporaire.creer(CENTRE_A, p2, lundi, salle1, matin, g1a, "AUTRE_PATIENT"),
                DeplacementTemporaire.creer(CENTRE_A, p1, mardi, salle1, matin, g1a, "AUTRE_JOUR")));
        etatDesDeplacementsTemporaires("apres_trois_enregistrements");
        temporaires.enregistrer(List.of(DeplacementTemporaire.creer(CENTRE_A, p2, lundi, salle1, matin, g1a, "DOUBLON_1"),
                DeplacementTemporaire.creer(CENTRE_A, p2, lundi, salle1, matin, g1b, "DOUBLON_2")));
        etatDesDeplacementsTemporaires("apres_un_doublon_dans_le_meme_appel");
        m.put("entre lundi et lundi", temporaires.entre(CENTRE_A, lundi, lundi).stream().map(DeplacementTemporaire::motif).toList());
        m.put("entre lundi et mardi", temporaires.entre(CENTRE_A, lundi, mardi).stream()
                .map(t -> t.date() + " " + t.motif()).toList());
        m.put("entre mardi et mardi", temporaires.entre(CENTRE_A, mardi, mardi).stream().map(DeplacementTemporaire::motif).toList());
        m.put("entre mercredi et jeudi", temporaires.entre(CENTRE_A, semaine(1, JourSemaine.MERCREDI), semaine(1, JourSemaine.JEUDI)).size());
        m.put("autre centre", temporaires.entre(CENTRE_B, lundi, mardi).size());
        m.put("liste vide enregistree", "sans effet");
        temporaires.enregistrer(List.of());
        m.put("lignes apres la liste vide", nombreDe("deplacement_temporaire"));
        arbre("lecture_des_deplacements_temporaires", m);
    }

    @Test
    void un_deplacement_temporaire_sans_place_complete_est_refuse_par_le_domaine() throws Exception {
        petitDecor();
        Map<String, String> m = new LinkedHashMap<>();
        UUID p = UUID.randomUUID();
        LocalDate jour = semaine(1, JourSemaine.LUNDI);
        m.put("sans generateur", essayer(() -> DeplacementTemporaire.creer(CENTRE_A, p, jour, salle1, matin, null, "x")));
        m.put("sans salle", essayer(() -> DeplacementTemporaire.creer(CENTRE_A, p, jour, null, matin, g1a, "x")));
        m.put("sans creneau", essayer(() -> DeplacementTemporaire.creer(CENTRE_A, p, jour, salle1, null, g1a, "x")));
        m.put("sans date", essayer(() -> DeplacementTemporaire.creer(CENTRE_A, p, null, salle1, matin, g1a, "x")));
        m.put("sans patient", essayer(() -> DeplacementTemporaire.creer(CENTRE_A, null, jour, salle1, matin, g1a, "x")));
        m.put("sans centre", essayer(() -> DeplacementTemporaire.creer(null, p, jour, salle1, matin, g1a, "x")));
        m.put("sans motif", essayer(() -> DeplacementTemporaire.creer(CENTRE_A, p, jour, salle1, matin, g1a, null)));
        arbre("deplacement_temporaire_invalide", m);
    }

    private static String essayer(java.util.function.Supplier<Object> action) {
        try {
            action.get();
            return "accepte";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName() + " : " + e.getMessage();
        }
    }
}
