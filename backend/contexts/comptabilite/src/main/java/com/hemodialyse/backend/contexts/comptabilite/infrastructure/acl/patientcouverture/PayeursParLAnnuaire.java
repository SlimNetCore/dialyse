package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.PayeurVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ACL vers le langage publié de Patient et couverture ({@link PayeursDuCentre}, RF3c, ADR 0128 ; ADR 0121, décision 3 révisée) : les payeurs
 * d'un centre sont les centres payeurs lus dans l'annuaire, jamais dans la table {@code centre_payeur}. Le compte client propre à chaque
 * payeur reste lu dans {@code comptes_payeurs}, table de la Comptabilité. Tri par nom puis code ; recherche sur le code et le nom sans casse
 * avec les jokers SQL non échappés ({@code %}, {@code _}, KB-CPT-05), reproduits ici puisque le filtrage n'est plus fait par la base.
 */
public final class PayeursParLAnnuaire implements LectureDesPayeurs {

    private static final Comparator<String> TEXTE = Comparator.nullsLast(Comparator.<String>naturalOrder());
    private static final Comparator<PayeursDuCentre.CentrePayeurPublie> PAR_NOM_PUIS_CODE =
            Comparator.comparing(PayeursDuCentre.CentrePayeurPublie::nom, TEXTE)
                    .thenComparing(PayeursDuCentre.CentrePayeurPublie::code, TEXTE);

    private final PayeursDuCentre annuaire;
    private final JdbcTemplate jdbc;

    public PayeursParLAnnuaire(PayeursDuCentre annuaire, JdbcTemplate jdbc) {
        this.annuaire = annuaire;
        this.jdbc = jdbc;
    }

    @Override
    public PageDe<PayeurVue> lister(CentreId centre, String recherche, int page, int taille) {
        Pattern motif = recherche == null || recherche.isBlank() ? null : motif("%" + recherche.trim().toLowerCase(Locale.ROOT) + "%");
        List<PayeursDuCentre.CentrePayeurPublie> retenus = new ArrayList<>();
        for (PayeursDuCentre.CentrePayeurPublie payeur : annuaire.centresPayeurs(centre.valeur())) {
            if (motif == null || correspond(motif, payeur.code()) || correspond(motif, payeur.nom())) {
                retenus.add(payeur);
            }
        }
        retenus.sort(PAR_NOM_PUIS_CODE);
        PageDe<PayeursDuCentre.CentrePayeurPublie> decoupee = PageDe.decoupee(retenus, page, taille);
        Map<UUID, String> comptes = comptesDes(centre, decoupee.items());
        return new PageDe<>(decoupee.items().stream()
                .map(p -> new PayeurVue(p.id(), p.code(), p.nom(), comptes.get(p.id()))).toList(),
                decoupee.total(), page, taille);
    }

    @Override
    public Optional<PayeurVue> trouver(CentreId centre, PayeurId payeur) {
        return annuaire.detail(centre.valeur(), payeur.valeur())
                .map(chaine -> new PayeurVue(chaine.centrePayeur().id(), chaine.centrePayeur().code(), chaine.centrePayeur().nom(), null));
    }

    @Override
    public Optional<String> compteDuPayeur(CentreId centre, PayeurId payeur) {
        return jdbc.queryForList("SELECT compte FROM comptes_payeurs WHERE center_id = ? AND payeur_id = ?", String.class,
                centre.valeur(), payeur.valeur()).stream().findFirst();
    }

    private Map<UUID, String> comptesDes(CentreId centre, List<PayeursDuCentre.CentrePayeurPublie> payeurs) {
        Map<UUID, String> comptes = new HashMap<>();
        if (payeurs.isEmpty()) {
            return comptes;
        }
        List<Object> parametres = new ArrayList<>(List.of(centre.valeur()));
        payeurs.forEach(p -> parametres.add(p.id()));
        String jokers = String.join(", ", payeurs.stream().map(p -> "?").toList());
        jdbc.query("SELECT payeur_id, compte FROM comptes_payeurs WHERE center_id = ? AND payeur_id IN (" + jokers + ")",
                rs -> {
                    comptes.put(rs.getObject("payeur_id", UUID.class), rs.getString("compte"));
                }, parametres.toArray());
        return comptes;
    }

    private static boolean correspond(Pattern motif, String texte) {
        return texte != null && motif.matcher(texte.toLowerCase(Locale.ROOT)).matches();
    }

    /** Le {@code LIKE} de SQL : {@code %} = toute suite, {@code _} = un caractère, {@code \} protège le caractère suivant. */
    private static Pattern motif(String like) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < like.length(); i++) {
            char c = like.charAt(i);
            if (c == '\\' && i + 1 < like.length()) {
                regex.append(Pattern.quote(String.valueOf(like.charAt(++i))));
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }
}
