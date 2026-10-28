package com.hemodialyse.backend.contexts.stock.infrastructure.acl.legacyseances;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.consommations.PerimetreDesSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import org.springframework.jdbc.core.JdbcTemplate;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * ACL vers les séances (contexte hors Stock, encore legacy) : la salle d'une séance est le code de sa salle ({@code salle.code}), sa
 * demi-journée se lit dans le code ou le libellé de son créneau ({@code position_creneau}) : « matin » ⇒ matin, « après-midi », « midi »
 * ou « soir » ⇒ après-midi ; sinon aucune.
 */
public class PerimetreDesSeancesLegacy implements PerimetreDesSeances {
    private final JdbcTemplate jdbc;

    public PerimetreDesSeancesLegacy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String simplifier(String texte) {
        return Normalizer.normalize(texte == null ? "" : texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    @Override
    public PerimetreDeSortie perimetreDe(CentreId centre, UUID seance) {
        List<UUID[]> ids = jdbc.query("SELECT salle_id, creneau_id FROM seances WHERE id = ? AND center_id = ?",
                (rs, i) -> new UUID[]{rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class)}, seance,
                centre.valeur());
        if (ids.isEmpty()) {
            return PerimetreDeSortie.journee();
        }
        String salle = ids.get(0)[0] == null ? null : jdbc.queryForList("SELECT code FROM salle WHERE id = ? AND center_id = ?",
                String.class, ids.get(0)[0], centre.valeur()).stream().findFirst().orElse(null);
        DemiJournee demi = null;
        if (ids.get(0)[1] != null) {
            List<String> textes = jdbc.query("SELECT code, libelle FROM position_creneau WHERE id = ? AND center_id = ?",
                    (rs, i) -> simplifier(rs.getString("code")) + " " + simplifier(rs.getString("libelle")), ids.get(0)[1],
                    centre.valeur());
            String texte = textes.isEmpty() ? "" : textes.get(0);
            if (texte.contains("APRES") || texte.contains("MIDI") || texte.contains("SOIR")) {
                demi = DemiJournee.APRES_MIDI;
            } else if (texte.contains("MATIN")) {
                demi = DemiJournee.MATIN;
            }
        }
        return new PerimetreDeSortie(salle, demi);
    }
}