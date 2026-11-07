package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link EtapesDeBilan} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class EtapesDeBilanContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7002"));
    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");

    protected abstract EtapesDeBilan etapes();

    static EtapeDeBilan etape(CentreId centre, PatientId patient, CategorieDEtape categorie, String libelle, Instant creeLe) {
        return EtapeDeBilan.ajouter(UUID.randomUUID(), patient, centre, categorie, libelle, creeLe);
    }

    @Test
    void une_etape_ajoutee_est_relue_a_faire_avec_tous_ses_champs_vides() {
        var e = etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "Consultation ORL", T0);
        etapes().ajouter(e);
        var relue = etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, e.id()).orElseThrow();
        assertThat(relue.categorie()).isEqualTo(CategorieDEtape.ORL);
        assertThat(relue.libelle()).isEqualTo("Consultation ORL");
        assertThat(relue.statut()).isEqualTo(StatutDEtape.A_FAIRE);
        assertThat(relue.dateRealisation()).isNull();
        assertThat(relue.creeLe()).isEqualTo(T0);
    }

    @Test
    void une_etape_est_cherchee_par_identifiant_patient_et_centre() {
        var e = etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "x", T0);
        etapes().ajouter(e);
        assertThat(etapes().trouverEnVerrouillant(CENTRE_B, PATIENT, e.id())).isEmpty();
        assertThat(etapes().trouverEnVerrouillant(CENTRE_A, AUTRE_PATIENT, e.id())).isEmpty();
        assertThat(etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_mise_a_jour_est_relue_avec_les_references_et_la_date_de_modification() {
        var e = etape(CENTRE_A, PATIENT, CategorieDEtape.VIROLOGIQUE, "Sérologie CMV", T0);
        etapes().ajouter(e);
        var chargee = etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, e.id()).orElseThrow();
        UUID demande = UUID.randomUUID();
        UUID serologie = UUID.randomUUID();
        chargee.mettreAJour(StatutDEtape.FAIT, LocalDate.of(2026, 11, 1), "Négatif", LocalDate.of(2027, 5, 1), demande, serologie, T0.plusSeconds(30));
        etapes().enregistrer(chargee);
        var relue = etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, e.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutDEtape.FAIT);
        assertThat(relue.dateRealisation()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(relue.resultat()).isEqualTo("Négatif");
        assertThat(relue.dateExpiration()).isEqualTo(LocalDate.of(2027, 5, 1));
        assertThat(relue.demandeExamenId()).isEqualTo(demande);
        assertThat(relue.serologieId()).isEqualTo(serologie);
        assertThat(relue.modifieLe()).isEqualTo(T0.plusSeconds(30));
        assertThat(relue.libelle()).isEqualTo("Sérologie CMV");
    }

    @Test
    void la_liste_est_triee_par_categorie_puis_libelle_et_limitee_au_patient_et_au_centre() {
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "B", T0));
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.DENTAIRE, "Z", T0));
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "A", T0));
        etapes().ajouter(etape(CENTRE_A, AUTRE_PATIENT, CategorieDEtape.ORL, "autre", T0));
        etapes().ajouter(etape(CENTRE_B, PATIENT, CategorieDEtape.ORL, "ailleurs", T0));
        assertThat(etapes().duPatient(CENTRE_A, PATIENT)).extracting(EtapeDeBilan::libelle).containsExactly("Z", "A", "B");
        assertThat(etapes().duPatient(CENTRE_B, AUTRE_PATIENT)).isEmpty();
    }

    @Test
    void les_cles_du_patient_permettent_de_ne_rien_doubler() {
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "Consultation ORL", T0));
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "Consultation ORL", T0));
        assertThat(etapes().clesDuPatient(CENTRE_A, PATIENT)).containsExactly("ORL|Consultation ORL");
        assertThat(etapes().clesDuPatient(CENTRE_B, PATIENT)).isEmpty();
    }

    @Test
    void deux_etapes_identiques_sont_deux_etapes() {
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.AUTRE, "x", T0));
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.AUTRE, "x", T0));
        assertThat(etapes().duPatient(CENTRE_A, PATIENT)).hasSize(2);
    }

    @Test
    void la_suppression_dit_si_quelque_chose_a_ete_supprime_et_ne_touche_pas_les_autres() {
        var e = etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "x", T0);
        var autre = etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "y", T0);
        etapes().ajouter(e);
        etapes().ajouter(autre);
        assertThat(etapes().supprimer(CENTRE_B, PATIENT, e.id())).isFalse();
        assertThat(etapes().supprimer(CENTRE_A, AUTRE_PATIENT, e.id())).isFalse();
        assertThat(etapes().supprimer(CENTRE_A, PATIENT, UUID.randomUUID())).isFalse();
        assertThat(etapes().supprimer(CENTRE_A, PATIENT, e.id())).isTrue();
        assertThat(etapes().supprimer(CENTRE_A, PATIENT, e.id())).isFalse();
        assertThat(etapes().duPatient(CENTRE_A, PATIENT)).extracting(EtapeDeBilan::id).containsExactly(autre.id());
    }

    @Test
    void le_verrou_de_generation_peut_etre_pris_plusieurs_fois_dans_la_meme_transaction() {
        etapes().verrouillerLaGeneration(CENTRE_A, PATIENT);
        etapes().verrouillerLaGeneration(CENTRE_A, PATIENT);
    }
}
