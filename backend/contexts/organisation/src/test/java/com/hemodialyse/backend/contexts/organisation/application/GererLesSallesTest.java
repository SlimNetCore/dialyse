package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles.FicheDeSalle;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles.LigneAImporter;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeSalle;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PL11-4 : gérer les salles d'un centre (D3 : tous les usages, D4 : désactivation, D8 : champs non précisés conservés, D10 : échec = refus). */
class GererLesSallesTest {

    private static final UUID CENTRE = new UUID(1, 1);
    private static final AtomicLong SEQ = new AtomicLong();

    static class EnMemoire implements SalleRepository {
        final List<Salle> lignes = new ArrayList<>();

        @Override
        public Optional<Salle> trouver(CentreId centre, SalleId id) {
            return lignes.stream().filter(s -> s.centreId().equals(centre) && s.id().equals(id)).findFirst();
        }

        @Override
        public void sauvegarder(Salle salle) {
            lignes.removeIf(s -> s.id().equals(salle.id()));
            lignes.add(salle);
        }

        @Override
        public List<Salle> listerParCentre(CentreId centre) {
            return listerTousParCentre(centre).stream().filter(Salle::actif).toList();
        }

        @Override
        public List<Salle> listerTousParCentre(CentreId centre) {
            return lignes.stream().filter(s -> s.centreId().equals(centre)).sorted(Comparator.comparing(Salle::code)).toList();
        }

        @Override
        public PageDeSalles page(CentreId centre, String recherche, int page, int taille) {
            List<Salle> propres = listerParCentre(centre).stream().filter(s -> recherche == null
                    || s.code().toLowerCase().contains(recherche.toLowerCase())
                    || s.nom().toLowerCase().contains(recherche.toLowerCase())).toList();
            return new PageDeSalles(propres.stream().skip((long) page * taille).limit(taille).toList(), propres.size());
        }

        @Override
        public void supprimer(CentreId centre, SalleId id) {
            lignes.removeIf(s -> s.id().equals(id) && s.centreId().equals(centre));
        }
    }

    private final EnMemoire depot = new EnMemoire();
    private final List<UsageDeLaSalle> usages = new ArrayList<>();
    private final GererLesSalles salles = new GererLesSalles(depot, () -> new UUID(2, SEQ.incrementAndGet()), UniteDeTravail.DIRECTE, usages);

    @Test
    void une_salle_creee_sans_precision_n_est_ni_d_isolement_ni_de_capacite_connue() {
        FicheDeSalle fiche = salles.creer(CENTRE, " S1 ", " Salle 1 ", null, null);

        assertThat(fiche.code()).isEqualTo("S1");
        assertThat(fiche.isolement()).isEqualTo("NON");
        assertThat(fiche.capacite()).isNull();
    }

    @Test
    void une_saisie_illisible_est_refusee_avec_les_anomalies_de_l_administration() {
        assertThatThrownBy(() -> salles.creer(CENTRE, "", "Salle", "peut-etre", "0")).isInstanceOfSatisfying(RefusDeSalle.class, r -> {
            assertThat(r.motif()).isEqualTo(RefusDeSalle.Motif.SAISIE_INVALIDE);
            assertThat(r.anomalies()).extracting(a -> a.code()).containsExactly("REQUIRED", "INVALID_VALUE", "INTEGER_OUT_OF_RANGE");
        });
    }

    @Test
    void un_code_deja_pris_dans_le_centre_est_refuse_casse_et_espaces_ignores() {
        salles.creer(CENTRE, "S1", "Salle 1", null, null);

        assertThatThrownBy(() -> salles.creer(CENTRE, " s1 ", "Doublon", null, null)).isInstanceOfSatisfying(RefusDeSalle.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeSalle.Motif.DEJA_EXISTANTE));
        assertThat(salles.creer(new UUID(1, 2), "S1", "Autre centre", null, null).code()).isEqualTo("S1");
    }

    @Test
    void une_salle_modifiee_sans_capacite_ni_isolement_les_conserve() {
        FicheDeSalle creee = salles.creer(CENTRE, "S1", "Salle 1", "OUI", "12");

        FicheDeSalle modifiee = salles.modifier(CENTRE, creee.id(), "S1", "Salle un", null, null);

        assertThat(modifiee.nom()).isEqualTo("Salle un");
        assertThat(modifiee.capacite()).isEqualTo(12);
        assertThat(modifiee.isolement()).isEqualTo("OUI");
    }

    @Test
    void une_salle_inconnue_ne_se_modifie_ni_ne_se_supprime() {
        assertThatThrownBy(() -> salles.modifier(CENTRE, new UUID(9, 9), "S1", "Salle", null, null)).isInstanceOfSatisfying(RefusDeSalle.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeSalle.Motif.INTROUVABLE));
        assertThatThrownBy(() -> salles.supprimer(CENTRE, new UUID(9, 9))).isInstanceOf(RefusDeSalle.class);
    }

    @Test
    void une_salle_que_rien_ne_reference_est_supprimee() {
        FicheDeSalle creee = salles.creer(CENTRE, "S1", "Salle 1", null, null);
        usages.add(salle -> 0);

        assertThat(salles.supprimer(CENTRE, creee.id())).isFalse();
        assertThat(depot.lignes).isEmpty();
    }

    @Test
    void une_salle_referencee_par_un_seul_proprietaire_est_desactivee_et_n_est_plus_proposee() {
        FicheDeSalle creee = salles.creer(CENTRE, "S1", "Salle 1", null, null);
        usages.add(salle -> 0);
        usages.add(salle -> 2);

        assertThat(salles.supprimer(CENTRE, creee.id())).isTrue();

        assertThat(depot.lignes).hasSize(1);
        assertThat(salles.proposees(CENTRE)).isEmpty();
        assertThat(salles.lister(CENTRE, null, 0, 20).total()).isZero();
    }

    @Test
    void l_echec_du_controle_d_usage_d_un_proprietaire_refuse_la_suppression_et_conserve_la_salle() {
        FicheDeSalle creee = salles.creer(CENTRE, "S1", "Salle 1", null, null);
        usages.add(salle -> {
            throw new IllegalStateException("table indisponible");
        });

        assertThatThrownBy(() -> salles.supprimer(CENTRE, creee.id())).isInstanceOfSatisfying(RefusDeSalle.class,
                r -> assertThat(r.motif()).isEqualTo(RefusDeSalle.Motif.CONTROLE_D_USAGE_IMPOSSIBLE));
        assertThat(salles.proposees(CENTRE)).hasSize(1);
    }

    @Test
    void recreer_ou_reimporter_le_code_d_une_salle_desactivee_la_reactive() {
        FicheDeSalle creee = salles.creer(CENTRE, "S1", "Salle 1", "OUI", "8");
        usages.add(salle -> 1);
        salles.supprimer(CENTRE, creee.id());

        FicheDeSalle recreee = salles.creer(CENTRE, "S1", "Salle 1 bis", null, null);

        assertThat(recreee.id()).isEqualTo(creee.id());
        assertThat(recreee.capacite()).isEqualTo(8);
        assertThat(depot.lignes).hasSize(1);
        assertThat(salles.proposees(CENTRE)).hasSize(1);
    }

    @Test
    void un_import_sans_les_colonnes_facultatives_conserve_l_isolement_et_la_capacite_d_une_salle_existante() {
        salles.creer(CENTRE, "S1", "Salle 1", "OUI", "6");

        var bilan = salles.importer(CENTRE, List.of(new LigneAImporter(2, "S1", "Salle un", null, null),
                new LigneAImporter(3, "S2", "Salle 2", null, null)), true);

        assertThat(bilan.aCreer()).isEqualTo(1);
        assertThat(bilan.aMettreAJour()).isEqualTo(1);
        assertThat(bilan.appliquee()).isTrue();
        FicheDeSalle s1 = salles.proposees(CENTRE).stream().filter(f -> f.code().equals("S1")).findFirst().orElseThrow();
        assertThat(s1.nom()).isEqualTo("Salle un");
        assertThat(s1.isolement()).isEqualTo("OUI");
        assertThat(s1.capacite()).isEqualTo(6);
    }

    @Test
    void un_import_avec_une_anomalie_ou_un_doublon_dans_le_fichier_n_ecrit_rien() {
        var bilan = salles.importer(CENTRE, List.of(new LigneAImporter(2, "S1", "Salle 1", null, null),
                new LigneAImporter(3, "s1", "Doublon", null, null), new LigneAImporter(4, "S3", "", null, null)), true);

        assertThat(bilan.appliquee()).isFalse();
        assertThat(bilan.anomalies()).extracting(a -> a.code()).containsExactly("DUPLICATE_IN_FILE", "REQUIRED");
        assertThat(depot.lignes).isEmpty();
    }
}
