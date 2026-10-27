package com.hemodialyse.backend.contexts.planification.interfaces.http;

import com.hemodialyse.backend.contexts.planification.application.ProposerDesPlaces;
import com.hemodialyse.backend.contexts.planification.application.SessionCourante;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Proposition;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter entrant HTTP de l'aide au placement (slice P5) : places libres (salle, créneau, générateur, jours) et grille de
 * disponibilité du centre. Lecture seule, bornée au centre, jamais mise en cache. Mêmes URL et JSON que le legacy ; paramètres
 * illisibles : 400 au lieu de 500 (KB-PLN-56) ; {@code limite} hors 1-30 ramenée dans les bornes (KB-PLN-57 reproduit).
 */
@RestController
@RequestMapping("/api/v1/planning/affectations")
@PreAuthorize("hasAnyRole('ADMIN','SECRETAIRE','MEDECIN','INFIRMIER')")
public class PropositionsControleur {

    private static final int LIMITE_PAR_DEFAUT = 12;

    private final SessionCourante session;
    private final ProposerDesPlaces proposer;

    public PropositionsControleur(SessionCourante session, ProposerDesPlaces proposer) {
        this.session = session;
        this.proposer = proposer;
    }

    /**
     * @param seances   nombre de séances par semaine (défaut 3) ; ignoré si des jours sont imposés
     * @param jours     jours imposés (ex. {@code LUNDI,MERCREDI,VENDREDI}), facultatif
     * @param patientId patient dont on modifie la place (libérée dans le calcul), facultatif
     * @param isolement patient à risque infectieux : absent = déduit des sérologies du patient
     */
    @GetMapping
    public ResponseEntity<PropositionsReponse> proposer(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "3") int seances,
            @RequestParam(required = false) List<JourDeLaSemaine> jours,
            @RequestParam(required = false) UUID creneauId,
            @RequestParam(required = false) UUID salleId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Boolean isolement,
            @RequestParam(defaultValue = "" + LIMITE_PAR_DEFAUT) int limite) {
        CentreId centre = session.centre(centerId);
        Set<JourDeLaSemaine> joursImposes = jours == null || jours.isEmpty() ? Set.of() : EnumSet.copyOf(jours);
        ProposerDesPlaces.Reponse reponse = proposer.executer(new ProposerDesPlaces.Commande(centre,
                patientId == null ? null : new PatientId(patientId), seances, joursImposes,
                creneauId == null ? null : new CreneauId(creneauId), salleId == null ? null : new SalleId(salleId), limite,
                isolement));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PropositionsReponse.depuis(reponse));
    }

    public record SalleReponse(UUID id, String nom) {
    }

    public record CreneauReponse(UUID id, String libelle, int ordre) {
    }

    public record GenerateurReponse(UUID id, String code, UUID salleId) {
    }

    public record FermetureReponse(JourDeLaSemaine jour, LocalDate date, String motif) {
    }

    public record PropositionReponse(SalleReponse salle, CreneauReponse creneau, GenerateurReponse generateur,
                                     List<GenerateurReponse> generateursAlternatifs, List<JourDeLaSemaine> jours, int score,
                                     List<String> raisons, List<FermetureReponse> fermetures) {
    }

    public record CaseReponse(UUID salleId, UUID creneauId, JourDeLaSemaine jour, int capacite, int occupes) {
    }

    public record PropositionsReponse(List<SalleReponse> salles, List<CreneauReponse> creneaux,
                                      List<PropositionReponse> propositions, List<CaseReponse> grille, List<String> alertes,
                                      boolean patientARisque) {

        static PropositionsReponse depuis(ProposerDesPlaces.Reponse r) {
            return new PropositionsReponse(
                    r.ressources().salles().stream().map(s -> new SalleReponse(s.id().valeur(), s.nom())).toList(),
                    r.ressources().creneaux().stream().map(PropositionsReponse::creneau).toList(),
                    r.propositions().stream().map(PropositionsReponse::proposition).toList(),
                    r.grille().stream().map(c -> new CaseReponse(c.salle().valeur(), c.creneau().valeur(), c.jour(), c.capacite(),
                            c.occupes())).toList(),
                    r.alertes().stream().map(Enum::name).toList(), r.patientARisque());
        }

        private static CreneauReponse creneau(RessourcesDuCentre.Creneau c) {
            return new CreneauReponse(c.id().valeur(), c.libelle(), c.ordre());
        }

        private static GenerateurReponse generateur(RessourcesDuCentre.Generateur g) {
            return new GenerateurReponse(g.id().valeur(), g.code(), g.salle().valeur());
        }

        private static PropositionReponse proposition(Proposition p) {
            return new PropositionReponse(new SalleReponse(p.salle().id().valeur(), p.salle().nom()), creneau(p.creneau()),
                    generateur(p.generateur()), p.generateursAlternatifs().stream().map(PropositionsReponse::generateur).toList(),
                    p.jours(), p.score(), p.raisons().stream().map(Enum::name).toList(),
                    p.fermetures().stream().map(f -> new FermetureReponse(f.jour(), f.date(), f.motif())).toList());
        }
    }
}
