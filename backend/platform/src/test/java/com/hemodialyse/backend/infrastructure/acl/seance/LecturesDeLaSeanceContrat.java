package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ReferentielDeForfaitsFourni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des deux lectures SQL que la plateforme fournit à la Séance (historique et forfait courant), joué sur H2 (mode PostgreSQL)
 * ET sur un PostgreSQL 16 jetable (sous-classe {@code ...PostgresContractIT}, schéma dédié aux colonnes lues : les noms réels de
 * colonnes sont contrôlés par le script {@code seance-se1-pg16-rejeu.sh} contre le schéma Flyway). Le double en mémoire de ces deux
 * ports est celui de l'application ({@code Banc}), prouvé par les tests d'application.
 */
abstract class LecturesDeLaSeanceContrat {

    static final UUID CENTRE = new UUID(0, 0xA);
    static final UUID AUTRE_CENTRE = new UUID(0, 0xB);
    static final UUID ALPHA = new UUID(1, 1);
    static final UUID BRAVO = new UUID(1, 2);
    static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 6, 15, 8, 0, 0, 0, ZoneOffset.UTC);

    abstract JdbcTemplate jdbc();

    @BeforeEach
    void schema() {
        JdbcTemplate j = jdbc();
        for (String t : List.of("seances", "prise_en_charge", "forfait", "patients")) j.execute("DROP TABLE IF EXISTS " + t);
        j.execute("CREATE TABLE patients (id uuid PRIMARY KEY, center_id uuid, nom varchar(100), prenom varchar(100), code_patient varchar(50))");
        j.execute("CREATE TABLE seances (id uuid PRIMARY KEY, center_id uuid, patient_id uuid, date_seance date, statut varchar(20), "
                + "created_at timestamp with time zone, validated_at timestamp with time zone, signed_infirmier_at timestamp with time zone, "
                + "signed_medecin_at timestamp with time zone, regularisation_deverrouillee_at timestamp with time zone, hors_planning boolean, "
                + "motif_hors_planning varchar(20), forfait_override_id uuid, forfait_override_code varchar(50), forfait_override_nom varchar(100), "
                + "forfait_override_prix numeric(12,2), forfait_override_updated_at timestamp with time zone, forfait_override_updated_by varchar(100))");
        j.execute("CREATE TABLE forfait (id uuid PRIMARY KEY, center_id uuid, code varchar(50), libelle varchar(100), prix numeric(12,2))");
        j.execute("CREATE TABLE prise_en_charge (id uuid PRIMARY KEY, center_id uuid, patient_id uuid, forfait_effectif_id uuid, "
                + "forfait_demande_id uuid, date_debut_effectif date, date_fin_effectif date, date_debut_demande date, date_fin_demande date, "
                + "created_at timestamp with time zone)");
        patient(CENTRE, ALPHA, "Alpha", "Anne", "SE1-ALPHA");
        patient(CENTRE, BRAVO, "Bravo", "Paul", "SE1-BRAVO");
    }

    // ---- amorçage

    void patient(UUID centre, UUID id, String nom, String prenom, String code) {
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, code_patient) VALUES (?,?,?,?,?)", id, centre, nom, prenom, code);
    }

    UUID seance(UUID centre, UUID patient, LocalDate jour, String statut, OffsetDateTime creeLe) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO seances (id, center_id, patient_id, date_seance, statut, created_at, hors_planning) VALUES (?,?,?,?,?,?,FALSE)",
                id, centre, patient, Date.valueOf(jour), statut, creeLe);
        return id;
    }

    UUID forfait(UUID centre, String code, String prix) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,?)", id, centre, code, "Forfait " + code,
                new BigDecimal(prix));
        return id;
    }

    void priseEnCharge(UUID centre, UUID patient, UUID effectif, UUID demande, LocalDate debutEffectif, LocalDate finEffectif,
                       LocalDate debutDemande, LocalDate finDemande, OffsetDateTime creeLe) {
        jdbc().update("INSERT INTO prise_en_charge (id, center_id, patient_id, forfait_effectif_id, forfait_demande_id, date_debut_effectif, "
                        + "date_fin_effectif, date_debut_demande, date_fin_demande, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), centre, patient, effectif, demande, sql(debutEffectif), sql(finEffectif), sql(debutDemande), sql(finDemande), creeLe);
    }

    private static Date sql(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    abstract HistoriqueFourni historique();

    abstract ForfaitsFournis forfaits();

    abstract ReferentielDeForfaitsFourni referentiel();

    private HistoriqueFourni.Page tout(Set<String> statuts, String texte, String ordre, boolean decroissant, Boolean deverrouillee,
                                       LocalDate du, LocalDate au, int page, int taille) {
        return historique().rechercher(CENTRE, du, au, statuts, texte, ordre, decroissant, deverrouillee, page, taille);
    }

    // ---- historique

    @Test
    void l_historique_est_trie_par_date_decroissante_puis_creation_decroissante_puis_identifiant() {
        UUID a = seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        UUID b = seance(CENTRE, BRAVO, JOUR, "VALIDEE", T0.plusHours(1));
        UUID c = seance(CENTRE, ALPHA, JOUR.plusDays(1), "SIGNEE", T0);

        var page = tout(Set.of(), null, "DATE", true, null, null, null, 0, 20);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.lignes()).extracting(HistoriqueFourni.Ligne::id).containsExactly(c, b, a);
    }

    @Test
    void la_ligne_porte_l_identite_du_patient_les_jalons_et_le_hors_planning() {
        UUID id = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        jdbc().update("UPDATE seances SET validated_at = ?, hors_planning = TRUE, motif_hors_planning = 'URGENCE' WHERE id = ?", T0.plusMinutes(5), id);

        var ligne = tout(Set.of(), null, "DATE", true, null, null, null, 0, 20).lignes().getFirst();

        assertThat(ligne.centre()).isEqualTo(CENTRE);
        assertThat(ligne.patient()).isEqualTo(ALPHA);
        assertThat(ligne.codePatient()).isEqualTo("SE1-ALPHA");
        assertThat(ligne.nomDuPatient()).isEqualTo("Alpha");
        assertThat(ligne.prenomDuPatient()).isEqualTo("Anne");
        assertThat(ligne.statut()).isEqualTo("VALIDEE");
        assertThat(ligne.valideeLe()).isNotNull();
        assertThat(ligne.signeeParLeMedecinLe()).isNull();
        assertThat(ligne.horsPlanning()).isTrue();
        assertThat(ligne.motifHorsPlanning()).isEqualTo("URGENCE");
    }

    @Test
    void les_filtres_de_periode_de_statut_de_deverrouillage_et_de_texte_se_cumulent() {
        UUID a = seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        UUID b = seance(CENTRE, BRAVO, JOUR.plusDays(2), "VALIDEE", T0);
        seance(CENTRE, ALPHA, JOUR.plusDays(5), "VALIDEE", T0);
        jdbc().update("UPDATE seances SET regularisation_deverrouillee_at = ? WHERE id = ?", T0, a);

        assertThat(tout(Set.of(), null, "DATE", true, null, JOUR, JOUR.plusDays(2), 0, 20).total()).isEqualTo(2);
        assertThat(tout(Set.of("VALIDEE"), null, "DATE", true, null, null, null, 0, 20).total()).isEqualTo(2);
        assertThat(tout(Set.of("CREE", "VALIDEE"), null, "DATE", true, true, null, null, 0, 20).lignes())
                .extracting(HistoriqueFourni.Ligne::id).containsExactly(a);
        assertThat(tout(Set.of(), null, "DATE", true, false, null, null, 0, 20).total()).isEqualTo(2);
        assertThat(tout(Set.of(), "bravo paul", "DATE", true, null, null, null, 0, 20).lignes())
                .extracting(HistoriqueFourni.Ligne::id).containsExactly(b);
        assertThat(tout(Set.of(), "se1-alpha", "DATE", true, null, null, null, 0, 20).total()).isEqualTo(2);
        assertThat(tout(Set.of("ABSENT"), null, "DATE", true, null, null, null, 0, 20).total()).isZero();
    }

    @Test
    void les_jokers_du_texte_libre_sont_litteraux() {
        seance(CENTRE, ALPHA, JOUR, "CREE", T0);

        assertThat(tout(Set.of(), "%", "DATE", true, null, null, null, 0, 20).total()).isZero();
        assertThat(tout(Set.of(), "_", "DATE", true, null, null, null, 0, 20).total()).isZero();
        assertThat(tout(Set.of(), "alph_", "DATE", true, null, null, null, 0, 20).total()).isZero();
    }

    @Test
    void les_tris_en_liste_blanche_et_la_pagination_sont_bornes() {
        UUID a = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0.plusHours(2));
        UUID b = seance(CENTRE, BRAVO, JOUR.plusDays(1), "CREE", T0.plusHours(1));
        UUID c = seance(CENTRE, ALPHA, JOUR.plusDays(2), "SIGNEE", T0);

        assertThat(tout(Set.of(), null, "PATIENT", false, null, null, null, 0, 20).lignes().getLast().patient()).isEqualTo(BRAVO);
        assertThat(tout(Set.of(), null, "CODE", false, null, null, null, 0, 20).lignes().getFirst().patient()).isEqualTo(ALPHA);
        assertThat(tout(Set.of(), null, "STATUT", false, null, null, null, 0, 20).lignes()).extracting(HistoriqueFourni.Ligne::id)
                .containsExactly(b, c, a);
        assertThat(tout(Set.of(), null, "CREATION", true, null, null, null, 0, 20).lignes()).extracting(HistoriqueFourni.Ligne::id)
                .containsExactly(a, b, c);
        var deuxieme = tout(Set.of(), null, "DATE", false, null, null, null, 1, 2);
        assertThat(deuxieme.total()).isEqualTo(3);
        assertThat(deuxieme.lignes()).extracting(HistoriqueFourni.Ligne::id).containsExactly(c);
    }

    @Test
    void une_seance_d_un_autre_centre_ou_d_un_patient_disparu_est_absente_et_un_statut_illisible_est_exclu_du_total() {
        seance(CENTRE, ALPHA, JOUR, "CREE", T0);
        seance(AUTRE_CENTRE, ALPHA, JOUR, "CREE", T0);
        seance(CENTRE, new UUID(9, 9), JOUR, "CREE", T0);
        seance(CENTRE, BRAVO, JOUR, "EN_COURS", T0);

        var page = tout(Set.of(), null, "DATE", true, null, null, null, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.lignes()).singleElement().extracting(HistoriqueFourni.Ligne::patient).isEqualTo(ALPHA);
    }

    // ---- forfait courant

    @Test
    void le_forfait_impose_sur_la_seance_prime_sur_celui_de_la_prise_en_charge() {
        UUID impose = forfait(CENTRE, "IMPOSE", "10.00");
        UUID couvrant = forfait(CENTRE, "PEC", "20.00");
        UUID seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, couvrant, null, JOUR.minusDays(5), null, null, null, T0);
        jdbc().update("UPDATE seances SET forfait_override_id = ?, forfait_override_code = 'IMPOSE', forfait_override_nom = 'Impose', "
                + "forfait_override_prix = 10.00, forfait_override_updated_at = ?, forfait_override_updated_by = 'admin' WHERE id = ?", impose, T0, seance);

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
        UUID seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
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
        UUID seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, null, demande, null, null, JOUR, JOUR, T0);

        assertThat(forfaits().courant(CENTRE, seance, ALPHA, JOUR)).get().extracting(ForfaitsFournis.Forfait::code).isEqualTo("DEMANDE");
    }

    @Test
    void a_defaut_de_prise_en_charge_couvrant_le_jour_la_plus_recente_qui_porte_un_forfait_est_retenue() {
        UUID ancien = forfait(CENTRE, "ANCIEN", "50.00");
        UUID recent = forfait(CENTRE, "RECENT", "60.00");
        UUID seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        priseEnCharge(CENTRE, ALPHA, ancien, null, JOUR.minusDays(90), JOUR.minusDays(60), null, null, T0);
        priseEnCharge(CENTRE, ALPHA, recent, null, JOUR.minusDays(50), JOUR.minusDays(20), null, null, T0.plusHours(1));
        priseEnCharge(CENTRE, ALPHA, null, null, null, null, null, null, T0.plusHours(2));

        assertThat(forfaits().courant(CENTRE, seance, ALPHA, JOUR)).get().extracting(ForfaitsFournis.Forfait::code).isEqualTo("RECENT");
    }

    @Test
    void sans_forfait_ni_prise_en_charge_ou_dans_un_autre_centre_il_n_y_a_pas_de_forfait() {
        UUID seance = seance(CENTRE, ALPHA, JOUR, "VALIDEE", T0);
        UUID ailleurs = forfait(AUTRE_CENTRE, "AILLEURS", "70.00");
        priseEnCharge(AUTRE_CENTRE, ALPHA, ailleurs, null, JOUR.minusDays(1), null, null, null, T0);

        assertThat(forfaits().courant(CENTRE, seance, ALPHA, JOUR)).isEmpty();
    }

    // ---- référentiel des forfaits (SE5, ADR 0092)

    @Test
    void le_referentiel_fournit_le_forfait_du_centre_avec_son_code_son_libelle_et_son_prix_tels_quels() {
        UUID forfait = forfait(CENTRE, "F1", "250.50");

        var lu = referentiel().forfait(CENTRE, forfait).orElseThrow();

        assertThat(lu.id()).isEqualTo(forfait);
        assertThat(lu.code()).isEqualTo("F1");
        assertThat(lu.libelle()).isEqualTo("Forfait F1");
        assertThat(lu.prix()).isEqualByComparingTo("250.50");
    }

    @Test
    void le_referentiel_fournit_un_forfait_sans_prix_avec_un_prix_absent() {
        UUID forfait = UUID.randomUUID();
        jdbc().update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?,?,?,?,NULL)", forfait, CENTRE, "SANS_PRIX", "Sans prix");

        assertThat(referentiel().forfait(CENTRE, forfait).orElseThrow().prix()).isNull();
    }

    @Test
    void le_referentiel_ne_fournit_ni_un_forfait_inconnu_ni_un_forfait_d_un_autre_centre() {
        UUID ailleurs = forfait(AUTRE_CENTRE, "AILLEURS", "70.00");

        assertThat(referentiel().forfait(CENTRE, ailleurs)).isEmpty();
        assertThat(referentiel().forfait(CENTRE, UUID.randomUUID())).isEmpty();
    }
}
