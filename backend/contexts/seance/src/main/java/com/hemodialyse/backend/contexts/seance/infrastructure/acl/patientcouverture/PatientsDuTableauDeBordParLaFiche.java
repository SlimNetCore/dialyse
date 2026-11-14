package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients;
import com.hemodialyse.backend.contexts.seance.application.PatientsDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.Demographie;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.seance.domain.PatientDuMois;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers Patient et couverture (LEC-PAT sous-slice i, ADR 0176) : la programmation des patients du centre, le sexe et la naissance
 * des patients présents dans le mois, et leur identité, par les langages publiés {@link ProgrammationDesPatients} et
 * {@link LotsDePatients}. Les séances restent lues dans la table de la Séance. Remplace {@code PatientsDuTableauDeBordJdbc}
 * (SQL direct sur {@code patients}, résidu R1 de l'ADR 0095) ; KB-SEA-64 reproduit : le centre du patient présent n'est pas filtré.
 */
public class PatientsDuTableauDeBordParLaFiche implements PatientsDuTableauDeBord {

    private final JdbcTemplate jdbc;
    private final ProgrammationDesPatients programmation;
    private final LotsDePatients lots;

    public PatientsDuTableauDeBordParLaFiche(JdbcTemplate jdbc, ProgrammationDesPatients programmation, LotsDePatients lots) {
        this.jdbc = jdbc;
        this.programmation = programmation;
        this.lots = lots;
    }

    @Override
    public List<PatientDuMois> duCentre(CentreId centre) {
        return programmation.duCentre(centre.valeur()).stream()
                .map(p -> new PatientDuMois(new PatientId(p.patient()), p.sexe(), p.dateNaissance(), p.dateAdmission(), p.enSommeil(),
                        p.etat(), p.dateEvenement(), p.joursDeDialyse()))
                .toList();
    }

    @Override
    public List<Demographie> presentsDuMois(CentreId centre, MoisDuTableauDeBord mois) {
        List<UUID> presents = jdbc.query("""
                        SELECT DISTINCT s.patient_id
                        FROM seances s
                        WHERE s.center_id = ?
                          AND s.date_seance BETWEEN ? AND ?
                          AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')
                        """,
                (rs, i) -> rs.getObject("patient_id", UUID.class),
                centre.valeur(), Date.valueOf(mois.premierJour()), Date.valueOf(mois.dernierJour()));
        return lots.etatCivil(presents).stream()
                .map(e -> new Demographie(new PatientId(e.patient()), e.sexe(), e.dateNaissance()))
                .toList();
    }

    @Override
    public Map<PatientId, Identite> identites(CentreId centre) {
        Map<PatientId, Identite> identites = new LinkedHashMap<>();
        lots.identitesDuCentre(centre.valeur()).forEach((patient, nom) ->
                identites.put(new PatientId(patient), new Identite(texte(nom.nom()), texte(nom.prenom()))));
        return identites;
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
