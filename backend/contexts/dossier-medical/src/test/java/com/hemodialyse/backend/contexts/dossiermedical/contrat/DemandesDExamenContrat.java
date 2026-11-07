package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDeDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeDemandeDExamen;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link DemandesDExamen} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class DemandesDExamenContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d3a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d3b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d3001"));
    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    protected abstract DemandesDExamen demandes();

    private static LigneDeDemandeDExamen ligne(String libelle, CodeDExamen code, String commentaire) {
        return LigneDeDemandeDExamen.nouvelle(UUID.randomUUID(), code, libelle, commentaire);
    }

    private static DemandeExamen demande(CentreId centre, PatientId patient, LocalDate date, Instant creee, LigneDeDemandeDExamen... lignes) {
        return DemandeExamen.demander(UUID.randomUUID(), patient, centre, "dr fictif", date, date, CategorieDExamen.BIOLOGIE, true, "motif fictif",
                List.of(lignes), creee);
    }

    @Test
    void une_demande_ajoutee_est_relue_avec_ses_lignes_dans_un_ordre_deterministe() {
        var d = demande(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0, ligne("Zinc", null, null), ligne("Albumine", null, "a jeun"),
                ligne(null, new CodeDExamen("LOINC", "718-7", "Hemoglobine"), null));
        demandes().ajouter(d);
        var relue = demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutDeDemandeDExamen.DEMANDE);
        assertThat(relue.conclusion()).isNull();
        assertThat(relue.prescripteur()).isEqualTo("dr fictif");
        assertThat(relue.categorie()).isEqualTo(CategorieDExamen.BIOLOGIE);
        assertThat(relue.urgent()).isTrue();
        assertThat(relue.motif()).isEqualTo("motif fictif");
        assertThat(relue.dateDemande()).isEqualTo(LocalDate.of(2026, 1, 3));
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.lignes()).extracting(LigneDeDemandeDExamen::libelle).containsExactly("Albumine", "Zinc", null);
        var codee = relue.lignes().get(2);
        assertThat(codee.code().code()).isEqualTo("718-7");
        assertThat(codee.code().nomenclature()).isEqualTo("LOINC");
        assertThat(codee.code().libelle()).isEqualTo("Hemoglobine");
        assertThat(relue.lignes().get(0).commentaire()).isEqualTo("a jeun");
    }

    @Test
    void une_demande_n_est_trouvee_que_par_son_identifiant_son_patient_et_son_centre() {
        var d = demande(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0, ligne("X", null, null));
        demandes().ajouter(d);
        assertThat(demandes().trouverEnVerrouillant(CENTRE_B, PATIENT, d.id())).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, new PatientId(UUID.randomUUID()), d.id())).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, UUID.randomUUID())).isEmpty();
        assertThat(demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id())).isPresent();
    }

    @Test
    void une_transition_est_enregistree_sans_toucher_aux_lignes() {
        var d = demande(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 3), T0, ligne("X", null, null), ligne("Y", null, null));
        demandes().ajouter(d);
        var chargee = demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        chargee.enregistrerLePrelevement(T0.plusSeconds(60));
        demandes().enregistrerLaTransition(chargee);
        var chargee2 = demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        chargee2.rendreLeResultatDisponible(T0.plusSeconds(120));
        chargee2.valider("Conclusion fictive", T0.plusSeconds(180));
        demandes().enregistrerLaTransition(chargee2);
        var relue = demandes().trouverEnVerrouillant(CENTRE_A, PATIENT, d.id()).orElseThrow();
        assertThat(relue.statut()).isEqualTo(StatutDeDemandeDExamen.VALIDE);
        assertThat(relue.conclusion()).isEqualTo("Conclusion fictive");
        assertThat(relue.modifieeLe()).isEqualTo(T0.plusSeconds(180));
        assertThat(relue.creeeLe()).isEqualTo(T0);
        assertThat(relue.lignes()).extracting(LigneDeDemandeDExamen::id)
                .containsExactlyInAnyOrderElementsOf(d.lignes().stream().map(LigneDeDemandeDExamen::id).toList());
    }

    @Test
    void la_liste_est_triee_de_la_plus_recente_a_la_plus_ancienne_et_paginee_dans_le_centre_et_pour_le_patient() {
        var a = demande(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 1), T0, ligne("A", null, null));
        var b = demande(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 4), T0, ligne("B", null, null));
        var c = demande(CENTRE_A, PATIENT, LocalDate.of(2026, 1, 2), T0, ligne("C", null, null));
        var autreCentre = demande(CENTRE_B, PATIENT, LocalDate.of(2026, 1, 9), T0, ligne("D", null, null));
        for (var d : List.of(a, b, c, autreCentre)) demandes().ajouter(d);
        var page0 = demandes().duPatient(CENTRE_A, PATIENT, 0, 2);
        assertThat(page0.total()).isEqualTo(3);
        assertThat(page0.items()).extracting(DemandeExamen::id).containsExactly(b.id(), c.id());
        var page1 = demandes().duPatient(CENTRE_A, PATIENT, 1, 2);
        assertThat(page1.items()).extracting(DemandeExamen::id).containsExactly(a.id());
        assertThat(demandes().duPatient(CENTRE_A, PATIENT, 2, 2).items()).isEmpty();
        assertThat(demandes().duPatient(CENTRE_A, new PatientId(UUID.randomUUID()), 0, 5).total()).isZero();
    }
}
