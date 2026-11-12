package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DossierDeGreffe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PL2 : l'export PDF du dossier de greffe garde le centre de la session (403), puis le patient (404 {@code PATIENT_INTROUVABLE}), puis
 * seulement lit la carte du patient et fait imprimer ; une carte absente laisse l'impression sans en-tête patient.
 */
class ExporterLeDossierDeGreffeTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000000c"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final DossierDeGreffe DOSSIER = new DossierDeGreffe(null, List.of(), List.of(), List.of(), List.of());

    private final List<String> appels = new ArrayList<>();
    private CentreId centreDeLaSession = CENTRE;
    private Optional<CartesDesPatients.Carte> carte = Optional.of(new CartesDesPatients.Carte("PAT-1", "Benali", "Karim", "A+"));

    private final SessionCourante session = demande -> {
        appels.add("session");
        if (centreDeLaSession == null || (demande != null && !demande.equals(centreDeLaSession.valeur()))) throw new AccesRefuse();
        return centreDeLaSession;
    };
    private final DossierDeGreffePublie dossiers = (centre, patient) -> {
        appels.add("dossier");
        if (!patient.equals(PATIENT)) throw new PatientIntrouvable();
        return DOSSIER;
    };
    private final CartesDesPatients cartes = (centre, patient) -> {
        appels.add("carte");
        return carte;
    };
    private final ImpressionDuDossierDeGreffe impression = (centre, carteLue, dossier) -> {
        appels.add("impression " + centre.valeur() + " " + carteLue.map(CartesDesPatients.Carte::code).orElse("sans carte"));
        return new byte[]{'%', 'P', 'D', 'F'};
    };
    private final ExporterLeDossierDeGreffe exporter = new ExporterLeDossierDeGreffe(session, dossiers, cartes, impression);

    @Test
    void should_imprimer_le_dossier_du_patient_du_centre_de_la_session() {
        byte[] pdf = exporter.executer(null, PATIENT);

        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(appels).containsExactly("session", "dossier", "carte", "impression " + CENTRE.valeur() + " PAT-1");
    }

    @Test
    void should_laisser_l_impression_sans_carte_quand_la_fiche_est_absente() {
        carte = Optional.empty();

        exporter.executer(CENTRE.valeur(), PATIENT);

        assertThat(appels).last().isEqualTo("impression " + CENTRE.valeur() + " sans carte");
    }

    @Test
    void should_refuser_un_autre_centre_avant_toute_lecture() {
        assertThatThrownBy(() -> exporter.executer(UUID.randomUUID(), PATIENT)).isInstanceOf(AccesRefuse.class);
        centreDeLaSession = null;
        assertThatThrownBy(() -> exporter.executer(null, PATIENT)).isInstanceOf(AccesRefuse.class);

        assertThat(appels).containsOnly("session");
    }

    @Test
    void should_refuser_un_patient_inconnu_avant_de_lire_la_carte() {
        assertThatThrownBy(() -> exporter.executer(null, UUID.randomUUID())).isInstanceOf(PatientIntrouvable.class);

        assertThat(appels).containsExactly("session", "dossier");
    }
}
