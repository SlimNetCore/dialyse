package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.AuteurDuForfaitImpose;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Imposition d'un forfait du référentiel à une séance (PUT /api/v1/seances/{id}/forfait, administrateur seul, KB-SEA-48 reproduit) : la
 * séance est lue sous verrou de ligne (deux impositions simultanées se suivent, 409 si le verrou n'est pas obtenu), le forfait est copié
 * tel quel (KB-SEA-41, aucun montant recalculé), sans diffusion. Ordre des refus du legacy : centre (403), séance introuvable (400), forfait
 * introuvable pour le centre (400), séance facturée (422). L'auteur est celui du corps (KB-SEA-42, reproduit) ; au-delà de 100
 * caractères : 400 (KB-SEA-44, corrigé). Le forfait courant rendu est relu après l'unité de travail, comme le legacy.
 */
public class ImposerUnForfait {

    private final SessionCourante session;
    private final Seances seances;
    private final ForfaitsDuCentre referentiel;
    private final ForfaitsDeSeance forfaits;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public ImposerUnForfait(SessionCourante session, Seances seances, ForfaitsDuCentre referentiel, ForfaitsDeSeance forfaits,
                            UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.seances = seances;
        this.referentiel = referentiel;
        this.forfaits = forfaits;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Resultat executer(UUID centreDemande, UUID seance, UUID forfait, String auteur) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        SeanceId id = new SeanceId(seance);
        ForfaitId forfaitDemande = new ForfaitId(forfait);
        AuteurDuForfaitImpose imposeur = new AuteurDuForfaitImpose(auteur);
        Seance imposee = uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            var choisi = referentiel.trouver(centre, forfaitDemande)
                    .orElseThrow(() -> new SaisieInvalide("Forfait introuvable pour le centre actif"));
            trouvee.imposerUnForfait(choisi, horloge.maintenant(), imposeur);
            seances.enregistrerLeForfaitImpose(trouvee);
            return trouvee;
        });
        return new Resultat(imposee, forfaits.courant(centre, imposee.id(), imposee.patient(), imposee.jour()));
    }

    /** La séance et son forfait courant (celui qui vient d'être imposé, relu). */
    public record Resultat(Seance seance, Optional<ForfaitsDeSeance.ForfaitCourant> forfait) {
    }
}
