package com.hemodialyse.backend.contexts.absencespatients.contrat;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencePatient;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.absencespatients.domain.DateOuverte;
import com.hemodialyse.backend.contexts.absencespatients.domain.DroitDeCorrection;
import com.hemodialyse.backend.contexts.absencespatients.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MotifAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.SourceAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.StatutAbsence;
import com.hemodialyse.backend.contexts.absencespatients.domain.Valorisation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des absences enregistrées : le même jeu de tests passe contre le double en mémoire, l'adaptateur JDBC sur H2 et sur
 * PostgreSQL 16 (le double est donc fidèle). Les identifiants croissent avec le rang pour que l'ordre de verrouillage soit le
 * même quelle que soit la base.
 */
public abstract class AbsencesContrat {

    protected static final CentreId CENTRE = new CentreId(new UUID(0x8000, 0xa));
    protected static final CentreId AUTRE_CENTRE = new CentreId(new UUID(0x8000, 0xb));
    protected static final PatientId AMRANI = new PatientId(new UUID(0x8001, 1));
    protected static final PatientId BELKACEM = new PatientId(new UUID(0x8001, 2));
    protected static final UtilisateurId SECRETAIRE = new UtilisateurId(new UUID(0x8002, 1));
    protected static final UtilisateurId MEDECIN = new UtilisateurId(new UUID(0x8002, 2));
    protected static final LocalDate JOUR = LocalDate.of(2026, 10, 10);
    protected static final Instant INSTANT = Instant.parse("2026-10-12T08:00:00Z");
    private static final Valorisation VALEUR = Valorisation.depuis(
            new ForfaitApplicable(new ForfaitId(new UUID(0x8003, 1)), "Forfait HD", new BigDecimal("10000.00")), new BigDecimal("19"));

    protected abstract AbsencesRepository absences();

    protected static AbsenceId id(int rang) {
        return new AbsenceId(new UUID(0x8004, rang));
    }

    /** Une absence « à qualifier » déclarée à cette date (identité de rang donné). */
    protected static AbsencePatient aQualifier(int rang, CentreId centre, PatientId patient, LocalDate jour) {
        return AbsencePatient.declarer(id(rang), new DateOuverte(centre, patient, jour), null, null, VALEUR, SECRETAIRE,
                LocalDate.of(2026, 10, 12), INSTANT);
    }

    /** Une absence relue dans l'état donné (fixture : le domaine n'y mène pas toujours). */
    protected static AbsencePatient dansLEtat(int rang, CentreId centre, PatientId patient, LocalDate jour, StatutAbsence statut,
                                              LocalDate rattrapage) {
        return AbsencePatient.reconstituer(id(rang), centre, patient, jour, SourceAbsence.AUTOMATIQUE, VALEUR, null, INSTANT,
                statut, statut == StatutAbsence.A_QUALIFIER ? null : MotifAbsence.MALADIE, null, null, null, rattrapage, null, null);
    }

    @Test
    void une_absence_creee_est_relue_a_l_identique_avec_ses_traces_et_sa_valeur_figee() {
        AbsencePatient declaree = AbsencePatient.declarer(id(1), new DateOuverte(CENTRE, AMRANI, JOUR), MotifAbsence.AUTRE,
                "  Précision  ", VALEUR, SECRETAIRE, LocalDate.of(2026, 10, 12), INSTANT);

        absences().creer(declaree);

        AbsencePatient lue = absences().verrouiller(CENTRE, id(1)).orElseThrow();
        assertThat(lue.id()).isEqualTo(id(1));
        assertThat(lue.centre()).isEqualTo(CENTRE);
        assertThat(lue.patient()).isEqualTo(AMRANI);
        assertThat(lue.dateDeSeance()).isEqualTo(JOUR);
        assertThat(lue.source()).isEqualTo(SourceAbsence.DECLAREE);
        assertThat(lue.statut()).isEqualTo(StatutAbsence.JUSTIFIEE);
        assertThat(lue.motif()).isEqualTo(MotifAbsence.AUTRE);
        assertThat(lue.commentaire()).isEqualTo("Précision");
        assertThat(lue.declareePar()).isEqualTo(SECRETAIRE);
        assertThat(lue.declareeLe()).isEqualTo(INSTANT);
        assertThat(lue.qualifieePar()).isEqualTo(SECRETAIRE);
        assertThat(lue.qualifieeLe()).isEqualTo(INSTANT);
        assertThat(lue.modifieePar()).isEqualTo(SECRETAIRE);
        assertThat(lue.modifieeLe()).isEqualTo(INSTANT);
        assertThat(lue.dateDeRattrapage()).isNull();
        assertThat(lue.valorisation().forfait()).isEqualTo(new ForfaitId(new UUID(0x8003, 1)));
        assertThat(lue.valorisation().libelleDuForfait()).isEqualTo("Forfait HD");
        assertThat(lue.valorisation().montantHt()).isEqualByComparingTo("10000.00");
        assertThat(lue.valorisation().montantTtc()).isEqualByComparingTo("11900.00");
        assertThat(lue.valorisation().tauxDeTva()).isEqualByComparingTo("19.00");
    }

    @Test
    void une_absence_sans_auteur_ni_forfait_est_relue_sans() {
        AbsencePatient sansAuteur = AbsencePatient.declarer(id(2), new DateOuverte(CENTRE, AMRANI, JOUR), null, null,
                Valorisation.depuis(null, BigDecimal.ZERO), null, LocalDate.of(2026, 10, 12), INSTANT);

        absences().creer(sansAuteur);

        AbsencePatient lue = absences().verrouiller(CENTRE, id(2)).orElseThrow();
        assertThat(lue.declareePar()).isNull();
        assertThat(lue.qualifieePar()).isNull();
        assertThat(lue.valorisation().forfait()).isNull();
        assertThat(lue.valorisation().montantTtc()).isEqualByComparingTo("0.00");
    }

    @Test
    void une_seule_absence_par_patient_et_par_jour_meme_annulee() {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));

        assertThatThrownBy(() -> absences().creer(aQualifier(2, CENTRE, AMRANI, JOUR))).isInstanceOf(EcritureConcurrente.class);
        absences().creer(aQualifier(3, CENTRE, AMRANI, JOUR.plusDays(1)));
        absences().creer(aQualifier(4, CENTRE, BELKACEM, JOUR));
        absences().creer(aQualifier(5, AUTRE_CENTRE, AMRANI, JOUR));

        AbsencePatient annulee = absences().verrouiller(CENTRE, id(1)).orElseThrow();
        annulee.annuler("Erreur", SECRETAIRE, DroitDeCorrection.REFUSE, false, (centre, patient, jour) -> false, INSTANT);
        absences().enregistrer(annulee);
        assertThatThrownBy(() -> absences().creer(aQualifier(6, CENTRE, AMRANI, JOUR))).isInstanceOf(EcritureConcurrente.class);
    }

    @Test
    void existe_voit_toute_absence_du_patient_ce_jour_la_quel_que_soit_son_statut_dans_son_centre() {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));
        absences().creer(dansLEtat(2, CENTRE, AMRANI, JOUR.plusDays(1), StatutAbsence.ANNULEE, null));

        assertThat(absences().existe(CENTRE, AMRANI, JOUR)).isTrue();
        assertThat(absences().existe(CENTRE, AMRANI, JOUR.plusDays(1))).isTrue();
        assertThat(absences().existe(CENTRE, AMRANI, JOUR.plusDays(2))).isFalse();
        assertThat(absences().existe(CENTRE, BELKACEM, JOUR)).isFalse();
        assertThat(absences().existe(AUTRE_CENTRE, AMRANI, JOUR)).isFalse();
    }

    @Test
    void verrouiller_une_absence_inconnue_ou_d_un_autre_centre_rend_vide() {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));

        assertThat(absences().verrouiller(AUTRE_CENTRE, id(1))).isEmpty();
        assertThat(absences().verrouiller(CENTRE, id(99))).isEmpty();
        assertThat(absences().verrouillerPourRattrapage(AUTRE_CENTRE, id(1))).isEmpty();
        assertThat(absences().verrouillerPourRattrapage(CENTRE, id(99))).isEmpty();
    }

    @Test
    void enregistrer_ecrit_le_nouvel_etat_et_ses_traces() {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));
        AbsencePatient absence = absences().verrouiller(CENTRE, id(1)).orElseThrow();
        absence.qualifier(MotifAbsence.NON_JUSTIFIEE, "Contrôlé", MEDECIN, DroitDeCorrection.ACCORDE, (c, p, j) -> false,
                INSTANT.plusSeconds(60));
        absences().enregistrer(absence);

        AbsencePatient lue = absences().verrouiller(CENTRE, id(1)).orElseThrow();

        assertThat(lue.statut()).isEqualTo(StatutAbsence.NON_JUSTIFIEE);
        assertThat(lue.motif()).isEqualTo(MotifAbsence.NON_JUSTIFIEE);
        assertThat(lue.commentaire()).isEqualTo("Contrôlé");
        assertThat(lue.qualifieePar()).isEqualTo(MEDECIN);
        assertThat(lue.qualifieeLe()).isEqualTo(INSTANT.plusSeconds(60));
        assertThat(lue.modifieePar()).isEqualTo(MEDECIN);
        assertThat(lue.declareePar()).isEqualTo(SECRETAIRE);
        assertThat(lue.declareeLe()).isEqualTo(INSTANT);
        assertThat(lue.valorisation().montantTtc()).isEqualByComparingTo("11900.00");
    }

    @Test
    void enregistrer_une_absence_qui_n_existe_plus_est_une_ecriture_concurrente() {
        assertThatThrownBy(() -> absences().enregistrer(aQualifier(1, CENTRE, AMRANI, JOUR))).isInstanceOf(EcritureConcurrente.class);
    }

    @Test
    void le_rattrapage_voit_les_dates_des_seules_absences_rattrapees_du_meme_patient_et_du_meme_centre() {
        absences().creer(dansLEtat(1, CENTRE, AMRANI, JOUR, StatutAbsence.RATTRAPEE, JOUR.plusDays(3)));
        absences().creer(dansLEtat(2, CENTRE, AMRANI, JOUR.plusDays(1), StatutAbsence.ANNULEE, JOUR.plusDays(4)));
        absences().creer(aQualifier(3, CENTRE, AMRANI, JOUR.plusDays(2)));
        absences().creer(dansLEtat(4, CENTRE, BELKACEM, JOUR, StatutAbsence.RATTRAPEE, JOUR.plusDays(5)));
        absences().creer(dansLEtat(5, AUTRE_CENTRE, AMRANI, JOUR, StatutAbsence.RATTRAPEE, JOUR.plusDays(6)));

        var lue = absences().verrouillerPourRattrapage(CENTRE, id(3)).orElseThrow();

        assertThat(lue.absence().id()).isEqualTo(id(3));
        assertThat(lue.absence().statut()).isEqualTo(StatutAbsence.A_QUALIFIER);
        assertThat(lue.datesDeRattrapageUtilisees()).containsExactly(JOUR.plusDays(3));
    }

    @Test
    void supprimer_retire_l_absence_de_son_centre_seulement_et_est_sans_effet_ensuite() {
        absences().creer(aQualifier(1, CENTRE, AMRANI, JOUR));

        absences().supprimer(AUTRE_CENTRE, id(1));
        assertThat(absences().verrouiller(CENTRE, id(1))).isPresent();
        absences().supprimer(CENTRE, id(1));
        absences().supprimer(CENTRE, id(1));

        assertThat(absences().verrouiller(CENTRE, id(1))).isEmpty();
        assertThat(absences().existe(CENTRE, AMRANI, JOUR)).isFalse();
        absences().creer(aQualifier(2, CENTRE, AMRANI, JOUR));
    }
}
