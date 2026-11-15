package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer;
import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer.LigneImprimable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link OrdonnancesAImprimer} (DOC5d, ADR 0196) : le double en mémoire et H2 passent le MÊME jeu de tests. */
abstract class OrdonnancesAImprimerContrat {

    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d5b2"));
    static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000d5001");
    static final UUID ORDONNANCE = UUID.fromString("00000000-0000-0000-0000-0000000d5101");
    static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000d5102");

    protected abstract OrdonnancesAImprimer ordonnances();

    /** Pose une ordonnance (en-tête et lignes) telle qu'enregistrée, sans aucun contrôle. */
    protected abstract void poser(CentreId centre, UUID id, UUID patient, String numero, LocalDate date, String medecin,
                                  List<LigneImprimable> lignes);

    @Test
    void une_ordonnance_est_relue_avec_son_en_tete_et_toutes_ses_lignes_telles_qu_enregistrees() {
        LigneImprimable complete = new LigneImprimable("Libellé du code", "Nom saisi", "1 par jour", "orale", 30, 2, "à jeun");
        LigneImprimable nue = new LigneImprimable(null, null, "à la demande", null, null, null, null);
        poser(CENTRE_A, ORDONNANCE, PATIENT, "ORD-00007", LocalDate.of(2026, 1, 4), "dr fictif", List.of(complete, nue));
        poser(CENTRE_A, AUTRE, PATIENT, "ORD-00008", LocalDate.of(2026, 1, 5), "autre", List.of(complete));

        var relue = ordonnances().trouver(CENTRE_A, ORDONNANCE).orElseThrow();

        assertThat(relue.patient()).isEqualTo(PATIENT);
        assertThat(relue.numero()).isEqualTo("ORD-00007");
        assertThat(relue.datePrescription()).isEqualTo(LocalDate.of(2026, 1, 4));
        assertThat(relue.medecin()).isEqualTo("dr fictif");
        assertThat(relue.lignes()).containsExactlyInAnyOrder(complete, nue);
    }

    @Test
    void un_numero_et_un_prescripteur_absents_restent_nuls() {
        poser(CENTRE_A, ORDONNANCE, PATIENT, null, LocalDate.of(2026, 1, 4), null,
                List.of(new LigneImprimable(null, "Seul", "1", null, null, null, null)));

        var relue = ordonnances().trouver(CENTRE_A, ORDONNANCE).orElseThrow();

        assertThat(relue.numero()).isNull();
        assertThat(relue.medecin()).isNull();
    }

    @Test
    void une_ordonnance_sans_ligne_est_trouvee_avec_une_liste_vide() {
        poser(CENTRE_A, ORDONNANCE, PATIENT, "ORD-00007", LocalDate.of(2026, 1, 4), "dr fictif", List.of());

        assertThat(ordonnances().trouver(CENTRE_A, ORDONNANCE).orElseThrow().lignes()).isEmpty();
    }

    @Test
    void une_ordonnance_inconnue_ou_d_un_autre_centre_n_est_pas_trouvee() {
        poser(CENTRE_B, ORDONNANCE, PATIENT, "ORD-00007", LocalDate.of(2026, 1, 4), "dr fictif",
                List.of(new LigneImprimable(null, "Seul", "1", null, null, null, null)));

        assertThat(ordonnances().trouver(CENTRE_A, ORDONNANCE)).isEmpty();
        assertThat(ordonnances().trouver(CENTRE_A, AUTRE)).isEmpty();
        assertThat(ordonnances().trouver(CENTRE_B, ORDONNANCE)).isPresent();
    }
}
