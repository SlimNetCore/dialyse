package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DonneursVivantsJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des donneurs vivants sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class DonneursVivantsJdbcContrat extends DonneursVivantsContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        TablesDuBilanPreGreffe.vider(jdbc(), CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected DonneursVivants donneurs() {
        return new DonneursVivantsJdbc(jdbc());
    }

    private UUID poser(String lien, String statut, String crossmatch) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO donneurs_vivants (id, patient_id, center_id, nom, lien_parente, statut_bilan, crossmatch_resultat, created_at, "
                + "updated_at) VALUES (?,?,?,?,?,?,?,?,?)", id, PATIENT.valeur(), CENTRE_A.valeur(), "Illisible", lien, statut, crossmatch,
                Timestamp.from(T0), Timestamp.from(T0));
        return id;
    }

    @Test
    void une_ligne_illisible_est_ignoree_par_la_lecture_et_supprimable() {
        donneurs().ajouter(donneur(CENTRE_A, PATIENT, "Sain", T0));
        UUID lien = poser("LIEN_DISPARU", "CANDIDAT", "NON_FAIT");
        UUID statut = poser("PARENT", "STATUT_DISPARU", "NON_FAIT");
        UUID crossmatch = poser("PARENT", "CANDIDAT", "XM_DISPARU");
        assertThat(donneurs().duPatient(CENTRE_A, PATIENT)).extracting(DonneurVivant::nom).containsExactly("Sain");
        for (UUID illisible : new UUID[]{lien, statut, crossmatch}) {
            assertThat(donneurs().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
            assertThat(donneurs().supprimer(CENTRE_A, PATIENT, illisible)).isTrue();
        }
    }

    @Test
    void une_ligne_illisible_est_journalisee_avec_son_seul_identifiant() {
        UUID illisible = poser("LIEN_DISPARU", "CANDIDAT", "NON_FAIT");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DonneursVivantsJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            donneurs().duPatient(CENTRE_A, PATIENT);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(1);
        assertThat(capture.list.getFirst().getFormattedMessage()).contains(illisible.toString()).doesNotContain("LIEN_DISPARU")
                .doesNotContain("Illisible").doesNotContain(PATIENT.valeur().toString());
    }
}
