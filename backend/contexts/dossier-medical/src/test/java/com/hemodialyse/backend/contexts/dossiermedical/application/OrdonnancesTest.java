package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.RedigerUneOrdonnance.LigneDemandee;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.OrdonnancesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutOrdonnance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des ordonnances, avec les doubles en mémoire des ports. */
class OrdonnancesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final OrdonnancesEnMemoire ordonnances = new OrdonnancesEnMemoire();
    private final Set<UUID> patientsConnus = new HashSet<>(Set.of(PATIENT));
    private final AtomicInteger compteur = new AtomicInteger();
    private final AtomicInteger identifiants = new AtomicInteger();
    // 31/12/2025 23:30 UTC = 01/01/2026 00:30 à Alger : le jour du CENTRE, pas celui de l'UTC.
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2025-12-31T23:30:00Z"), ZoneOffset.UTC),
            java.time.ZoneId.of("Africa/Algiers"));
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

    private final RedigerUneOrdonnance rediger = new RedigerUneOrdonnance(session, (centre, patient) -> patientsConnus.contains(patient.valeur()),
            ordonnances, unite, () -> new UUID(0, identifiants.incrementAndGet()), horloge);
    private final SignerUneOrdonnance signer = new SignerUneOrdonnance(session, ordonnances,
            centre -> "ORD-" + String.format("%05d", compteur.incrementAndGet()), unite, horloge);
    private final MarquerUneOrdonnanceImprimee imprimer = new MarquerUneOrdonnanceImprimee(session, ordonnances, unite, horloge);
    private final AnnulerUneOrdonnance annuler = new AnnulerUneOrdonnance(session, ordonnances, unite, horloge);
    private final ConsulterLesOrdonnancesDuPatient consulter = new ConsulterLesOrdonnancesDuPatient(session, ordonnances);

    private static List<LigneDemandee> uneLigne() {
        return List.of(new LigneDemandee(null, null, null, "Medicament fictif", "1 par jour", null, null, null, null));
    }

    @Test
    void la_date_absente_est_le_jour_du_centre() {
        var o = rediger.executer(null, PATIENT, null, null, uneLigne());
        assertThat(o.datePrescription()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(o.statut()).isEqualTo(StatutOrdonnance.BROUILLON);
    }

    @Test
    void un_patient_inconnu_du_centre_est_refuse_et_rien_n_est_enregistre() {
        assertThatThrownBy(() -> rediger.executer(null, UUID.randomUUID(), null, null, uneLigne())).isInstanceOf(PatientIntrouvable.class);
        assertThat(ordonnances.duPatient(CENTRE, new PatientId(PATIENT), 0, 10).total()).isZero();
    }

    @Test
    void des_lignes_absentes_ou_nulles_sont_une_saisie_invalide_et_des_lignes_vides_une_regle_violee() {
        assertThatThrownBy(() -> rediger.executer(null, PATIENT, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> rediger.executer(null, PATIENT, null, null, java.util.Collections.singletonList(null))).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> rediger.executer(null, PATIENT, null, null, List.of())).isInstanceOf(RegleDeGestionViolee.class);
    }

    @Test
    void un_code_blanc_est_ignore_mais_un_code_sans_nomenclature_est_refuse() {
        var ok = rediger.executer(null, PATIENT, null, null,
                List.of(new LigneDemandee(null, " ", null, "X", "1", null, null, null, null)));
        assertThat(ok.lignes().get(0).medicament()).isNull();
        assertThatThrownBy(() -> rediger.executer(null, PATIENT, null, null,
                List.of(new LigneDemandee(null, "B03XA02", null, null, "1", null, null, null, null)))).isInstanceOf(SaisieInvalide.class);
    }

    @Test
    void les_numeros_suivent_l_ordre_des_signatures() {
        var a = rediger.executer(null, PATIENT, null, null, uneLigne());
        var b = rediger.executer(null, PATIENT, null, null, uneLigne());
        assertThat(signer.executer(null, PATIENT, b.id()).numero()).isEqualTo("ORD-00001");
        assertThat(signer.executer(null, PATIENT, a.id()).numero()).isEqualTo("ORD-00002");
    }

    @Test
    void un_refus_de_signature_consomme_un_numero_comme_le_legacy_mais_une_ordonnance_introuvable_non() {
        var annulee = rediger.executer(null, PATIENT, null, null, uneLigne());
        annuler.executer(null, PATIENT, annulee.id());
        assertThatThrownBy(() -> signer.executer(null, PATIENT, annulee.id())).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(compteur).hasValue(1);
        assertThatThrownBy(() -> signer.executer(null, PATIENT, UUID.randomUUID())).isInstanceOf(OrdonnanceIntrouvable.class);
        assertThat(compteur).hasValue(1);
        var suivante = rediger.executer(null, PATIENT, null, null, uneLigne());
        assertThat(signer.executer(null, PATIENT, suivante.id()).numero()).isEqualTo("ORD-00002");
    }

    @Test
    void une_ordonnance_inconnue_ou_d_un_autre_patient_est_introuvable() {
        var o = rediger.executer(null, PATIENT, null, null, uneLigne());
        assertThatThrownBy(() -> signer.executer(null, PATIENT, UUID.randomUUID())).isInstanceOf(OrdonnanceIntrouvable.class);
        assertThatThrownBy(() -> imprimer.executer(null, UUID.randomUUID(), o.id())).isInstanceOf(OrdonnanceIntrouvable.class);
    }

    @Test
    void le_cycle_complet_signer_imprimer_annuler() {
        var o = rediger.executer(null, PATIENT, null, null, uneLigne());
        signer.executer(null, PATIENT, o.id());
        assertThat(imprimer.executer(null, PATIENT, o.id()).statut()).isEqualTo(StatutOrdonnance.IMPRIMEE);
        assertThatThrownBy(() -> imprimer.executer(null, PATIENT, o.id())).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(annuler.executer(null, PATIENT, o.id()).numero()).isEqualTo("ORD-00001");
    }

    @Test
    void la_liste_reproduit_les_bornes_du_legacy() {
        for (int i = 0; i < 3; i++) rediger.executer(null, PATIENT, null, LocalDate.of(2026, 1, 1 + i), uneLigne());
        assertThat(consulter.executer(null, PATIENT, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.executer(null, PATIENT, -1, 0).page()).isZero();
        assertThat(consulter.executer(null, PATIENT, 0, 1000).taille()).isEqualTo(200);
        assertThat(consulter.executer(null, PATIENT, 0, 2).items()).hasSize(2);
        assertThat(consulter.executer(null, UUID.randomUUID(), 0, 2).items()).isEmpty();
    }
}
