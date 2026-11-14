package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DonneesMedicalesDeLaReprise;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RepriseDeLHistoriqueMedical;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat de l'écriture de l'historique médical d'un lot de reprise (RP3b, ADR 0172), joué par le double en mémoire et par l'adapter JDBC
 * (H2) : recherche par clé bornée au centre (valeur nulle lue « est nulle »), création horodatée (modification posée sauf pour les abords
 * vasculaires), mise à jour bornée au centre qui ne change jamais le patient, natures et champs en liste fermée.
 */
abstract class RepriseDeLHistoriqueMedicalContrat {

    static final UUID CENTRE_A = new UUID(0, 0xA);
    static final UUID CENTRE_B = new UUID(0, 0xB);
    static final UUID PATIENT = new UUID(1, 1);
    static final UUID AUTRE_PATIENT = new UUID(1, 2);

    protected abstract RepriseDeLHistoriqueMedical reprise();

    /** La valeur écrite d'un champ (nom du fichier d'import) d'une ligne, en texte ; {@code null} si la colonne est vide. */
    protected abstract String valeur(String nature, UUID id, String champ);

    /** Vrai si la date de création (ou de modification) est posée. */
    protected abstract boolean horodate(String nature, UUID id, String colonne);

    /** Champs utilisés par le contrat, par nature (le double en mémoire n'accepte que ceux-là). */
    protected static List<String> champs(String nature) {
        return valeurs(nature, PATIENT).keySet().stream().toList();
    }

    static Map<String, Object> valeurs(String nature, UUID patient) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("patientId", patient);
        switch (nature) {
            case RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE -> {
                v.put("dateMiseEnDialyse", LocalDate.of(2018, 6, 15));
                v.put("nephropathieInitiale", "Diabete");
            }
            case RepriseDeLHistoriqueMedical.ANTECEDENTS -> {
                v.put("typeAntecedent", "MEDICAL");
                v.put("libelleLibre", "Diabete");
                v.put("dateDebut", LocalDate.of(2010, 1, 1));
                v.put("statutClinique", "ACTIF");
            }
            case RepriseDeLHistoriqueMedical.SEROLOGIES -> {
                v.put("marqueur", "AG_HBS");
                v.put("resultat", "NEGATIF");
                v.put("datePrelevement", LocalDate.of(2026, 1, 15));
            }
            case RepriseDeLHistoriqueMedical.ABORDS_VASCULAIRES -> {
                v.put("typeAbord", "FAV");
                v.put("dateCreation", LocalDate.of(2018, 3, 10));
                v.put("actif", true);
            }
            default -> {
                v.put("datePrelevement", LocalDate.of(2026, 1, 5));
                v.put("hbGDl", new java.math.BigDecimal("10.80"));
            }
        }
        return v;
    }

    /** Champ de la clé d'import de la nature (le patient plus les champs qui identifient la ligne). */
    static Map<String, Object> cle(String nature, UUID patient) {
        Map<String, Object> v = valeurs(nature, patient);
        Map<String, Object> cle = new LinkedHashMap<>();
        cle.put("patientId", patient);
        switch (nature) {
            case RepriseDeLHistoriqueMedical.ANTECEDENTS -> List.of("typeAntecedent", "libelleLibre", "dateDebut").forEach(c -> cle.put(c, v.get(c)));
            case RepriseDeLHistoriqueMedical.SEROLOGIES -> List.of("marqueur", "datePrelevement").forEach(c -> cle.put(c, v.get(c)));
            case RepriseDeLHistoriqueMedical.ABORDS_VASCULAIRES -> List.of("typeAbord", "dateCreation").forEach(c -> cle.put(c, v.get(c)));
            case RepriseDeLHistoriqueMedical.ANALYSES -> cle.put("datePrelevement", v.get("datePrelevement"));
            default -> {
            }
        }
        return cle;
    }

    @Test
    void should_write_the_natures_the_batch_can_cancel() {
        assertThat(RepriseDeLHistoriqueMedical.NATURES).containsExactlyInAnyOrderElementsOf(DonneesMedicalesDeLaReprise.NATURES_DU_LOT);
    }

    @Test
    void should_create_a_line_stamped_and_find_it_by_its_key_in_the_centre_of_the_batch_only() {
        for (String nature : RepriseDeLHistoriqueMedical.NATURES) {
            assertThat(reprise().dejaPresente(CENTRE_A, nature, cle(nature, PATIENT))).as(nature + " : rien avant").isEmpty();

            UUID id = reprise().reprendre(CENTRE_A, nature, valeurs(nature, PATIENT));

            assertThat(reprise().dejaPresente(CENTRE_A, nature, cle(nature, PATIENT))).as(nature).contains(id);
            assertThat(reprise().dejaPresente(CENTRE_B, nature, cle(nature, PATIENT))).as(nature + " : un autre centre ne la voit pas").isEmpty();
            assertThat(reprise().dejaPresente(CENTRE_A, nature, cle(nature, AUTRE_PATIENT))).as(nature + " : un autre patient non plus").isEmpty();
            assertThat(valeur(nature, id, "patientId")).isEqualTo(PATIENT.toString());
            assertThat(horodate(nature, id, "created_at")).as(nature + " : date de création").isTrue();
            assertThat(horodate(nature, id, "updated_at")).as(nature + " : date de modification")
                    .isEqualTo(!RepriseDeLHistoriqueMedical.ABORDS_VASCULAIRES.equals(nature));
        }
    }

    @Test
    void should_read_a_missing_key_value_as_null() {
        String nature = RepriseDeLHistoriqueMedical.ANTECEDENTS;
        Map<String, Object> sansLibelle = valeurs(nature, PATIENT);
        sansLibelle.remove("libelleLibre");
        UUID sans = reprise().reprendre(CENTRE_A, nature, sansLibelle);
        reprise().reprendre(CENTRE_A, nature, valeurs(nature, PATIENT));
        Map<String, Object> cle = cle(nature, PATIENT);
        cle.put("libelleLibre", null);

        assertThat(reprise().dejaPresente(CENTRE_A, nature, cle)).as("libellé nul : la ligne sans libellé").contains(sans);
    }

    @Test
    void should_update_a_line_only_in_its_centre_and_never_change_its_patient() {
        for (String nature : RepriseDeLHistoriqueMedical.NATURES) {
            UUID id = reprise().reprendre(CENTRE_A, nature, valeurs(nature, PATIENT));
            Map<String, Object> modif = new LinkedHashMap<>();
            modif.put("patientId", AUTRE_PATIENT);
            String champ = champs(nature).get(1);
            Object nouvelle = nouvelleValeur(nature, champ);
            modif.put(champ, nouvelle);

            reprise().completer(CENTRE_B, nature, id, modif);
            assertThat(valeur(nature, id, champ)).as(nature + " : un autre centre ne modifie rien")
                    .isEqualTo(String.valueOf(valeurs(nature, PATIENT).get(champ)));

            reprise().completer(CENTRE_A, nature, id, modif);
            assertThat(valeur(nature, id, champ)).as(nature + " : modifiée dans le centre du lot").isEqualTo(String.valueOf(nouvelle));
            assertThat(valeur(nature, id, "patientId")).as(nature + " : le patient ne change jamais").isEqualTo(PATIENT.toString());
            assertThat(horodate(nature, id, "updated_at")).isEqualTo(!RepriseDeLHistoriqueMedical.ABORDS_VASCULAIRES.equals(nature));

            reprise().completer(CENTRE_A, nature, id, Map.of("patientId", AUTRE_PATIENT));
            assertThat(valeur(nature, id, "patientId")).as(nature + " : rien à écrire, rien ne change").isEqualTo(PATIENT.toString());
        }
    }

    private static Object nouvelleValeur(String nature, String champ) {
        Object actuelle = valeurs(nature, PATIENT).get(champ);
        if (actuelle instanceof LocalDate d) return d.plusDays(1);
        if (actuelle instanceof Boolean b) return !b;
        if (actuelle instanceof java.math.BigDecimal) return new java.math.BigDecimal("12.50");
        return actuelle + "-bis";
    }

    @Test
    void should_refuse_a_nature_or_a_field_outside_the_closed_lists() {
        assertThatThrownBy(() -> reprise().dejaPresente(CENTRE_A, "patients", Map.of("patientId", PATIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprise().reprendre(CENTRE_A, "ordonnances", valeurs("ordonnances", PATIENT)))
                .as("saisie dans la plateforme, jamais écrite par le lot").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprise().reprendre(CENTRE_A, "seances; DROP TABLE x", Map.of("patientId", PATIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprise().dejaPresente(CENTRE_A, RepriseDeLHistoriqueMedical.ANALYSES,
                Map.of("patientId", PATIENT, "id; DROP TABLE x", "1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reprise().reprendre(CENTRE_A, RepriseDeLHistoriqueMedical.ANALYSES,
                Map.of("patientId", PATIENT, "centerId", CENTRE_B))).isInstanceOf(IllegalArgumentException.class);
        UUID id = reprise().reprendre(CENTRE_A, RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE, valeurs(RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE, PATIENT));
        assertThatThrownBy(() -> reprise().completer(CENTRE_A, RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE, id, Map.of("createdAt", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Optional.ofNullable(valeur(RepriseDeLHistoriqueMedical.DOSSIER_DE_BASE, id, "patientId"))).isPresent();
    }
}
