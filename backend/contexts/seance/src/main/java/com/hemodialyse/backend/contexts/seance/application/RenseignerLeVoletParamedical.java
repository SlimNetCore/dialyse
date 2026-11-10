package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.MesuresDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.VoletParamedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Saisie du volet paramédical d'une séance (PUT /api/v1/seances/{id}/volet-paramedical, administrateur, infirmier ou secrétaire ; SE7f,
 * ADR 0100, parité DM1). REMPLACE les dix mesures en entier. Ordre des refus du legacy : centre (403), séance introuvable (400), séance
 * facturée (422), mesures (422), fiche du patient absente du centre (400). Deux écritures simultanées se suivent (séance lue sous verrou
 * de ligne ; verrou non obtenu : 409).
 * <p>
 * CORRIGÉ (L4, 2026-11-10, ADR 0116, à confirmer par les métiers avant le 2026-12-15) : KB-SEA-93, la fiche du patient est gardée dans
 * l'unité de travail AVANT l'écriture du volet (refus 400 « Patient introuvable » : rien d'écrit, rien de diffusé ; avant, le volet était
 * écrit puis la réponse échouait) ; KB-SEA-94, le volet de la séance est cherché par la séance (le centre de la séance, déjà gardé, fait
 * foi) : un volet posé sous un autre centre est remplacé sans changer son centre (aucune réécriture de donnée). Les deux diffusions du
 * legacy sont émises dans le même ordre (volet enregistré, puis saisie de l'infirmier de type {@code PARAMEDICAL}).
 */
public class RenseignerLeVoletParamedical {

    public static final String TYPE_DE_SAISIE = "PARAMEDICAL";

    private final SessionCourante session;
    private final Seances seances;
    private final VoletsParamedicaux volets;
    private final PatientsDeLaSeance patients;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final DiffusionsDeSeance diffusions;

    public RenseignerLeVoletParamedical(SessionCourante session, Seances seances, VoletsParamedicaux volets, PatientsDeLaSeance patients,
                                        UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge,
                                        DiffusionsDeSeance diffusions) {
        this.session = session;
        this.seances = seances;
        this.volets = volets;
        this.patients = patients;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.diffusions = diffusions;
    }

    /** @param mesuresDemandees lit et valide les mesures du corps, APRÈS les contrôles du centre, de la séance et de son statut */
    public VoletParamedical executer(UUID centreDemande, UUID seance, Supplier<MesuresDeSeance> mesuresDemandees) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400
        SeanceId id = new SeanceId(seance);
        Seance[] trouvee = new Seance[1];
        FicheDuPatient[] fiche = new FicheDuPatient[1];
        VoletParamedical volet = uniteDeTravail.executer(() -> {
            trouvee[0] = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee[0].exigerUnVoletParamedical();
            MesuresDeSeance mesures = mesuresDemandees.get();
            // KB-SEA-93 (corrigé, L4) : la fiche est gardée AVANT toute écriture ; absente du centre : 400, rien d'écrit
            fiche[0] = patients.fiche(centre, trouvee[0].patient()).orElseThrow(() -> new SaisieInvalide("Patient introuvable"));
            var maintenant = horloge.maintenant();
            VoletParamedical existant = volets.deLaSeance(centre, id).orElse(null);
            if (existant == null) {
                existant = VoletParamedical.ouvrir(identifiants.suivant(), centre, id, mesures, maintenant);
            } else {
                existant.remplacerLesMesures(mesures, maintenant);
            }
            volets.enregistrer(existant);
            return existant;
        });
        diffusions.voletParamedicalEnregistre(centre, trouvee[0], fiche[0]);
        diffusions.saisieDeLInfirmier(centre, TYPE_DE_SAISIE, trouvee[0], fiche[0], session.utilisateur());
        return volet;
    }
}
