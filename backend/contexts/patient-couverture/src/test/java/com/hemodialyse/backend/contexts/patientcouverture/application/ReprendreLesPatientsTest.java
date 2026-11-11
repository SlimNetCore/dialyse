package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AssuresEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.AssureSurLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients.PatientDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients.ReportDuPatient;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reprise des patients (S6b-2, ADR 0134) : une reprise crée ou met à jour la fiche, n'efface jamais une donnée, rattache l'assuré
 * principal comme à l'écran, et retrouve sa fiche (avec ses affectations) quand le patient a été supprimé depuis (KB-S6a-3).
 */
class ReprendreLesPatientsTest {

    private static final LocalDate NAISSANCE = LocalDate.of(1962, 3, 15);
    private static final LocalDate ADMISSION = LocalDate.of(2019, 1, 2);

    private PatientsEnMemoireDeLaReprise patients;
    private AssuresEnMemoire assures;
    private ReprendreLesPatients reprise;

    @BeforeEach
    void preparer() {
        patients = new PatientsEnMemoireDeLaReprise();
        assures = new AssuresEnMemoire();
        reprise = new ReprendreLesPatients(patients, assures, new IdentifiantsSequentiels(), new HorlogeFixe());
    }

    @Test
    void un_patient_assure_lui_meme_est_cree_avec_son_code_ses_defauts_et_sa_propre_fiche_d_assure() {
        ReportDuPatient r = new Report("ZP222", "KACI").prenom("Nadia").sexe("F").vacancier().qualite("ASSURE_LUI_MEME").assure(null)
                .adresse("12 rue X").telPersonnel("0550").telMobile("0661").groupeSanguin("O+").build();

        UUID id = reprise.reprendre(CENTRE.valeur(), null, r);

        Fiche fiche = patients.trouver(CENTRE, id).orElseThrow();
        assertThat(fiche.code()).startsWith("PAT-");
        assertThat(fiche.nombreEnfants()).isZero();
        assertThat(fiche.vacancier()).isTrue();
        assertThat(fiche.assureNumeroAssurance()).isEqualTo("ZP222");
        assertThat(fiche.assure()).isEqualTo(new AssureSurLaFiche("F", "KACI", "Nadia", NAISSANCE, "0550", "12 rue X", "O+", "0661",
                null));
        assertThat(patients.creesLe.get(id)).isEqualTo(MAINTENANT);
        assertThat(assures.affectations).isEmpty();
    }

    @Test
    void un_ayant_droit_est_rattache_a_son_assure_par_une_affectation_principale_commencant_a_l_admission() {
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").build());

        Fiche fiche = patients.trouver(CENTRE, id).orElseThrow();
        assertThat(fiche.assureNumeroAssurance()).isEqualTo("ZP900");
        assertThat(fiche.assure()).isEqualTo(AssureSurLaFiche.vide());
        assertThat(assures.historique(CENTRE, new PatientId(id))).singleElement().satisfies(a -> {
            assertThat(a.numeroAssurance()).isEqualTo("ZP900");
            assertThat(a.principal()).isTrue();
            assertThat(a.dateDebut()).isEqualTo(ADMISSION);
        });
    }

    @Test
    void un_rejeu_n_efface_aucune_valeur_renseignee_et_remplace_celles_du_fichier() {
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").code("C-1").civilite("M.").nombreEnfants(3)
                .adresse("12 rue X").telMobile("0661").observation("Note initiale").jours(Set.of(DayOfWeek.MONDAY)).build());

        reprise.reprendre(CENTRE.valeur(), id, new Report("ZP111", "BENALI-MODIFIE").etat("TRANSFERE").build());

        Fiche fiche = patients.trouver(CENTRE, id).orElseThrow();
        assertThat(fiche.nom()).isEqualTo("BENALI-MODIFIE");
        assertThat(fiche.etat()).isEqualTo("TRANSFERE");
        assertThat(fiche.code()).isEqualTo("C-1");
        assertThat(fiche.civilite()).isEqualTo("M.");
        assertThat(fiche.nombreEnfants()).isEqualTo(3);
        assertThat(fiche.adresse()).isEqualTo("12 rue X");
        assertThat(fiche.telMobile()).isEqualTo("0661");
        assertThat(fiche.observation()).isEqualTo("Note initiale");
        assertThat(fiche.jours()).containsExactly(DayOfWeek.MONDAY);
    }

    @Test
    void la_place_et_les_jours_ne_changent_que_lorsque_le_fichier_les_renseigne() {
        UUID salle = UUID.randomUUID();
        UUID generateur = UUID.randomUUID();
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").salle(salle).generateur(generateur)
                .jours(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).build());

        reprise.reprendre(CENTRE.valeur(), id, new Report("ZP111", "BENALI").build());

        Fiche fiche = patients.trouver(CENTRE, id).orElseThrow();
        assertThat(fiche.salle()).isEqualTo(salle);
        assertThat(fiche.generateur()).isEqualTo(generateur);
        assertThat(fiche.jours()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void un_autre_assure_principal_remplace_le_precedent_mais_le_meme_assure_a_la_casse_pres_ne_cree_rien() {
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").build());

        reprise.reprendre(CENTRE.valeur(), id, new Report("ZP111", "BENALI").assure("zp900").build());
        assertThat(assures.historique(CENTRE, new PatientId(id))).hasSize(1);

        reprise.reprendre(CENTRE.valeur(), id, new Report("ZP111", "BENALI").assure("ZP901").build());
        List<Affectation> historique = assures.historique(CENTRE, new PatientId(id));
        assertThat(historique).hasSize(2);
        assertThat(historique).filteredOn(Affectation::principal).singleElement().satisfies(a ->
                assertThat(a.numeroAssurance()).isEqualTo("ZP901"));
        assertThat(patients.trouver(CENTRE, id).orElseThrow().assureNumeroAssurance()).isEqualTo("ZP901");
    }

    @Test
    void un_patient_qui_devient_son_propre_assure_n_a_plus_d_affectation_principale() {
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").build());

        reprise.reprendre(CENTRE.valeur(), id, new Report("ZP111", "BENALI").qualite("ASSURE_LUI_MEME").assure(null).build());

        assertThat(assures.historique(CENTRE, new PatientId(id))).hasSize(1).noneMatch(Affectation::principal);
        Fiche fiche = patients.trouver(CENTRE, id).orElseThrow();
        assertThat(fiche.assureNumeroAssurance()).isEqualTo("ZP111");
        assertThat(fiche.assure().nom()).isEqualTo("BENALI");
    }

    @Test
    void un_patient_supprime_depuis_est_recree_sous_son_identifiant_et_ses_affectations_ne_sont_pas_orphelines() {
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").build());
        patients.lignes.clear();

        UUID recree = reprise.reprendre(CENTRE.valeur(), id, new Report("ZP111", "BENALI").build());

        assertThat(recree).isEqualTo(id);
        assertThat(patients.trouver(CENTRE, id)).isPresent();
        assertThat(assures.historique(CENTRE, new PatientId(id))).hasSize(1).allMatch(Affectation::principal);
    }

    @Test
    void la_fiche_d_un_autre_centre_n_est_jamais_mise_a_jour() {
        UUID id = reprise.reprendre(AUTRE_CENTRE.valeur(), null, new Report("ZP111", "DU-CENTRE-B").build());

        UUID cree = reprise.reprendre(CENTRE.valeur(), id, new Report("ZP222", "BENALI").build());

        assertThat(cree).isNotEqualTo(id);
        assertThat(patients.trouver(AUTRE_CENTRE, id).orElseThrow().nom()).isEqualTo("DU-CENTRE-B");
        assertThat(patients.trouver(CENTRE, cree).orElseThrow().nom()).isEqualTo("BENALI");
    }

    @Test
    void les_patients_du_centre_sont_listes_pour_le_rapprochement() {
        UUID id = reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", "BENALI").build());
        reprise.reprendre(AUTRE_CENTRE.valeur(), null, new Report("ZP222", "DU-CENTRE-B").build());

        assertThat(reprise.duCentre(CENTRE.valeur())).singleElement().satisfies(p ->
                assertThat(p).isEqualTo(new PatientDuCentre(id, "ZP111", p.code(), "BENALI", "Karim", NAISSANCE)));
    }

    @Test
    void un_nom_absent_est_refuse_sans_rien_ecrire() {
        assertThatThrownBy(() -> reprise.reprendre(CENTRE.valeur(), null, new Report("ZP111", " ").build()))
                .isInstanceOf(SaisieInvalide.class);
        assertThat(patients.lignes).isEmpty();
        assertThat(assures.affectations).isEmpty();
    }

    /** Report d'un patient ayant droit de ZP900 par défaut (ENFANT), construit champ par champ. */
    private static final class Report {
        private final String numero;
        private final String nom;
        private String prenom = "Karim";
        private String sexe = "M";
        private boolean vacancier;
        private String etat = "PERMANENT";
        private String qualite = "ENFANT";
        private String code;
        private String civilite;
        private Integer nombreEnfants;
        private String adresse;
        private String telPersonnel;
        private String telMobile;
        private String groupeSanguin;
        private String observation;
        private UUID salle;
        private UUID generateur;
        private Set<DayOfWeek> jours;
        private String assure = "ZP900";

        Report(String numero, String nom) {
            this.numero = numero;
            this.nom = nom;
        }

        Report prenom(String v) {
            prenom = v;
            return this;
        }

        Report sexe(String v) {
            sexe = v;
            return this;
        }

        Report vacancier() {
            vacancier = true;
            return this;
        }

        Report etat(String v) {
            etat = v;
            return this;
        }

        Report qualite(String v) {
            qualite = v;
            return this;
        }

        Report code(String v) {
            code = v;
            return this;
        }

        Report civilite(String v) {
            civilite = v;
            return this;
        }

        Report nombreEnfants(int v) {
            nombreEnfants = v;
            return this;
        }

        Report adresse(String v) {
            adresse = v;
            return this;
        }

        Report telPersonnel(String v) {
            telPersonnel = v;
            return this;
        }

        Report telMobile(String v) {
            telMobile = v;
            return this;
        }

        Report groupeSanguin(String v) {
            groupeSanguin = v;
            return this;
        }

        Report observation(String v) {
            observation = v;
            return this;
        }

        Report salle(UUID v) {
            salle = v;
            return this;
        }

        Report generateur(UUID v) {
            generateur = v;
            return this;
        }

        Report jours(Set<DayOfWeek> v) {
            jours = v;
            return this;
        }

        Report assure(String v) {
            assure = v;
            return this;
        }

        ReportDuPatient build() {
            return new ReportDuPatient(numero, nom, prenom, sexe, NAISSANCE, ADMISSION, vacancier, etat, qualite, code, civilite,
                    null, null, nombreEnfants, null, adresse, telPersonnel, telMobile, null, null, groupeSanguin, null, observation,
                    null, null, salle, null, generateur, null, null, jours, assure);
        }
    }

    /** Fiches en mémoire de la reprise (fidèle à la table : bornée au centre). */
    static final class PatientsEnMemoireDeLaReprise implements PatientsDeLaReprise {
        final Map<UUID, Ligne> lignes = new LinkedHashMap<>();
        final Map<UUID, Instant> creesLe = new LinkedHashMap<>();

        record Ligne(CentreId centre, Fiche fiche) {
        }

        @Override
        public List<PatientDuCentre> duCentre(CentreId centre) {
            List<PatientDuCentre> out = new ArrayList<>();
            lignes.values().stream().filter(l -> l.centre().equals(centre)).forEach(l -> out.add(new PatientDuCentre(l.fiche().id(),
                    l.fiche().numeroAssurance(), l.fiche().code(), l.fiche().nom(), l.fiche().prenom(), l.fiche().dateNaissance())));
            return out;
        }

        @Override
        public Optional<Fiche> trouver(CentreId centre, UUID id) {
            return Optional.ofNullable(lignes.get(id)).filter(l -> l.centre().equals(centre)).map(Ligne::fiche);
        }

        @Override
        public boolean identifiantPris(UUID id) {
            return lignes.containsKey(id);
        }

        @Override
        public void creer(CentreId centre, Fiche fiche, Instant creeLe) {
            lignes.put(fiche.id(), new Ligne(centre, fiche));
            creesLe.put(fiche.id(), creeLe);
        }

        @Override
        public void mettreAJour(CentreId centre, Fiche fiche) {
            lignes.put(fiche.id(), new Ligne(centre, fiche));
        }
    }
}
