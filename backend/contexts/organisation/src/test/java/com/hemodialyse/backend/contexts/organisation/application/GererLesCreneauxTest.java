package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.BilanDImport;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.FicheDeCreneau;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.LigneAImporter;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeCreneau;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDuCreneau;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PL11-2 : gérer les créneaux d'un centre, avec un dépôt en mémoire (D3 : tous les usages, D4 : désactivation, D10 : échec = refus). */
class GererLesCreneauxTest {

    private static final UUID CENTRE = new UUID(1, 1);
    private static final AtomicLong SEQ = new AtomicLong();

    /** Dépôt en mémoire. */
    static class EnMemoire implements CreneauRepository {
        final List<Creneau> lignes = new ArrayList<>();

        @Override
        public Optional<Creneau> trouver(CentreId centre, CreneauId id) {
            return lignes.stream().filter(c -> c.centreId().equals(centre) && c.id().equals(id)).findFirst();
        }

        @Override
        public void sauvegarder(Creneau creneau) {
            lignes.removeIf(c -> c.id().equals(creneau.id()));
            lignes.add(creneau);
        }

        @Override
        public List<Creneau> listerParCentre(CentreId centre) {
            return listerTousParCentre(centre).stream().filter(Creneau::actif).toList();
        }

        @Override
        public List<Creneau> listerTousParCentre(CentreId centre) {
            return lignes.stream().filter(c -> c.centreId().equals(centre)).sorted(java.util.Comparator.comparing(Creneau::code)).toList();
        }

        @Override
        public PageDeCreneaux page(CentreId centre, String recherche, int page, int taille) {
            List<Creneau> propres = listerParCentre(centre).stream().filter(c -> recherche == null
                    || c.code().toLowerCase().contains(recherche.toLowerCase())
                    || c.libelle().toLowerCase().contains(recherche.toLowerCase())).toList();
            return new PageDeCreneaux(propres.stream().skip((long) page * taille).limit(taille).toList(), propres.size());
        }

        @Override
        public void supprimer(CentreId centre, CreneauId id) {
            lignes.removeIf(c -> c.id().equals(id) && c.centreId().equals(centre));
        }
    }

    private final EnMemoire depot = new EnMemoire();
    private final List<UsageDuCreneau> usages = new ArrayList<>();
    private final GererLesCreneaux creneaux = new GererLesCreneaux(depot, () -> new UUID(2, SEQ.incrementAndGet()),
            UniteDeTravail.DIRECTE, usages);

    @Test
    void un_creneau_cree_est_propose_avec_son_code_et_son_libelle_nettoyes() {
        FicheDeCreneau fiche = creneaux.creer(CENTRE, "  C1 ", " Matin ");

        assertThat(fiche.code()).isEqualTo("C1");
        assertThat(fiche.libelle()).isEqualTo("Matin");
        assertThat(depot.listerParCentre(new CentreId(CENTRE))).hasSize(1);
    }

    @Test
    void un_code_deja_pris_dans_le_centre_est_refuse_casse_et_espaces_ignores() {
        creneaux.creer(CENTRE, "C1", "Matin");

        assertThatThrownBy(() -> creneaux.creer(CENTRE, " c1 ", "Doublon")).isInstanceOfSatisfying(RefusDeCreneau.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeCreneau.Motif.DEJA_EXISTANT));
        assertThat(creneaux.creer(new UUID(1, 2), "C1", "Autre centre").code()).isEqualTo("C1");
    }

    @Test
    void une_saisie_sans_code_ni_libelle_signale_les_deux_anomalies() {
        assertThatThrownBy(() -> creneaux.creer(CENTRE, "", "")).isInstanceOfSatisfying(RefusDeCreneau.class, r ->
                assertThat(r.anomalies()).extracting(a -> a.champ() + ":" + a.code()).containsExactly("code:REQUIRED", "libelle:REQUIRED"));
        assertThatThrownBy(() -> creneaux.creer(CENTRE, "c".repeat(51), "x")).isInstanceOfSatisfying(RefusDeCreneau.class, r ->
                assertThat(r.anomalies().getFirst().message()).isEqualTo("« Code » dépasse 50 caractères (51)."));
    }

    @Test
    void un_creneau_que_rien_ne_reference_est_supprime() {
        FicheDeCreneau fiche = creneaux.creer(CENTRE, "C1", "Matin");

        boolean desactive = creneaux.supprimer(CENTRE, fiche.id());

        assertThat(desactive).isFalse();
        assertThat(depot.lignes).isEmpty();
    }

    @Test
    void un_creneau_reference_par_un_seul_proprietaire_est_desactive_et_n_est_plus_propose() {
        FicheDeCreneau fiche = creneaux.creer(CENTRE, "C1", "Matin");
        usages.add(c -> 0);
        usages.add(c -> 2); // ex. les affectations d'infirmiers de la Planification

        boolean desactive = creneaux.supprimer(CENTRE, fiche.id());

        assertThat(desactive).isTrue();
        assertThat(depot.lignes).hasSize(1).allMatch(c -> !c.actif());
        assertThat(creneaux.lister(CENTRE, null, 0, 20).lignes()).isEmpty();
        assertThatThrownBy(() -> creneaux.supprimer(CENTRE, fiche.id())).isInstanceOfSatisfying(RefusDeCreneau.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeCreneau.Motif.INTROUVABLE));
    }

    @Test
    void si_un_proprietaire_ne_sait_pas_repondre_la_suppression_est_refusee_et_le_creneau_conserve() {
        FicheDeCreneau fiche = creneaux.creer(CENTRE, "C1", "Matin");
        usages.add(c -> 0);
        usages.add(c -> {
            throw new IllegalStateException("table indisponible");
        });

        assertThatThrownBy(() -> creneaux.supprimer(CENTRE, fiche.id())).isInstanceOfSatisfying(RefusDeCreneau.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeCreneau.Motif.CONTROLE_D_USAGE_IMPOSSIBLE));
        assertThat(depot.lignes).hasSize(1).allMatch(Creneau::actif);
    }

    @Test
    void recreer_le_code_d_un_creneau_desactive_le_reactive_au_lieu_de_creer_un_doublon() {
        FicheDeCreneau fiche = creneaux.creer(CENTRE, "C1", "Matin");
        usages.add(c -> 1);
        creneaux.supprimer(CENTRE, fiche.id());

        FicheDeCreneau retrouve = creneaux.creer(CENTRE, "C1", "Matin bis");

        assertThat(retrouve.id()).isEqualTo(fiche.id());
        assertThat(depot.lignes).hasSize(1).allMatch(Creneau::actif);
        assertThat(retrouve.libelle()).isEqualTo("Matin bis");
    }

    @Test
    void la_modification_garde_son_propre_code_mais_refuse_celui_d_un_autre() {
        FicheDeCreneau c1 = creneaux.creer(CENTRE, "C1", "Matin");
        FicheDeCreneau c2 = creneaux.creer(CENTRE, "C2", "Midi");

        assertThat(creneaux.modifier(CENTRE, c1.id(), "C1", "Matin bis").libelle()).isEqualTo("Matin bis");
        assertThatThrownBy(() -> creneaux.modifier(CENTRE, c2.id(), "C1", "x")).isInstanceOfSatisfying(RefusDeCreneau.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeCreneau.Motif.DEJA_EXISTANT));
        assertThatThrownBy(() -> creneaux.modifier(new UUID(1, 2), c1.id(), "C9", "x")).isInstanceOfSatisfying(RefusDeCreneau.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeCreneau.Motif.INTROUVABLE));
    }

    @Test
    void l_import_est_tout_ou_rien_met_a_jour_par_code_et_signale_les_doublons_du_fichier() {
        creneaux.creer(CENTRE, "C1", "Ancien");

        BilanDImport verification = creneaux.importer(CENTRE, List.of(new LigneAImporter(2, "C1", "Matin"),
                new LigneAImporter(3, "C2", "Midi")), false);
        assertThat(verification.aCreer()).isEqualTo(1);
        assertThat(verification.aMettreAJour()).isEqualTo(1);
        assertThat(verification.appliquee()).isFalse();
        assertThat(depot.lignes).hasSize(1);

        BilanDImport refuse = creneaux.importer(CENTRE, List.of(new LigneAImporter(2, "C4", "Nuit"),
                new LigneAImporter(3, "c4", "Nuit bis"), new LigneAImporter(4, "C5", "")), true);
        assertThat(refuse.anomalies()).extracting(a -> a.ligne() + ":" + a.code()).containsExactly("3:DUPLICATE_IN_FILE", "4:REQUIRED");
        assertThat(depot.lignes).hasSize(1);

        BilanDImport applique = creneaux.importer(CENTRE, List.of(new LigneAImporter(2, "C1", "Matin"),
                new LigneAImporter(3, "C2", "Midi")), true);
        assertThat(applique.appliquee()).isTrue();
        assertThat(depot.lignes).extracting(Creneau::libelle).containsExactlyInAnyOrder("Matin", "Midi");
    }
}
