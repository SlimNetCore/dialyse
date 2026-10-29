package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;

import java.time.Instant;

/**
 * Exécution d'optimisation (racine d'agrégat, P20 à P23) : suit un calcul de planning de son lancement à son éventuelle application,
 * avec la preuve (l'{@link Empreinte}) que le centre n'a pas changé entre-temps. Cycle de vie : {@code EN_COURS}, puis
 * {@code TERMINEE} (y compris arrêtée avant son terme : la meilleure solution est conservée) ou {@code ECHEC} ; une exécution
 * terminée avec un résultat s'applique une seule fois. Immuable : chaque intention rend l'exécution suivante.
 * <p>
 * « Une seule en cours par centre » est un ensemble : il se juge sous le verrou du centre, par le cas d'usage, avec
 * {@link #exigerAucuneEnCours}. Rejets : {@code OPTIMISATION_DEJA_EN_COURS} (409), {@code OPTIMISATION_NON_APPLICABLE},
 * {@code OPTIMISATION_DEJA_APPLIQUEE} (409), {@code OPTIMISATION_PERIMEE} (409), {@code OPTIMISATION_SUPPRESSION_EN_COURS}.
 */
public record ExecutionDOptimisation(
        ExecutionId id,
        CentreId centre,
        Statut statut,
        ParametresDOptimisation parametres,
        Instant creeLe,
        Instant termineLe,
        String lancePar,
        String phase,
        String score,
        Empreinte empreinte,
        ResultatDOptimisation.Resume resume,
        ResultatDOptimisation resultat,
        String erreur,
        Instant appliqueeLe
) {

    public enum Statut {
        EN_COURS,
        TERMINEE,
        ECHEC
    }

    public ExecutionDOptimisation {
        if (id == null) throw new SaisieInvalide("Identifiant requis");
        if (centre == null) throw new SaisieInvalide("Centre requis");
        if (statut == null) throw new SaisieInvalide("Statut requis");
        if (parametres == null) throw new SaisieInvalide("Paramètres requis");
    }

    /** P20 : le centre ne peut pas avoir deux calculs en même temps. */
    public static void exigerAucuneEnCours(boolean uneEstEnCours) {
        if (uneEstEnCours) {
            throw new RegleDePlanificationViolee("OPTIMISATION_DEJA_EN_COURS", "Une optimisation est déjà en cours pour ce centre");
        }
    }

    public static ExecutionDOptimisation lancer(ExecutionId id, CentreId centre, ParametresDOptimisation parametres, String lancePar,
                                                Empreinte empreinte, Instant maintenant) {
        return new ExecutionDOptimisation(id, centre, Statut.EN_COURS, parametres, maintenant, null, lancePar, null, null, empreinte,
                null, null, null, null);
    }

    public boolean enCours() {
        return statut == Statut.EN_COURS;
    }

    /**
     * Les transitions du calcul ne partent que de {@code EN_COURS} : une exécution terminée, en échec ou interrompue ne change plus
     * (aucune sortie d'un état final, B1 de la revue P8).
     */
    private void exigerEnCours(String intention) {
        if (statut != Statut.EN_COURS) {
            throw new RegleDePlanificationViolee("OPTIMISATION_TRANSITION_INTERDITE",
                    "Impossible de " + intention + " une optimisation qui n'est plus en cours (" + statut + ")");
        }
    }

    /** L'avancement du calcul (sans autre effet). */
    public ExecutionDOptimisation progresser(String phase, String score) {
        exigerEnCours("faire progresser");
        return new ExecutionDOptimisation(id, centre, statut, parametres, creeLe, termineLe, lancePar, phase, score, empreinte,
                resume, resultat, erreur, appliqueeLe);
    }

    public ExecutionDOptimisation terminer(ResultatDOptimisation resultat, String score, Instant maintenant) {
        exigerEnCours("terminer");
        return new ExecutionDOptimisation(id, centre, Statut.TERMINEE, parametres, creeLe, maintenant, lancePar, phase, score,
                empreinte, resultat.resume(), resultat, null, appliqueeLe);
    }

    public ExecutionDOptimisation echouer(String erreur, Instant maintenant) {
        exigerEnCours("faire échouer");
        return new ExecutionDOptimisation(id, centre, Statut.ECHEC, parametres, creeLe, maintenant, lancePar, phase, score, empreinte,
                null, null, erreur, appliqueeLe);
    }

    /** P22 : seule une exécution terminée avec un résultat, pas encore appliquée, est applicable. */
    public void exigerApplicable() {
        if (statut != Statut.TERMINEE || resultat == null) {
            throw new RegleDePlanificationViolee("OPTIMISATION_NON_APPLICABLE",
                    "Seule une optimisation terminée avec un résultat peut être appliquée");
        }
        if (appliqueeLe != null) {
            throw new RegleDePlanificationViolee("OPTIMISATION_DEJA_APPLIQUEE", "Cette optimisation a déjà été appliquée");
        }
    }

    /**
     * Marque la proposition comme appliquée, si le centre est resté tel qu'il était au calcul.
     *
     * @param empreinteActuelle l'empreinte de l'état actuel du centre, lue sous verrou
     * @throws RegleDePlanificationViolee {@code OPTIMISATION_NON_APPLICABLE}, {@code OPTIMISATION_DEJA_APPLIQUEE} ou
     *                                    {@code OPTIMISATION_PERIMEE}
     */
    public ExecutionDOptimisation appliquer(Empreinte empreinteActuelle, Instant maintenant) {
        exigerApplicable();
        if (!empreinte.equals(empreinteActuelle)) {
            throw new RegleDePlanificationViolee("OPTIMISATION_PERIMEE",
                    "Le centre a changé depuis le calcul de cette proposition : relancez l'optimisation");
        }
        return new ExecutionDOptimisation(id, centre, statut, parametres, creeLe, termineLe, lancePar, phase, score, empreinte, resume,
                resultat, erreur, maintenant);
    }

    /** Un calcul en cours doit d'abord être arrêté. */
    public void exigerSupprimable() {
        if (enCours()) {
            throw new RegleDePlanificationViolee("OPTIMISATION_SUPPRESSION_EN_COURS",
                    "Un calcul en cours ne peut pas être supprimé : arrêtez-le d'abord");
        }
    }
}
