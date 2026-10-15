package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.PayeurVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeur;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des payeurs d'un centre et de leur compte client : même contrat que les adaptateurs JPA et JDBC (voir
 * les tests de contrat). Un seul objet joue les deux ports, comme les deux tables jointes par {@code payeur_id}.
 */
public class PayeursEnMemoire implements CompteClientDuPayeurRepository, LectureDesPayeurs {

    private final Map<Cle, PayeurVue> payeurs = new HashMap<>();
    private final Map<Cle, String> comptes = new HashMap<>();

    /**
     * Un payeur du référentiel (tenu hors de la comptabilité).
     */
    public PayeurId ajouterPayeur(CentreId centre, String code, String nom) {
        PayeurId id = new PayeurId(UUID.randomUUID());
        payeurs.put(new Cle(centre, id), new PayeurVue(id.valeur(), code, nom, null));
        return id;
    }

    /**
     * Une ligne de compte client, valide ou non (donnée ancienne ou écrite directement en base).
     */
    public void poserLeCompteBrut(CentreId centre, PayeurId payeur, String compte) {
        comptes.put(new Cle(centre, payeur), compte);
    }

    @Override
    public void enregistrer(CompteClientDuPayeur compteClient) {
        comptes.put(new Cle(compteClient.centre(), compteClient.payeur()), compteClient.compte().valeur());
    }

    @Override
    public void retirer(CentreId centre, PayeurId payeur) {
        comptes.remove(new Cle(centre, payeur));
    }

    @Override
    public PageDe<PayeurVue> lister(CentreId centre, String recherche, int page, int taille) {
        String motif = recherche == null || recherche.isBlank() ? null : "%" + recherche.trim().toLowerCase(Locale.ROOT) + "%";
        List<PayeurVue> tous = new ArrayList<>();
        payeurs.forEach((cle, p) -> {
            if (cle.centre().equals(centre)
                    && (motif == null || Like.correspond(motif, p.code().toLowerCase(Locale.ROOT))
                    || Like.correspond(motif, p.nom().toLowerCase(Locale.ROOT)))) {
                tous.add(new PayeurVue(p.payeurId(), p.code(), p.nom(), comptes.get(cle)));
            }
        });
        tous.sort(Comparator.comparing(PayeurVue::nom).thenComparing(PayeurVue::code));
        return PageDe.decoupee(tous, page, taille);
    }

    @Override
    public Optional<PayeurVue> trouver(CentreId centre, PayeurId payeur) {
        return Optional.ofNullable(payeurs.get(new Cle(centre, payeur)));
    }

    @Override
    public Optional<String> compteDuPayeur(CentreId centre, PayeurId payeur) {
        return Optional.ofNullable(comptes.get(new Cle(centre, payeur)));
    }

    private record Cle(CentreId centre, PayeurId payeur) {
    }
}
