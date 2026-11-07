package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemanderUnExamen.LigneDemandee;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.DemandesDExamenEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeDemandeDExamen;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des demandes d'examen, avec les doubles en mémoire des ports. */
class DemandesDExamenTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final DemandesDExamenEnMemoire demandes = new DemandesDExamenEnMemoire();
    private final Set<UUID> patientsConnus = new HashSet<>(Set.of(PATIENT));
    private final AtomicInteger identifiants = new AtomicInteger();
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
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

    private final DemanderUnExamen demander = new DemanderUnExamen(session, (centre, patient) -> patientsConnus.contains(patient.valeur()),
            demandes, unite, () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final EnregistrerLePrelevement prelever = new EnregistrerLePrelevement(session, demandes, unite, horloge);
    private final RendreLeResultatDisponible rendreDisponible = new RendreLeResultatDisponible(session, demandes, unite, horloge);
    private final ValiderUneDemandeDExamen valider = new ValiderUneDemandeDExamen(session, demandes, unite, horloge);
    private final AnnulerUneDemandeDExamen annuler = new AnnulerUneDemandeDExamen(session, demandes, unite, horloge);
    private final ConsulterLesDemandesDExamenDuPatient consulter = new ConsulterLesDemandesDExamenDuPatient(session, demandes);

    private static List<LigneDemandee> uneLigne() {
        return List.of(new LigneDemandee("LOINC", "718-7", "Hemoglobine", "Hemoglobine", null));
    }

    private com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen nouvelle() {
        return demander.executer(null, PATIENT, "dr", null, "BIOLOGIE", false, null, uneLigne());
    }

    @Test
    void la_date_absente_est_le_jour_de_l_horloge_et_la_demande_nait_a_l_etat_demande() {
        var d = nouvelle();
        assertThat(d.dateDemande()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(d.statut()).isEqualTo(StatutDeDemandeDExamen.DEMANDE);
        assertThat(d.lignes()).hasSize(1);
    }

    @Test
    void une_date_future_est_refusee_en_regle_de_gestion_et_rien_n_est_enregistre() {
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, LocalDate.of(2026, 1, 2), "BIOLOGIE", false, null, uneLigne()))
                .isInstanceOf(RegleDeGestionViolee.class);
        assertThat(demandes.duPatient(CENTRE, new PatientId(PATIENT), 0, 10).total()).isZero();
    }

    @Test
    void un_patient_inconnu_du_centre_est_refuse_et_rien_n_est_enregistre() {
        assertThatThrownBy(() -> demander.executer(null, UUID.randomUUID(), null, null, "BIOLOGIE", false, null, uneLigne()))
                .isInstanceOf(PatientIntrouvable.class);
        assertThat(demandes.duPatient(CENTRE, new PatientId(PATIENT), 0, 10).total()).isZero();
    }

    @Test
    void des_lignes_absentes_ou_nulles_sont_une_saisie_invalide_et_des_lignes_vides_une_regle_violee() {
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, "BIOLOGIE", false, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, "BIOLOGIE", false, null, Collections.singletonList(null)))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, "BIOLOGIE", false, null, List.of()))
                .isInstanceOf(RegleDeGestionViolee.class);
    }

    @Test
    void la_categorie_et_le_caractere_urgent_sont_requis() {
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, null, false, null, uneLigne())).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, "RADIO", false, null, uneLigne())).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, "BIOLOGIE", null, null, uneLigne())).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void un_code_blanc_est_ignore_mais_un_code_sans_nomenclature_est_refuse() {
        var ok = demander.executer(null, PATIENT, null, null, "BIOLOGIE", false, null, List.of(new LigneDemandee(null, " ", null, "X", null)));
        assertThat(ok.lignes().get(0).code()).isNull();
        assertThatThrownBy(() -> demander.executer(null, PATIENT, null, null, "BIOLOGIE", false, null,
                List.of(new LigneDemandee(null, "718-7", null, null, null)))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void le_parcours_complet_prelever_rendre_disponible_valider() {
        var d = nouvelle();
        assertThat(prelever.executer(null, PATIENT, d.id()).statut()).isEqualTo(StatutDeDemandeDExamen.PRELEVE);
        assertThat(rendreDisponible.executer(null, PATIENT, d.id()).statut()).isEqualTo(StatutDeDemandeDExamen.RESULTAT_DISPONIBLE);
        var validee = valider.executer(null, PATIENT, d.id(), "Conclusion fictive");
        assertThat(validee.statut()).isEqualTo(StatutDeDemandeDExamen.VALIDE);
        assertThat(validee.conclusion()).isEqualTo("Conclusion fictive");
        assertThat(validee.lignes()).hasSize(1);
    }

    @Test
    void un_rejeu_est_refuse_en_regle_de_gestion_et_ne_change_rien() {
        var d = nouvelle();
        prelever.executer(null, PATIENT, d.id());
        assertThatThrownBy(() -> prelever.executer(null, PATIENT, d.id())).isInstanceOf(RegleDeGestionViolee.class);
        assertThatThrownBy(() -> valider.executer(null, PATIENT, d.id(), "x")).isInstanceOf(RegleDeGestionViolee.class);
        annuler.executer(null, PATIENT, d.id());
        assertThatThrownBy(() -> annuler.executer(null, PATIENT, d.id())).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(consulter.executer(null, PATIENT, 0, 10).items().get(0).statut()).isEqualTo(StatutDeDemandeDExamen.ANNULE);
    }

    @Test
    void une_demande_inconnue_ou_d_un_autre_patient_est_introuvable() {
        var d = nouvelle();
        assertThatThrownBy(() -> prelever.executer(null, PATIENT, UUID.randomUUID())).isInstanceOf(DemandeExamenIntrouvable.class);
        assertThatThrownBy(() -> annuler.executer(null, UUID.randomUUID(), d.id())).isInstanceOf(DemandeExamenIntrouvable.class);
        assertThatThrownBy(() -> rendreDisponible.executer(null, PATIENT, UUID.randomUUID())).isInstanceOf(DemandeExamenIntrouvable.class);
        assertThatThrownBy(() -> valider.executer(null, PATIENT, UUID.randomUUID(), null)).isInstanceOf(DemandeExamenIntrouvable.class);
    }

    @Test
    void la_liste_reproduit_les_bornes_du_legacy() {
        for (int i = 0; i < 3; i++) demander.executer(null, PATIENT, null, LocalDate.of(2026, 1, 1).minusDays(i), "BIOLOGIE", false, null, uneLigne());
        assertThat(consulter.executer(null, PATIENT, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.executer(null, PATIENT, -1, 0).page()).isZero();
        assertThat(consulter.executer(null, PATIENT, 0, 1000).taille()).isEqualTo(200);
        assertThat(consulter.executer(null, PATIENT, 0, 2).items()).hasSize(2);
        assertThat(consulter.executer(null, UUID.randomUUID(), 0, 2).items()).isEmpty();
    }
}
