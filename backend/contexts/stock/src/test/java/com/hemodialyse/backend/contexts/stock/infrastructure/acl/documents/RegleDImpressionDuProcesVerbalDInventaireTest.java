package com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DOC5j (ADR 0202) : la règle d'impression directe fournit les lignes de l'inventaire désigné, sans garde, comme l'ancienne requête. */
class RegleDImpressionDuProcesVerbalDInventaireTest {

    private static final UUID CENTRE = UUID.fromString("99997800-0000-0000-0000-000000000001");
    private static final OffsetDateTime LE = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);

    private final UUID inventaireId = UUID.randomUUID();
    private final Map<String, Inventaire> lus = new HashMap<>();
    private final Inventaires inventaires = (Inventaires) Proxy.newProxyInstance(Inventaires.class.getClassLoader(),
            new Class<?>[]{Inventaires.class}, (proxy, methode, args) -> {
                if (!methode.getName().equals("parId")) throw new UnsupportedOperationException(methode.getName());
                CentreId centre = (CentreId) args[0];
                return Optional.ofNullable(lus.get(centre.valeur() + "/" + args[1]));
            });
    private final RegleDImpressionDuProcesVerbalDInventaire regle = new RegleDImpressionDuProcesVerbalDInventaire(inventaires);

    private void inventaireDuCentre() {
        LigneDInventaire l = LigneDInventaire.theorique(UUID.randomUUID(), UUID.randomUUID(), "ART", "Article", "boîte", null, "L1", null,
                BigDecimal.TEN, BigDecimal.ONE);
        lus.put(CENTRE + "/" + inventaireId, Inventaire.ouvrir(inventaireId, new CentreId(CENTRE), "INV-1", LocalDate.of(2026, 3, 10), null,
                "pharma", LE, List.of(l)));
    }

    private static Map<String, String> parametres(String identifiant) {
        Map<String, String> p = new HashMap<>();
        if (identifiant != null) p.put("INVENTAIRE_ID", identifiant);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lignes(Map<String, Object> donnees) {
        return (List<Map<String, Object>>) donnees.get("LIGNES");
    }

    @Test
    void le_type_est_celui_du_proces_verbal_et_n_a_aucune_garde() {
        assertEquals("INVENTAIRE_STOCK", regle.type());
        assertDoesNotThrow(() -> regle.garder(null));
        assertDoesNotThrow(() -> regle.garder(CENTRE));
    }

    @Test
    void l_inventaire_du_centre_fournit_ses_lignes() {
        inventaireDuCentre();

        List<Map<String, Object>> lignes = lignes(regle.donneesDuServeur(CENTRE, parametres(inventaireId.toString())));

        assertEquals(1, lignes.size());
        assertEquals("L1", lignes.getFirst().get("LOT_LIBELLE"));
    }

    @Test
    void un_inventaire_inconnu_ou_d_un_autre_centre_ne_fournit_aucune_ligne() {
        inventaireDuCentre();

        assertEquals(List.of(), lignes(regle.donneesDuServeur(CENTRE, parametres(UUID.randomUUID().toString()))));
        assertEquals(List.of(), lignes(regle.donneesDuServeur(UUID.randomUUID(), parametres(inventaireId.toString()))));
    }

    @Test
    void un_identifiant_absent_ne_designe_rien() {
        assertEquals(List.of(), lignes(regle.donneesDuServeur(CENTRE, parametres(null))));
    }

    @Test
    void un_identifiant_vide_ou_illisible_est_un_echec_d_impression_au_texte_de_l_ancienne_erreur() {
        for (String brut : List.of("", "pas-un-uuid")) {
            IllegalStateException echec = assertThrows(IllegalStateException.class, () -> regle.donneesDuServeur(CENTRE, parametres(brut)));
            assertEquals("Error executing SQL statement for: inventaire_stock.", echec.getMessage());
        }
    }
}
