package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.AntecedentsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.BilansPreGreffeEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.DonneursVivantsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.EtapesDeBilanEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.SerologiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AvisRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DecisionRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatDeCrossmatch;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanDuDonneur;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAntecedent;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dossier de greffe publié : garde du patient, contenus et ordres du PDF legacy (goldens DM7 {@code pdf_complet}, {@code pdf_vide}). */
class ConstituerLeDossierDeGreffeTest {
    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xC1));
    private static final PatientId PATIENT = new PatientId(new UUID(0, 0xA1));
    private static final Instant T = Instant.parse("2026-11-07T10:00:00Z");

    private final BilansPreGreffeEnMemoire bilans = new BilansPreGreffeEnMemoire();
    private final EtapesDeBilanEnMemoire etapes = new EtapesDeBilanEnMemoire();
    private final SerologiesEnMemoire serologies = new SerologiesEnMemoire();
    private final AntecedentsEnMemoire antecedents = new AntecedentsEnMemoire();
    private final DonneursVivantsEnMemoire donneurs = new DonneursVivantsEnMemoire();
    private final ConstituerLeDossierDeGreffe dossier = new ConstituerLeDossierDeGreffe((c, p) -> p.equals(PATIENT), bilans, etapes, serologies,
            antecedents, donneurs);

    @Test
    void un_patient_inconnu_du_centre_est_refuse() {
        assertThatThrownBy(() -> dossier.duPatient(CENTRE.valeur(), new UUID(0, 0xFF))).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void un_patient_sans_rien_donne_un_dossier_vide_sans_bilan() {
        var d = dossier.duPatient(CENTRE.valeur(), PATIENT.valeur());
        assertThat(d.bilan()).isNull();
        assertThat(d.etapes()).isEmpty();
        assertThat(d.serologies()).isEmpty();
        assertThat(d.antecedentsActifs()).isEmpty();
        assertThat(d.donneurs()).isEmpty();
    }

    @Test
    void le_dossier_complet_reprend_bilan_decisions_etapes_serologies_antecedents_actifs_et_donneurs_dans_l_ordre_du_legacy() {
        bilans.ouvrir(BilanPreGreffe.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, StatutDuBilanPreGreffe.ELIGIBLE, null, null, null, "A+",
                "HLA", new BigDecimal("10"), new BigDecimal("20"), "aucune", "favorable",
                List.of(new DecisionRcp(UUID.randomUUID(), LocalDate.of(2026, 1, 5), AvisRcp.AJOURNE, "cr1", null),
                        new DecisionRcp(UUID.randomUUID(), LocalDate.of(2026, 3, 5), AvisRcp.FAVORABLE, "cr2", LocalDate.of(2026, 9, 1))), T, T));
        etapes.ajouter(EtapeDeBilan.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, CategorieDEtape.ORL, "Consultation ORL", StatutDEtape.A_FAIRE,
                null, null, null, null, null, T, T));
        etapes.ajouter(EtapeDeBilan.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, CategorieDEtape.CARDIOLOGIQUE, "Echographie", StatutDEtape.FAIT,
                LocalDate.of(2026, 2, 1), "normale", null, null, null, T, T));
        serologies.ajouter(Serologie.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.AG_HBS, ResultatSerologique.NEGATIF, null,
                null, LocalDate.of(2026, 1, 1), null, null, null, T, T));
        serologies.ajouter(Serologie.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, MarqueurSerologique.VIH_AC, ResultatSerologique.POSITIF, null,
                null, LocalDate.of(2026, 2, 1), null, null, "isoler", T, T));
        antecedents.ajouter(Antecedent.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL,
                CodeClinique.reconstituer(SystemeDeCodage.CIM10, "I10", "Hypertension"), null, LocalDate.of(2020, 1, 1), null, StatutClinique.ACTIF,
                null, null, T, T));
        antecedents.ajouter(Antecedent.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.CHIRURGICAL, null, "Appendicectomie",
                LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 2), StatutClinique.RESOLU, null, null, T, T));
        antecedents.ajouter(Antecedent.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, TypeDAntecedent.MEDICAL, null, "Diabete",
                LocalDate.of(2022, 1, 1), null, StatutClinique.ACTIF, null, null, T, T));
        donneurs.ajouter(DonneurVivant.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, "Donneur", "Un", null, LienDeParente.CONJOINT, null, "O+",
                null, StatutDuBilanDuDonneur.CANDIDAT, ResultatDeCrossmatch.NON_FAIT, null, null, null, null, null, T, T));
        donneurs.ajouter(DonneurVivant.reconstituer(UUID.randomUUID(), PATIENT, CENTRE, "Donneur", "Deux", null, LienDeParente.ENFANT, null, "A-",
                null, StatutDuBilanDuDonneur.COMPATIBLE, ResultatDeCrossmatch.NEGATIF, null, null, null, null, null, T.plusSeconds(60), T));

        var d = dossier.duPatient(CENTRE.valeur(), PATIENT.valeur());
        assertThat(d.bilan().statut()).isEqualTo("ELIGIBLE");
        assertThat(d.bilan().praClasseII()).isEqualByComparingTo("20");
        assertThat(d.bilan().decisions()).extracting(x -> x.avis()).containsExactly("FAVORABLE", "AJOURNE");
        assertThat(d.etapes()).extracting(e -> e.categorie()).containsExactly("CARDIOLOGIQUE", "ORL");
        assertThat(d.etapes().get(0).dateRealisation()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(d.serologies()).extracting(s -> s.marqueur()).containsExactly("VIH_AC", "AG_HBS");
        assertThat(d.antecedentsActifs()).extracting(a -> a.libelle()).containsExactly("Diabete", "Hypertension");
        assertThat(d.donneurs()).extracting(x -> x.prenom()).containsExactly("Deux", "Un");
        assertThat(d.donneurs().get(0).crossmatch()).isEqualTo("NEGATIF");
    }
}
