package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.BilansPreGreffeEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.SerologiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.NiveauDeRisqueKdigo;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StadeDeMaladieRenale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Évaluation KDIGO du candidat avec les doubles en mémoire : mêmes cas que les goldens DM7 {@code lecteurs/*}. */
class EvaluerLeCandidatALaGreffeTest {
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xC1));
    private static final UUID PATIENT = new UUID(0, 0xA1);
    private static final UUID INCONNU = new UUID(0, 0xFF);
    private static final Instant T = Instant.parse("2026-11-07T10:00:00Z");

    private final BilansPreGreffeEnMemoire bilans = new BilansPreGreffeEnMemoire();
    private final SerologiesEnMemoire serologies = new SerologiesEnMemoire();
    private BigDecimal creatinine;
    private IdentitesDesPatients.Identite identite = new IdentitesDesPatients.Identite("Nom", "Prenom", "M", LocalDate.of(1976, 6, 1), null);
    private final DerniereCreatininePubliee derniereCreatinine = (c, p) -> Optional.ofNullable(creatinine);
    private final EvaluerLeCandidatALaGreffe evaluer = new EvaluerLeCandidatALaGreffe(d -> CENTRE, (c, p) -> p.valeur().equals(PATIENT), bilans,
            (c, p) -> Optional.ofNullable(identite), derniereCreatinine, serologies,
            new HorlogeDuCentre(Clock.fixed(T, ZoneOffset.UTC), ZoneOffset.UTC));

    private void serologie(MarqueurSerologique m, ResultatSerologique r, LocalDate j, Instant saisie) {
        serologies.ajouter(Serologie.reconstituer(UUID.randomUUID(), new PatientId(PATIENT), CENTRE, m, r, null, null, j, null, null, null, saisie, saisie));
    }

    @Test
    void un_patient_inconnu_du_centre_est_refuse_sans_rien_lire() {
        assertThatThrownBy(() -> evaluer.executer(null, INCONNU)).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void sans_bilan_ni_donnees_tout_est_non_evaluable_et_la_lecture_n_ouvre_pas_de_bilan() {
        identite = null;
        var e = evaluer.executer(null, PATIENT);
        assertThat(e.risqueImmunologique().niveau()).isEqualTo(NiveauDeRisqueKdigo.NON_EVALUABLE);
        assertThat(e.fonctionRenale().stade()).isEqualTo(StadeDeMaladieRenale.NON_EVALUABLE);
        assertThat(e.alertesSerologiques()).isEmpty();
        assertThat(bilans.trouver(CENTRE, new PatientId(PATIENT))).isEmpty();
    }

    @Test
    void la_forme_complete_reprend_le_pra_du_bilan_la_creatinine_l_age_au_jour_de_l_appel_et_les_serologies() {
        bilans.ouvrir(BilanPreGreffe.reconstituer(UUID.randomUUID(), new PatientId(PATIENT), CENTRE, StatutDuBilanPreGreffe.BILAN_EN_COURS, null, null,
                null, "A+", null, new BigDecimal("10"), new BigDecimal("85"), null, null, List.of(), T, T));
        creatinine = new BigDecimal("1.1");
        serologie(MarqueurSerologique.VIH_AC, ResultatSerologique.POSITIF, LocalDate.of(2026, 1, 5), T);
        serologie(MarqueurSerologique.CMV_IGG, ResultatSerologique.POSITIF, LocalDate.of(2026, 1, 5), T);
        var e = evaluer.executer(null, PATIENT);
        assertThat(e.risqueImmunologique().niveau()).isEqualTo(NiveauDeRisqueKdigo.ELEVE);
        assertThat(e.risqueImmunologique().valeur()).isEqualByComparingTo("85");
        assertThat(e.fonctionRenale().egfrMlMin173m2()).isEqualByComparingTo("81.8");
        assertThat(e.fonctionRenale().stade()).isEqualTo(StadeDeMaladieRenale.G2);
        identite = new IdentitesDesPatients.Identite("Nom", "Prenom", "M", null, null);
        assertThat(evaluer.executer(null, PATIENT).fonctionRenale().stade()).isEqualTo(StadeDeMaladieRenale.NON_EVALUABLE);
        assertThat(e.alertesSerologiques()).extracting(a -> a.marqueur()).containsExactly("VIH_AC");
    }

    /** KB-MED-94 (reproduit, risque clinique à confirmer) : à deux résultats d'un marqueur, le plus ANCIEN l'emporte. */
    @Test
    void a_deux_resultats_d_un_meme_marqueur_le_plus_ancien_l_emporte_kb_med_94() {
        serologie(MarqueurSerologique.AG_HBS, ResultatSerologique.POSITIF, LocalDate.of(2026, 1, 5), T);
        serologie(MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, LocalDate.of(2026, 2, 5), T);
        assertThat(evaluer.executer(null, PATIENT).alertesSerologiques()).extracting(a -> a.marqueur()).containsExactly("AG_HBS");
        serologies.ajouter(Serologie.reconstituer(UUID.randomUUID(), new PatientId(PATIENT), CENTRE, MarqueurSerologique.AG_HBS,
                ResultatSerologique.NEGATIF, null, null, LocalDate.of(2025, 12, 1), null, null, null, T, T));
        assertThat(evaluer.executer(null, PATIENT).alertesSerologiques()).isEmpty();
    }
}
