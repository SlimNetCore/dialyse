package com.hemodialyse.backend.characterization.organisation;

import com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation O1/O5 : le logo et le pied de page d'une société, écrits par l'API des sociétés, sont lus en direct (table
 * {@code societes}) par {@code DocumentIdentityProvider} pour l'en-tête et le pied de page des documents. Le point d'observation est la
 * lecture publique de la plateforme ({@code paramsFor}, {@code decorateHtml}) : même scénario sur le legacy et sur le nouveau code.
 */
class LogoDansLesDocumentsCharacterizationIntegrationTest extends OrganisationCharacterizationSupport {

    private static final String S = "/api/v1/societes";

    @Autowired
    private DocumentIdentityProvider identite;

    @Override
    protected String theme() {
        return "o1-logo-documents";
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(image, "png", sortie);
        return sortie.toByteArray();
    }

    private ObjectNode observer(UUID centre, byte[] logoEnvoye) throws Exception {
        Map<String, Object> params = identite.paramsFor(centre);
        ObjectNode n = mapper.createObjectNode();
        n.put("SOCIETE_NOM", String.valueOf(params.get("SOCIETE_NOM")));
        n.put("SOCIETE_PIED_PAGE", String.valueOf(params.get("SOCIETE_PIED_PAGE")));
        n.put("CENTRE_NOM", String.valueOf(params.get("CENTRE_NOM")));
        Object logo = params.get("SOCIETE_LOGO");
        n.put("logoPresent", logo != null);
        if (logo instanceof java.io.InputStream in) {
            n.put("logoIdentiqueAuLogoEnvoye", java.util.Arrays.equals(in.readAllBytes(), logoEnvoye));
        }
        String html = identite.decorateHtml("<html><body>CORPS</body></html>", centre);
        n.put("htmlContientLeLogoEnDataUri", html.contains("data:image/png;base64,"));
        n.put("htmlContientLePiedDePage", html.contains("Pied du document"));
        n.put("htmlContientLeCorps", html.contains("CORPS"));
        return n;
    }

    @Test
    void le_logo_et_le_pied_de_page_ecrits_par_l_api_sont_imprimes_puis_retires() throws Exception {
        UUID societe = societe("SOCIETE_1", "S1", true);
        UUID centre = centre("CENTRE_1", societe, "C1", true);
        byte[] logo = png();

        goldenNode("avant-logo-et-pied", observer(centre, logo));

        doPut(S + "/" + societe, "{'code':'ORGT-S1','raisonSociale':'Société S1','piedDePage':'Pied du document'}", superadmin());
        doPutLogo(S + "/" + societe + "/logo", logo, "image/png", superadmin());
        goldenNode("apres-logo-et-pied", observer(centre, logo));

        doDelete(S + "/" + societe + "/logo", superadmin());
        goldenNode("apres-suppression-du-logo", observer(centre, logo));
    }

    @Test
    void un_centre_inconnu_donne_une_identite_vide() throws Exception {
        UUID inconnu = UUID.fromString("99998200-0000-0000-0000-0000000000e9");
        goldenNode("centre-inconnu", observer(inconnu, new byte[0]));
    }
}
