package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.BilansPreGreffeEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.DonneursVivantsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.EtapesDeBilanEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AvisRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivantIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilanIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ListeStandardDesEtapes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanDuDonneur;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatDeCrossmatch;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage du bilan pré-greffe, des étapes et des donneurs vivants, avec les doubles en mémoire des ports. */
class BilanPreGreffeTest {
    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 7);

    private final BilansPreGreffeEnMemoire bilans = new BilansPreGreffeEnMemoire();
    private final EtapesDeBilanEnMemoire etapes = new EtapesDeBilanEnMemoire();
    private final DonneursVivantsEnMemoire donneurs = new DonneursVivantsEnMemoire();
    private final PatientsDuCentre patients = (centre, patient) -> patient.valeur().equals(PATIENT);
    private final AtomicInteger numero = new AtomicInteger();
    private final GenerateurDIdentifiants identifiants = () -> new UUID(0, numero.incrementAndGet());
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
    private final SessionCourante session = demande -> CENTRE;
    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T independante(Supplier<T> action) {
            return action.get();
        }
    };

    private final ConsulterLeBilanPreGreffe consulter = new ConsulterLeBilanPreGreffe(session, patients, bilans, unite, identifiants, horloge);
    private final ChangerLeStatutDuBilanPreGreffe changerLeStatut = new ChangerLeStatutDuBilanPreGreffe(session, patients, bilans, unite,
            identifiants, horloge);
    private final MettreAJourLeBilanImmunologique immunologie = new MettreAJourLeBilanImmunologique(session, patients, bilans, unite, identifiants,
            horloge);
    private final MettreAJourLesNotesDuBilan notes = new MettreAJourLesNotesDuBilan(session, patients, bilans, unite, identifiants, horloge);
    private final AjouterUneDecisionDeRcp decision = new AjouterUneDecisionDeRcp(session, patients, bilans, unite, identifiants, horloge);
    private final AjouterUneEtapeDeBilan ajouterEtape = new AjouterUneEtapeDeBilan(session, patients, etapes, unite, identifiants, horloge);
    private final GenererLesEtapesStandard generer = new GenererLesEtapesStandard(session, patients, etapes, unite, identifiants, horloge);
    private final MettreAJourUneEtapeDeBilan majEtape = new MettreAJourUneEtapeDeBilan(session, etapes, unite, horloge);
    private final SupprimerUneEtapeDeBilan supprimerEtape = new SupprimerUneEtapeDeBilan(session, etapes, unite);
    private final EnregistrerUnDonneurVivant enregistrerDonneur = new EnregistrerUnDonneurVivant(session, patients, donneurs, unite, identifiants,
            horloge);
    private final MettreAJourUnDonneurVivant majDonneur = new MettreAJourUnDonneurVivant(session, donneurs, unite, horloge);
    private final SupprimerUnDonneurVivant supprimerDonneur = new SupprimerUnDonneurVivant(session, donneurs, unite);
    private final ConsulterLesDonneursVivants listerDonneurs = new ConsulterLesDonneursVivants(session, donneurs);

    @Test
    void la_garde_publiee_laisse_passer_un_patient_du_centre_et_refuse_un_patient_inconnu() {
        var garde = new GardeDePatientDuCentre(patients);
        garde.exiger(CENTRE.valeur(), PATIENT);
        assertThatThrownBy(() -> garde.exiger(CENTRE.valeur(), INCONNU)).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void la_premiere_lecture_d_un_patient_valide_ouvre_un_bilan_non_debute_et_la_seconde_le_relit() {
        var premier = consulter.executer(null, PATIENT);
        var second = consulter.executer(null, PATIENT);
        assertThat(premier.statut()).isEqualTo(StatutDuBilanPreGreffe.NON_DEBUTE);
        assertThat(second.id()).isEqualTo(premier.id());
        assertThat(bilans.nombreDuPatient(CENTRE, new PatientId(PATIENT))).isEqualTo(1);
    }

    @Test
    void un_patient_inconnu_est_refuse_sans_ouvrir_de_bilan_ni_rien_ecrire() {
        assertThatThrownBy(() -> consulter.executer(null, INCONNU)).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> changerLeStatut.executer(null, INCONNU, StatutDuBilanPreGreffe.BILAN_EN_COURS)).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> ajouterEtape.executer(null, INCONNU, CategorieDEtape.ORL, "x")).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> generer.executer(null, INCONNU)).isInstanceOf(PatientIntrouvable.class);
        assertThatThrownBy(() -> enregistrerDonneur.executer(null, INCONNU, "x", null, null, LienDeParente.PARENT, null, null, null))
                .isInstanceOf(PatientIntrouvable.class);
        assertThat(bilans.nombreDuPatient(CENTRE, new PatientId(INCONNU))).isZero();
        assertThat(etapes.duPatient(CENTRE, new PatientId(INCONNU))).isEmpty();
        assertThat(donneurs.duPatient(CENTRE, new PatientId(INCONNU))).isEmpty();
    }

    @Test
    void le_changement_de_statut_ouvre_le_bilan_s_il_le_faut_pose_la_date_et_refuse_une_transition_invalide() {
        var b = changerLeStatut.executer(null, PATIENT, StatutDuBilanPreGreffe.BILAN_EN_COURS);
        assertThat(b.dateDebutBilan()).isEqualTo(JOUR);
        assertThatThrownBy(() -> changerLeStatut.executer(null, PATIENT, StatutDuBilanPreGreffe.GREFFE_REALISEE))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("BILAN_GREFFE_TRANSITION_INVALIDE"));
        assertThat(consulter.executer(null, PATIENT).statut()).isEqualTo(StatutDuBilanPreGreffe.BILAN_EN_COURS);
        assertThat(bilans.nombreDuPatient(CENTRE, new PatientId(PATIENT))).isEqualTo(1);
    }

    @Test
    void le_bilan_immunologique_et_les_notes_sont_enregistres_et_relus() {
        immunologie.executer(null, PATIENT, "A+", "A1 B8", new BigDecimal("12.5"), null);
        notes.executer(null, PATIENT, "aucune", null);
        var b = consulter.executer(null, PATIENT);
        assertThat(b.groupeSanguinConfirme()).isEqualTo("A+");
        assertThat(b.praClasseI()).isEqualByComparingTo("12.5");
        assertThat(b.praClasseII()).isNull();
        assertThat(b.contreIndications()).isEqualTo("aucune");
        assertThat(b.conclusionNephrologue()).isNull();
    }

    @Test
    void une_decision_de_rcp_est_ajoutee_au_bilan_et_une_date_future_est_refusee_sans_rien_ecrire() {
        decision.executer(null, PATIENT, JOUR, AvisRcp.FAVORABLE, "ok", null);
        assertThatThrownBy(() -> decision.executer(null, PATIENT, JOUR.plusDays(1), AvisRcp.FAVORABLE, null, null))
                .isInstanceOfSatisfying(RegleDeGestionViolee.class, e -> assertThat(e.code()).isEqualTo("DECISION_RCP_DATE_FUTURE"));
        assertThat(consulter.executer(null, PATIENT).decisions()).hasSize(1);
    }

    @Test
    void la_generation_cree_la_liste_standard_sans_rien_doubler_et_respecte_les_etapes_deja_presentes() {
        ajouterEtape.executer(null, PATIENT, CategorieDEtape.ORL, "Consultation ORL");
        var premiere = generer.executer(null, PATIENT);
        var seconde = generer.executer(null, PATIENT);
        assertThat(premiere).hasSize(ListeStandardDesEtapes.ETAPES.size());
        assertThat(seconde).hasSize(ListeStandardDesEtapes.ETAPES.size());
    }

    @Test
    void une_etape_est_mise_a_jour_puis_supprimee_et_une_etape_inconnue_est_introuvable() {
        var e = ajouterEtape.executer(null, PATIENT, CategorieDEtape.VIROLOGIQUE, "Sérologie CMV");
        var maj = majEtape.executer(null, PATIENT, e.id(), StatutDEtape.FAIT, JOUR, "Négatif", null, null, null);
        assertThat(maj.statut()).isEqualTo(StatutDEtape.FAIT);
        assertThatThrownBy(() -> majEtape.executer(null, PATIENT, UUID.randomUUID(), StatutDEtape.FAIT, JOUR, null, null, null, null))
                .isInstanceOf(EtapeDeBilanIntrouvable.class);
        assertThatThrownBy(() -> majEtape.executer(null, PATIENT, e.id(), StatutDEtape.FAIT, null, null, null, null, null))
                .isInstanceOf(RegleDeGestionViolee.class);
        assertThat(supprimerEtape.executer(null, PATIENT, e.id())).isTrue();
        assertThat(supprimerEtape.executer(null, PATIENT, e.id())).isFalse();
    }

    @Test
    void un_donneur_est_enregistre_candidat_mis_a_jour_liste_et_supprime() {
        var d = enregistrerDonneur.executer(null, PATIENT, "Donneur", null, null, LienDeParente.PARENT, null, null, null);
        assertThat(d.statutBilan()).isEqualTo(StatutDuBilanDuDonneur.CANDIDAT);
        majDonneur.executer(null, PATIENT, d.id(), "Donneur", null, null, LienDeParente.PARENT, null, null, null, StatutDuBilanDuDonneur.RETENU,
                ResultatDeCrossmatch.NEGATIF, null, null, null, null, null);
        assertThat(listerDonneurs.executer(null, PATIENT)).singleElement().satisfies(x -> assertThat(x.statutBilan())
                .isEqualTo(StatutDuBilanDuDonneur.RETENU));
        assertThatThrownBy(() -> majDonneur.executer(null, PATIENT, UUID.randomUUID(), "x", null, null, LienDeParente.PARENT, null, null, null,
                StatutDuBilanDuDonneur.RETENU, ResultatDeCrossmatch.NEGATIF, null, null, null, null, null)).isInstanceOf(DonneurVivantIntrouvable.class);
        assertThat(supprimerDonneur.executer(null, PATIENT, d.id())).isTrue();
        assertThat(supprimerDonneur.executer(null, PATIENT, d.id())).isFalse();
    }
}
