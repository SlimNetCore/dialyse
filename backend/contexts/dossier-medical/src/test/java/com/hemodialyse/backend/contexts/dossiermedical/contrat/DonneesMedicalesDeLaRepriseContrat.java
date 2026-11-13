package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DonneesMedicalesDeLaReprise;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat des données médicales d'un lot de reprise (RP2, ADR 0170), joué par le double en mémoire et par l'adapter JDBC (H2) : lecture des
 * lignes d'un patient SANS centre (KB-S6a-9 reproduit), suppression bornée au centre du lot, natures en liste fermée.
 */
abstract class DonneesMedicalesDeLaRepriseContrat {

    static final UUID CENTRE_A = new UUID(0, 0xA);
    static final UUID CENTRE_B = new UUID(0, 0xB);
    static final UUID PATIENT = new UUID(1, 1);
    static final UUID AUTRE_PATIENT = new UUID(1, 2);

    protected abstract DonneesMedicalesDeLaReprise reprise();

    /** Une ligne de la nature donnée, pour un patient, sous un centre. */
    protected abstract void donnee(String nature, UUID id, UUID centre, UUID patient);

    /** Vrai si la ligne existe encore. */
    protected abstract boolean existe(String nature, UUID id);

    private static List<String> toutes() {
        List<String> natures = new ArrayList<>(DonneesMedicalesDeLaReprise.NATURES_CONTROLEES_AVANT_LES_BONS_DE_SORTIE);
        natures.addAll(DonneesMedicalesDeLaReprise.NATURES_CONTROLEES_APRES_LES_BONS_DE_SORTIE);
        return natures;
    }

    @Test
    void should_keep_the_historical_order_of_the_controls_and_the_natures_the_batch_can_create() {
        assertThat(toutes()).as("ordre du contrôle : le refus nomme la première nature qui bloque").containsExactly("dossier_medical_patient",
                "prescriptions_medicales", "antecedents_medicaux", "allergies_patient", "serologies_patient", "abords_vasculaires",
                "resultats_analyses", "observations_biologiques", "ordonnances", "demandes_examen", "administrations_anemie",
                "bilans_pre_greffe");
        assertThat(DonneesMedicalesDeLaReprise.NATURES_DU_LOT).containsExactlyInAnyOrder("dossier_medical_patient", "antecedents_medicaux",
                "serologies_patient", "abords_vasculaires", "resultats_analyses");
    }

    @Test
    void should_list_the_lines_of_the_patient_of_every_nature_whatever_the_centre() {
        for (String nature : toutes()) {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            donnee(nature, a, CENTRE_A, PATIENT);
            donnee(nature, b, CENTRE_B, PATIENT);
            donnee(nature, UUID.randomUUID(), CENTRE_A, AUTRE_PATIENT);
            assertThat(reprise().lignesDuPatient(PATIENT, nature)).as(nature + " : tous centres (KB-S6a-9)")
                    .containsExactlyInAnyOrder(a.toString(), b.toString());
        }
        assertThat(reprise().lignesDuPatient(UUID.randomUUID(), "resultats_analyses")).as("patient sans donnée").isEmpty();
    }

    @Test
    void should_delete_a_batch_line_only_in_the_centre_of_the_batch() {
        for (String nature : DonneesMedicalesDeLaReprise.NATURES_DU_LOT) {
            UUID ligne = UUID.randomUUID();
            UUID voisine = UUID.randomUUID();
            donnee(nature, ligne, CENTRE_A, PATIENT);
            donnee(nature, voisine, CENTRE_A, AUTRE_PATIENT);

            reprise().supprimerLaDonneeDuLot(CENTRE_B, nature, ligne);
            assertThat(existe(nature, ligne)).as(nature + " : un autre centre ne supprime rien").isTrue();
            reprise().supprimerLaDonneeDuLot(CENTRE_A, nature, ligne);
            assertThat(existe(nature, ligne)).as(nature + " : supprimée dans le centre du lot").isFalse();
            assertThat(existe(nature, voisine)).as(nature + " : la ligne voisine reste").isTrue();
        }
    }

    @Test
    void should_refuse_a_nature_outside_the_closed_list() {
        UUID ordonnance = UUID.randomUUID();
        donnee("ordonnances", ordonnance, CENTRE_A, PATIENT);

        assertThatThrownBy(() -> reprise().lignesDuPatient(PATIENT, "patients")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprise().lignesDuPatient(PATIENT, "seances; DROP TABLE x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprise().supprimerLaDonneeDuLot(CENTRE_A, "ordonnances", ordonnance))
                .as("saisie dans la plateforme, jamais créée par le lot").isInstanceOf(IllegalArgumentException.class);
        assertThat(existe("ordonnances", ordonnance)).isTrue();
    }
}
