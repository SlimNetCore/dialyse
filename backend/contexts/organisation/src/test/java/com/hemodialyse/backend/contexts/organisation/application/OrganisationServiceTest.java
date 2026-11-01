package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires du service {@link OrganisationService} pour les lectures
 * ajoutées par la bascule O5 (consulter une société, lister les centres d'une société).
 */
class OrganisationServiceTest {

    private static final SocieteId SOCIETE = new SocieteId(UUID.fromString("99997300-0000-0000-0000-0000000000a1"));
    private static final java.time.OffsetDateTime MAINTENANT_SOCIETE = java.time.OffsetDateTime.ofInstant(
            java.time.Instant.parse("2026-03-10T08:00:00Z"), java.time.ZoneOffset.UTC);

    private OrganisationService nouveauService(FakeSocieteRepository societes, FakeCentreRepository centres) {
        return new OrganisationService(societes, centres,
                new FakeSalleRepository(), new FakeCreneauRepository());
    }

    @Test
    void consulter_societe_existante() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        Societe trouvee = service.consulterSociete(SOCIETE);

        assertEquals(SOCIETE, trouvee.id());
        assertEquals("RENADIAL", trouvee.code());
    }

    @Test
    void consulter_societe_inconnue_rejette() {
        OrganisationService service = nouveauService(new FakeSocieteRepository(), new FakeCentreRepository());

        assertThrows(IllegalArgumentException.class, () -> service.consulterSociete(SOCIETE));
    }

    @Test
    void lister_centres_de_la_societe() {
        FakeCentreRepository centres = new FakeCentreRepository();
        CentreId id1 = new CentreId(UUID.randomUUID());
        CentreId id2 = new CentreId(UUID.randomUUID());
        centres.sauvegarder(new Centre(id1, SOCIETE, "C1", "Centre 1", null, "Africa/Algiers", true));
        centres.sauvegarder(new Centre(id2, SOCIETE, "C2", "Centre 2", null, "Africa/Algiers", false));
        OrganisationService service = nouveauService(new FakeSocieteRepository(), centres);

        List<Centre> resultat = service.listerCentres(SOCIETE);

        assertEquals(2, resultat.size());
    }

    @Test
    void lister_centres_societe_sans_centre_est_vide() {
        OrganisationService service = nouveauService(new FakeSocieteRepository(), new FakeCentreRepository());

        assertEquals(0, service.listerCentres(SOCIETE).size());
    }

    private static byte[] pngValide() throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    void lister_societes_par_page_filtre_et_pagine() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(new SocieteId(UUID.randomUUID()), "RENADIAL", "Renadial", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        societes.sauvegarder(new Societe(new SocieteId(UUID.randomUUID()), "NEPHRO", "Nephro Plus", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        societes.sauvegarder(new Societe(new SocieteId(UUID.randomUUID()), "CENTREX", "Centrex", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> page =
                service.listerSocietesParPage("na", 0, 20);

        assertEquals(1, page.total());
        assertEquals(1, page.items().size());
        assertEquals("RENADIAL", page.items().get(0).code());
    }

    @Test
    void televerser_lire_puis_supprimer_logo() throws Exception {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        byte[] png = pngValide();
        service.televerserLogoSociete(SOCIETE, png);

        com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete relu =
                service.lireLogoSociete(SOCIETE);
        assertEquals("image/png", relu.contentType());

        service.supprimerLogoSociete(SOCIETE);
        assertThrows(IllegalArgumentException.class, () -> service.lireLogoSociete(SOCIETE));
    }

    @Test
    void televerser_logo_invalide_rejette() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        assertThrows(com.hemodialyse.backend.contexts.organisation.domain.LogoRefuse.class,
                () -> service.televerserLogoSociete(SOCIETE, "<svg/>".getBytes()));
    }

    @Test
    void televerser_logo_societe_inconnue_rejette() throws Exception {
        OrganisationService service = nouveauService(new FakeSocieteRepository(), new FakeCentreRepository());

        byte[] png = pngValide();
        assertThrows(IllegalArgumentException.class, () -> service.televerserLogoSociete(SOCIETE, png));
    }

    @Test
    void societes_avec_logo() throws Exception {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        SocieteId autre = new SocieteId(UUID.randomUUID());
        societes.sauvegarder(new Societe(autre, "AUTRE", "Autre", null, null, null, null, null, true, MAINTENANT_SOCIETE));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());
        service.televerserLogoSociete(SOCIETE, pngValide());

        java.util.Set<SocieteId> avecLogo =
                service.societesAvecLogo(java.util.List.of(SOCIETE, autre));

        assertEquals(1, avecLogo.size());
        assertTrue(avecLogo.contains(SOCIETE));
    }

    @Test
    void lister_societes_sans_filtre_pagine() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        for (int i = 0; i < 5; i++) {
            societes.sauvegarder(new Societe(new SocieteId(UUID.randomUUID()), "CODE" + i, "Raison " + i, null, null, null, null, null, true, MAINTENANT_SOCIETE));
        }
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> premiere =
                service.listerSocietesParPage(null, 0, 2);
        com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> troisieme =
                service.listerSocietesParPage("", 2, 2);

        assertEquals(5, premiere.total());
        assertEquals(2, premiere.items().size());
        assertEquals(1, troisieme.items().size());
    }

    @Test
    void creer_societe_code_deja_utilise_rejette() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null,
                null, null, true, MAINTENANT_SOCIETE));
        OrganisationService service = nouveauService(societes, new FakeCentreRepository());

        assertThrows(IllegalArgumentException.class, () -> service.creerSociete(
                        new CommandesOrganisation.CreerSociete("RENADIAL", "Autre", null, null, null, null, null)),
                "SOCIETE_CODE_DEJA_UTILISE");
    }

    @Test
    void desactiver_dernier_centre_actif_rejette() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null,
                null, null, true, MAINTENANT_SOCIETE));
        FakeCentreRepository centres = new FakeCentreRepository();
        CentreId seul = new CentreId(UUID.randomUUID());
        centres.sauvegarder(new Centre(seul, SOCIETE, "C1", "Centre 1", null, "Africa/Algiers", true));
        OrganisationService service = nouveauService(societes, centres);

        assertThrows(IllegalArgumentException.class, () -> service.modifierCentre(
                new CommandesOrganisation.ModifierCentre(seul, "C1", "Centre 1", null,
                        "Africa/Algiers", false)));
    }

    @Test
    void transferer_dernier_centre_actif_rejette() {
        FakeSocieteRepository societes = new FakeSocieteRepository();
        societes.sauvegarder(new Societe(SOCIETE, "RENADIAL", "Renadial", null, null, null,
                null, null, true, MAINTENANT_SOCIETE));
        SocieteId cible = new SocieteId(UUID.randomUUID());
        societes.sauvegarder(new Societe(cible, "AUTRE", "Autre", null, null, null,
                null, null, true, MAINTENANT_SOCIETE));
        FakeCentreRepository centres = new FakeCentreRepository();
        CentreId seul = new CentreId(UUID.randomUUID());
        centres.sauvegarder(new Centre(seul, SOCIETE, "C1", "Centre 1", null, "Africa/Algiers", true));
        OrganisationService service = nouveauService(societes, centres);

        assertThrows(IllegalArgumentException.class, () -> service.transfererCentre(
                new CommandesOrganisation.TransfererCentre(seul, cible)));
    }

    private static class FakeSocieteRepository implements SocieteRepository {
        private final Map<SocieteId, Societe> stock = new HashMap<>();
        private final Map<SocieteId, com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete> logos = new HashMap<>();

        @Override
        public Optional<Societe> trouver(SocieteId id) {
            return Optional.ofNullable(stock.get(id));
        }

        @Override
        public Optional<Societe> trouverParCode(String code) {
            return stock.values().stream().filter(s -> s.code().equals(code)).findFirst();
        }

        @Override
        public void sauvegarder(Societe societe) {
            stock.put(societe.id(), societe);
        }

        @Override
        public List<Societe> lister() {
            return new ArrayList<>(stock.values());
        }

        @Override
        public com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult<Societe> listerParPage(
                String recherche, int page, int size) {
            String filtre = (recherche == null || recherche.isBlank()) ? "" : recherche.trim().toLowerCase();
            List<Societe> filtrees = stock.values().stream()
                    .filter(s -> filtre.isEmpty()
                            || s.code().toLowerCase().contains(filtre)
                            || s.raisonSociale().toLowerCase().contains(filtre))
                    .sorted(java.util.Comparator.comparing(Societe::code))
                    .toList();
            int from = Math.min(page * size, filtrees.size());
            int to = Math.min(from + size, filtrees.size());
            return com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult.of(
                    filtrees.subList(from, to), filtrees.size(), page, size);
        }

        @Override
        public Optional<com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete> lireLogo(
                SocieteId id) {
            return Optional.ofNullable(logos.get(id));
        }

        @Override
        public void enregistrerLogo(SocieteId id,
                                    com.hemodialyse.backend.contexts.organisation.domain.LogoDeSociete logo) {
            logos.put(id, logo);
        }

        @Override
        public void supprimerLogo(SocieteId id) {
            logos.remove(id);
        }

        @Override
        public java.util.Set<SocieteId> idsAvecLogo(java.util.Collection<SocieteId> ids) {
            java.util.Set<SocieteId> resultat = new java.util.HashSet<>(ids);
            resultat.retainAll(logos.keySet());
            return resultat;
        }
    }

    private static class FakeCentreRepository implements CentreRepository {
        private final Map<CentreId, Centre> stock = new HashMap<>();

        @Override
        public Optional<Centre> trouver(CentreId id) {
            return Optional.ofNullable(stock.get(id));
        }

        @Override
        public Optional<Centre> trouverParCode(String code) {
            return stock.values().stream().filter(c -> c.code().equals(code)).findFirst();
        }

        @Override
        public void sauvegarder(Centre centre) {
            stock.put(centre.id(), centre);
        }

        @Override
        public List<Centre> listerActifs(
                com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId societeId) {
            return stock.values().stream()
                    .filter(c -> c.societeId().equals(societeId) && c.actif()).toList();
        }

        @Override
        public List<Centre> listerParSociete(
                com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId societeId) {
            return stock.values().stream()
                    .filter(c -> c.societeId().equals(societeId)).toList();
        }
    }

    private static class FakeSalleRepository implements SalleRepository {
        @Override
        public Optional<com.hemodialyse.backend.contexts.organisation.domain.Salle> trouver(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId,
                com.hemodialyse.backend.contexts.organisation.domain.SalleId salleId) {
            return Optional.empty();
        }

        @Override
        public void sauvegarder(com.hemodialyse.backend.contexts.organisation.domain.Salle salle) {
        }

        @Override
        public List<com.hemodialyse.backend.contexts.organisation.domain.Salle> listerParCentre(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId) {
            return List.of();
        }
    }

    private static class FakeCreneauRepository implements CreneauRepository {
        @Override
        public Optional<com.hemodialyse.backend.contexts.organisation.domain.Creneau> trouver(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId,
                com.hemodialyse.backend.contexts.organisation.domain.CreneauId creneauId) {
            return Optional.empty();
        }

        @Override
        public void sauvegarder(com.hemodialyse.backend.contexts.organisation.domain.Creneau creneau) {
        }

        @Override
        public List<com.hemodialyse.backend.contexts.organisation.domain.Creneau> listerParCentre(
                com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centreId) {
            return List.of();
        }
    }
}
