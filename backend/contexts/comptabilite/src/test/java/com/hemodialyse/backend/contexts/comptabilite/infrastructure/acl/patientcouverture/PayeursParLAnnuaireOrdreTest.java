package com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.payeur.PayeurVue;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF4, constat 4 de la revue RF1-RF3d : le tri en Java par nom puis code ne départageait pas deux payeurs de mêmes nom et code ; leur ordre
 * dépendait de l'ordre de l'annuaire (donc des pages). Départage final par identifiant : l'ordre est le même quel que soit l'annuaire.
 */
class PayeursParLAnnuaireOrdreTest {

    private static final UUID CENTRE = UUID.fromString("99996800-0000-0000-0000-0000000000c0");
    private static final UUID PETIT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MOYEN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GRAND = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void deux_payeurs_de_memes_nom_et_code_sont_ordonnes_par_identifiant_quel_que_soit_l_ordre_de_l_annuaire() {
        List<UUID> ordreDirect = identifiants(lecture(List.of(payeur(PETIT, "A", "Nom"), payeur(MOYEN, "A", "Nom"), payeur(GRAND, "A", "Nom"))));
        List<UUID> ordreInverse = identifiants(lecture(List.of(payeur(GRAND, "A", "Nom"), payeur(MOYEN, "A", "Nom"), payeur(PETIT, "A", "Nom"))));

        assertThat(ordreDirect).containsExactly(PETIT, MOYEN, GRAND);
        assertThat(ordreInverse).containsExactly(PETIT, MOYEN, GRAND);
    }

    @Test
    void le_nom_puis_le_code_priment_sur_l_identifiant() {
        List<UUID> ordre = identifiants(lecture(List.of(payeur(PETIT, "B", "Zeta"), payeur(MOYEN, "B", "Alpha"), payeur(GRAND, "A", "Alpha"))));

        assertThat(ordre).containsExactly(GRAND, MOYEN, PETIT);
    }

    private static PayeursParLAnnuaire lecture(List<PayeursDuCentre.CentrePayeurPublie> annuaire) {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:ordre_payeurs;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE IF NOT EXISTS comptes_payeurs (center_id UUID NOT NULL, payeur_id UUID NOT NULL, compte VARCHAR(255))");
        return new PayeursParLAnnuaire(new AnnuaireFixe(annuaire), jdbc);
    }

    private static List<UUID> identifiants(PayeursParLAnnuaire lecture) {
        PageDe<PayeurVue> page = lecture.lister(new CentreId(CENTRE), null, 0, 50);
        return new ArrayList<>(page.items().stream().map(PayeurVue::payeurId).toList());
    }

    private static PayeursDuCentre.CentrePayeurPublie payeur(UUID id, String code, String nom) {
        return new PayeursDuCentre.CentrePayeurPublie(id, code, nom, null, null);
    }

    private record AnnuaireFixe(List<PayeursDuCentre.CentrePayeurPublie> payeurs) implements PayeursDuCentre {
        @Override
        public List<CaissePubliee> caisses(UUID centre) {
            return List.of();
        }

        @Override
        public List<AgencePubliee> agences(UUID centre) {
            return List.of();
        }

        @Override
        public List<CentrePayeurPublie> centresPayeurs(UUID centre) {
            return payeurs;
        }

        @Override
        public Optional<ChaineDuPayeur> detail(UUID centre, UUID centrePayeur) {
            return Optional.empty();
        }

        @Override
        public List<ChaineDuPayeur> chaines(UUID centre) {
            return List.of();
        }

        @Override
        public boolean existe(UUID centre, UUID centrePayeur) {
            return true;
        }
    }
}
