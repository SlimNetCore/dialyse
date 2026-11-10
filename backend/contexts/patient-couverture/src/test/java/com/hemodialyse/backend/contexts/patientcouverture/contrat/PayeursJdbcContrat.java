package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.application.UsageDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agence;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agences;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CaisseDAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Caisses;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentrePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentresPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AgencesJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CaissesJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.CentresPayeursJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LectureDesPayeursJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat de la chaîne des payeurs sur les tables réelles (H2 et PostgreSQL 16 : sous-classes). */
abstract class PayeursJdbcContrat extends PayeursContrat {

    /** Écritures signalées par niveau (invalidation des listes en cache après commit). */
    protected final List<String> ecritures = new ArrayList<>();

    protected abstract JdbcTemplate jdbc();

    private Caisses laCaisse() {
        return new CaissesJdbc(jdbc(), centre -> ecritures.add("caisse " + centre.valeur().toString().substring(35)));
    }

    private Agences lAgence() {
        return new AgencesJdbc(jdbc(), centre -> ecritures.add("agence " + centre.valeur().toString().substring(35)));
    }

    private CentresPayeurs leCentrePayeur() {
        return new CentresPayeursJdbc(jdbc(), centre -> ecritures.add("centre payeur " + centre.valeur().toString().substring(35)));
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE.valeur(), AUTRE.valeur()}) {
            jdbc().update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM agence WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM caisse_assurance WHERE center_id = ?", centre);
        }
        ecritures.clear();
    }

    @Override
    protected Caisses caisses() {
        return laCaisse();
    }

    @Override
    protected Agences agences() {
        return lAgence();
    }

    @Override
    protected CentresPayeurs centresPayeurs() {
        return leCentrePayeur();
    }

    @Override
    protected LectureDesPayeurs lecture() {
        return new LectureDesPayeursJdbc(jdbc());
    }

    @Override
    protected UsageDesPayeurs usage() {
        return new LectureDesPayeursJdbc(jdbc());
    }

    @Test
    void chaque_ecriture_signale_le_centre_de_son_niveau_pour_l_invalidation_du_cache_et_pas_les_lectures() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur cp = centre(CENTRE, a, "CP-1", "Centre 1", null);
        String fin = CENTRE.valeur().toString().substring(35);
        assertThat(ecritures).containsExactly("caisse " + fin, "agence " + fin, "centre payeur " + fin);

        caisses().remplacer(c.corriger("CNAS2", "Caisse", null));
        agences().remplacer(a.corriger("AG-1B", "Agence 1", c.id().toString(), List.of(c)));
        centresPayeurs().remplacer(cp.corriger("CP-1B", "Centre 1", null, a.id().toString(), List.of(a)));
        centresPayeurs().supprimer(CENTRE, cp.id());
        agences().supprimer(CENTRE, a.id());
        caisses().supprimer(CENTRE, c.id());
        assertThat(ecritures).hasSize(9);

        caisses().trouver(CENTRE, c.id());
        caisses().duCentre(CENTRE);
        lecture().page(CENTRE, NiveauDePayeur.CAISSE, null, 0, 20);
        assertThat(ecritures).hasSize(9);
    }

    private void patient(CentreId centre, UUID centrePayeur) {
        jdbc().update("INSERT INTO patients (id, center_id, nom, prenom, sexe, date_admission, numero_assurance, type_patient, "
                        + "centre_payeur_id) VALUES (?, ?, 'Nom', 'Prenom', 'M', ?, ?, 'NON_VACANCIER', ?)",
                UUID.randomUUID(), centre.valeur(), Date.valueOf(LocalDate.of(2025, 1, 1)), "ASS-" + UUID.randomUUID(), centrePayeur);
    }

    @Test
    void les_patients_retiennent_leur_centre_payeur() {
        CaisseDAssurance c = caisse(CENTRE, "CNAS", "Caisse", null);
        Agence a = agence(CENTRE, c, "AG-1", "Agence 1");
        CentrePayeur utilise = centre(CENTRE, a, "CP-1", "Centre 1", null);
        CentrePayeur libre = centre(CENTRE, a, "CP-2", "Centre 2", null);
        patient(CENTRE, utilise.id());
        patient(CENTRE, utilise.id());
        patient(CENTRE, null);

        assertThat(usage().nombreDeReferences(NiveauDePayeur.CENTRE_PAYEUR, utilise.id())).isEqualTo(2);
        assertThat(usage().nombreDeReferences(NiveauDePayeur.CENTRE_PAYEUR, libre.id())).isZero();
        assertThat(usage().nombreDeReferences(NiveauDePayeur.CENTRE_PAYEUR, UUID.randomUUID())).isZero();
    }
}
