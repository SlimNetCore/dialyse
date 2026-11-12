package com.hemodialyse.backend.contexts.documents.domain;

import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable.Societe;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Le papier en-tete : composition des parametres et habillage du HTML, repris a l'identique de DocumentIdentityProvider (DOC1). */
class PapierEnTeteTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final String HTML = "<html><head></head><body><h1>Doc</h1><p>Contenu</p></body></html>";

    private static Societe societe(byte[] logo) {
        return new Societe("Societe Papier", "1 rue Alpha", "Alger", "Alger", "021 00 00 00", "soc@x.dz", "https://soc.x.dz",
                "NIF-1", "NIS-2", "RC-3", "Mention legale", logo);
    }

    private static IdentiteImprimable centre(Societe societe) {
        return new IdentiteImprimable("Centre Papier", "2 rue Beta", "Blida", "Tipaza", "025 11 11 11", "c@x.dz", "https://c.x.dz", societe);
    }

    @Test
    void should_compose_every_identity_parameter_from_the_centre_and_its_company() {
        Map<String, Object> p = PapierEnTete.parametres(centre(societe(PNG)));

        assertThat(p).containsEntry("CENTRE_NOM", "Centre Papier")
                .containsEntry("CENTRE_ADRESSE", "2 rue Beta, Blida – Tipaza")
                .containsEntry("CENTRE_TELEPHONE", "025 11 11 11")
                .containsEntry("CENTRE_CONTACT", "Tél : 025 11 11 11 · Email : c@x.dz · Web : https://c.x.dz")
                .containsEntry("CENTRE_LIGNE", "Centre Papier · 2 rue Beta, Blida – Tipaza · Tél : 025 11 11 11 · Email : c@x.dz · Web : https://c.x.dz")
                .containsEntry("SOCIETE_NOM", "Societe Papier")
                .containsEntry("SOCIETE_ADRESSE", "1 rue Alpha, Alger")
                .containsEntry("SOCIETE_CONTACT", "1 rue Alpha, Alger · Tél : 021 00 00 00 · Email : soc@x.dz · Web : https://soc.x.dz")
                .containsEntry("SOCIETE_LEGAL", "NIF : NIF-1 · NIS : NIS-2 · RC : RC-3")
                .containsEntry("SOCIETE_PIED_PAGE", "Mention legale");
        assertThat(p.get("SOCIETE_LOGO")).isInstanceOf(InputStream.class);
    }

    @Test
    void should_give_a_fresh_logo_stream_at_every_composition() throws Exception {
        var identite = centre(societe(PNG));

        var premier = (InputStream) PapierEnTete.parametres(identite).get("SOCIETE_LOGO");
        premier.readAllBytes();
        var second = (InputStream) PapierEnTete.parametres(identite).get("SOCIETE_LOGO");

        assertThat(second.readAllBytes()).isEqualTo(PNG);
    }

    @Test
    void should_give_blank_texts_and_no_logo_when_the_centre_is_unknown() {
        for (Map<String, Object> p : List.of(PapierEnTete.parametres(null))) {
            assertThat(p).hasSize(16).containsEntry("CENTRE_NOM", "").containsEntry("SOCIETE_LEGAL", "").containsEntry("SOCIETE_LOGO", null);
        }
    }

    @Test
    void should_not_repeat_the_city_when_it_equals_the_wilaya_ignoring_case_and_omit_empty_fields() {
        var s = new Societe("S", "", "Blida", "blida", "", "", "", "", "", "RC-9", "", null);
        var identite = new IdentiteImprimable("C", "", "Blida", "blida", "", "", "https://c.x.dz", s);

        Map<String, Object> p = PapierEnTete.parametres(identite);

        assertThat(p).containsEntry("CENTRE_ADRESSE", "Blida").containsEntry("CENTRE_CONTACT", "Web : https://c.x.dz")
                .containsEntry("SOCIETE_LEGAL", "RC : RC-9").containsEntry("SOCIETE_PIED_PAGE", "");
    }

    @Test
    void should_put_header_and_footer_around_the_body_with_the_logo_as_a_data_uri() {
        String habille = PapierEnTete.habiller(HTML, PapierEnTete.parametres(centre(societe(PNG))));

        assertThat(habille).startsWith("<html><head></head><body><table").contains("data:image/png;base64,").contains("Societe Papier")
                .contains("<h1>Doc</h1><p>Contenu</p>").endsWith("</div></body></html>");
        assertThat(habille.indexOf("Societe Papier")).isLessThan(habille.indexOf("<h1>Doc</h1>"));
        assertThat(habille.lastIndexOf("Mention legale")).isGreaterThan(habille.indexOf("Contenu"));
    }

    @Test
    void should_recognise_a_jpeg_by_its_first_byte_and_serve_any_other_content_as_png() {
        assertThat(PapierEnTete.habiller(HTML, PapierEnTete.parametres(centre(societe(JPEG))))).contains("data:image/jpeg;base64,/9j/4AAQ");
        assertThat(PapierEnTete.habiller(HTML, PapierEnTete.parametres(centre(societe(new byte[]{1, 2, 3, 4, 5})))))
                .contains("data:image/png;base64,AQIDBAU=");
        assertThat(PapierEnTete.habiller(HTML, PapierEnTete.parametres(centre(societe(null))))).doesNotContain("<img");
    }

    @Test
    void should_escape_every_printed_text() {
        var s = new Societe("<b>Soc</b> & \"Cie\" 'x'", "", "", "", "", "", "", "", "", "", "<script>1</script>", null);

        String habille = PapierEnTete.habiller(HTML, PapierEnTete.parametres(centre(s)));

        assertThat(habille).contains("&lt;b&gt;Soc&lt;/b&gt; &amp; &quot;Cie&quot; &#39;x&#39;").contains("&lt;script&gt;1&lt;/script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void should_leave_the_document_unchanged_when_it_has_no_plain_body_tag() {
        var parametres = PapierEnTete.parametres(centre(societe(PNG)));

        assertThat(PapierEnTete.habiller("<html><p>x</p></html>", parametres)).isEqualTo("<html><p>x</p></html>");
        // dette reproduite (KB-DOC1-5 f) : un body qui porte un attribut n'est pas reconnu
        assertThat(PapierEnTete.habiller("<html><body class='a'>x</body></html>", parametres)).isEqualTo("<html><body class='a'>x</body></html>");
    }

    @Test
    void should_give_the_letterhead_of_a_company_alone_and_nothing_for_an_unknown_one() {
        var entete = PapierEnTete.enTeteDeLaSociete(societe(PNG)).orElseThrow();

        assertThat(entete.nom()).isEqualTo("Societe Papier");
        assertThat(entete.contact()).isEqualTo("1 rue Alpha, Alger · Tél : 021 00 00 00 · Email : soc@x.dz · Web : https://soc.x.dz");
        assertThat(entete.legal()).isEqualTo("NIF : NIF-1 · NIS : NIS-2 · RC : RC-3");
        assertThat(entete.piedDePage()).isEqualTo("Mention legale");
        assertThat(entete.logo()).isEqualTo(PNG);
        assertThat(PapierEnTete.enTeteDeLaSociete(null)).isEmpty();
    }
}
