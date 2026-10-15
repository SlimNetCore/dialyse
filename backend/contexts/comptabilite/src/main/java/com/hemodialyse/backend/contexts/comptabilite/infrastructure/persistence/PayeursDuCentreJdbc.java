package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.PayeurVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Payeurs d'un centre (référentiel {@code centre_payeur}, lu seulement) avec le compte client propre à chacun (table
 * {@code comptes_payeurs}). Chaque requête est bornée au centre ; SQL compatible H2 et PostgreSQL. La recherche garde les
 * jokers SQL non échappés (KB-CPT-05).
 */
public class PayeursDuCentreJdbc implements LectureDesPayeurs {

    private static final RowMapper<PayeurVue> PAYEUR = (rs, i) ->
            new PayeurVue(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"), null);

    private final JdbcTemplate jdbc;

    public PayeursDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageDe<PayeurVue> lister(CentreId centre, String recherche, int page, int taille) {
        StringBuilder filtre = new StringBuilder(" FROM centre_payeur WHERE center_id = ?");
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        if (recherche != null && !recherche.isBlank()) {
            filtre.append(" AND (LOWER(code) LIKE ? OR LOWER(nom) LIKE ?)");
            String motif = "%" + recherche.trim().toLowerCase(Locale.ROOT) + "%";
            parametres.add(motif);
            parametres.add(motif);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filtre, Long.class, parametres.toArray());
        parametres.add(taille);
        parametres.add((long) page * taille);
        List<PayeurVue> payeurs = jdbc.query("SELECT id, code, nom" + filtre + " ORDER BY nom, code LIMIT ? OFFSET ?", PAYEUR,
                parametres.toArray());
        Map<UUID, String> comptes = comptesDes(centre, payeurs);
        return new PageDe<>(payeurs.stream()
                .map(p -> new PayeurVue(p.payeurId(), p.code(), p.nom(), comptes.get(p.payeurId()))).toList(),
                total == null ? 0 : total, page, taille);
    }

    @Override
    public Optional<PayeurVue> trouver(CentreId centre, PayeurId payeur) {
        return jdbc.query("SELECT id, code, nom FROM centre_payeur WHERE center_id = ? AND id = ?", PAYEUR, centre.valeur(),
                payeur.valeur()).stream().findFirst();
    }

    @Override
    public Optional<String> compteDuPayeur(CentreId centre, PayeurId payeur) {
        return jdbc.queryForList("SELECT compte FROM comptes_payeurs WHERE center_id = ? AND payeur_id = ?", String.class,
                centre.valeur(), payeur.valeur()).stream().findFirst();
    }

    private Map<UUID, String> comptesDes(CentreId centre, List<PayeurVue> payeurs) {
        Map<UUID, String> comptes = new HashMap<>();
        if (payeurs.isEmpty()) {
            return comptes;
        }
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        payeurs.forEach(p -> parametres.add(p.payeurId()));
        String jokers = String.join(", ", payeurs.stream().map(p -> "?").toList());
        jdbc.query("SELECT payeur_id, compte FROM comptes_payeurs WHERE center_id = ? AND payeur_id IN (" + jokers + ")",
                rs -> {
                    comptes.put(rs.getObject("payeur_id", UUID.class), rs.getString("compte"));
                }, parametres.toArray());
        return comptes;
    }
}
