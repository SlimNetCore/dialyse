package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins.FicheDeMedecin;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins.LigneAImporter;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeMedecin;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PL11-3 (ADR 0159) : D5 (désactivation d'un médecin utilisé), D6 (réactivation), D7 (une seule règle d'unicité). */
class GererLesMedecinsTest {

    static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID AUTRE_CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    static class MedecinsEnMemoire implements MedecinRepository {
        final List<Medecin> tous = new ArrayList<>();

        @Override
        public Optional<Medecin> trouver(MedecinId id) {
            return tous.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        @Override
        public void sauvegarder(Medecin m) {
            if (!tous.contains(m)) tous.add(m);
        }

        @Override
        public PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size) {
            List<Medecin> actifs = tous.stream().filter(m -> m.centreId().equals(centreId) && m.actif()).toList();
            return PagedResult.of(actifs, actifs.size(), page, size);
        }

        @Override
        public List<Medecin> listerTousParCentre(CentreId centreId) {
            return tous.stream().filter(m -> m.centreId().equals(centreId)).toList();
        }

        @Override
        public PageDeMedecins page(CentreId centreId, String recherche, int page, int taille) {
            List<Medecin> actifs = tous.stream().filter(m -> m.centreId().equals(centreId) && m.actif())
                    .filter(m -> recherche == null || (m.nom() + " " + m.prenom() + " " + m.specialite()).toLowerCase().contains(recherche.toLowerCase()))
                    .sorted(Comparator.comparing(Medecin::nom)).toList();
            return new PageDeMedecins(actifs, actifs.size());
        }
    }

    private final MedecinsEnMemoire repo = new MedecinsEnMemoire();
    private final GererLesMedecins medecins = new GererLesMedecins(repo, UUID::randomUUID, UniteDeTravail.DIRECTE);

    @Test
    void un_medecin_utilise_peut_etre_desactive_et_reste_dans_le_centre() {
        FicheDeMedecin m = medecins.creer(CENTRE, "Benali", "Karim", "Neph");
        medecins.desactiver(CENTRE, m.id());
        assertEquals(0, medecins.lister(CENTRE, null, 0, 20).total());
        assertFalse(repo.trouver(new MedecinId(m.id())).orElseThrow().actif());
    }

    @Test
    void desactiver_deux_fois_est_introuvable_pour_l_administration() {
        FicheDeMedecin m = medecins.creer(CENTRE, "Benali", "Karim", null);
        medecins.desactiver(CENTRE, m.id());
        assertEquals(RefusDeMedecin.Motif.INTROUVABLE,
                assertThrows(RefusDeMedecin.class, () -> medecins.desactiver(CENTRE, m.id())).motif());
    }

    @Test
    void creer_un_medecin_de_meme_nom_qu_un_desactive_le_reactive_sans_doublon() {
        FicheDeMedecin ancien = medecins.creer(CENTRE, "Benali", "Karim", "Neph");
        medecins.desactiver(CENTRE, ancien.id());
        FicheDeMedecin recree = medecins.creer(CENTRE, " BENALI ", "karim", "Cardio");
        assertEquals(ancien.id(), recree.id());
        assertEquals(1, repo.listerTousParCentre(new CentreId(CENTRE)).size());
        assertTrue(repo.trouver(new MedecinId(ancien.id())).orElseThrow().actif());
        assertEquals("Cardio", recree.specialite());
    }

    @Test
    void l_import_reactive_un_medecin_desactive() {
        FicheDeMedecin m = medecins.creer(CENTRE, "Ouaret", "Mustapha", "Card");
        medecins.desactiver(CENTRE, m.id());
        var bilan = medecins.importer(CENTRE, List.of(new LigneAImporter(2, "Ouaret", "Mustapha", "Neph")), true);
        assertEquals(0, bilan.aCreer());
        assertEquals(1, bilan.aMettreAJour());
        assertEquals(1, repo.listerTousParCentre(new CentreId(CENTRE)).size());
        assertTrue(repo.listerTousParCentre(new CentreId(CENTRE)).getFirst().actif());
    }

    @Test
    void un_nom_et_un_prenom_designent_un_seul_medecin_propose_par_centre() {
        medecins.creer(CENTRE, "Benali", "Karim", null);
        assertEquals(RefusDeMedecin.Motif.DEJA_EXISTANT,
                assertThrows(RefusDeMedecin.class, () -> medecins.creer(CENTRE, "benali", " KARIM ", null)).motif());
        medecins.creer(CENTRE, "Benali", "Sofiane", null);
        medecins.creer(CENTRE, "Benali", null, null);
        medecins.creer(AUTRE_CENTRE, "Benali", "Karim", null);
        assertEquals(RefusDeMedecin.Motif.DEJA_EXISTANT,
                assertThrows(RefusDeMedecin.class, () -> medecins.creer(CENTRE, "Benali", "", null)).motif());
    }

    @Test
    void la_saisie_est_controlee_comme_pour_l_administration() {
        assertEquals(RefusDeMedecin.Motif.SAISIE_INVALIDE,
                assertThrows(RefusDeMedecin.class, () -> medecins.creer(CENTRE, "  ", null, null)).motif());
        RefusDeMedecin trois = assertThrows(RefusDeMedecin.class,
                () -> medecins.creer(CENTRE, "", "p".repeat(256), "s".repeat(256)));
        assertEquals(List.of("nom", "prenom", "specialite"), trois.anomalies().stream().map(a -> a.champ()).toList());
        FicheDeMedecin vide = medecins.creer(CENTRE, "Vide", "", "");
        assertNull(vide.prenom());
        assertNull(vide.specialite());
    }

    @Test
    void modifier_remplace_tout_et_refuse_l_identite_d_un_autre() {
        FicheDeMedecin k = medecins.creer(CENTRE, "Benali", "Karim", "Neph");
        FicheDeMedecin s = medecins.creer(CENTRE, "Seul", null, null);
        assertEquals(RefusDeMedecin.Motif.DEJA_EXISTANT,
                assertThrows(RefusDeMedecin.class, () -> medecins.modifier(CENTRE, s.id(), "Benali", "Karim", null)).motif());
        FicheDeMedecin change = medecins.modifier(CENTRE, k.id(), "Benali", null, null);
        assertNull(change.prenom());
        assertNull(change.specialite());
    }

    @Test
    void la_route_d_organisation_applique_la_meme_unicite_et_conserve_la_correction_partielle() {
        FicheDeMedecin k = medecins.creer(CENTRE, "Benali", "Karim", "Neph");
        FicheDeMedecin s = medecins.creer(CENTRE, "Seul", null, null);
        medecins.ajuster(new CentreId(CENTRE), new MedecinId(k.id()), null, null, "Autre");
        assertEquals("Autre", repo.trouver(new MedecinId(k.id())).orElseThrow().specialite());
        assertEquals("Karim", repo.trouver(new MedecinId(k.id())).orElseThrow().prenom());
        assertThrows(RefusDeMedecin.class,
                () -> medecins.ajuster(new CentreId(CENTRE), new MedecinId(s.id()), "Benali", "Karim", null));
        medecins.desactiverSansEchec(new CentreId(CENTRE), new MedecinId(k.id()));
        medecins.desactiverSansEchec(new CentreId(CENTRE), new MedecinId(k.id()));
        assertThrows(ElementIntrouvable.class,
                () -> medecins.desactiverSansEchec(new CentreId(AUTRE_CENTRE), new MedecinId(k.id())));
    }
}
