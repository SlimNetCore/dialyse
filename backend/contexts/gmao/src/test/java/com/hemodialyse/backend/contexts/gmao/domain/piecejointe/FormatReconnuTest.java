package com.hemodialyse.backend.contexts.gmao.domain.piecejointe;

import com.hemodialyse.backend.contexts.gmao.doubles.Fichiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.hemodialyse.backend.contexts.gmao.doubles.Fichiers.octets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R22 : le format se reconnaît sur la structure entière du fichier, pas sur ses premiers octets (KB-GMAO-59). */
class FormatReconnuTest {

    @Test
    void should_recognize_a_complete_pdf_png_and_jpeg() {
        assertThat(FormatReconnu.reconnaitre(Fichiers.pdf())).isEqualTo(FormatReconnu.PDF);
        assertThat(FormatReconnu.reconnaitre(Fichiers.png())).isEqualTo(FormatReconnu.PNG);
        assertThat(FormatReconnu.reconnaitre(Fichiers.jpeg())).isEqualTo(FormatReconnu.JPEG);
    }

    @Test
    void should_serve_each_format_with_its_own_content_type() {
        assertThat(FormatReconnu.PDF.contentType()).isEqualTo("application/pdf");
        assertThat(FormatReconnu.PNG.contentType()).isEqualTo("image/png");
        assertThat(FormatReconnu.JPEG.contentType()).isEqualTo("image/jpeg");
        assertThat(FormatReconnu.depuisContentType("image/jpeg")).contains(FormatReconnu.JPEG);
        assertThat(FormatReconnu.depuisContentType("text/html")).isEmpty();
        assertThat(FormatReconnu.depuisContentType(null)).isEmpty();
    }

    // ------------------------------------------------------------------ format inconnu

    static Stream<Arguments> formatsInconnus() {
        return Stream.of(
                Arguments.of("%PDF sans tiret", octets("%PDF")),
                Arguments.of("PDF en minuscules", octets("%pdf-1.4\n%%EOF")),
                Arguments.of("PDF précédé d'un espace", octets(" %PDF-1.4\n%%EOF")),
                Arguments.of("PDF précédé d'un BOM", new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '%', 'P', 'D', 'F', '-'}),
                Arguments.of("PNG sans 0x89", new byte[]{1, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
                Arguments.of("JPEG FFD8 seul", new byte[]{(byte) 0xFF, (byte) 0xD8}),
                Arguments.of("JPEG FFD8 puis 00", new byte[]{(byte) 0xFF, (byte) 0xD8, 0, (byte) 0xE0}),
                Arguments.of("GIF", octets("GIF89a......")),
                Arguments.of("ZIP", octets("PK\u0003\u0004....")),
                Arguments.of("exécutable Windows", octets("MZ......")),
                Arguments.of("HTML", octets("<html><body>x</body></html>")),
                Arguments.of("SVG", octets("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("formatsInconnus")
    void should_reject_an_unknown_format(String cas, byte[] contenu) {
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(contenu)).isInstanceOf(FichierRefuse.class)
                .hasMessage("Format non accepté (PDF, PNG ou JPEG uniquement)");
    }

    // ------------------------------------------------------------------ fichiers incomplets ou altérés

    static Stream<Arguments> fichiersAlteres() {
        byte[] png = Fichiers.png();
        byte[] pngCrcFaux = png.clone();
        pngCrcFaux[20] ^= 1;
        byte[] pngSansDonnees = pngSansIdat();
        return Stream.of(
                Arguments.of("PDF réduit à %PDF-", octets("%PDF-")),
                Arguments.of("PDF sans marqueur de fin", octets("%PDF-1.4\n1 0 obj<<>>endobj\n")),
                Arguments.of("PDF suivi de HTML", octets("%PDF-1.4\n<html><script>alert(1)</script>")),
                Arguments.of("PDF terminé puis HTML", octets("%PDF-1.4\n%%EOF\n<html><script>alert(1)</script>")),
                Arguments.of("PDF sans numéro de version", octets("%PDF-x.y\n%%EOF")),
                Arguments.of("PDF dont le marqueur de fin est loin de la fin",
                        Fichiers.suivi(Fichiers.pdf(), " ".repeat(1100) + "x")),
                Arguments.of("PNG réduit à la signature", Arrays.copyOf(png, 8)),
                Arguments.of("PNG tronqué avant IEND", Fichiers.sansLesDerniers(png, 12)),
                Arguments.of("PNG tronqué au milieu", Fichiers.sansLesDerniers(png, 5)),
                Arguments.of("PNG suivi d'octets", Fichiers.suivi(png, "<html>")),
                Arguments.of("PNG avec une somme de contrôle fausse", pngCrcFaux),
                Arguments.of("PNG sans bloc de données", pngSansDonnees),
                Arguments.of("PNG : 4 premiers octets puis quelconques", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4}),
                Arguments.of("JPEG réduit à FFD8FF", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
                Arguments.of("JPEG tronqué", Fichiers.sansLesDerniers(Fichiers.jpeg(), 2)),
                Arguments.of("JPEG suivi de HTML", Fichiers.suivi(Fichiers.jpeg(), "<html>")));
    }

    private static byte[] pngSansIdat() {
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        flux.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        Fichiers.bloc(flux, "IHDR", new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0});
        Fichiers.bloc(flux, "IEND", new byte[0]);
        return flux.toByteArray();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fichiersAlteres")
    void should_reject_a_recognized_format_whose_structure_is_not_complete(String cas, byte[] contenu) {
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(contenu)).isInstanceOf(FichierRefuse.class)
                .hasMessageContaining("incomplet ou altéré");
    }

    @Test
    void should_reject_a_png_whose_header_is_incoherent() {
        for (byte[] ihdr : new byte[][]{
                {0, 0, 0, 0, 0, 0, 0, 1, 8, 2, 0, 0, 0},      // largeur nulle
                {0, 0, 0, 1, 0, 0, 0, 0, 8, 2, 0, 0, 0},      // hauteur nulle
                {0, 0, 0, 1, 0, 0, 0, 1, 7, 2, 0, 0, 0},      // profondeur inconnue
                {0, 0, 0, 1, 0, 0, 0, 1, 8, 5, 0, 0, 0},      // type de couleur inconnu
                {0, 0, 0, 1, 0, 0, 0, 1, 4, 2, 0, 0, 0},      // profondeur 4 impossible en RVB
                {0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 1, 0, 0},      // compression inconnue
                {0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 2}}) {   // entrelacement inconnu
            ByteArrayOutputStream flux = new ByteArrayOutputStream();
            flux.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            Fichiers.bloc(flux, "IHDR", ihdr);
            Fichiers.bloc(flux, "IDAT", new byte[]{1, 2, 3});
            Fichiers.bloc(flux, "IEND", new byte[0]);
            assertThatThrownBy(() -> FormatReconnu.reconnaitre(flux.toByteArray())).isInstanceOf(FichierRefuse.class);
        }
    }

    private static byte[] pngAvec(byte[] ihdr, byte[]... blocsIntermediaires) {
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        flux.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        Fichiers.bloc(flux, "IHDR", ihdr);
        for (byte[] bloc : blocsIntermediaires) flux.writeBytes(bloc);
        Fichiers.bloc(flux, "IEND", new byte[0]);
        return flux.toByteArray();
    }

    private static byte[] bloc(String type, byte[] donnees) {
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        Fichiers.bloc(flux, type, donnees);
        return flux.toByteArray();
    }

    private static byte[] ihdr(long largeur, long hauteur, int profondeur, int couleur, int entrelacement) {
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        flux.writeBytes(Fichiers.entier(largeur));
        flux.writeBytes(Fichiers.entier(hauteur));
        flux.writeBytes(new byte[]{(byte) profondeur, (byte) couleur, 0, 0, (byte) entrelacement});
        return flux.toByteArray();
    }

    @Test
    void should_accept_every_valid_png_depth_and_color_type_combination() {
        int[][] valides = {{1, 0}, {2, 0}, {4, 0}, {8, 0}, {16, 0}, {8, 2}, {16, 2}, {1, 3}, {2, 3}, {4, 3}, {8, 3}, {8, 4},
                {16, 4}, {8, 6}, {16, 6}};
        byte[] idat = bloc("IDAT", new byte[]{1, 2, 3});
        for (int[] c : valides) {
            assertThat(FormatReconnu.reconnaitre(pngAvec(ihdr(1, 1, c[0], c[1], 0), idat))).as("profondeur %d couleur %d", c[0], c[1])
                    .isEqualTo(FormatReconnu.PNG);
        }
        assertThat(FormatReconnu.reconnaitre(pngAvec(ihdr(1, 1, 8, 2, 1), idat))).isEqualTo(FormatReconnu.PNG);
        assertThat(FormatReconnu.reconnaitre(pngAvec(ihdr(Integer.MAX_VALUE, Integer.MAX_VALUE, 8, 2, 0), idat))).isEqualTo(FormatReconnu.PNG);
    }

    @Test
    void should_reject_png_dimensions_and_combinations_just_outside_the_valid_range() {
        byte[] idat = bloc("IDAT", new byte[]{1, 2, 3});
        for (byte[] ihdr : new byte[][]{ihdr(0x80000000L, 1, 8, 2, 0), ihdr(1, 0x80000000L, 8, 2, 0), ihdr(1, 1, 16, 3, 0),
                ihdr(1, 1, 2, 4, 0), ihdr(1, 1, 8, 1, 0), ihdr(1, 1, 8, 7, 0), ihdr(1, 1, 0, 0, 0), ihdr(1, 1, 8, 2, -1)}) {
            assertThatThrownBy(() -> FormatReconnu.reconnaitre(pngAvec(ihdr, idat))).isInstanceOf(FichierRefuse.class);
        }
        // IHDR d'une autre longueur que 13
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(pngAvec(new byte[12], idat))).isInstanceOf(FichierRefuse.class);
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(pngAvec(new byte[14], idat))).isInstanceOf(FichierRefuse.class);
    }

    @Test
    void should_walk_the_png_blocks_accept_ancillary_ones_and_reject_malformed_ones() {
        byte[] idat = bloc("IDAT", new byte[]{1, 2, 3});
        assertThat(FormatReconnu.reconnaitre(pngAvec(ihdr(1, 1, 8, 2, 0), bloc("tEXt", new byte[]{'a', 0, 'b'}), idat, idat)))
                .isEqualTo(FormatReconnu.PNG);
        // le premier bloc doit être IHDR
        ByteArrayOutputStream sansIhdr = new ByteArrayOutputStream();
        sansIhdr.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        sansIhdr.writeBytes(idat);
        sansIhdr.writeBytes(bloc("IEND", new byte[0]));
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(sansIhdr.toByteArray())).isInstanceOf(FichierRefuse.class);
        // un second IHDR
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(pngAvec(ihdr(1, 1, 8, 2, 0), bloc("IHDR", ihdr(1, 1, 8, 2, 0)), idat)))
                .isInstanceOf(FichierRefuse.class);
        // type de bloc avec un chiffre, ou un signe juste en dehors des lettres
        for (String type : new String[]{"ID4T", "I@AT", "I[AT", "I`AT", "I{AT"}) {
            assertThatThrownBy(() -> FormatReconnu.reconnaitre(pngAvec(ihdr(1, 1, 8, 2, 0), bloc(type, new byte[]{1}), idat)))
                    .as(type).isInstanceOf(FichierRefuse.class);
        }
        // IEND qui porte des données
        ByteArrayOutputStream iendNonVide = new ByteArrayOutputStream();
        iendNonVide.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        Fichiers.bloc(iendNonVide, "IHDR", ihdr(1, 1, 8, 2, 0));
        iendNonVide.writeBytes(idat);
        Fichiers.bloc(iendNonVide, "IEND", new byte[]{1});
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(iendNonVide.toByteArray())).isInstanceOf(FichierRefuse.class);
        // un bloc qui annonce plus d'octets que le fichier n'en contient, ou une longueur géante
        byte[] png = Fichiers.png();
        byte[] trop = png.clone();
        trop[8] = 0x7F;
        trop[9] = (byte) 0xFF;
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(trop)).isInstanceOf(FichierRefuse.class);
        byte[] geant = png.clone();
        geant[8] = (byte) 0xFF;
        geant[9] = (byte) 0xFF;
        geant[10] = (byte) 0xFF;
        geant[11] = (byte) 0xFF;
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(geant)).isInstanceOf(FichierRefuse.class);
    }

    @Test
    void should_read_the_pdf_version_digits_and_the_end_window_exactly() {
        assertThat(FormatReconnu.reconnaitre(octets("%PDF-0.0\n%%EOF"))).isEqualTo(FormatReconnu.PDF);
        assertThat(FormatReconnu.reconnaitre(octets("%PDF-9.9\n%%EOF"))).isEqualTo(FormatReconnu.PDF);
        for (String entete : new String[]{"%PDF-/.0", "%PDF-:.0", "%PDF-1.:", "%PDF-1./", "%PDF-1,4", "%PDF-14."}) {
            assertThatThrownBy(() -> FormatReconnu.reconnaitre(octets(entete + "\n%%EOF"))).as(entete).isInstanceOf(FichierRefuse.class);
        }
        // %%EOF à 1024 octets de la fin : dans la fenêtre ; à 1025 : hors fenêtre
        String blancs1019 = " ".repeat(1019);
        assertThat(FormatReconnu.reconnaitre(octets("%PDF-1.4\n" + "x".repeat(50) + "%%EOF" + blancs1019))).isEqualTo(FormatReconnu.PDF);
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(octets("%PDF-1.4\n" + "x".repeat(50) + "%%EOF" + blancs1019 + " ")))
                .isInstanceOf(FichierRefuse.class);
        // le marqueur ne peut pas se confondre avec l'en-tête
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(octets("%PDF-1.4"))).isInstanceOf(FichierRefuse.class);
        assertThat(FormatReconnu.reconnaitre(octets("%PDF-1.4%%EOF"))).isEqualTo(FormatReconnu.PDF);
    }

    @Test
    void should_require_at_least_six_bytes_for_a_jpeg() {
        byte[] cinq = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xFF, (byte) 0xD9};
        byte[] six = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0xFF, (byte) 0xD9};
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(cinq)).isInstanceOf(FichierRefuse.class);
        assertThat(FormatReconnu.reconnaitre(six)).isEqualTo(FormatReconnu.JPEG);
        byte[] finFausse = six.clone();
        finFausse[5] = (byte) 0xD8;
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(finFausse)).isInstanceOf(FichierRefuse.class);
        byte[] finDecalee = six.clone();
        finDecalee[4] = (byte) 0xFE;
        assertThatThrownBy(() -> FormatReconnu.reconnaitre(finDecalee)).isInstanceOf(FichierRefuse.class);
    }

    @Test
    void should_accept_a_pdf_whose_end_marker_is_followed_by_blanks_only() {
        assertThat(FormatReconnu.reconnaitre(Fichiers.suivi(Fichiers.pdf(), "\r\n \0"))).isEqualTo(FormatReconnu.PDF);
    }
}
