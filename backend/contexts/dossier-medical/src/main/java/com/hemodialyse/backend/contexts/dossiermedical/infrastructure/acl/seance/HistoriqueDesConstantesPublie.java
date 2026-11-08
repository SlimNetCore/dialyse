package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Constantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConstantesDeSeance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Pagination;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees.ConstantesDeSeancePubliees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * ACL du Dossier médical vers le langage publié de la Séance (ADR 0098, SE7d ; dépendance directe, sans cycle Maven) : l'historique des
 * constantes d'un patient vient de {@link ConstantesDesSeancesPubliees}. Même déroulé que {@code HistoriqueDesConstantesJdbc}, qu'elle
 * remplace : le total d'abord, puis la page ; ordre et découpage inchangés (KB-SEA-79 : la création nulle se classe selon le SGBD).
 */
public class HistoriqueDesConstantesPublie implements HistoriqueDesConstantes {

    private final ConstantesDesSeancesPubliees seances;

    public HistoriqueDesConstantesPublie(ConstantesDesSeancesPubliees seances) {
        this.seances = seances;
    }

    @Override
    public PageDeConstantes dePatient(CentreId centre, PatientId patient, Pagination pagination) {
        long total = seances.nombreDeSeances(centre.valeur(), patient.valeur());
        List<ConstantesDeSeance> items = seances.page(centre.valeur(), patient.valeur(), pagination.taille(), pagination.rang()).stream()
                .map(HistoriqueDesConstantesPublie::traduire).toList();
        return new PageDeConstantes(items, total, pagination.page(), pagination.taille());
    }

    static ConstantesDeSeance traduire(ConstantesDeSeancePubliees c) {
        return new ConstantesDeSeance(new SeanceId(c.seance()), c.date(), new Constantes(c.poidsAvantKg(), c.poidsApresKg(), c.tensionAvant(),
                c.tensionApres(), c.debitSangMlMin(), c.ultrafiltrationMl(), c.dureeMinutes()));
    }
}
