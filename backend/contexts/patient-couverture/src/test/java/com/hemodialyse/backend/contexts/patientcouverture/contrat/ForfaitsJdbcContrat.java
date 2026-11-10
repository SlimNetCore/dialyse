package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Forfait;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.ForfaitsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.UsageDuForfaitParLesPec;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du catalogue de forfaits sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class ForfaitsJdbcContrat extends ForfaitsContrat {

    /** Centres dont une écriture a été signalée (invalidation du cache de lecture après commit). */
    protected final List<com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId> ecritures = new ArrayList<>();

    protected abstract JdbcTemplate jdbc();

    private ForfaitsJdbc adapter() {
        return new ForfaitsJdbc(jdbc(), ecritures::add);
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc().update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM forfait WHERE center_id = ?", centre);
        }
        ecritures.clear();
    }

    @Override
    protected CatalogueDeForfaits catalogue() {
        return adapter();
    }

    @Override
    protected LectureDuCatalogueDeForfaits lecture() {
        return adapter();
    }

    @Override
    protected ForfaitsDuCentre publie() {
        return adapter();
    }

    @Test
    void chaque_ecriture_signale_le_centre_pour_l_invalidation_du_cache_et_pas_les_lectures() {
        Forfait f = enregistre(CENTRE, "A", "Alpha", null);
        assertThat(ecritures).containsExactly(CENTRE);

        catalogue().remplacer(f.corriger("A2", "Alpha Est", "1"));
        catalogue().supprimer(CENTRE, f.id());
        assertThat(ecritures).containsExactly(CENTRE, CENTRE, CENTRE);

        catalogue().trouver(CENTRE, f.id());
        catalogue().duCentre(CENTRE);
        lecture().page(CENTRE, null, 0, 20);
        publie().lister(CENTRE.valeur());
        assertThat(ecritures).hasSize(3);
    }

    private void pec(com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId centre, UUID demande, UUID effectif) {
        jdbc().update("INSERT INTO prise_en_charge (id, patient_id, center_id, forfait_demande_id, forfait_effectif_id, statut, created_at) "
                        + "VALUES (?,?,?,?,?,'DEMANDEE',?)", UUID.randomUUID(), UUID.randomUUID(), centre.valeur(), demande, effectif,
                Timestamp.from(Instant.parse("2026-01-01T10:00:00Z")));
    }

    @Test
    void les_prises_en_charge_retiennent_le_forfait_demande_et_effectif_des_seules_pec_du_centre_du_forfait() {
        // KB-RF2-02 corrigé (ADR 0125) : les prises en charge d'un autre centre ne comptent plus
        Forfait f = enregistre(CENTRE, "A", "Alpha", null);
        Forfait libre = enregistre(CENTRE, "B", "Beta", null);
        pec(CENTRE, f.id(), null);
        pec(CENTRE, null, f.id());
        pec(CENTRE, f.id(), f.id());
        pec(AUTRE, f.id(), null);
        pec(AUTRE, null, f.id());

        UsageDuForfaitParLesPec usage = new UsageDuForfaitParLesPec(jdbc());
        assertThat(usage.nombreDeReferences(CENTRE.valeur(), f.id())).isEqualTo(4);
        assertThat(usage.nombreDeReferences(CENTRE.valeur(), libre.id())).isZero();
        assertThat(usage.nombreDeReferences(CENTRE.valeur(), UUID.randomUUID())).isZero();
    }

    @Test
    void un_forfait_utilise_seulement_par_des_pec_d_un_autre_centre_n_a_aucune_reference() {
        Forfait f = enregistre(CENTRE, "A", "Alpha", null);
        pec(AUTRE, f.id(), f.id());

        assertThat(new UsageDuForfaitParLesPec(jdbc()).nombreDeReferences(CENTRE.valeur(), f.id())).isZero();
    }
}
