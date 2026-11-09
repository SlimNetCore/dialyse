package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.IdentiteImprimee.IdentiteDeLaSociete;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du langage publié {@link IdentiteImprimee} (O4 lot E, ADR 0111) : exécuté sur le faux en mémoire et sur l'adapter JDBC (H2). Il
 * fixe le comportement du lecteur SQL retiré de {@code DocumentIdentityProvider} : textes rognés et jamais nuls, logo nul s'il est vide,
 * société désactivée lue comme une autre, centre ou société inconnus absents.
 */
abstract class IdentiteImprimeeContrat {

    static final UUID SOCIETE = UUID.fromString("99998200-0000-0000-0000-0000000000e1");
    static final UUID SOCIETE_NUE = UUID.fromString("99998200-0000-0000-0000-0000000000e2");
    static final UUID SOCIETE_INACTIVE = UUID.fromString("99998200-0000-0000-0000-0000000000e3");
    static final UUID CENTRE = UUID.fromString("99998200-0000-0000-0000-0000000000e4");
    static final UUID CENTRE_NU = UUID.fromString("99998200-0000-0000-0000-0000000000e5");
    static final UUID CENTRE_INACTIF = UUID.fromString("99998200-0000-0000-0000-0000000000e6");
    static final UUID INCONNU = UUID.fromString("99998200-0000-0000-0000-0000000000ef");

    /** Une société telle qu'elle est stockée : textes bruts (blancs de bord, {@code null}). */
    record Fiche(String raisonSociale, String adresse, String ville, String wilaya, String telephone, String email, String siteWeb,
                 String nif, String nis, String rc, String piedDePage, byte[] logo) {
    }

    protected abstract IdentiteImprimee identite();

    protected abstract void societe(UUID id, boolean actif, Fiche fiche);

    protected abstract void centre(UUID id, UUID societe, String nom, String adresse, String ville, String wilaya, String telephone,
                                   String email, String siteWeb, boolean actif);

    protected void donnees() {
        societe(SOCIETE, true, new Fiche("  Alpha  ", " 1 rue A ", "Alger", "Alger", "021 00", "a@x.dz", "https://a.dz", "NIF-1",
                null, " RC-3 ", "Mention", new byte[]{(byte) 0xFF, 1, 2, 3, 4}));
        societe(SOCIETE_NUE, true, new Fiche("Nue", null, null, null, null, null, null, null, null, null, "   ", new byte[0]));
        societe(SOCIETE_INACTIVE, false, new Fiche("Dormante", null, null, null, null, null, null, null, null, null, "Pied dormant",
                new byte[]{1, 2, 3, 4, 5}));
        centre(CENTRE, SOCIETE, " Centre A ", "2 rue B", "Blida", "Tipaza", "025 11", null, "https://c.dz", true);
        centre(CENTRE_NU, SOCIETE_NUE, "Centre nu", null, null, null, null, null, null, true);
        centre(CENTRE_INACTIF, SOCIETE_INACTIVE, "Centre inactif", null, "Oran", null, null, null, null, false);
    }

    @Test
    void l_identite_d_un_centre_porte_ses_textes_rognes_et_ceux_de_sa_societe() {
        donnees();
        var centre = identite().duCentre(CENTRE).orElseThrow();
        assertThat(centre.nom()).isEqualTo("Centre A");
        assertThat(centre.adresse()).isEqualTo("2 rue B");
        assertThat(centre.ville()).isEqualTo("Blida");
        assertThat(centre.wilaya()).isEqualTo("Tipaza");
        assertThat(centre.telephone()).isEqualTo("025 11");
        assertThat(centre.email()).as("absent = chaîne vide").isEqualTo("");
        assertThat(centre.siteWeb()).isEqualTo("https://c.dz");
        IdentiteDeLaSociete societe = centre.societe();
        assertThat(societe.raisonSociale()).isEqualTo("Alpha");
        assertThat(societe.adresse()).isEqualTo("1 rue A");
        assertThat(societe.nif()).isEqualTo("NIF-1");
        assertThat(societe.nis()).isEqualTo("");
        assertThat(societe.rc()).isEqualTo("RC-3");
        assertThat(societe.piedDePage()).isEqualTo("Mention");
        assertThat(societe.logo()).containsExactly((byte) 0xFF, 1, 2, 3, 4);
    }

    @Test
    void une_societe_sans_logo_ni_contact_donne_des_chaines_vides_et_un_logo_nul() {
        donnees();
        var societe = identite().duCentre(CENTRE_NU).orElseThrow().societe();
        assertThat(societe.raisonSociale()).isEqualTo("Nue");
        assertThat(societe.piedDePage()).as("pied blanc").isEqualTo("");
        assertThat(societe.logo()).as("logo vide").isNull();
        assertThat(societe.adresse()).isEqualTo("");
        assertThat(societe.telephone()).isEqualTo("");
    }

    @Test
    void une_societe_desactivee_et_son_centre_desactive_sont_lus_comme_les_autres() {
        donnees();
        var centre = identite().duCentre(CENTRE_INACTIF).orElseThrow();
        assertThat(centre.nom()).isEqualTo("Centre inactif");
        assertThat(centre.ville()).isEqualTo("Oran");
        assertThat(centre.societe().raisonSociale()).isEqualTo("Dormante");
        assertThat(centre.societe().piedDePage()).isEqualTo("Pied dormant");
        assertThat(identite().deLaSociete(SOCIETE_INACTIVE).orElseThrow().logo()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void l_identite_de_la_seule_societe_est_celle_de_sa_fiche() {
        donnees();
        var societe = identite().deLaSociete(SOCIETE).orElseThrow();
        assertThat(societe.raisonSociale()).isEqualTo("Alpha");
        assertThat(societe.telephone()).isEqualTo("021 00");
        assertThat(societe.email()).isEqualTo("a@x.dz");
        assertThat(societe.siteWeb()).isEqualTo("https://a.dz");
        assertThat(societe.rc()).isEqualTo("RC-3");
    }

    @Test
    void un_centre_ou_une_societe_inconnus_ou_nuls_sont_absents() {
        donnees();
        assertThat(identite().duCentre(INCONNU)).isEmpty();
        assertThat(identite().duCentre(null)).isEmpty();
        assertThat(identite().deLaSociete(INCONNU)).isEmpty();
        assertThat(identite().deLaSociete(null)).isEmpty();
    }
}
