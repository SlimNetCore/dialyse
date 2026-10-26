package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

/**
 * Preuve qu'un infirmier peut remplacer sur une case : on n'en obtient une que par {@link #prouver}. Le constructeur n'est pas
 * public, donc un {@link Remplacement} ne s'affecte jamais à un remplaçant dont l'éligibilité n'a pas été jugée.
 */
public final class RemplacantEligible {
    private final InfirmierId infirmier;

    private RemplacantEligible(InfirmierId infirmier) {
        this.infirmier = infirmier;
    }

    /**
     * Situation d'un candidat remplaçant sur la case voulue, constatée par le cas d'usage sous le verrou de sa fiche.
     *
     * @param actif                 la fiche existe dans le centre et est active
     * @param habiliteIsolement     la fiche est habilitée à l'isolement
     * @param salleDIsolement       la salle de la case est une salle d'isolement
     * @param absentCeJour          une absence couvre le jour
     * @param dejaPrevuSurLeCreneau prévu ce jour-là sur ce créneau, dans n'importe quelle salle (roulement ou autre remplacement)
     */
    public record Situation(InfirmierId infirmier, boolean actif, boolean habiliteIsolement, boolean salleDIsolement,
                            boolean absentCeJour, boolean dejaPrevuSurLeCreneau) {
    }

    /**
     * Éligible : actif, présent ce jour-là, libre sur ce créneau et, en salle d'isolement, habilité (RG-INF-052).
     */
    public static RemplacantEligible prouver(Situation situation) {
        if (!situation.actif() || situation.absentCeJour() || situation.dejaPrevuSurLeCreneau()
                || (situation.salleDIsolement() && !situation.habiliteIsolement())) {
            throw nonEligible();
        }
        return new RemplacantEligible(situation.infirmier());
    }

    /**
     * L'infirmier est inconnu du centre : inéligible, comme avant.
     */
    public static RegleDePlanificationViolee nonEligible() {
        return new RegleDePlanificationViolee("REMPLACEMENT_INELIGIBLE",
                "Cet infirmier ne peut pas être affecté à cette case (absent, déjà prévu ou non habilité)");
    }

    public InfirmierId infirmier() {
        return infirmier;
    }
}
