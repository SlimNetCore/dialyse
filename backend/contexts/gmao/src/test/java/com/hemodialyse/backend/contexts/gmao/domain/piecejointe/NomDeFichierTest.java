package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** R22 et KB-GMAO-60 : le nom est assaini, borné à 150 caractères, sans couper un emoji. */
class NomDeFichierTest {

    private static String nom(String brut) {
        return NomDeFichier.assainir(brut).valeur();
    }

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(delimiter = ';', value = {
            "devis.pdf;devis.pdf",
            "été 2026.pdf;été 2026.pdf",
            "../../etc/passwd.pdf;passwd.pdf",
            "..\\..\\windows\\system32\\x.pdf;x.pdf",
            "C:\\dir\\f.pdf;f.pdf",
            "dossier/;document",
            "..;document",
            ".;document",
            "...;document",
            "a..b.pdf;a.b.pdf",
            "a....b.pdf;a.b.pdf",
            "a.<.>.pdf;a.pdf",
            ".htaccess;htaccess",
            "rapport.pdf.;rapport.pdf",
            "x\"<>:*?|y.pdf;xy.pdf",
            "${jndi:ldap://x}.pdf;x}.pdf"})
    void should_keep_only_a_clean_file_name(String brut, String attendu) {
        assertThat(nom(brut)).isEqualTo(attendu);
    }

    @Test
    void should_trim_every_unicode_space_so_the_extension_cannot_hide_behind_it() {
        assertThat(nom("facture.exe ")).isEqualTo("facture.exe");
        assertThat(nom("facture.exe  　 ")).isEqualTo("facture.exe");
        assertThat(nom("  facture.exe​")).isEqualTo("facture.exe");
        assertThat(nom(" 　")).isEqualTo("document");
    }

    @Test
    void should_name_a_blank_or_missing_file_document() {
        assertThat(nom(null)).isEqualTo("document");
        assertThat(nom("")).isEqualTo("document");
        assertThat(nom("   ")).isEqualTo("document");
        assertThat(nom(" . . ")).isEqualTo("document");
    }

    @Test
    void should_remove_control_and_bidirectional_characters() {
        assertThat(nom("a\u0000b\tc\nd.pdf")).isEqualTo("abcd.pdf");
        assertThat(nom("a\u0085b.pdf")).isEqualTo("ab.pdf");
        assertThat(nom("rtl\u202Egnp.exe.pdf")).isEqualTo("rtlgnp.exe.pdf");
        assertThat(nom("a\u200Bb\u200Ec\u2066d\u2069.pdf")).isEqualTo("abcd.pdf");
        assertThat(nom("a\u2028b\u2029c\uFEFF.pdf")).isEqualTo("abc.pdf");
    }

    @Test
    void should_drop_lone_surrogates_but_keep_emoji() {
        assertThat(nom("a\uD83D.pdf")).isEqualTo("a.pdf");
        assertThat(nom("a\uDE00.pdf")).isEqualTo("a.pdf");
        assertThat(nom("\uD83D\uDE00.pdf")).isEqualTo("\uD83D\uDE00.pdf");
    }

    @Test
    void should_normalize_to_nfc() {
        assertThat(nom("e\u0301te\u0301.pdf")).isEqualTo("\u00E9t\u00E9.pdf");
    }

    @Test
    void should_keep_the_last_150_characters_so_the_extension_stays_visible() {
        String long300 = "a".repeat(296) + ".pdf";
        assertThat(nom(long300)).hasSize(150).endsWith(".pdf");
        assertThat(nom("b".repeat(146) + ".pdf")).hasSize(150);
        assertThat(nom("b".repeat(145) + ".pdf")).hasSize(149);
        assertThat(nom("X" + "b".repeat(146) + ".pdf")).isEqualTo("b".repeat(146) + ".pdf");
    }

    @Test
    void should_never_cut_a_surrogate_pair_when_truncating() {
        String emoji = "\uD83D\uDE00";
        String coupeAuMilieu = nom(emoji + "d".repeat(149));
        assertThat(coupeAuMilieu).isEqualTo("d".repeat(149));
        assertThat(nom(emoji.repeat(150))).isEqualTo(emoji.repeat(75));
        // 154 unit\u00E9s : on perd les 4 premi\u00E8res, soit exactement deux emoji
        assertThat(nom(emoji.repeat(75) + ".pdf")).isEqualTo(emoji.repeat(73) + ".pdf");
        assertThat(isWellFormed(nom(emoji.repeat(75) + ".pdf"))).isTrue();
    }

    private static boolean isWellFormed(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    static Stream<String> nomsQuelconques() {
        return Stream.of("devis.pdf", "../../a.pdf", "a..b", "rtl\u202Egnp.exe.pdf", "\uD83D\uDE00".repeat(100), "x".repeat(400),
                "  .. .pdf  ", "e\u0301.pdf");
    }

    @ParameterizedTest
    @MethodSource("nomsQuelconques")
    void should_be_idempotent_so_it_can_be_replayed_on_read(String brut) {
        String une = nom(brut);
        assertThat(nom(une)).isEqualTo(une);
    }

    @Test
    void should_keep_ordinary_symbols_and_supplementary_characters_next_to_the_forbidden_ones() {
        assertThat(nom("a;b,c'd&e$f%g#h@i!j(k)l=m+n~o.pdf")).isEqualTo("a;b,c'd&e$f%g#h@i!j(k)l=m+n~o.pdf");
        assertThat(nom("😀a😀.pdf")).isEqualTo("😀a😀.pdf");
        assertThat(nom("a b.pdf")).isEqualTo("a b.pdf");
        assertThat(nom("a b.pdf")).isEqualTo("a b.pdf");
    }

    @Test
    void should_read_the_extension_boundaries() {
        assertThat(NomDeFichier.assainir("x.abcdefgh").extension()).contains("abcdefgh");
        assertThat(NomDeFichier.assainir("x.abcdefghi").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("x.7z").extension()).contains("7z");
        assertThat(NomDeFichier.assainir("x.a b").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("x.a_b").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("x.a-b").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("x.pdf").extension()).contains("pdf");
        assertThat(NomDeFichier.assainir("x.z").extension()).contains("z");
        assertThat(NomDeFichier.assainir("x.9").extension()).contains("9");
        assertThat(NomDeFichier.assainir("x.0").extension()).contains("0");
    }

    @Test
    void should_read_the_extension_only_when_it_looks_like_one() {
        assertThat(NomDeFichier.assainir("plan.PDF").extension()).contains("pdf");
        assertThat(NomDeFichier.assainir("a.b.JPeG").extension()).contains("jpeg");
        assertThat(NomDeFichier.assainir("malware.exe").extension()).contains("exe");
        assertThat(NomDeFichier.assainir("sans-extension").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("Rapport 12.5 final").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("x.tropdelongueur").extension()).isEmpty();
        assertThat(NomDeFichier.assainir("x.é").extension()).isEmpty();
    }
}
