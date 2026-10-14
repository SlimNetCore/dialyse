package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Jalon;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCoutId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Rectification;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutTache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Tache;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TacheId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mapping explicite agrégat <-> modèles de persistance de l'intervention, de ses lignes de coût et de ses rectifications. */
final class InterventionMapper {

    private InterventionMapper() {
    }

    static Intervention versDomaine(InterventionJpa e, List<LigneDeCoutJpa> lignes, List<RectificationJpa> rectifications,
                              List<TacheJpa> taches) {
        return Intervention.reconstituer(new InterventionId(e.id), new EquipementId(e.equipementId), new CentreId(e.centreId),
                TypeIntervention.valueOf(e.type), StatutIntervention.valueOf(e.statut), e.dateDebut, e.dateFin,
                e.intervenantId == null ? null : new IntervenantId(e.intervenantId), e.description, e.actions,
                e.pieceRemplacee, e.observations, statutEquipement(e.etatEquipementAvant),
                statutEquipement(e.etatEquipementApres),
                e.priorite == null ? PrioriteIntervention.NORMALE : PrioriteIntervention.valueOf(e.priorite), e.echeance,
                e.symptome, e.cause, new Jalon(utilisateur(e.creePar), e.dateCreation),
                jalon(e.modifiePar, e.dateModification), jalon(e.demarrePar, e.demarreLe), jalon(e.cloturePar, e.clotureLe),
                jalon(e.annulePar, e.annuleLe), lignes.stream().map(InterventionMapper::versDomaine).toList(),
                rectifications.stream().map(InterventionMapper::versDomaine).toList(),
                taches.stream().map(InterventionMapper::versDomaine).toList());
    }

    /** Recopie l'état de l'agrégat dans le modèle de persistance (neuf ou géré) ; une écriture rétablit une suppression logique. */
    static void recopier(Intervention i, InterventionJpa e) {
        e.equipementId = i.equipementId().valeur();
        e.centreId = i.centreId().valeur();
        e.type = i.type().name();
        e.statut = i.statut().name();
        e.dateDebut = i.debut();
        e.dateFin = i.fin();
        e.intervenantId = i.intervenantId() == null ? null : i.intervenantId().valeur();
        e.etatEquipementAvant = i.etatAvant() == null ? null : i.etatAvant().name();
        e.etatEquipementApres = i.etatApres() == null ? null : i.etatApres().name();
        e.priorite = i.priorite().name();
        e.echeance = i.echeance();
        e.symptome = i.symptome();
        e.cause = i.cause();
        e.demarrePar = par(i.demarrage());
        e.demarreLe = le(i.demarrage());
        e.cloturePar = par(i.cloture());
        e.clotureLe = le(i.cloture());
        e.annulePar = par(i.annulation());
        e.annuleLe = le(i.annulation());
        e.description = i.description();
        e.actions = i.actions();
        e.pieceRemplacee = i.pieceRemplacee();
        e.observations = i.observations();
        e.dateCreation = i.creation().le();
        e.creePar = par(i.creation());
        e.dateModification = le(i.modification());
        e.modifiePar = par(i.modification());
        e.deletedAt = null;
    }

    static void recopier(LigneDeCout l, UUID interventionId, LigneDeCoutJpa e) {
        e.interventionId = interventionId;
        e.type = l.type().name();
        e.libelle = l.libelle();
        e.quantite = l.quantite();
        e.prixUnitaire = l.prixUnitaire();
        e.articleStockId = l.articleStockId();
        e.automatique = l.estAutomatique();
    }

    static void recopier(Tache t, int rang, UUID interventionId, UUID centreId, TacheJpa e) {
        e.interventionId = interventionId;
        e.centreId = centreId;
        e.rang = rang;
        e.statut = t.statut().name();
        e.description = t.description();
        e.creeLe = t.creation().le();
        e.creePar = par(t.creation());
        e.demarreLe = le(t.demarrage());
        e.demarrePar = par(t.demarrage());
        e.acheveLe = le(t.achevement());
        e.achevePar = par(t.achevement());
    }

    static void recopier(Rectification r, UUID interventionId, RectificationJpa e) {
        e.interventionId = interventionId;
        e.motif = r.motif();
        e.par = r.par() == null ? null : r.par().valeur();
        e.le = r.le();
        e.clotureAnterieureLe = r.clotureAnterieureLe();
        e.clotureAnterieurePar = r.clotureAnterieurePar() == null ? null : r.clotureAnterieurePar().valeur();
    }

    private static LigneDeCout versDomaine(LigneDeCoutJpa e) {
        return LigneDeCout.reconstituer(new LigneDeCoutId(e.id), TypeLigneCout.valueOf(e.type), e.libelle, e.quantite,
                e.prixUnitaire, e.articleStockId, Boolean.TRUE.equals(e.automatique));
    }

    private static Tache versDomaine(TacheJpa e) {
        return Tache.reconstituer(new TacheId(e.id), e.description, StatutTache.valueOf(e.statut),
                new Jalon(utilisateur(e.creePar), e.creeLe), jalon(e.demarrePar, e.demarreLe), jalon(e.achevePar, e.acheveLe));
    }

    private static Rectification versDomaine(RectificationJpa e) {
        return new Rectification(e.id, e.motif, utilisateur(e.par), e.le, e.clotureAnterieureLe,
                utilisateur(e.clotureAnterieurePar));
    }

    private static StatutEquipement statutEquipement(String code) {
        return code == null ? null : StatutEquipement.valueOf(code);
    }

    private static UtilisateurId utilisateur(UUID id) {
        return id == null ? null : new UtilisateurId(id);
    }

    /** Un jalon n'existe que si l'une de ses deux colonnes est renseignée. */
    private static Jalon jalon(UUID par, Instant le) {
        return par == null && le == null ? null : new Jalon(utilisateur(par), le);
    }

    private static UUID par(Jalon jalon) {
        return jalon == null || jalon.par() == null ? null : jalon.par().valeur();
    }

    private static Instant le(Jalon jalon) {
        return jalon == null ? null : jalon.le();
    }
}
