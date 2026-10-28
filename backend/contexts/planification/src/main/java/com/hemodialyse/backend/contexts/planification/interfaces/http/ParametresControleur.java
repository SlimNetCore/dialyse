package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ConsulterLesParametres;
import com.hemodialyse.backend.contexts.planification.application.ModifierLesParametres;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter entrant HTTP du paramétrage du planning du centre (slice P1) : mêmes URLs et même JSON que le legacy. Lecture pour
 * l'administrateur, le secrétariat, le médecin et l'infirmier (le filtre de périmètre de la plateforme refuse le médecin seul et
 * l'infirmier seul : KB-PLN-03, règle de gestion corrigée) ; écriture réservée à l'administrateur.
 * <p>
 * Écarts décidés (26/10/2026) : entrées illisibles 400 au lieu de 500 (KB-PLN-01), écriture concurrente 409 (KB-PLN-10).
 */
@RestController
@RequestMapping("/api/v1/planning/parametres")
public class ParametresControleur {

    private final SessionCourante session;
    private final ConsulterLesParametres consulter;
    private final ModifierLesParametres modifier;

    public ParametresControleur(SessionCourante session, ConsulterLesParametres consulter, ModifierLesParametres modifier) {
        this.session = session;
        this.consulter = consulter;
        this.modifier = modifier;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
    public ResponseEntity<ParametresReponse> lire(@RequestParam(required = false) UUID centerId) {
        CentreId centre = session.centre(centerId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ParametresReponse.depuis(consulter.executer(centre)));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametresReponse> enregistrer(@RequestParam(required = false) UUID centerId,
                                                         @RequestBody ParametresDemande demande) {
        CentreId centre = session.centre(centerId);
        Set<JourDeLaSemaine> jours = new HashSet<>();
        if (demande.joursOuverts() != null) {
            for (JourDeLaSemaine jour : demande.joursOuverts()) {
                if (jour == null) throw new SaisieInvalide("Jour d'ouverture illisible");
                jours.add(jour);
            }
        }
        Set<SalleId> salles = new HashSet<>();
        if (demande.sallesIsolement() != null) {
            for (UUID salle : demande.sallesIsolement()) salles.add(new SalleId(salle));
        }
        ParametresDuPlanning enregistres = modifier.executer(centre, new ModifierLesParametres.Commande(jours, salles,
                demande.patientsParInfirmier(), demande.patientsParPosteEtSerie()));
        return ResponseEntity.ok(ParametresReponse.depuis(enregistres));
    }

    /**
     * @param patientsParInfirmier    ratio de sécurité ; absent = valeur par défaut
     * @param patientsParPosteEtSerie patients suivis par poste et par série (capacité théorique) ; absent = défaut
     */
    public record ParametresDemande(List<JourDeLaSemaine> joursOuverts, List<UUID> sallesIsolement,
                                    Integer patientsParInfirmier, Integer patientsParPosteEtSerie) {
    }

    public record ParametresReponse(List<JourDeLaSemaine> joursOuverts, List<UUID> sallesIsolement,
                                    int patientsParInfirmier, int patientsParPosteEtSerie) {
        static ParametresReponse depuis(ParametresDuPlanning p) {
            return new ParametresReponse(EnumSet.copyOf(p.joursOuverts()).stream().toList(),
                    p.sallesIsolement().stream().map(SalleId::valeur).sorted(Comparator.naturalOrder()).toList(),
                    p.ratio().patientsParInfirmier(), p.patientsParPoste().nombre());
        }
    }
}
