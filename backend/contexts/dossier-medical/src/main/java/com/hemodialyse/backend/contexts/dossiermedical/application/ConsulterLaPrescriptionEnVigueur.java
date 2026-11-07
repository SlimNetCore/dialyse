package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * La prescription en vigueur un jour donné : la plus récente dont la date n'est pas postérieure à ce jour. Sans date, le jour est
 * celui du CENTRE (KB-MED-15, avant : jour de la JVM). Aucune : vide (204). Droits : MEDECIN, ADMIN et INFIRMIER.
 */
public class ConsulterLaPrescriptionEnVigueur {

    private final SessionCourante session;
    private final Prescriptions prescriptions;
    private final DetaillerLesPrescriptions details;
    private final HorlogeDuCentre horloge;

    public ConsulterLaPrescriptionEnVigueur(SessionCourante session, Prescriptions prescriptions, CatalogueDesArticles catalogue,
                                            HorlogeDuCentre horloge) {
        this.session = session;
        this.prescriptions = prescriptions;
        this.details = new DetaillerLesPrescriptions(catalogue);
        this.horloge = horloge;
    }

    public Optional<PrescriptionDetaillee> executer(UUID centreDemande, UUID patient, LocalDate date) {
        var centre = session.centre(centreDemande);
        LocalDate jour = date != null ? date : horloge.aujourdhui(centre);
        return prescriptions.duPatientSurLaPeriode(centre, new PatientId(patient), Periode.jusquau(jour)).stream().findFirst()
                .map(p -> details.de(centre, p));
    }
}
