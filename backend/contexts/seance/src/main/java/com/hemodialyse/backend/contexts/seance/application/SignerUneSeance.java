package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.Signataire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Signature de la séance par le médecin (POST /api/v1/seances/{id}/signer-medecin, administrateur et médecin) : sous verrou de la ligne
 * (KB-SEA-18, deux signatures simultanées ne réussissent plus toutes les deux), indifférente au jour de la séance, sans diffusion. Le
 * signataire est celui du corps de la requête (KB-SEA-11, reproduit : risque d'imputation de signature médicale, à faire confirmer par le
 * juridique et le DPO avant la mise en production), borné à 100 caractères.
 */
public class SignerUneSeance {

    private final SessionCourante session;
    private final Seances seances;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public SignerUneSeance(SessionCourante session, Seances seances, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.seances = seances;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Seance executer(UUID centreDemande, UUID seance, String signataire) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        SeanceId id = new SeanceId(seance);
        Signataire auteur = Signataire.lire(signataire);
        return uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.signerParLeMedecin(horloge.maintenant(), auteur);
            seances.enregistrer(trouvee);
            return trouvee;
        });
    }
}
