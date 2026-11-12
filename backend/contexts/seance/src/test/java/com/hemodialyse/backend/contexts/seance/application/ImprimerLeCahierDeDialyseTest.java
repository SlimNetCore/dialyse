package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CahierRefuse;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Impression du cahier de dialyse (PL5, ADR 0144) : gardes, ligne d'identité, édition, portée du volet médical. */
class ImprimerLeCahierDeDialyseTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-12T10:30:00Z"), ZoneOffset.UTC);

    private boolean medical = true;
    private boolean refuse = false;
    private int seancesImprimables = 2;
    private Optional<FicheDuPatient> fiche = Optional.of(fiche("Amel", "Benali", "0555 00 00 00", null));
    private final List<DemandeDeCahier> demandes = new ArrayList<>();

    private static FicheDuPatient fiche(String prenom, String nom, String telephone, LocalDate naissance) {
        return new FicheDuPatient(new PatientId(PATIENT), "P-001", nom, prenom, "F", naissance, null, " A+ ", telephone, null, null, null, null,
                null, null, null);
    }

    private ImprimerLeCahierDeDialyse casDUsage() {
        SessionCourante session = new SessionCourante() {
            @Override
            public CentreId centre(UUID centreDemande) {
                if (refuse) throw new AccesRefuse();
                return new CentreId(CENTRE);
            }

            @Override
            public String utilisateur() {
                return "secretaire1";
            }

            @Override
            public boolean estAdministrateur() {
                return false;
            }

            @Override
            public boolean valideAuScan() {
                return false;
            }

            @Override
            public boolean peutLireLeVoletMedical() {
                return medical;
            }
        };
        return new ImprimerLeCahierDeDialyse(session, (centre, patient) -> fiche, (centre, patient, periode) -> seancesImprimables,
                (centre, demande) -> {
                    demandes.add(demande);
                    return new CahierImprime(new byte[]{1}, CahierImprime.Format.PDF);
                }, HORLOGE);
    }

    @Test
    void le_cahier_est_confie_a_documents_avec_la_ligne_d_identite_l_edition_et_la_periode_par_defaut() {
        casDUsage().executer(null, PATIENT, null, null, "Asia/Tokyo");

        DemandeDeCahier d = demandes.getFirst();
        assertThat(d.ligneDIdentite()).isEqualTo("Patient : Amel Benali  ·  Code P-001  ·  Sexe F  ·  Groupe A+  ·  Tél 0555 00 00 00");
        assertThat(d.editeLe()).isEqualTo("12/11/2026 19:30");
        assertThat(d.editePar()).isEqualTo("secretaire1");
        assertThat(d.periode().du()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(d.periode().au()).isEqualTo(LocalDate.of(2999, 12, 31));
    }

    @Test
    void un_fuseau_absent_ou_invalide_donne_l_heure_UTC() {
        casDUsage().executer(null, PATIENT, null, null, "Nulle/Part");
        casDUsage().executer(null, PATIENT, null, null, " ");

        assertThat(demandes).extracting(DemandeDeCahier::editeLe).containsOnly("12/11/2026 10:30");
    }

    @Test
    void le_volet_medical_n_est_visible_qu_aux_profils_habilites() {
        medical = false;
        casDUsage().executer(null, PATIENT, null, null, null);

        assertThat(demandes.getFirst().voletMedicalVisible()).isFalse();
    }

    @Test
    void une_periode_inversee_est_refusee_avant_toute_lecture() {
        assertThatThrownBy(() -> casDUsage().executer(null, PATIENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null))
                .isInstanceOfSatisfying(CahierRefuse.class, e -> assertThat(e.code()).isEqualTo("CAHIER_PERIODE_INVALIDE"));
    }

    @Test
    void un_patient_inconnu_ou_d_un_autre_centre_est_introuvable() {
        fiche = Optional.empty();

        assertThatThrownBy(() -> casDUsage().executer(null, PATIENT, null, null, null)).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void un_cahier_sans_seance_imprimable_est_refuse() {
        seancesImprimables = 0;

        assertThatThrownBy(() -> casDUsage().executer(null, PATIENT, null, null, null))
                .isInstanceOfSatisfying(CahierRefuse.class, e -> assertThat(e.code()).isEqualTo("CAHIER_VIDE"));
        assertThat(demandes).isEmpty();
    }

    @Test
    void le_centre_de_la_session_est_garde_avant_tout() {
        refuse = true;
        fiche = Optional.empty();

        assertThatThrownBy(() -> casDUsage().executer(UUID.randomUUID(), PATIENT, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(AccesRefuse.class);
    }
}
