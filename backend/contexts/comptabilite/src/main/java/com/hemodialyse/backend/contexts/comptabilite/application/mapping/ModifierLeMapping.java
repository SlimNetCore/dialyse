package com.hemodialyse.backend.contexts.comptabilite.application.mapping;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.application.journal.JournauxDuCentreVus;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.ComptesDuStockSaisis;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.PlanDuCentre;

import java.util.EnumMap;
import java.util.Map;

/**
 * Redéfinit le mapping du centre : les cinq comptes obligatoires et la TVA collectée sont remplacés, les comptes du stock et les
 * journaux non précisés gardent leur valeur actuelle. Tout le mapping est revalidé, journaux d'abord (JOURNAL_INCONNU) puis
 * comptes du plan (COMPTE_INCONNU) ; un refus n'enregistre rien. Le mapping ne réimpute jamais le passé.
 */
public final class ModifierLeMapping {

    private final MappingComptableRepository mappings;
    private final MappingDuCentreVu vus;
    private final JournauxDuCentreVus journaux;
    private final PlanDuCentre plan;
    private final UniteDeTravail uniteDeTravail;

    public ModifierLeMapping(MappingComptableRepository mappings, MappingDuCentreVu vus, JournauxDuCentreVus journaux,
                             PlanDuCentre plan, UniteDeTravail uniteDeTravail) {
        this.mappings = mappings;
        this.vus = vus;
        this.journaux = journaux;
        this.plan = plan;
        this.uniteDeTravail = uniteDeTravail;
    }

    public MappingVue executer(CentreId centre, Commande commande) {
        Map<OperationComptable, CodeJournal> choix = new EnumMap<>(OperationComptable.class);
        if (commande.journaux() != null) {
            commande.journaux().forEach((operation, code) -> choix.put(OperationComptable.de(operation), CodeJournal.de(code)));
        }
        return uniteDeTravail.executer(() -> {
            MappingComptable nouveau = vus.effectif(centre).redefini(commande.compteVentes(), commande.compteClientPatient(),
                    commande.compteClientDefaut(), commande.compteBanque(), commande.compteCaisse(),
                    commande.compteTvaCollectee(),
                    new ComptesDuStockSaisis(commande.compteStock(), commande.compteConsommation(),
                            commande.compteFacturesNonParvenues(), commande.compteBoniInventaire(),
                            commande.compteMaliInventaire()),
                    choix);
            nouveau.exigerJournauxActifs(journaux.effectifs(centre));
            nouveau.exigerComptesDuPlan(plan);
            mappings.enregistrer(nouveau);
            return MappingVue.depuis(nouveau);
        });
    }

    /**
     * @param journaux journal choisi par nom d'opération ; une opération absente garde son journal actuel
     */
    public record Commande(String compteVentes, String compteClientPatient, String compteClientDefaut,
                           String compteBanque,
                           String compteCaisse, String compteTvaCollectee, String compteStock,
                           String compteConsommation,
                           String compteFacturesNonParvenues, String compteBoniInventaire, String compteMaliInventaire,
                           Map<String, String> journaux) {
    }
}
