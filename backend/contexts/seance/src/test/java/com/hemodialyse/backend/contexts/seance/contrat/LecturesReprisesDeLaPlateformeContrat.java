package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.GenerateursDesPatients;
import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des lectures que la plateforme fournissait à la Séance et que la Séance tient elle-même depuis SE7f (ADR 0100) : historique,
 * forfait courant, référentiel des forfaits, planning du patient, place du patient, générateur. Joué sur H2 (mode PostgreSQL) ET sur un
 * PostgreSQL 16 jetable (sous-classe {@code ...PostgresContractIT}, schéma dédié aux colonnes lues : les noms réels de colonnes sont
 * contrôlés par {@code seance-se7f-pg16-rejeu.sh} contre le schéma Flyway). Les anciens cas du contrat de plateforme
 * {@code LecturesDeLaSeanceContrat} sont repris ; les doubles en mémoire sont ceux du banc d'application.
 */
abstract class LecturesReprisesDeLaPlateformeContrat {

    static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    static final CentreId AUTRE_CENTRE = new CentreId(new UUID(0, 0xB));
    static final PatientId ALPHA = new PatientId(new UUID(1, 1));
    static final PatientId BRAVO = new PatientId(new UUID(1, 2));
    static final LocalDate JOUR = LocalDate.of(2026, 6, 14); // un dimanche
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC);

    abstract JdbcTemplate jdbc();

    abstract HistoriqueDesSeances historique();

    abstract ForfaitsDeSeance forfaits();

    abstract ForfaitsDuCentre referentiel();

    abstract PlanningDesPatients planning();

    abstract GenerateursDesPatients generateurs();

    /** La place habituelle publiée par la Planification est un double : la Séance ne lit que le déplacement temporaire. */
    abstract PlaceDuPatient place(PlacesDesPatients places);

    @BeforeEach
    void schema() {
        JdbcTemplate j = jdbc();
        for (String t : List.of("seances", "prise_en_charge", "forfait", "patients", "center_holiday", "center_closure_day",
                "deplacement_temporaire", "gmao_equipements")) {
            j.execute("DROP TABLE IF EXISTS " + t);
        }
        j.execute("CREATE TABLE patients (id uuid PRIMARY KEY, center_id uuid, nom varchar(100), prenom varchar(100), code_patient varchar(50), "
                + "sexe varchar(10), date_naissance date, en_sommeil boolean, etat_patient varchar(30), date_evenement_etat date, date_admission date, jour_dimanche boolean, "
                + "jour_lundi boolean, jour_mardi boolean, jour_mercredi boolean, jour_jeudi boolean, jour_vendredi boolean, jour_samedi boolean)");
        j.execute("CREATE TABLE seances (id uuid PRIMARY KEY, center_id uuid, patient_id uuid, date_seance date, statut varchar(20), "
                + "created_at timestamp with time zone, validated_at timestamp with time zone, signed_infirmier_at timestamp with time zone, "
                + "signed_medecin_at timestamp with time zone, regularisation_deverrouillee_at timestamp with time zone, hors_planning boolean, "
                + "motif_hors_planning varchar(20), forfait_override_id uuid, forfait_override_code varchar(50), forfait_override_nom varchar(100), "
                + "forfait_override_prix numeric(12,2), forfait_override_updated_at timestamp with time zone, forfait_override_updated_by varchar(100))");
        j.execute("CREATE TABLE forfait (id uuid PRIMARY KEY, center_id uuid, code varchar(50), libelle varchar(100), prix numeric(12,2))");
        j.execute("CREATE TABLE prise_en_charge (id uuid PRIMARY KEY, center_id uuid, patient_id uuid, forfait_effectif_id uuid, "
                + "forfait_demande_id uuid, date_debut_effectif date, date_fin_effectif date, date_debut_demande date, date_fin_demande date, "
                + "created_at timestamp with time zone)");
        j.execute("CREATE TABLE center_holiday (id uuid PRIMARY KEY, center_id uuid, day_date date, label varchar(255))");
        j.execute("CREATE TABLE center_closure_day (id uuid PRIMARY KEY, center_id uuid, day_date date, reason varchar(255))");
        j.execute("CREATE TABLE deplacement_temporaire (id uuid PRIMARY KEY, center_id uuid, patient_id uuid, date_seance date, salle_id uuid, "
                + "creneau_id uuid, generateur_id uuid)");
        j.execute("CREATE TABLE gmao_equipements (id uuid PRIMARY KEY, centre_id uuid, code varchar(50), fabricant varchar(100), statut varchar(30), "
                + "deleted_at timestamp with time zone)");
        patient(CENTRE, ALPHA, "Alpha", "Anne", "SE1-ALPHA");
        patient(CENTRE, BRAVO, "Bravo", "Paul", "SE1-BRAVO");
    }

    // ---- amorçage

    void patient(CentreId centre, PatientId id, String nom, String prenom, String code) {
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, code_patient, jour_dimanche, date_admission) VALUES (?,?,?,?,?,TRUE,?)",
                id.valeur(), centre.valeur(), nom, prenom, code, Date.valueOf(JOUR.minusDays(30)));
    }

    SeanceId seance(CentreId centre, PatientId patient, LocalDate jour, String statut, OffsetDateTime creeLe) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at, hors_planning) VALUES (?,?,?,?,?,?,FALSE)",
                id, centre.valeur(), patient.valeur(), Date.valueOf(jour), statut, creeLe);
        return new SeanceId(id);
    }

    UUID forfait(CentreId centre, String code, String prix) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,?)", id, centre.valeur(), code, "Forfait " + code,
                new BigDecimal(prix));
        return id;
    }

    void priseEnCharge(CentreId centre, PatientId patient, UUID effectif, UUID demande, LocalDate debutEffectif, LocalDate finEffectif,
                       LocalDate debutDemande, LocalDate finDemande, OffsetDateTime creeLe) {
        jdbc().update("INSERT INTO prise_en_charge (id, center_id, patient_id, forfait_effectif_id, forfait_demande_id, date_debut_effectif, "
                        + "date_fin_effectif, date_debut_demande, date_fin_demande, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre.valeur(), patient.valeur(), effectif, demande, sql(debutEffectif), sql(finEffectif), sql(debutDemande),
                sql(finDemande), creeLe);
    }

    private static Date sql(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    private HistoriqueDesSeances.LigneDeSeance premiere(RechercheDeSeances r) {
        return historique().rechercher(CENTRE, r, new Pagination(0, 20)).items().getFirst();
    }

    private static RechercheDeSeances recherche(Set<StatutDeSeance> statuts, String texte, String ordre, boolean decroissant,
                                               Boolean deverrouillee, LocalDate du, LocalDate au) {
        return new RechercheDeSeances(du, au, statuts, texte, RechercheDeSeances.OrdreDeTri.lire(ordre), decroissant, deverrouillee);
    }

    private long total(RechercheDeSeances r) {
        return historique().rechercher(CENTRE, r, new Pagination(0, 20)).total();
    }

    // ---- historique

    @Test
    void l_historique_est_trie_par_date_decroissante_puis_creation_decroissante_puis_identifiant() {
        SeanceId a = seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        SeanceId b = seance(CENTRE, BRAVO, JOUR, "VALIDEE", T0.plusHours(1));
        SeanceId c = seance(CENTRE, ALPHA, JOUR.plusDays(1), "SIGNEE", T0);

        var page = historique().rechercher(CENTRE, RechercheDeSeances.toutes(), new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(c, b, a);
    }

    @Test
    void la_ligne_porte_l_identite_du_patient_les_jalons_et_le_hors_planning() {
        SeanceId id = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        jdbc().update("UPDATE seances SET validated_at = ?, hors_planning = TRUE, motif_hors_planning = 'URGENCE' WHERE id = ?", T0.plusMinutes(5),
                id.valeur());

        var ligne = premiere(RechercheDeSeances.toutes());

        assertThat(ligne.centre()).isEqualTo(CENTRE);
        assertThat(ligne.patient()).isEqualTo(ALPHA);
        assertThat(ligne.codePatient()).isEqualTo("SE1-ALPHA");
        assertThat(ligne.nomDuPatient()).isEqualTo("Alpha");
        assertThat(ligne.prenomDuPatient()).isEqualTo("Anne");
        assertThat(ligne.statut()).isEqualTo(StatutDeSeance.VALIDEE);
        assertThat(ligne.valideeLe()).isNotNull();
        assertThat(ligne.signeeParLeMedecinLe()).isNull();
        assertThat(ligne.horsPlanning()).isTrue();
        assertThat(ligne.motifHorsPlanning()).isEqualTo(MotifHorsPlanning.URGENCE);
    }

    @Test
    void les_filtres_de_periode_de_statut_de_deverrouillage_et_de_texte_se_cumulent() {
        SeanceId a = seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        SeanceId b = seance(CENTRE, BRAVO, JOUR.plusDays(2), "VALIDEE", T0);
        seance(CENTRE, ALPHA, JOUR.plusDays(5), "VALIDEE", T0);
        jdbc().update("UPDATE seances SET regularisation_deverrouillee_at = ? WHERE id = ?", T0, a.valeur());

        assertThat(total(recherche(Set.of(), null, "date", true, null, JOUR, JOUR.plusDays(2)))).isEqualTo(2);
        assertThat(total(recherche(Set.of(StatutDeSeance.VALIDEE), null, "date", true, null, null, null))).isEqualTo(2);
        assertThat(historique().rechercher(CENTRE, recherche(Set.of(StatutDeSeance.CREE, StatutDeSeance.VALIDEE), null, "date", true, true,
                null, null), new Pagination(0, 20)).items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(a);
        assertThat(total(recherche(Set.of(), null, "date", true, false, null, null))).isEqualTo(2);
        assertThat(historique().rechercher(CENTRE, recherche(Set.of(), "bravo paul", "date", true, null, null, null), new Pagination(0, 20)).items())
                .extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(b);
        assertThat(total(recherche(Set.of(), "se1-alpha", "date", true, null, null, null))).isEqualTo(2);
        assertThat(total(recherche(Set.of(StatutDeSeance.ABSENT), null, "date", true, null, null, null))).isZero();
    }

    @Test
    void les_jokers_du_texte_libre_sont_litteraux() {
        seance(CENTRE, ALPHA, JOUR, "CREE", T0);

        assertThat(total(recherche(Set.of(), "%", "date", true, null, null, null))).isZero();
        assertThat(total(recherche(Set.of(), "_", "date", true, null, null, null))).isZero();
        assertThat(total(recherche(Set.of(), "alph_", "date", true, null, null, null))).isZero();
    }

    @Test
    void les_tris_en_liste_blanche_et_la_pagination_sont_bornes() {
        SeanceId a = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0.plusHours(2));
        SeanceId b = seance(CENTRE, BRAVO, JOUR.plusDays(1), "CREE", T0.plusHours(1));
        SeanceId c = seance(CENTRE, ALPHA, JOUR.plusDays(2), "SIGNEE", T0);

        assertThat(historique().rechercher(CENTRE, recherche(Set.of(), null, "patientNom", false, null, null, null), new Pagination(0, 20)).items()
                .getLast().patient()).isEqualTo(BRAVO);
        assertThat(historique().rechercher(CENTRE, recherche(Set.of(), null, "patientCode", false, null, null, null), new Pagination(0, 20)).items()
                .getFirst().patient()).isEqualTo(ALPHA);
        assertThat(historique().rechercher(CENTRE, recherche(Set.of(), null, "status", false, null, null, null), new Pagination(0, 20)).items())
                .extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(b, c, a);
        assertThat(historique().rechercher(CENTRE, recherche(Set.of(), null, "createdAt", true, null, null, null), new Pagination(0, 20)).items())
                .extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(a, b, c);
        var deuxieme = historique().rechercher(CENTRE, recherche(Set.of(), null, "date", false, null, null, null), new Pagination(1, 2));
        assertThat(deuxieme.total()).isEqualTo(3);
        assertThat(deuxieme.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(c);
    }

    @Test
    void l_ordre_par_patient_departage_les_homonymes_par_creation_decroissante_et_ne_porte_que_sur_les_patients_du_centre() {
        PatientId premierAlpha = new PatientId(new UUID(1, 3));
        patient(CENTRE, premierAlpha, "Alpha", "Anne", "SE1-ALPHA2");
        PatientId etranger = new PatientId(new UUID(1, 4));
        patient(AUTRE_CENTRE, etranger, "Aaron", "Zoe", "SE1-AARON");
        SeanceId a1 = seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        SeanceId a2 = seance(CENTRE, premierAlpha, JOUR, "CREE", T0.plusHours(3));
        SeanceId a3 = seance(CENTRE, ALPHA, JOUR.plusDays(1), "CREE", T0.plusHours(2));
        SeanceId b1 = seance(CENTRE, BRAVO, JOUR, "CREE", T0.plusHours(5));
        seance(CENTRE, etranger, JOUR, "CREE", T0.plusHours(9));

        var croissant = historique().rechercher(CENTRE, recherche(Set.of(), null, "patientNom", false, null, null, null), new Pagination(0, 20));
        var decroissant = historique().rechercher(CENTRE, recherche(Set.of(), null, "patientNom", true, null, null, null), new Pagination(0, 20));
        var parCode = historique().rechercher(CENTRE, recherche(Set.of(), null, "patientCode", false, null, null, null), new Pagination(0, 20));
        var deuxiemePage = historique().rechercher(CENTRE, recherche(Set.of(), null, "patientNom", false, null, null, null), new Pagination(1, 2));

        assertThat(croissant.total()).isEqualTo(4);
        assertThat(croissant.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id)
                .as("les deux Alpha Anne sont à égalité : création décroissante").containsExactly(a2, a3, a1, b1);
        assertThat(decroissant.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(b1, a2, a3, a1);
        assertThat(parCode.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(a3, a1, a2, b1);
        assertThat(deuxiemePage.total()).isEqualTo(4);
        assertThat(deuxiemePage.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(a1, b1);
    }

    @Test
    void le_texte_libre_ne_retient_que_les_seances_des_patients_du_centre_qui_le_contiennent() {
        PatientId etranger = new PatientId(new UUID(1, 4));
        patient(AUTRE_CENTRE, etranger, "Alpha", "Anne", "SE1-ALPHA");
        SeanceId duCentre = seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        seance(CENTRE, etranger, JOUR, "CREE", T0);
        seance(CENTRE, BRAVO, JOUR, "CREE", T0);

        var page = historique().rechercher(CENTRE, recherche(Set.of(), "alpha", "date", true, null, null, null), new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(duCentre);
    }

    @Test
    void une_seance_d_un_autre_centre_ou_d_un_patient_disparu_est_absente_et_un_statut_illisible_est_exclu_du_total() {
        seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        seance(AUTRE_CENTRE, ALPHA, JOUR, "CREE", T0);
        seance(CENTRE, new PatientId(new UUID(9, 9)), JOUR, "CREE", T0);
        seance(CENTRE, BRAVO, JOUR, "EN_COURS", T0);

        var page = historique().rechercher(CENTRE, RechercheDeSeances.toutes(), new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().extracting(HistoriqueDesSeances.LigneDeSeance::patient).isEqualTo(ALPHA);
    }

    @Test
    void une_ligne_au_motif_de_hors_planning_illisible_est_ignoree_mais_comptee() {
        SeanceId lisible = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        SeanceId illisible = seance(CENTRE, BRAVO, JOUR, "VALIDEE", T0.plusHours(1));
        jdbc().update("UPDATE seances SET hors_planning = TRUE, motif_hors_planning = 'INCONNU' WHERE id = ?", illisible.valeur());

        var page = historique().rechercher(CENTRE, RechercheDeSeances.toutes(), new Pagination(0, 20));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(HistoriqueDesSeances.LigneDeSeance::id).containsExactly(lisible);
    }

    // ---- forfait courant

    @Test
    void le_forfait_impose_sur_la_seance_prime_sur_celui_de_la_prise_en_charge() {
        UUID impose = forfait(CENTRE, "IMPOSE", "10.00");
        UUID couvrant = forfait(CENTRE, "PEC", "20.00");
        SeanceId seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, couvrant, null, JOUR.minusDays(5), null, null, null, T0);
        jdbc().update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = 'IMPOSE', forfait_override_nom = 'Impose', "
                + "forfait_override_prix = 10.00, forfait_override_updated_at = ?, forfait_override_updated_by = 'admin' WHERE id = ?", impose, T0,
                seance.valeur());

        var forfait = forfaits().courant(CENTRE, seance, ALPHA, JOUR).orElseThrow();

        assertThat(forfait.surcharge()).isTrue();
        assertThat(forfait.id()).isEqualTo(impose);
        assertThat(forfait.code()).isEqualTo("IMPOSE");
        assertThat(forfait.nombreDeSeances()).isNull();
        assertThat(forfait.misAJourPar()).isEqualTo("admin");
        assertThat(forfait.misAJourLe()).isNotNull();
    }

    @Test
    void la_prise_en_charge_effective_qui_couvre_le_jour_prime_sur_la_demandee() {
        UUID effectif = forfait(CENTRE, "EFFECTIF", "30.00");
        UUID demande = forfait(CENTRE, "DEMANDE", "40.00");
        SeanceId seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, null, demande, null, null, JOUR.minusDays(1), null, T0.plusHours(1));
        priseEnCharge(CENTRE, ALPHA, effectif, demande, JOUR.minusDays(1), JOUR.plusDays(1), null, null, T0);

        var forfait = forfaits().courant(CENTRE, seance, ALPHA, JOUR).orElseThrow();

        assertThat(forfait.surcharge()).isFalse();
        assertThat(forfait.code()).isEqualTo("EFFECTIF");
        assertThat(forfait.prix()).isEqualByComparingTo("30.00");
        assertThat(forfait.nombreDeSeances()).isZero();
        assertThat(forfait.misAJourLe()).isNull();
    }

    @Test
    void la_prise_en_charge_demandee_couvre_le_jour_quand_aucune_n_est_effective() {
        UUID demande = forfait(CENTRE, "DEMANDE", "40.00");
        SeanceId seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, null, demande, null, null, JOUR, JOUR, T0);

        assertThat(forfaits().courant(CENTRE, seance, ALPHA, JOUR)).get().extracting(ForfaitsDeSeance.ForfaitCourant::code).isEqualTo("DEMANDE");
    }

    @Test
    void a_defaut_de_prise_en_charge_couvrant_le_jour_la_plus_recente_qui_porte_un_forfait_est_retenue() {
        UUID ancien = forfait(CENTRE, "ANCIEN", "50.00");
        UUID recent = forfait(CENTRE, "RECENT", "60.00");
        SeanceId seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, ancien, null, JOUR.minusDays(90), JOUR.minusDays(60), null, null, T0);
        priseEnCharge(CENTRE, ALPHA, recent, null, JOUR.minusDays(50), JOUR.minusDays(20), null, null, T0.plusHours(1));
        priseEnCharge(CENTRE, ALPHA, null, null, null, null, null, null, T0.plusHours(2));

        assertThat(forfaits().courant(CENTRE, seance, ALPHA, JOUR)).get().extracting(ForfaitsDeSeance.ForfaitCourant::code).isEqualTo("RECENT");
    }

    @Test
    void sans_forfait_ni_prise_en_charge_ou_dans_un_autre_centre_il_n_y_a_pas_de_forfait() {
        SeanceId seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        UUID ailleurs = forfait(AUTRE_CENTRE, "AILLEURS", "70.00");
        priseEnCharge(AUTRE_CENTRE, ALPHA, ailleurs, null, JOUR.minusDays(1), null, null, null, T0);

        assertThat(forfaits().courant(CENTRE, seance, ALPHA, JOUR)).isEmpty();
    }

    // ---- référentiel des forfaits (SE5, ADR 0092)

    @Test
    void le_referentiel_fournit_le_forfait_du_centre_avec_son_code_son_libelle_devenu_le_nom_et_son_prix_tels_quels() {
        UUID forfait = forfait(CENTRE, "F1", "250.50");

        var lu = referentiel().trouver(CENTRE, new ForfaitId(forfait)).orElseThrow();

        assertThat(lu.id()).isEqualTo(new ForfaitId(forfait));
        assertThat(lu.code()).isEqualTo("F1");
        assertThat(lu.nom()).isEqualTo("Forfait F1");
        assertThat(lu.prix()).isEqualByComparingTo("250.50");
    }

    @Test
    void le_referentiel_fournit_un_forfait_sans_prix_avec_un_prix_absent_et_ignore_l_inconnu_ou_l_autre_centre() {
        UUID sansPrix = UUID.randomUUID();
        jdbc().update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,NULL)", sansPrix, CENTRE.valeur(), "SANS_PRIX", "Sans prix");
        UUID ailleurs = forfait(AUTRE_CENTRE, "AILLEURS", "70.00");

        assertThat(referentiel().trouver(CENTRE, new ForfaitId(sansPrix)).orElseThrow().prix()).isNull();
        assertThat(referentiel().trouver(CENTRE, new ForfaitId(ailleurs))).isEmpty();
        assertThat(referentiel().trouver(CENTRE, new ForfaitId(UUID.randomUUID()))).isEmpty();
    }

    // ---- planning du patient (SE1 scan ; KB-SEA-95)

    @Test
    void la_situation_lit_le_jour_de_dialyse_le_sommeil_l_etat_et_les_dates_de_la_fiche_au_centre() {
        jdbc().update("UPDATE patients SET en_sommeil = NULL, etat_patient = 'TRANSFERE', date_evenement_etat = ? WHERE id = ?",
                Date.valueOf(JOUR.plusDays(3)), ALPHA.valeur());

        var s = planning().situation(CENTRE, ALPHA, JOUR);

        assertThat(s.centreFerme()).isFalse();
        assertThat(s.jourDeDialyse()).isTrue();
        assertThat(s.enSommeil()).isFalse();
        assertThat(s.etatDuPatient()).isEqualTo("TRANSFERE");
        assertThat(s.dateDeLEtat()).isEqualTo(JOUR.plusDays(3));
        assertThat(s.dateDAdmission()).isEqualTo(JOUR.minusDays(30));
        assertThat(planning().situation(CENTRE, ALPHA, JOUR.plusDays(1)).jourDeDialyse()).as("lundi non coché").isFalse();
    }

    @Test
    void un_jour_ferie_ou_une_fermeture_du_centre_ferme_le_centre_a_cette_date_seulement_et_dans_ce_centre_seulement() {
        jdbc().update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", UUID.randomUUID(), CENTRE.valeur(),
                Date.valueOf(JOUR), "Ferie");
        jdbc().update("INSERT INTO center_closure_day (id, center_id, day_date, reason) VALUES (?,?,?,?)", UUID.randomUUID(), CENTRE.valeur(),
                Date.valueOf(JOUR.plusDays(2)), "Fermeture");
        jdbc().update("INSERT INTO center_holiday (id, center_id, day_date, label) VALUES (?,?,?,?)", UUID.randomUUID(), AUTRE_CENTRE.valeur(),
                Date.valueOf(JOUR.plusDays(1)), "Ferie ailleurs");

        assertThat(planning().situation(CENTRE, ALPHA, JOUR).centreFerme()).as("ferie").isTrue();
        assertThat(planning().situation(CENTRE, ALPHA, JOUR.plusDays(2)).centreFerme()).as("fermeture").isTrue();
        assertThat(planning().situation(CENTRE, ALPHA, JOUR.plusDays(1)).centreFerme()).as("ferie d'un autre centre").isFalse();
        assertThat(planning().situation(CENTRE, ALPHA, JOUR.minusDays(1)).centreFerme()).as("autre date").isFalse();
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_introuvable_pour_le_planning() {
        assertThatThrownBy(() -> planning().situation(AUTRE_CENTRE, ALPHA, JOUR)).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> planning().situation(CENTRE, new PatientId(new UUID(9, 9)), JOUR)).isInstanceOf(PatientIntrouvable.class);
    }

    // ---- place du patient (SE2 validation ; P6)

    @Test
    void le_deplacement_temporaire_du_jour_au_centre_prime_sur_la_place_de_la_fiche() {
        UUID salle = new UUID(3, 1);
        UUID creneau = new UUID(4, 1);
        jdbc().update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), CENTRE.valeur(), ALPHA.valeur(), Date.valueOf(JOUR), salle, creneau, new UUID(5, 1));
        jdbc().update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), AUTRE_CENTRE.valeur(), ALPHA.valeur(), Date.valueOf(JOUR.plusDays(1)), salle, creneau, null);
        PlacesDesPatients fiche = placesDeLaFiche(Optional.of(new PlacesDesPatients.PlaceDuPatient(ALPHA.valeur(), new UUID(3, 9), new UUID(4, 9), null, Set.of())));

        assertThat(place(fiche).placeLe(CENTRE, ALPHA, JOUR)).contains(new PlaceDeSeance(salle, creneau, new UUID(5, 1)));
        assertThat(place(fiche).placeLe(CENTRE, ALPHA, JOUR.plusDays(1))).as("deplacement d'un autre centre ignore : place de la fiche")
                .contains(new PlaceDeSeance(new UUID(3, 9), new UUID(4, 9), null));
    }

    @Test
    void sans_deplacement_la_place_de_la_fiche_est_rendue_et_une_place_incomplete_est_vide() {
        assertThat(place(placesDeLaFiche(Optional.of(new PlacesDesPatients.PlaceDuPatient(ALPHA.valeur(), new UUID(3, 9), null, null, Set.of()))))
                .placeLe(CENTRE, ALPHA, JOUR)).isEmpty();
        assertThat(place(placesDeLaFiche(Optional.empty())).placeLe(CENTRE, ALPHA, JOUR)).isEmpty();
        jdbc().update("INSERT INTO deplacement_temporaire (id, center_id, patient_id, date_seance, salle_id, creneau_id, generateur_id) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), CENTRE.valeur(), ALPHA.valeur(), Date.valueOf(JOUR), new UUID(3, 1), null, null);
        assertThat(place(placesDeLaFiche(Optional.empty())).placeLe(CENTRE, ALPHA, JOUR)).as("deplacement sans creneau").isEmpty();
    }

    private static PlacesDesPatients placesDeLaFiche(Optional<PlacesDesPatients.PlaceDuPatient> place) {
        return new PlacesDesPatients() {
            @Override
            public Optional<PlaceDuPatient> deLaFiche(UUID centre, UUID patient) {
                return place;
            }

            @Override
            public List<PlaceDuPatient> duCentre(UUID centre, LocalDate le) {
                return List.of();
            }
        };
    }

    // ---- générateur du patient (GMAO)

    @Test
    void le_generateur_est_lu_au_centre_hors_equipement_supprime() {
        UUID g = UUID.randomUUID();
        UUID supprime = UUID.randomUUID();
        UUID ailleurs = UUID.randomUUID();
        jdbc().update("INSERT INTO gmao_equipements (id, centre_id, code, fabricant, statut, deleted_at) VALUES (?,?,?,?,?,NULL)", g, CENTRE.valeur(),
                "G1", "Fresenius", "EN_SERVICE");
        jdbc().update("INSERT INTO gmao_equipements (id, centre_id, code, fabricant, statut, deleted_at) VALUES (?,?,?,?,?,?)", supprime,
                CENTRE.valeur(), "G2", "Baxter", "REFORME", T0);
        jdbc().update("INSERT INTO gmao_equipements (id, centre_id, code, fabricant, statut, deleted_at) VALUES (?,?,?,?,?,NULL)", ailleurs,
                AUTRE_CENTRE.valeur(), "G3", "Nipro", "EN_SERVICE");

        assertThat(generateurs().duCentre(CENTRE, g)).contains(new GenerateursDesPatients.Generateur(g, "G1", "Fresenius", "EN_SERVICE"));
        assertThat(generateurs().duCentre(CENTRE, supprime)).isEmpty();
        assertThat(generateurs().duCentre(CENTRE, ailleurs)).isEmpty();
    }
}
