package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.EtapesDeBilanJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des étapes du bilan pré-greffe sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class EtapesDeBilanJdbcContrat extends EtapesDeBilanContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        TablesDuBilanPreGreffe.vider(jdbc(), CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected EtapesDeBilan etapes() {
        return new EtapesDeBilanJdbc(jdbc());
    }

    private UUID poser(String categorie, String statut) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO etapes_bilan_pre_greffe (id, patient_id, center_id, categorie, libelle, statut, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", id, PATIENT.valeur(), CENTRE_A.valeur(), categorie, "Illisible", statut, Timestamp.from(T0),
                Timestamp.from(T0));
        return id;
    }

    @Test
    void une_ligne_illisible_est_ignoree_par_la_lecture_comptee_par_les_cles_et_supprimable() {
        etapes().ajouter(etape(CENTRE_A, PATIENT, CategorieDEtape.ORL, "Saine", T0));
        UUID categorie = poser("CATEGORIE_DISPARUE", "A_FAIRE");
        UUID statut = poser("ORL", "STATUT_DISPARU");
        assertThat(etapes().duPatient(CENTRE_A, PATIENT)).extracting(EtapeDeBilan::libelle).containsExactly("Saine");
        assertThat(etapes().clesDuPatient(CENTRE_A, PATIENT)).contains("CATEGORIE_DISPARUE|Illisible", "ORL|Illisible", "ORL|Saine");
        assertThat(etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, categorie)).isEmpty();
        assertThat(etapes().trouverEnVerrouillant(CENTRE_A, PATIENT, statut)).isEmpty();
        assertThat(etapes().supprimer(CENTRE_A, PATIENT, categorie)).isTrue();
        assertThat(etapes().supprimer(CENTRE_A, PATIENT, statut)).isTrue();
    }

    @Test
    void une_ligne_illisible_est_journalisee_avec_son_seul_identifiant() {
        UUID illisible = poser("CATEGORIE_DISPARUE", "A_FAIRE");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(EtapesDeBilanJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            etapes().duPatient(CENTRE_A, PATIENT);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(1);
        assertThat(capture.list.getFirst().getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(capture.list.getFirst().getFormattedMessage()).contains(illisible.toString()).doesNotContain("CATEGORIE_DISPARUE")
                .doesNotContain(PATIENT.valeur().toString());
    }
}
