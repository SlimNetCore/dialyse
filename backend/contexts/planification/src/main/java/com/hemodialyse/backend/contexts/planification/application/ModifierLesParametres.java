package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuCentre;

import java.util.Set;

/**
 * Remplace le paramétrage du planning du centre (RG-PLN-001 à 006). Les valeurs sont validées avant toute écriture ; puis, dans
 * une transaction sous le verrou du centre : les salles d'isolement doivent appartenir au centre (une salle inconnue et une salle
 * d'un autre centre ne se distinguent pas, KB-PLN-06 reproduit), puis le paramétrage et la marque d'isolement des salles sont
 * remplacés ensemble. Remplacement total (KB-PLN-05).
 */
public class ModifierLesParametres {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDuCentre verrou;
    private final ParametresDuPlanningRepository parametres;
    private final SallesDuCentre salles;

    public ModifierLesParametres(UniteDeTravail uniteDeTravail, VerrouDuCentre verrou,
                                 ParametresDuPlanningRepository parametres, SallesDuCentre salles) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.parametres = parametres;
        this.salles = salles;
    }

    /**
     * @param ratio            absent : valeur par défaut
     * @param patientsParPoste absent : valeur par défaut
     */
    public record Commande(Set<JourDeLaSemaine> joursOuverts, Set<SalleId> sallesIsolement, Integer ratio,
                           Integer patientsParPoste) {
    }

    public ParametresDuPlanning executer(CentreId centre, Commande commande) {
        ParametresDuPlanning nouveaux = ParametresDuPlanning.definir(commande.joursOuverts(), commande.sallesIsolement(),
                commande.ratio(), commande.patientsParPoste());
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            if (!salles.toutes(centre).containsAll(nouveaux.sallesIsolement())) {
                throw new SaisieInvalide("Une salle d'isolement n'appartient pas à ce centre");
            }
            parametres.enregistrer(centre, nouveaux);
            return nouveaux;
        });
    }
}
