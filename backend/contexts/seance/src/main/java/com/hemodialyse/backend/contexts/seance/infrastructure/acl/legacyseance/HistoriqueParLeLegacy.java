package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Pagination;
import com.hemodialyse.backend.contexts.seance.domain.RechercheDeSeances;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ACL de l'historique : traduit la recherche et les lignes du langage publié {@link HistoriqueFourni}. Une ligne de statut ou de motif
 * illisible est ignorée et journalisée (KB-SEA-07, corrigé) ; la plateforme les filtre déjà, ceci est la garde du contexte.
 */
public class HistoriqueParLeLegacy implements HistoriqueDesSeances {

    private static final Logger JOURNAL = LoggerFactory.getLogger(HistoriqueParLeLegacy.class);

    private final HistoriqueFourni historique;

    public HistoriqueParLeLegacy(HistoriqueFourni historique) {
        this.historique = historique;
    }

    @Override
    public PagedResult<LigneDeSeance> rechercher(CentreId centre, RechercheDeSeances r, Pagination pagination) {
        Set<String> statuts = r.statuts().stream().map(StatutDeSeance::name).collect(Collectors.toSet());
        var page = historique.rechercher(centre.valeur(), r.du(), r.au(), statuts, r.texte(), r.ordre().name(), r.decroissant(),
                r.deverrouillee(), pagination.page(), pagination.taille());
        List<LigneDeSeance> lignes = new ArrayList<>();
        for (var l : page.lignes()) {
            try {
                lignes.add(traduire(l));
            } catch (IllegalArgumentException | NullPointerException e) {
                JOURNAL.warn("Séance {} ignorée de l'historique : statut ou motif illisible", l.id());
            }
        }
        return PagedResult.of(lignes, page.total(), pagination.page(), pagination.taille());
    }

    static LigneDeSeance traduire(HistoriqueFourni.Ligne l) {
        return new LigneDeSeance(new SeanceId(l.id()), new CentreId(l.centre()), new PatientId(l.patient()), l.codePatient(),
                l.nomDuPatient(), l.prenomDuPatient(), l.jour(), StatutDeSeance.valueOf(l.statut()), l.creeLe(), l.valideeLe(),
                l.signeeParLInfirmierLe(), l.signeeParLeMedecinLe(), l.deverrouilleeLe(), l.horsPlanning(),
                l.motifHorsPlanning() == null ? null : MotifHorsPlanning.valueOf(l.motifHorsPlanning()));
    }
}
