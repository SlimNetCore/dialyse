package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.application.GererLesGabarits.ResultatDuDepot;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.GabaritsLivresEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.HistoriquesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.ModelesDuCentreEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.UniteImmediate;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.VerificationFixe;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.JrxmlSecurityValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le dépôt, la mise en vigueur et le retour au gabarit livré (DOC3), avec des ports en mémoire. La liste blanche de sécurité est la VRAIE
 * ({@link JrxmlSecurityValidator}, contre le gabarit d'attestation livré) : aucun refus de sécurité n'est simulé.
 */
class GererLesGabaritsTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);
    private static final UUID ATTESTATION = new UUID(5L, 1L);
    private static final UUID SANS_GABARIT = new UUID(5L, 2L);
    private static final UUID DE_E = new UUID(5L, 3L);
    private static final String CHEMIN = "reports/attestation.jrxml";

    private final HistoriquesEnMemoire historiques = new HistoriquesEnMemoire();
    private final ModelesDuCentreEnMemoire modeles = new ModelesDuCentreEnMemoire();
    private final GabaritsLivresEnMemoire livres = new GabaritsLivresEnMemoire();
    private final VerificationFixe verification = new VerificationFixe();
    private final UniteImmediate unite = new UniteImmediate();
    private GererLesGabarits gabarits;
    private String original;
    private int identifiants;

    @BeforeEach
    void preparer() throws Exception {
        try (var in = new ClassPathResource(CHEMIN).getInputStream()) {
            original = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        livres.livrer(CHEMIN, original);
        modeles.ajouter(new ModeleDuCentre(ATTESTATION, D, "ATT", "ATTESTATION", CHEMIN));
        modeles.ajouter(new ModeleDuCentre(SANS_GABARIT, D, "PERSO", "CUSTOM", "reports/inconnu.jrxml"));
        modeles.ajouter(new ModeleDuCentre(DE_E, E, "ATT-E", "ATTESTATION", CHEMIN));
        gabarits = new GererLesGabarits(new SessionFixe(D), modeles, livres, historiques, verification, unite,
                Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC), () -> new UUID(9L, ++identifiants));
    }

    private ResultatDuDepot deposer(String contenu) {
        return gabarits.deposer(D, ATTESTATION, "modele.jrxml", contenu.getBytes(StandardCharsets.UTF_8), "note");
    }

    private String marge(int valeur) {
        return original.replace("topMargin=\"40\"", "topMargin=\"" + valeur + "\"");
    }

    @Test
    void should_store_a_valid_deposit_as_version_1_in_force_by_the_session_user_at_the_clock_time() {
        ResultatDuDepot r = deposer(marge(36));

        assertThat(r.accepte()).isTrue();
        assertThat(r.version().numero()).isEqualTo(1);
        assertThat(r.version().active()).isTrue();
        assertThat(r.version().deposePar()).isEqualTo("admin-fictif");
        assertThat(r.version().deposeLe()).isEqualTo(Instant.parse("2026-11-13T10:00:00Z"));
        assertThat(historiques.contenuEnVigueur(D, ATTESTATION)).hasValue(marge(36));
        assertThat(unite.executions).isEqualTo(1);
    }

    @Test
    void should_compile_the_candidate_on_a_trial_basis_before_keeping_it() {
        deposer(marge(36));

        assertThat(verification.verifies).containsExactly(marge(36));
    }

    @Test
    void should_refuse_with_the_compile_message_and_store_nothing_when_the_template_does_not_compile() {
        verification.echec = new IllegalStateException("enveloppe", new RuntimeException("For input string: \"x\"\nsuite"));

        ResultatDuDepot r = deposer(marge(36));

        assertThat(r.accepte()).isFalse();
        assertThat(r.anomalies()).singleElement().satisfies(a -> {
            assertThat(a.code()).isEqualTo("COMPILE_FAILED");
            assertThat(a.detail()).isEqualTo("For input string: \"x\"");
        });
        assertThat(historiques.enregistrements).isZero();
    }

    @Test
    void should_never_weaken_the_security_whitelist_a_forbidden_expression_and_a_changed_query_are_refused() {
        // DOC5h (ADR 0200) : l'attestation livrée n'a plus de requête ; une requête ajoutée est refusée.
        String requete = original.replace("<field name=\"NOM\"",
                "<queryString><![CDATA[SELECT username, password_hash FROM app_user]]></queryString><field name=\"NOM\"");
        String code = original.replaceFirst("(?s)<textFieldExpression>.*?</textFieldExpression>",
                "<textFieldExpression><![CDATA[Runtime.getRuntime().exec(\"calc\")]]></textFieldExpression>");

        assertThat(deposer(requete).anomalies()).extracting(a -> a.code()).containsExactly("QUERY_MODIFIED");
        assertThat(deposer(code).anomalies()).extracting(a -> a.code()).contains("EXPRESSION_FORBIDDEN");
        assertThat(deposer("pas du XML").anomalies()).extracting(a -> a.code()).containsExactly("XML_INVALID");
        assertThat(historiques.enregistrements).isZero();
        assertThat(verification.verifies).isEmpty();
    }

    @Test
    void should_refuse_the_form_of_the_file_in_the_order_extension_empty_size_reference_encoding() {
        byte[] bon = marge(36).getBytes(StandardCharsets.UTF_8);

        assertThat(gabarits.deposer(D, ATTESTATION, "x.exe", new byte[]{1}, null).anomalies()).extracting(a -> a.code()).containsExactly("FILE_TYPE");
        assertThat(gabarits.deposer(D, ATTESTATION, "x.jrxml", new byte[0], null).anomalies()).extracting(a -> a.code()).containsExactly("EMPTY");
        assertThat(gabarits.deposer(D, ATTESTATION, "x.jrxml", new byte[JrxmlSecurityValidator.MAX_BYTES + 1], null).anomalies())
                .extracting(a -> a.code()).containsExactly("TOO_LARGE");
        assertThat(gabarits.deposer(D, SANS_GABARIT, "x.jrxml", bon, null).anomalies()).extracting(a -> a.code()).containsExactly("NOT_CUSTOMIZABLE");
        assertThat(gabarits.deposer(D, ATTESTATION, "x.jrxml", new byte[]{'<', (byte) 0xE9}, null).anomalies()).extracting(a -> a.code())
                .containsExactly("ENCODING");
        // un fichier vide l'emporte sur un modèle sans gabarit livré ; la taille, sur l'absence de gabarit livré
        assertThat(gabarits.deposer(D, SANS_GABARIT, "x.jrxml", new byte[0], null).anomalies()).extracting(a -> a.code()).containsExactly("EMPTY");
    }

    @Test
    void should_keep_the_deposit_of_exactly_the_maximum_size() {
        byte[] exact = new byte[JrxmlSecurityValidator.MAX_BYTES];
        byte[] base = original.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(base, 0, exact, 0, base.length);
        java.util.Arrays.fill(exact, base.length, exact.length, (byte) ' ');

        assertThat(gabarits.deposer(D, ATTESTATION, "x.jrxml", exact, null).accepte()).isTrue();
    }

    @Test
    void should_treat_the_model_of_another_centre_or_an_unknown_one_as_missing_for_every_operation() {
        assertThatThrownBy(() -> gabarits.historique(D, DE_E, 0, 20)).isInstanceOf(GabaritIntrouvable.class);
        assertThatThrownBy(() -> gabarits.gabarit(D, DE_E, null, false)).isInstanceOf(GabaritIntrouvable.class);
        assertThatThrownBy(() -> gabarits.deposer(D, DE_E, "x.jrxml", new byte[]{1}, null)).isInstanceOf(GabaritIntrouvable.class);
        assertThatThrownBy(() -> gabarits.mettreEnVigueur(D, DE_E, 1)).isInstanceOf(GabaritIntrouvable.class);
        assertThatThrownBy(() -> gabarits.reinitialiser(D, DE_E)).isInstanceOf(GabaritIntrouvable.class);
        assertThatThrownBy(() -> gabarits.historique(D, new UUID(7L, 7L), 0, 20)).isInstanceOf(GabaritIntrouvable.class);
    }

    @Test
    void should_refuse_a_centre_that_is_not_the_one_of_the_session() {
        assertThatThrownBy(() -> gabarits.exigerLeCentre(E)).isInstanceOf(AccesRefuse.class);
        assertThat(gabarits.exigerLeCentre(null).valeur()).isEqualTo(D);
    }

    @Test
    void should_give_the_version_in_force_then_the_delivered_template_to_edit() {
        assertThat(gabarits.gabarit(D, ATTESTATION, null, false).contenu()).isEqualTo(original);
        deposer(marge(36));

        assertThat(gabarits.gabarit(D, ATTESTATION, null, false).contenu()).isEqualTo(marge(36));
        assertThat(gabarits.gabarit(D, ATTESTATION, null, true).contenu()).isEqualTo(original);
        assertThat(gabarits.gabarit(D, ATTESTATION, 1, true).contenu()).isEqualTo(marge(36));
        assertThat(gabarits.gabarit(D, ATTESTATION, null, false).codeDuModele()).isEqualTo("ATT");
        assertThatThrownBy(() -> gabarits.gabarit(D, ATTESTATION, 2, false)).isInstanceOf(GabaritIntrouvable.class);
        assertThatThrownBy(() -> gabarits.gabarit(D, SANS_GABARIT, null, false)).isInstanceOf(GabaritIntrouvable.class);
    }

    @Test
    void should_page_the_history_most_recent_first_and_tell_the_version_in_force() {
        deposer(marge(31));
        deposer(marge(32));
        deposer(marge(33));
        gabarits.mettreEnVigueur(D, ATTESTATION, 2);

        var page = gabarits.historique(D, ATTESTATION, 0, 2);
        var suite = gabarits.historique(D, ATTESTATION, 1, 2);

        assertThat(page.versions()).extracting(v -> v.numero()).containsExactly(3, 2);
        assertThat(suite.versions()).extracting(v -> v.numero()).containsExactly(1);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.versionEnVigueur()).isEqualTo(2);
        assertThat(gabarits.historique(D, ATTESTATION, -4, 0).taille()).isEqualTo(1);
    }

    @Test
    void should_put_an_older_version_in_force_and_go_back_to_the_delivered_template() {
        deposer(marge(31));
        deposer(marge(32));

        assertThat(gabarits.mettreEnVigueur(D, ATTESTATION, 1).active()).isTrue();
        assertThat(historiques.contenuEnVigueur(D, ATTESTATION)).hasValue(marge(31));
        assertThatThrownBy(() -> gabarits.mettreEnVigueur(D, ATTESTATION, 7)).isInstanceOf(GabaritIntrouvable.class);

        gabarits.reinitialiser(D, ATTESTATION);

        assertThat(historiques.contenuEnVigueur(D, ATTESTATION)).isEmpty();
        assertThat(gabarits.historique(D, ATTESTATION, 0, 20).versionEnVigueur()).isNull();
        assertThat(gabarits.historique(D, ATTESTATION, 0, 20).total()).isEqualTo(2);
    }

    @Test
    void should_remove_the_history_with_the_model_only_when_the_model_belongs_to_the_centre() {
        deposer(marge(31));

        gabarits.retirerLHistorique(E, ATTESTATION);
        assertThat(historiques.contenuEnVigueur(D, ATTESTATION)).isPresent();

        gabarits.retirerLHistorique(D, ATTESTATION);
        assertThat(historiques.contenuEnVigueur(D, ATTESTATION)).isEmpty();
    }
}
