package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesMouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.MouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PatientsJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat contre les vraies tables {@code patients} et {@code mouvement_patient} : le jeu commun, plus ce qui n'a de sens qu'en
 * base (la place n'est jamais écrite par la fiche, ligne héritée aux colonnes nulles, journal des mouvements). Exécuté sur H2 et
 * sur PostgreSQL 16.
 */
abstract class PatientsJdbcContrat extends PatientsContrat {

    protected abstract JdbcTemplate jdbc();

    @Override
    protected PatientRepository depot() {
        return new PatientsJdbc(jdbc());
    }

    @Test
    void should_ne_jamais_ecrire_la_place_ni_les_jours_de_dialyse_la_planification_en_est_proprietaire() {
        Patient p = admis(CENTRE, Fiches.nominale("JD-1", null, null));
        depot().enregistrer(p);
        UUID salle = UUID.randomUUID();
        jdbc().update("UPDATE patients SET salle_id = ?, jour_lundi = TRUE WHERE id = ?", salle, p.id().valeur());
        Patient relu = depot().trouver(CENTRE, p.id()).orElseThrow();
        relu.modifier(Fiches.nominale("JD-1", null, null), Fiches.AUJOURDHUI, T0, UUID::randomUUID);

        depot().enregistrer(relu);

        assertThat(jdbc().queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, p.id().valeur())).isEqualTo(salle);
        assertThat(jdbc().queryForObject("SELECT jour_lundi FROM patients WHERE id = ?", Boolean.class, p.id().valeur())).isTrue();
    }

    @Test
    void should_ecrire_l_etat_et_sa_date_dans_les_vraies_colonnes() {
        Patient p = admis(CENTRE, Fiches.nominale("JD-2", EtatDuPatient.TRANSFERE, LocalDate.of(2026, 10, 5)));
        depot().enregistrer(p);

        assertThat(jdbc().queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, p.id().valeur()))
                .isEqualTo("TRANSFERE");
        assertThat(jdbc().queryForObject("SELECT date_evenement_etat FROM patients WHERE id = ?", Date.class, p.id().valeur()))
                .isEqualTo(Date.valueOf(LocalDate.of(2026, 10, 5)));
    }

    @Test
    void should_relire_une_ligne_heritee_aux_colonnes_nulles_comme_permanente_et_la_laisser_modifier() {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, type_patient, numero_assurance, date_admission, "
                        + "date_naissance) VALUES (?,?,?,?,?,?,?,?,?)", id, CENTRE.valeur(), "Heritee", "Ligne", "F",
                "NON_VACANCIER", "HER-1", Date.valueOf(LocalDate.of(2020, 1, 1)), Date.valueOf(LocalDate.of(1960, 1, 1)));

        Patient relu = depot().trouver(CENTRE, new com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId(id))
                .orElseThrow();

        assertThat(relu.etat()).isEqualTo(EtatDuPatient.PERMANENT);
        assertThat(relu.fiche().suivi().enSommeil()).isFalse();
        assertThat(relu.fiche().affectation().jours().codes()).isEmpty();
        relu.modifier(Fiches.avec("HER-1", null, null, LocalDate.of(2020, 1, 1), LocalDate.of(1960, 1, 1)), Fiches.AUJOURDHUI, T0,
                UUID::randomUUID);
        depot().enregistrer(relu);
        assertThat(jdbc().queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, id)).isEqualTo("PERMANENT");
    }

    // ---- état inconnu (donnée ancienne) : la valeur brute est lue et jamais réécrite

    @Test
    void should_relire_un_etat_inconnu_tel_quel_et_ne_pas_le_reecrire_a_un_put_sans_etat() {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, type_patient, numero_assurance, date_admission, "
                        + "date_naissance, etat_patient, qualite_assure) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id, CENTRE.valeur(), "Inconnu",
                "Etat", "F", "NON_VACANCIER", "INC-1", Date.valueOf(LocalDate.of(2020, 1, 1)), Date.valueOf(LocalDate.of(1960, 1, 1)),
                "BIDON", "ASSURE_LUI_MEME");
        var patientId = new com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId(id);

        Patient relu = depot().trouver(CENTRE, patientId).orElseThrow();
        assertThat(relu.etatBrut()).isEqualTo("BIDON");
        relu.modifier(Fiches.avec("INC-1", null, null, LocalDate.of(2020, 1, 1), LocalDate.of(1960, 1, 1)), Fiches.AUJOURDHUI, T0,
                UUID::randomUUID);
        depot().enregistrer(relu);

        assertThat(jdbc().queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, id)).isEqualTo("BIDON");
    }

    // ---- concurrence réelle (H2 et PostgreSQL 16) : verrou du centre en tête de toute écriture de fiche

    private void assurerLaLigneDuCentre() {
        Integer n = jdbc().queryForObject("SELECT COUNT(*) FROM planning_parametres WHERE center_id = ?", Integer.class,
                CENTRE.valeur());
        if (n == null || n == 0) {
            jdbc().update("INSERT INTO planning_parametres (center_id, jours_ouverts, updated_at) VALUES (?, 'LUNDI', CURRENT_TIMESTAMP)",
                    CENTRE.valeur());
        }
    }

    private com.hemodialyse.backend.contexts.patientcouverture.application.VerrouDuCentre verrouDuCentre() {
        return centre -> jdbc().queryForList("SELECT center_id FROM planning_parametres WHERE center_id = ? FOR UPDATE",
                centre.valeur());
    }

    private com.hemodialyse.backend.contexts.patientcouverture.application.UniteDeTravail unite() {
        return new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.transaction.UniteDeTravailSpring(
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc().getDataSource())),
                jdbc(), java.time.Duration.ofSeconds(3));
    }

    private static final com.hemodialyse.backend.contexts.patientcouverture.application.PlaceDeLaFiche SANS_PLACE =
            new com.hemodialyse.backend.contexts.patientcouverture.application.PlaceDeLaFiche() {
                @Override
                public void verifier(CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p,
                                     com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation a, EtatDuPatient e, LocalDate d) {
                }

                @Override
                public void placer(CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p,
                                   com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation a, EtatDuPatient e, LocalDate d) {
                }

                @Override
                public boolean libererSiEchue(CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p) {
                    return false;
                }
            };

    private static final com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche COUVERTURE_VIDE =
            new com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche() {
                @Override
                public boolean attestationEnregistree(CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p) {
                    return true;
                }

                @Override
                public void enregistrer(CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p,
                                        com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture couv, AttestationSaisie a) {
                }
            };

    private com.hemodialyse.backend.contexts.patientcouverture.application.AdmettreUnPatient admission() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.AdmettreUnPatient(session(), horloge(), identifiants(),
                SANS_PLACE, depot(), new MouvementsJdbc(jdbc()), COUVERTURE_VIDE, unite(), verrouDuCentre(),
                new com.hemodialyse.backend.contexts.patientcouverture.application.PrincipalDeLaFiche(
                        new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc(jdbc())),
                pecDeLaFiche());
    }

    private com.hemodialyse.backend.contexts.patientcouverture.application.PecDeLaFiche pecDeLaFiche() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.PecDeLaFiche(
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PrisesEnChargeJdbc(jdbc()),
                identifiants(), horloge());
    }

    private com.hemodialyse.backend.contexts.patientcouverture.application.ModifierLaFiche modification() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.ModifierLaFiche(session(), horloge(), identifiants(),
                SANS_PLACE, depot(), new MouvementsJdbc(jdbc()), COUVERTURE_VIDE, unite(), verrouDuCentre(),
                new com.hemodialyse.backend.contexts.patientcouverture.application.PrincipalDeLaFiche(
                        new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc(jdbc())),
                pecDeLaFiche());
    }

    private static com.hemodialyse.backend.contexts.patientcouverture.application.SessionCourante session() {
        return demande -> CENTRE;
    }

    private static com.hemodialyse.backend.contexts.patientcouverture.application.HorlogeDesCentres horloge() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.HorlogeDesCentres() {
            @Override
            public LocalDate aujourdhui(CentreId centre) {
                return Fiches.AUJOURDHUI;
            }

            @Override
            public java.time.Instant maintenant() {
                return T0;
            }
        };
    }

    private static com.hemodialyse.backend.contexts.patientcouverture.application.Identifiants identifiants() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.Identifiants() {
            @Override
            public UUID nouveau() {
                return UUID.randomUUID();
            }

            @Override
            public String nouveauCodePatient() {
                return "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
        };
    }

    private static com.hemodialyse.backend.contexts.patientcouverture.application.SaisieDeLaFiche saisie(String numero) {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.SaisieDeLaFiche(CENTRE.valeur(),
                Fiches.nominale(numero, null, null),
                new com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche.AttestationSaisie(null,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), null);
    }

    @Test
    void should_laisser_passer_une_seule_des_deux_admissions_simultanees_du_meme_numero() throws Exception {
        assurerLaLigneDuCentre();
        for (int tour = 0; tour < 6; tour++) {
            jdbc().update("DELETE FROM mouvement_patient WHERE center_id = ?", CENTRE.valeur());
            jdbc().update("DELETE FROM patients WHERE center_id = ?", CENTRE.valeur());
            java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.CompletableFuture<Object>> envois = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                envois.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        depart.await();
                        return (Object) admission().executer(saisie("CONC-1"));
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            depart.countDown();
            java.util.List<Object> resultats = new java.util.ArrayList<>();
            for (var envoi : envois) resultats.add(envoi.get(30, java.util.concurrent.TimeUnit.SECONDS));

            long reussies = resultats.stream().filter(r -> !(r instanceof Exception)).count();
            assertThat(reussies).as("tour " + tour + " : " + resultats).isEqualTo(1);
            resultats.stream().filter(r -> r instanceof Exception).forEach(r -> assertThat(r)
                    .as("le perdant reçoit 422 (numéro déjà utilisé) ou 409 (écriture concurrente)")
                    .isInstanceOfAny(com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee.class,
                            com.hemodialyse.backend.contexts.patientcouverture.domain.EcritureConcurrente.class));
            assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM patients WHERE center_id = ? AND numero_assurance = 'CONC-1'",
                    Integer.class, CENTRE.valeur())).isEqualTo(1);
        }
    }

    @Test
    void should_ne_jamais_s_interbloquer_entre_la_modification_d_une_fiche_et_un_traitement_qui_prend_le_centre_puis_la_fiche()
            throws Exception {
        assurerLaLigneDuCentre();
        var tx = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc().getDataSource()));
        for (int tour = 0; tour < 8; tour++) {
            jdbc().update("DELETE FROM mouvement_patient WHERE center_id = ?", CENTRE.valeur());
            jdbc().update("DELETE FROM patients WHERE center_id = ?", CENTRE.valeur());
            var admis = admission().executer(saisie("CONC-2"));
            java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
            var fiche = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    depart.await();
                    modification().executer(admis.id().valeur(), saisie("CONC-2"));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            });
            var nuit = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    depart.await();
                    tx.executeWithoutResult(s -> {
                        jdbc().queryForList("SELECT center_id FROM planning_parametres WHERE center_id = ? FOR UPDATE", CENTRE.valeur());
                        jdbc().update("UPDATE patients SET nom = 'Nuit' WHERE id = ?", admis.id().valeur());
                    });
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            });
            depart.countDown();
            fiche.get(30, java.util.concurrent.TimeUnit.SECONDS);
            nuit.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    // ---- R1 (PC2) : un seul assuré principal, même quand deux affectations se croisent

    @Test
    void should_garder_un_seul_assure_principal_actif_quand_deux_affectations_se_croisent() throws Exception {
        assurerLaLigneDuCentre();
        var assures = new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc(jdbc());
        var gerer = new com.hemodialyse.backend.contexts.patientcouverture.application.GererLesAssures(session(), horloge(), depot(),
                assures, unite(), verrouDuCentre());
        for (int tour = 0; tour < 6; tour++) {
            jdbc().update("DELETE FROM assure_patient WHERE center_id = ?", CENTRE.valeur());
            jdbc().update("DELETE FROM assure WHERE center_id = ?", CENTRE.valeur());
            jdbc().update("DELETE FROM patients WHERE center_id = ?", CENTRE.valeur());
            Patient p = admis(CENTRE, Fiches.tiers("R1-" + tour, "R1-AS-0"));
            depot().enregistrer(p);
            for (String numero : new String[]{"R1-AS-1", "R1-AS-2"}) {
                assures.declarer(new com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure(numero, "Nom",
                        "Prenom", "M", null, null, null, null, null, null, CENTRE.valeur()));
            }
            java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.CompletableFuture<Object>> envois = new java.util.ArrayList<>();
            for (String numero : new String[]{"R1-AS-1", "R1-AS-2"}) {
                envois.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        depart.await();
                        return (Object) gerer.affecter(CENTRE.valeur(), p.id().valeur(), numero);
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            depart.countDown();
            for (var envoi : envois) {
                Object r = envoi.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (r instanceof Exception e) {
                    assertThat(e).as("tour " + tour).isInstanceOf(com.hemodialyse.backend.contexts.patientcouverture.domain.EcritureConcurrente.class);
                }
            }
            assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM assure_patient WHERE center_id = ? AND patient_id = ? "
                            + "AND is_primary = true AND date_fin_affectation IS NULL", Integer.class, CENTRE.valeur(), p.id().valeur()))
                    .as("tour " + tour + " : un seul principal actif").isEqualTo(1);
        }
    }

    // ---- KB-PAT-31 en course : le même numéro d'assuré déclaré en même temps par deux centres (verrous de centres différents)

    @Test
    void should_refuser_la_capture_d_un_assure_d_un_autre_centre_meme_en_course() throws Exception {
        var assures = new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc(jdbc());
        for (int tour = 0; tour < 10; tour++) {
            String numero = "KB31-" + tour;
            jdbc().update("DELETE FROM assure WHERE numero_assurance = ?", numero);
            java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.CompletableFuture<Object>> envois = new java.util.ArrayList<>();
            for (var centre : new java.util.UUID[]{CENTRE.valeur(), AUTRE.valeur()}) {
                envois.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        depart.await();
                        assures.declarer(new com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure(
                                numero, "Nom " + centre, "Prenom", "M", null, null, null, null, null, null, centre));
                        return (Object) centre;
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            depart.countDown();
            int gagnants = 0;
            for (var envoi : envois) {
                Object r = envoi.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (r instanceof Exception e) {
                    assertThat(e).as("tour " + tour).isInstanceOf(com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee.class);
                    assertThat(((com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee) e).code())
                            .isEqualTo("ASSURE_D_UN_AUTRE_CENTRE");
                } else {
                    gagnants++;
                }
            }
            assertThat(gagnants).as("tour " + tour + " : un seul centre détient l'assuré").isEqualTo(1);
            var ligne = jdbc().queryForMap("SELECT center_id, nom FROM assure WHERE numero_assurance = ?", numero);
            assertThat(ligne.get("nom")).as("tour " + tour + " : l'identité n'est pas écrasée par l'autre centre")
                    .isEqualTo("Nom " + ligne.get("center_id"));
        }
    }

    @Test
    void should_ne_pas_ecraser_un_assure_deja_detenu_par_un_autre_centre() {
        var assures = new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc(jdbc());
        jdbc().update("DELETE FROM assure WHERE numero_assurance = 'KB31-SEQ'");
        assures.declarer(new com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure("KB31-SEQ", "Du centre A",
                "P", "M", null, null, null, null, null, null, CENTRE.valeur()));
        org.junit.jupiter.api.Assertions.assertThrows(com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee.class,
                () -> assures.declarer(new com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure("KB31-SEQ",
                        "Du centre B", "P", "M", null, null, null, null, null, null, AUTRE.valeur())));
        assertThat(jdbc().queryForObject("SELECT nom FROM assure WHERE numero_assurance = 'KB31-SEQ'", String.class)).isEqualTo("Du centre A");
    }

    @Test
    void should_ne_pas_ajouter_de_ligne_d_affectation_a_chaque_enregistrement_de_la_fiche_d_un_ayant_droit() {
        assurerLaLigneDuCentre();
        jdbc().update("DELETE FROM assure_patient WHERE center_id = ?", CENTRE.valeur());
        jdbc().update("DELETE FROM assure WHERE center_id = ?", CENTRE.valeur());
        jdbc().update("DELETE FROM patients WHERE center_id = ?", CENTRE.valeur());
        var saisie = new com.hemodialyse.backend.contexts.patientcouverture.application.SaisieDeLaFiche(CENTRE.valeur(),
                Fiches.tiers("R1-IDEM", "R1-AS-9"), new com.hemodialyse.backend.contexts.patientcouverture.application
                .CouvertureDeLaFiche.AttestationSaisie(null, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), null);
        var admis = admission().executer(saisie);

        modification().executer(admis.id().valeur(), saisie);
        modification().executer(admis.id().valeur(), saisie);

        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM assure_patient WHERE patient_id = ?", Integer.class, admis.id().valeur()))
                .isEqualTo(1);
    }

    // ---- PC3 : prises en charge (table prise_en_charge)

    private com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge pecs() {
        return new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PrisesEnChargeJdbc(jdbc());
    }

    private void viderLesPec() {
        jdbc().update("DELETE FROM prise_en_charge WHERE center_id = ?", CENTRE.valeur());
        jdbc().update("DELETE FROM attestation_droit WHERE center_id = ?", CENTRE.valeur());
        jdbc().update("DELETE FROM patients WHERE center_id = ?", CENTRE.valeur());
    }

    private com.hemodialyse.backend.contexts.patientcouverture.application.GererLesPec gererLesPec() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.GererLesPec(session(), horloge(), identifiants(),
                pecs(), depot(),
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacyattestation.AttestationsDuPatientJdbc(jdbc()),
                unite(), verrouDuCentre());
    }

    @Test
    void should_relire_une_pec_champ_par_champ_et_la_mettre_a_jour_sans_la_dupliquer() {
        viderLesPec();
        Patient p = admis(CENTRE, Fiches.nominale("PEC-C1", null, null));
        depot().enregistrer(p);
        UUID forfait = UUID.randomUUID();
        var pec = com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge.demander(UUID.randomUUID(), p.id(), CENTRE,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1), forfait, T0);
        pecs().enregistrer(pec);

        pec.valider(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 1), null);
        pecs().enregistrer(pec);

        var relue = pecs().trouver(CENTRE, pec.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec.VALIDEE);
        assertThat(relue.demandeForfait()).isEqualTo(forfait);
        assertThat(relue.accordFin()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(relue.creeLe()).isEqualTo(T0);
        assertThat(pecs().duPatient(CENTRE, p.id())).hasSize(1);
        assertThat(pecs().duCentre(CENTRE)).hasSize(1);
        assertThat(pecs().trouver(AUTRE, pec.id())).isEmpty();
        assertThat(pecs().identifiantPris(pec.id())).isTrue();
        pecs().supprimer(pec.id());
        assertThat(pecs().identifiantPris(pec.id())).isFalse();
    }

    @Test
    void should_lire_la_validite_d_une_attestation_a_un_jour_bornes_incluses() {
        viderLesPec();
        Patient p = admis(CENTRE, Fiches.nominale("PEC-C2", null, null));
        depot().enregistrer(p);
        jdbc().update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), p.id().valeur(), CENTRE.valeur(), Date.valueOf(LocalDate.of(2026, 1, 1)), Date.valueOf(LocalDate.of(2026, 12, 31)));
        var attestations = new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacyattestation.AttestationsDuPatientJdbc(jdbc());

        assertThat(attestations.valideLe(CENTRE, p.id(), LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(attestations.valideLe(CENTRE, p.id(), LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(attestations.valideLe(CENTRE, p.id(), LocalDate.of(2027, 1, 1))).isFalse();
        assertThat(attestations.valideLe(AUTRE, p.id(), LocalDate.of(2026, 6, 1))).isFalse();
    }

    @Test
    void should_rechercher_les_pec_du_centre_avec_les_filtres_le_tri_et_la_page() {
        viderLesPec();
        Patient a = admis(CENTRE, Fiches.nominale("PEC-C3", EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1)));
        depot().enregistrer(a);
        for (int i = 1; i <= 3; i++) {
            var pec = com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge.demander(UUID.randomUUID(), a.id(), CENTRE,
                    LocalDate.of(2026, i, 1), LocalDate.of(2026, 3 + i, 1), null, T0);
            if (i == 2) pec.valider(null, null, null);
            pecs().enregistrer(pec);
        }
        var lecture = new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesPecJdbc(jdbc());
        java.util.function.Function<String, com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec> statut =
                s -> new com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec(0, 10, null, null, null, null,
                        null, null, s);

        var toutes = lecture.rechercher(CENTRE, statut.apply(null));
        assertThat(toutes.total()).isEqualTo(3);
        assertThat(toutes.items()).extracting(l -> l.date_fin_demande()).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(toutes.items().get(0).nom()).isEqualTo("Benali");
        assertThat(lecture.rechercher(CENTRE, statut.apply("valid")).items()).hasSize(1);
        assertThat(lecture.rechercher(AUTRE, statut.apply(null)).total()).isZero();
        var parNom = new com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec(0, 2, "benali", null, null, null,
                null, null, null);
        assertThat(lecture.rechercher(CENTRE, parNom).items()).hasSize(2);
        assertThat(lecture.rechercher(CENTRE, parNom).total()).isEqualTo(3);
        var parDate = new com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec(0, 10, null, null, null, null,
                "2026-02-01..2026-03-31", null, null);
        assertThat(lecture.rechercher(CENTRE, parDate).items()).hasSize(2);
        var illisible = new com.hemodialyse.backend.contexts.patientcouverture.application.CriteresDePec(0, 10, null, null, null, null,
                "n'importe quoi", null, null);
        assertThat(lecture.rechercher(CENTRE, illisible).total()).as("saisie illisible ignorée").isEqualTo(3);
    }

    @Test
    void should_laisser_passer_une_seule_des_deux_validations_simultanees_d_une_pec() throws Exception {
        assurerLaLigneDuCentre();
        for (int tour = 0; tour < 6; tour++) {
            viderLesPec();
            Patient p = admis(CENTRE, Fiches.nominale("PEC-R" + tour, EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1)));
            depot().enregistrer(p);
            var pec = com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge.demander(UUID.randomUUID(), p.id(), CENTRE,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 1), null, T0);
            pecs().enregistrer(pec);
            var gerer = gererLesPec();
            java.util.concurrent.CountDownLatch depart = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.CompletableFuture<Object>> envois = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                envois.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        depart.await();
                        return (Object) gerer.valider(CENTRE.valeur(), pec.id(), null, null, null);
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            depart.countDown();
            java.util.List<Object> resultats = new java.util.ArrayList<>();
            for (var envoi : envois) resultats.add(envoi.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(resultats.stream().filter(r -> !(r instanceof Exception)).count()).as("tour " + tour + " : " + resultats)
                    .isEqualTo(1);
            resultats.stream().filter(r -> r instanceof Exception).forEach(r -> assertThat(r).isInstanceOfAny(
                    com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee.class,
                    com.hemodialyse.backend.contexts.patientcouverture.domain.EcritureConcurrente.class));
        }
    }

    @Test
    void should_ajouter_un_mouvement_et_le_relire_du_plus_recent_au_plus_ancien() {
        Patient p = admis(CENTRE, Fiches.nominale("JD-3", null, null));
        depot().enregistrer(p);
        var mouvements = new MouvementsJdbc(jdbc());
        MouvementDuPatient admission = p.retirerLesMouvements().get(0);
        mouvements.ajouter(admission);
        p.modifier(Fiches.nominale("JD-3", EtatDuPatient.GUERRI, LocalDate.of(2026, 10, 3)), Fiches.AUJOURDHUI, T0, UUID::randomUUID);
        p.retirerLesMouvements().forEach(mouvements::ajouter);
        depot().enregistrer(p);

        var lues = new LectureDesMouvementsJdbc(jdbc()).lister(CENTRE,
                new LectureDesMouvements.Filtre(p.id().valeur(), null, null, null), 0, 20);

        assertThat(lues.total()).isEqualTo(2);
        assertThat(lues.items()).extracting(LectureDesMouvements.Ligne::type)
                .containsExactly(TypeDeMouvement.GUERISON, TypeDeMouvement.ADMISSION);
        assertThat(lues.items().get(0).etatPrecedent()).isEqualTo("PERMANENT");
        assertThat(lues.items().get(0).patientNom()).isEqualTo("Karim Benali");
        var filtrees = new LectureDesMouvementsJdbc(jdbc()).lister(CENTRE,
                new LectureDesMouvements.Filtre(null, TypeDeMouvement.GUERISON, null, null), 0, 20);
        assertThat(filtrees.items()).extracting(LectureDesMouvements.Ligne::patientId).contains(p.id().valeur());
        assertThat(new LectureDesMouvementsJdbc(jdbc()).lister(AUTRE, new LectureDesMouvements.Filtre(null, null, null, null), 0, 20)
                .items()).isEmpty();
    }
}
