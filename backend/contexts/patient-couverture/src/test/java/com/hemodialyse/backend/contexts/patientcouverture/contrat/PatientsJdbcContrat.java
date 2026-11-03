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
                public java.util.Optional<com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture.AssureSurLaFiche> assurePrincipal(
                        CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p) {
                    return java.util.Optional.empty();
                }

                @Override
                public void enregistrer(CentreId c, com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId p,
                                        com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture couv, AttestationSaisie a,
                                        DemandeDePec d) {
                }
            };

    private com.hemodialyse.backend.contexts.patientcouverture.application.AdmettreUnPatient admission() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.AdmettreUnPatient(session(), horloge(), identifiants(),
                SANS_PLACE, depot(), new MouvementsJdbc(jdbc()), COUVERTURE_VIDE, unite(), verrouDuCentre());
    }

    private com.hemodialyse.backend.contexts.patientcouverture.application.ModifierLaFiche modification() {
        return new com.hemodialyse.backend.contexts.patientcouverture.application.ModifierLaFiche(session(), horloge(), identifiants(),
                SANS_PLACE, depot(), new MouvementsJdbc(jdbc()), COUVERTURE_VIDE, unite(), verrouDuCentre());
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
