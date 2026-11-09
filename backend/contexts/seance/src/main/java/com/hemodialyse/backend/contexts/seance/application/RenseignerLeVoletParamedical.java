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
 * facturée (422), mesures (422). Deux écritures simultanées se suivent (séance lue sous verrou de ligne ; verrou non obtenu : 409).
 * <p>
 * REPRODUIT (KB-SEA-93, à décider au plus tard le 2026-12-15) : le volet est ÉCRIT ET VALIDÉ dans sa propre unité de travail, PUIS la
 * fiche du patient est relue pour les diffusions ; une fiche hors du centre de la séance rend 400 « Patient introuvable » alors que le
 * volet est en base, et rien n'est diffusé. REPRODUIT (KB-SEA-94) : un volet de la même séance posé sous un autre centre est invisible
 * et fait échouer l'insertion (unicité par séance). Les deux diffusions du legacy sont émises dans le même ordre (volet enregistré, puis
 * saisie de l'infirmier de type {@code PARAMEDICAL}).
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
        VoletParamedical volet = uniteDeTravail.executer(() -> {
            trouvee[0] = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee[0].exigerUnVoletParamedical();
            MesuresDeSeance mesures = mesuresDemandees.get();
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
        // KB-SEA-93 (reproduit) : la fiche est relue APRÈS la validation de l'écriture ; absente du centre : 400, aucune diffusion.
        FicheDuPatient fiche = patients.fiche(centre, trouvee[0].patient()).orElseThrow(() -> new SaisieInvalide("Patient introuvable"));
        diffusions.voletParamedicalEnregistre(centre, trouvee[0], fiche);
        diffusions.saisieDeLInfirmier(centre, TYPE_DE_SAISIE, trouvee[0], fiche, session.utilisateur());
        return volet;
    }
}
