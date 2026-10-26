package com.hemodialyse.backend.contexts.planification.interfaces;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.application.DelierAvantLaSuppressionDuCompte;
import com.hemodialyse.backend.contexts.planification.application.SuivreLEtatDuCompte;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesFichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesIdentifiantsDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers.EcritureConcurrenteDesFiches;
import com.hemodialyse.backend.contexts.planification.doubles.InfirmiersEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.IdentiteDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.Qualification;
import com.hemodialyse.backend.contexts.planification.interfaces.publie.FichesDesInfirmiersPourLeLegacy;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers.CompteLieAUneFicheActive;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le langage publié d'écriture vers l'administration des utilisateurs : traduction des identifiants et du refus, sans logique.
 */
class FichesDesInfirmiersPourLeLegacyTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99997814-0000-0000-0000-00000000000a"));

    private final InfirmiersEnMemoire monde = new InfirmiersEnMemoire();
    private final FichesDesInfirmiers publie = new FichesDesInfirmiersPourLeLegacy(
            new SuivreLEtatDuCompte(monde.unite, monde.infirmiers),
            new DelierAvantLaSuppressionDuCompte(monde.unite, monde.infirmiers),
            new VerrouillerLesIdentifiantsDeConnexion(monde.unite, monde.comptesUtilisateurs),
            new VerrouillerLesFichesDesInfirmiers(monde.unite, monde.infirmiers));

    private Infirmier ficheLiee(CompteId compte) {
        Infirmier fiche = Infirmier.enregistrer(new InfirmierId(UUID.randomUUID()), CENTRE, Matricule.depuisLaSaisie("M-1"),
                new IdentiteDeLInfirmier("Amrani", null, null), Qualification.INFIRMIER, false).lierCompte(compte, true);
        monde.infirmiers.enregistrer(fiche);
        return fiche;
    }

    @Test
    void should_make_the_nurse_follow_the_account_state() {
        CompteId compte = monde.compte("sara", "Sara", true, CENTRE);
        Infirmier fiche = ficheLiee(compte);

        publie.suivreLEtatDuCompte(compte.valeur(), false, CENTRE.valeur());

        assertThat(monde.infirmiers.pourEcriture(CENTRE, fiche.id()).orElseThrow().actif()).isFalse();
        publie.suivreLEtatDuCompte(compte.valeur(), true, null);
        assertThat(monde.infirmiers.pourEcriture(CENTRE, fiche.id()).orElseThrow().actif()).isTrue();
    }

    @Test
    void should_take_the_identifier_lock_and_translate_a_concurrent_write_into_the_published_exception() {
        publie.verrouillerLesIdentifiantsDeConnexion();
        assertThat(monde.verrousDIdentifiants).isEqualTo(1);

        FichesDesInfirmiers enConflit = new FichesDesInfirmiersPourLeLegacy(
                new SuivreLEtatDuCompte(monde.unite, monde.infirmiers) {
                    @Override
                    public void executer(CompteId compte, boolean compteActif, CentreId centre) {
                        throw new EcritureConcurrente();
                    }
                },
                new DelierAvantLaSuppressionDuCompte(monde.unite, monde.infirmiers) {
                    @Override
                    public void executer(CompteId compte) {
                        throw new EcritureConcurrente();
                    }
                },
                new VerrouillerLesIdentifiantsDeConnexion(monde.unite, monde.comptesUtilisateurs) {
                    @Override
                    public void executer() {
                        throw new EcritureConcurrente();
                    }
                },
                new VerrouillerLesFichesDesInfirmiers(monde.unite, monde.infirmiers) {
                    @Override
                    public void executer(CentreId centre, java.util.Collection<InfirmierId> fiches) {
                        throw new EcritureConcurrente();
                    }
                });

        UUID compte = UUID.randomUUID();
        assertThatThrownBy(() -> enConflit.suivreLEtatDuCompte(compte, true, null)).isInstanceOf(EcritureConcurrenteDesFiches.class);
        assertThatThrownBy(() -> enConflit.delierAvantLaSuppressionDuCompte(compte)).isInstanceOf(EcritureConcurrenteDesFiches.class);
        assertThatThrownBy(enConflit::verrouillerLesIdentifiantsDeConnexion).isInstanceOf(EcritureConcurrenteDesFiches.class);
        assertThatThrownBy(() -> enConflit.verrouillerLesFiches(CENTRE.valeur(), java.util.List.of(compte)))
                .isInstanceOf(EcritureConcurrenteDesFiches.class);
    }

    @Test
    void should_translate_the_refusal_to_delete_an_account_linked_to_an_active_nurse() {
        CompteId compte = monde.compte("sara", "Sara", true, CENTRE);
        Infirmier fiche = ficheLiee(compte);

        assertThatThrownBy(() -> publie.delierAvantLaSuppressionDuCompte(compte.valeur()))
                .isInstanceOf(CompteLieAUneFicheActive.class);
        monde.infirmiers.enregistrer(fiche.desactiver());
        publie.delierAvantLaSuppressionDuCompte(compte.valeur());

        assertThat(monde.infirmiers.pourEcriture(CENTRE, fiche.id()).orElseThrow().compte()).isEmpty();
        assertThat(monde.comptes).containsKey(compte);
        ComptesUtilisateurs.Compte inchange = monde.comptesUtilisateurs.parIds(java.util.List.of(compte)).getFirst();
        assertThat(inchange.actif()).isTrue();
    }
}
