package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CarnetDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Transporteur;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CategoriesDeTransportJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.TransporteursJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du carnet de transporteurs sur les tables réelles (H2 et PostgreSQL 16 : sous-classes). */
abstract class TransporteursJdbcContrat extends TransporteursContrat {

    /** Centres dont une écriture a été signalée (invalidation des caches de lecture après commit). */
    protected final List<CentreId> ecritures = new ArrayList<>();

    protected abstract JdbcTemplate jdbc();

    private TransporteursJdbc adapter() {
        return new TransporteursJdbc(jdbc(), ecritures::add);
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc().update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM transporteur WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM categorie_transport WHERE center_id = ?", centre);
        }
        ecritures.clear();
    }

    @Override
    protected CarnetDeTransporteurs carnet() {
        return adapter();
    }

    @Override
    protected LectureDesTransporteurs lecture() {
        return adapter();
    }

    @Override
    protected Transporteurs publie() {
        return adapter();
    }

    @Override
    protected CategoriesDeTransport categories() {
        return new CategoriesDeTransportJdbc(jdbc());
    }

    @Override
    protected UUID categorie(CentreId centre, String libelle) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO categorie_transport (id, center_id, libelle) VALUES (?,?,?)", id, centre.valeur(), libelle);
        return id;
    }

    @Override
    protected void patientUtilisant(CentreId centreDuPatient, UUID aller, UUID retour) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "transporteur_aller_id, transporteur_retour_id) VALUES (?,?,?,?,?,?,?,?,?,?)", id, centreDuPatient.valeur(),
                "Patient", "Test", "M", Date.valueOf(LocalDate.of(2020, 1, 1)), "ASS-" + id, "NON_VACANCIER", aller, retour);
    }

    @Test
    void chaque_ecriture_signale_le_centre_pour_l_invalidation_des_caches_et_pas_les_lectures() {
        Transporteur t = enregistre(CENTRE, "Alpha", null);
        assertThat(ecritures).containsExactly(CENTRE);

        carnet().remplacer(t.corriger("Alpha Est", null));
        carnet().supprimer(CENTRE, t.id());
        assertThat(ecritures).containsExactly(CENTRE, CENTRE, CENTRE);

        carnet().trouver(CENTRE, t.id());
        carnet().duCentre(CENTRE);
        lecture().page(CENTRE, null, 0, 20);
        publie().lister(CENTRE.valeur());
        assertThat(ecritures).hasSize(3);
    }
}
