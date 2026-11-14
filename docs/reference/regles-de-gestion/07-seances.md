# 07 — Séances de dialyse (cahier de dialyse)

> Préfixe `RG-SEA`. Sources : `Seance`, `SeanceStatus`, `SeanceDomainService`, `SeanceApplicationService`,
> `SeanceBillingEligibilityJdbcAdapter`, `VoletParamedicalDomainService`, `RenseignerLeVoletMedical`,
> `TensionArterielle`,
> `SeancesControleur`, `RoutesResiduellesControleur`, `TableauDeBordDesSeancesControleur`, `VoletParamedicalRestController`, `BonSortieService`.

## 7.1 Cycle de vie

- **RG-SEA-001** — Statuts d'une séance : `CREE` → `VALIDEE` (validation infirmière) → `SIGNEE` (signature médecin) →
  `FACTUREE`
  (facturation). Aucun retour en arrière. Statut particulier `ABSENT` : séance exclue de la facturation depuis la
  simulation (RG-FAC-023) ; elle n'est plus validable ni facturable, ne compte pas comme réalisée et n'accepte pas de
  volet médical.
- **RG-SEA-002** — Une séance **facturée** ne peut plus être modifiée : date, forfait, volets paramédical et médical,
  consommables (« La seance facturee ne peut plus etre modifiee »). L'entrée de stock liée à une séance facturée est
  également immuable. Tous ces refus répondent 422 `SEANCE_FACTUREE_NON_MODIFIABLE` (date, forfait, validation, signature
  du médecin, consommables, volets, déverrouillage, administration de traitement, bon de sortie) ; la suppression garde
  `SEANCE_FACTUREE_NON_SUPPRIMABLE` (RG-SEA-050).
- **RG-SEA-003** — Une séance appartient à un patient et à un centre ; une séance inconnue du centre : « Seance
  introuvable ».
- **RG-SEA-050** — **Suppression d'une séance** (`ADMIN` seul, écran « Historique des séances » ;
  `DELETE /api/v1/seances/{id}`), tout ou rien : la séance doit exister dans le centre (`SEANCE_INTROUVABLE`) et ne
  pas être facturée (`SEANCE_FACTUREE_NON_SUPPRIMABLE` : annuler d'abord la facture) ; un **motif** de 5 à 500
  caractères est exigé (`SEANCE_SUPPRESSION_MOTIF_INVALIDE`). Une séance créée, validée ou signée peut être supprimée.
  Effets : les consommables et administrations EPO/fer sortis du stock pour la séance sont **restitués** (lots
  recrédités, mouvements et bons de sortie supprimés, PMP recalculé) ; les volets paramédical et médical et les
  administrations EPO/fer de la séance sont effacés ; la séance est supprimée. La suppression est **journalisée**
  (séance, patient, date, statut, motif, auteur, horodatage — table `seance_suppression`) en plus du journal d'audit
  (RG-SEC-050), et notifiée en temps réel (`SEANCE_SUPPRIMEE`) pour rafraîchir les écrans. Irréversible. Reprise dans le module
  `seance` : RG-SEA-057 (qui **refuse aussi** toute séance rattachée à une facture, et **ne restitue aucun stock** : lire « restitués »
  ci-dessus à la lumière de KB-SEA-51).

## 7.2 Création

- **RG-SEA-010** — Création : `ADMIN` ou `INFIRMIER`. Le patient doit exister dans le centre. La date par défaut est
  aujourd'hui.
- **RG-SEA-011** — **Le patient doit être facturable à la date de la séance** : une prise en charge au statut `VALIDEE`,
  avec un forfait (effectif sinon demandé), dont la période couvre la date, **et** une attestation de droits couvrant
  la date. La période est celle de la **période effective** (début et fin pris ensemble : sans fin, elle reste ouverte)
  ou, à défaut, celle de la **période demandée** — jamais un mélange des bornes, comme pour la résolution du forfait de
  la séance. Sans cela, la séance ne se crée pas, et le message dit **ce qui manque réellement** : « Le patient doit
  avoir une prise en charge valide pour être facturé » quand aucune prise en charge ne couvre la date, « La prise en
  charge du patient couvre le … mais n'est pas validée » quand elle existe mais n'est pas validée, ou « La prise en
  charge du patient est valide mais aucune attestation de droits ne couvre le … » quand seule l'attestation est
  absente ou expirée.
- **RG-SEA-012** — Création par **scan de QR** (`ADMIN`, `INFIRMIER`, `SECRETAIRE`) : le code est résolu, dans cet
  ordre, comme identifiant patient (UUID,
  éventuellement après un préfixe `xxx:`), code patient, numéro d'assurance, puis par comparaison tolérante (sans
  espace, tiret ni ponctuation) sur code
  patient, numéro d'assurance du patient ou de l'assuré ; QR vide ou introuvable : refus explicite. Si le patient a déjà
  une séance ce jour-là, **elle est
  renvoyée** (pas de doublon). **Scan par un infirmier (`INFIRMIER`, `ADMIN`) : la séance est validée directement** —
  si une séance `CREE` existe ce jour-là (par exemple créée par la secrétaire) elle passe à `VALIDEE`, sinon elle est
  créée directement à l'état `VALIDEE` (RG-SEA-011 reste exigée ; le patient doit en outre être programmé ce jour-là,
  voir RG-SEA-048), sans passer par « Modifier → Enregistrer » ; la
  signature de l'infirmier est celle de l'utilisateur qui scanne. Un second scan d'une séance déjà `VALIDEE`, `SIGNEE`
  ou `FACTUREE` ne change rien (réponse `alreadyValidated`). Le scan de la **secrétaire** crée seulement la séance
  (`CREE`) : elle ne peut pas valider (RG-SEA-020). La réponse indique `created`, `validatedNow`, `alreadyValidated` et
  l'identité du patient.
- **RG-SEA-013** — La création notifie le centre en temps réel (séance créée) ; la réponse indique le générateur affecté
  au patient et son état (statut GMAO) pour
  avertir l'équipe d'un générateur indisponible.
- **RG-SEA-014** — Modification de la date d'une séance : `ADMIN` seul, interdite si facturée ; sans date, aujourd'hui.
- **RG-SEA-052** — **Création, scan et consultation reprises dans le module `seance`** (slice SE1, ADR 0088 ;
  `POST /api/v1/seances`, `POST /api/v1/seances/scan`, `GET /api/v1/seances/{id}`, `GET /api/v1/seances`, mêmes URLs, mêmes
  rôles que RG-SEA-010, 012 et 029, mêmes tables). Décisions de la reprise : **corrigés** — (KB-SEA-01) « aujourd'hui »
  est le **jour du centre** (fuseau du centre, `Africa/Algiers` par défaut), pour la création sans date et pour le scan seulement (la liste des séances récentes d'un patient du poste infirmier et la modification de date lisent encore la date du
  serveur : à corriger avec SE4 et SE7 ; la validation et le déverrouillage lisent le jour du centre depuis SE2, RG-SEA-053) ;
  (KB-SEA-02) plusieurs séances ce jour-là pour le patient lors d'un scan : 409 `SEANCE_DU_JOUR_AMBIGUE` au lieu d'un
  500 ; (KB-SEA-04) une requête mal formée répond 400 et non 500 (`BAD_REQUEST`, `VALIDATION_ERROR`, `centerId` absent du détail
  et de la liste, précision de motif
  au-delà de 255 caractères), un refus de droits 403 `ACCESS_DENIED` passe avant le 400, une écriture concurrente répond
  409 `ECRITURE_CONCURRENTE` ; une création sans centre répond 400 « Centre requis » ; (KB-SEA-06) un QR sans caractère
  alphanumérique est refusé en 400 ; un patient inconnu ou d'un autre centre répond 404 `PATIENT_INTROUVABLE` ; (KB-SEA-07)
  une séance au statut illisible n'empêche plus la liste (ligne ignorée, avertissement journalisé avec l'identifiant) et
  son détail est refusé sans exposer de nom de classe. **Reproduits** (à décider avec les métiers) — (KB-SEA-03) la
  création manuelle ne contrôle pas le planning (RG-SEA-048 ne vise que le scan) ; (KB-SEA-05) le drapeau `created` du
  scan de la secrétaire reste faux et la diffusion est refaite à chaque scan tant que la séance est `CREE` ; (KB-SEA-08)
  l'audit ne couvre que la création et le scan, sans identifiant d'entité ; (KB-SEA-09) l'auteur de la création n'est pas
  conservé ; (KB-SEA-10) l'éligibilité de prise en charge reste cumulative (RG-SEA-011, code générique
  `BUSINESS_RULE_VIOLATION`). Plusieurs séances par patient et par jour restent créables à la main (unicité à décider).
  **Diffusions** : un échec de diffusion en temps réel après l'enregistrement est journalisé et n'annule ni ne fait échouer la
  séance (le legacy répondait 500 après l'écriture) ; à confirmer avec les métiers.
  *Source :* `SeancesControleur`, `ErreursDeSeanceAdvice`, `HorlogeDuCentre`.

## 7.3 Validation infirmière et consommables

- **RG-SEA-053** — **Validation, signature du médecin et déverrouillage de régularisation reprises dans le module `seance`**
  (slice SE2, ADR 0089 ; `POST /api/v1/seances/{id}/valider` `ADMIN`, `INFIRMIER` ; `POST /api/v1/seances/{id}/signer-medecin`
  `ADMIN`, `MEDECIN` ; `POST /api/v1/seances/{id}/deverrouiller-regularisation?centerId=` `ADMIN` ; mêmes URLs, mêmes rôles, mêmes
  tables, mêmes matrices de statuts et mêmes messages que RG-SEA-020, 021, 046 et 049). Le scan de l'infirmier (RG-SEA-012) valide
  par la même règle. **Corrigés** — (KB-SEA-18) la séance est lue sous verrou avant d'être modifiée : un verrou non obtenu répond
  409 `ECRITURE_CONCURRENTE` ; (KB-SEA-16) un corps illisible, absent, nul ou en tableau, un `centerId` ou un identifiant de séance
  absent ou illisible, une ligne de consommable nulle répondent 400 `BAD_REQUEST` sans message technique (le refus de droits 403 passe
  avant le 400) ; un signataire de plus de 100 caractères répond 400 (et non 500) sans rien écrire ; une séance inconnue ou d'un autre
  centre répond 400 « Seance introuvable », un patient disparu 404 `PATIENT_INTROUVABLE`, un article inconnu ou inactif 400, un statut
  illisible en base 400 sans nom de classe interne ; le « jour passé » du verrou de validation (RG-SEA-046) et du déverrouillage est le
  **jour du centre** et non plus celui du serveur (KB-SEA-01, 13e écart décidé de SE2 à confirmer avec les métiers : entre minuit du
  centre et minuit du serveur, une séance d'« hier » au sens du centre est verrouillée pour l'infirmier). **Reproduits, à confirmer avant la mise en production** — (KB-SEA-11) le signataire est lu dans le corps
  de la requête et non dans la session : un compte peut valider ou signer au nom d'un autre, **risque d'imputation d'une signature
  médicale (décision juridique et DPO)** ; (KB-SEA-12) une séance à venir se valide sans limite ; (KB-SEA-13) la fenêtre de 7 jours de
  RG-SEA-046 ne borne pas le déverrouillage (elle ne concerne que le rappel) ; (KB-SEA-15) les consommables relevés se **cumulent** à
  chaque validation répétée. **Reproduits sans alerte** — (KB-SEA-14) rediffusion à chaque appel ; (KB-SEA-17) l'audit des trois routes
  porte l'action `SEANCES_CREATION` avec l'identifiant de la séance ; (KB-SEA-19) aucun cache à invalider ; (KB-SEA-20) un seul message
  pour toute séance non signable ; (KB-SEA-21) les droits (le `SUPERADMIN` reste refusé en 403 `ROLE_SCOPE`) et la place figée est la place
  courante de la fiche du patient, le déplacement temporaire du jour prioritaire. Un refus du Stock à la validation (stock insuffisant,
  article inactif) annule toute la validation (statut, place, consommations).
  *Source :* `ValiderUneSeance`, `SignerUneSeance`, `DeverrouillerUneSeance`, `Seance`, `SeancesControleur`, `ErreursDeSeanceAdvice`.

- **RG-SEA-054** — **Consommables déclarés d'une séance et raccourcis de consommables du centre repris dans le module `seance`**
  (slice SE3, ADR 0090 ; `POST /api/v1/seances/{id}/consommables`, `PUT /api/v1/seances/{id}/consommables/{articleId}`,
  `DELETE /api/v1/seances/{id}/consommables/{articleId}` `ADMIN`, `INFIRMIER` ; `GET /api/v1/seances/raccourcis-consommables`
  `ADMIN`, `INFIRMIER`, `SECRETAIRE` ; `PUT /api/v1/seances/raccourcis-consommables` `ADMIN` ; mêmes URLs, mêmes rôles, mêmes tables,
  même cache `seance.raccourcis`, mêmes messages que RG-SEA-022, 025 et 027). La Séance **déclare**, le Stock impute (langage publié
  `ConsommationsDeSeance`). **Corrigés, à confirmer avec les métiers** — (KB-SEA-26) un corps illisible, une quantité ou un identifiant
  illisible répondent 400 `BAD_REQUEST` au lieu de 500, le refus de droits 403 passant avant le 400 ; (KB-SEA-27) la lecture du patient, la
  vérification du statut et l'écriture se font dans une seule unité de travail : un patient disparu répond 404 `PATIENT_INTROUVABLE` sans
  rien écrire (comme SE1 et SE2) ; (KB-SEA-25) un seul statut par cause : article inconnu, d'un autre centre ou inactif et quantité
  invalide répondent 400 `BAD_REQUEST` sur les trois routes (les refus du Stock pour séance facturée ou pièce rapprochée restent 422 avec le
  code du Stock) ; écritures concurrentes : la séance est lue sous verrou (`FOR UPDATE`), le remplacement des raccourcis sous verrou
  consultatif du centre, verrou non obtenu en 5 s : 409 `ECRITURE_CONCURRENTE`. **Reproduits, à confirmer avant la mise en production** —
  (KB-SEA-33) l'auteur de la saisie n'est pas conservé par le Stock : **risque médico-légal de traçabilité**, à valider avec le DPO ;
  (KB-SEA-22) aucune sortie de stock n'est créée à la déclaration malgré la Javadoc FEFO ; (KB-SEA-24) la modification et le retrait sont
  acceptés sur une séance `CREE` ou `ABSENT` ; (KB-SEA-31) `PUT` des raccourcis avec un corps vide **efface tous les raccourcis du centre**.
  **Reproduits sans alerte** — (KB-SEA-23, 28, 30, 32, 34) ; (KB-SEA-29) l'audit porte `SEANCES_CREATION` (POST), `SEANCES_MODIFICATION`
  (PUT) et `SEANCES_SUPPRESSION` (DELETE) et, pour les raccourcis, `SEANCES_MODIFICATION` sans identifiant d'entité ; les droits par
  route. L'ajout exige une séance `VALIDEE` ou `SIGNEE` et non facturée ; les raccourcis sont limités à 12 articles actifs du centre,
  sans doublon, dans l'ordre choisi (liste nulle = liste vide).
  *Source :* `AjouterUnConsommable`, `ModifierLaQuantiteDUnConsommable`, `RetirerUnConsommable`, `ConsulterLesRaccourcis`,
  `RemplacerLesRaccourcis`, `RaccourcisDeConsommables`, `Seance`, `SeancesControleur`, `ErreursDeSeanceAdvice`.

- **RG-SEA-055** — **Rappel quotidien des séances à régulariser repris dans le module `seance`** (slice SE4, ADR 0091 ; aucune route,
  aucune table ajoutée ; suite de RG-SEA-046 et 053). Chaque jour à **07:00** (`app.seance.fuseau-par-defaut`), pour **tous** les
  centres, `RappelerLesSeancesARegulariser` compte les séances `CREE` des **sept jours passés du centre** (la veille comprise, le jour du
  centre exclu) **non déverrouillées**, avec la date de la plus ancienne, et envoie à l'administrateur la notification
  `SEANCES_A_REGULARISER` (`ADMIN` seul, journal durable) ; rien n'est envoyé s'il n'y en a aucune ; un centre en erreur n'arrête pas
  les autres. **Il n'existe pas d'agrégat « dérogation de régularisation »** : la dérogation est le déverrouillage
  (`regularisation_deverrouillee_at/by`, RG-SEA-053), sans motif ni autorité ; la table `derogations_regularisation` (V50) n'est jamais
  écrite (KB-SEA-40, conservée, vide). **Corrigés, à confirmer avec les métiers** — (KB-SEA-35) **un seul rappel par centre et par jour du
  centre** (identifiant déterministe dans le journal existant ; pour deux instances, **le verrou consultatif par centre et jour est la garantie**, sans repli sur la violation de clé du journal, et le scénario à deux instances n'est pas prouvé de bout en bout) : un second
  passage du même jour n'émet rien et le rappel du jour reste figé à son premier passage ; (KB-SEA-36) « aujourd'hui » est le jour du
  fuseau du centre, plus le jour UTC. **Reproduit, à confirmer avant la mise en production** — (KB-SEA-37) **la fenêtre est de 7 jours
  alors que le verrou des jours passés n'a aucune borne** : une séance oubliée depuis 8 jours ou plus reste bloquée pour l'infirmier, n'est
  plus jamais rappelée et le patient reste compté absent (décision métier prioritaire). **Reproduits sans alerte** — (KB-SEA-38) une
  séance `CREE` dont le patient a disparu est comptée par le rappel mais refusée au déverrouillage et à la validation ; (KB-SEA-39) le
  détail d'une séance ne porte pas l'indicateur de déverrouillage ; droits et libellés inchangés. Le planificateur est actif dès le
  déploiement, sans indicateur d'activation : une seule instance du planificateur ou le verrou consultatif vérifié.
  *Source :* `RappelerLesSeancesARegulariser`, `FenetreDeRegularisation`, `SeancesARegulariser`, `RappelDeRegularisation`,
  `RappelDesRegularisationsPlanifie`, `VerrousDesRappelsJdbc`, `RappelsFournisLegacy`.

- **RG-SEA-056** — **Surcharge du forfait d'une séance reprise dans le module `seance`** (slice SE5, ADR 0092 ; `PUT
  /api/v1/seances/{id}/forfait` `ADMIN` seul ; mêmes URL, mêmes champs JSON `centerId`, `forfaitId`, `userId`, mêmes tables et colonnes
  `seances.forfait_override_*`, aucune migration ; suite de RG-SEA-033). Intention « imposer un forfait » : la séance copie le **code,
  le nom et le prix** du forfait du référentiel du centre (instantané jamais rafraîchi quand le référentiel change), l'instant et
  l'auteur, et le forfait imposé **prime** sur celui de la prise en charge (détail et liste à sept clés) ; une nouvelle imposition remplace
  tout, sans historique. Ordre des refus : centre du corps différent de la session 403, séance introuvable 400, forfait introuvable pour
  le centre 400, séance facturée 422 `SEANCE_FACTUREE_NON_MODIFIABLE`. Aucun montant n'est recalculé : ni TVA ni arrondi. **Corrigés, à
  confirmer avec les métiers** — (KB-SEA-44) un identifiant de forfait, de centre ou de séance illisible, un corps illisible ou absent et un
  auteur de plus de 100 caractères répondent 400 `BAD_REQUEST` au lieu de 500, le refus de droits passant avant le 400 ; deux impositions
  simultanées de la même séance se suivent sous le verrou de ligne (`FOR UPDATE`), verrou non obtenu en 5 s : 409 `ECRITURE_CONCURRENTE`
  (l'ancien code écrasait en silence) ; un statut illisible répond 400 sans nom de classe. **Reproduits, RISQUES D'ARGENT à confirmer
  avant la mise en production** — (KB-SEA-41) un forfait sans prix ou à 0.00 est imposé sans alerte ; (KB-SEA-43) seul le statut
  `FACTUREE` est refusé : une séance `CREE` ou `ABSENT` reçoit un forfait et `facture_id` n'est pas regardé ; (KB-SEA-46) le forfait courant
  ignore le statut de la prise en charge (une PEC refusée, annulée ou en brouillon fournit le forfait affiché) ; (KB-SEA-47) la
  Facturation (`poserLeForfait`, hors périmètre, non corrigée) n'écrit pas `forfait_override_code` ; (KB-SEA-42) l'auteur est un champ libre
  du corps, jamais le compte de session. **Reproduits sans alerte** — (KB-SEA-45) aucun historique, audit réduit à `SEANCES_MODIFICATION`,
  la route et le statut ; (KB-SEA-48) droits : seul `ADMIN` (`DIRECTION` et `SUPERADMIN` : 403 `ROLE_SCOPE`, `MEDECIN`, `SECRETAIRE`,
  `INFIRMIER` : 403 `ACCESS_DENIED`). Le verrou de ligne ne couvre pas la Facturation (résidu daté, ADR 0092).
  *Source :* `ImposerUnForfait`, `Seance.imposerUnForfait`, `ForfaitImpose`, `AuteurDuForfaitImpose`, `ForfaitsDuCentreParLeLegacy`,
  `ReferentielDeForfaitsFourniLegacy`, `SeancesJdbc`, `SeancesControleur`, `ErreursDeSeanceAdvice`.

- **RG-SEA-057** — **Changement de jour et suppression d'une séance repris dans le module `seance`** (slice SE6, ADR 0093 ; `PUT
  /api/v1/seances/{id}` et `DELETE /api/v1/seances/{id}?centerId=` `ADMIN` seul ; mêmes URL, mêmes champs JSON (`centerId`, `dateSeance`,
  `motif`), mêmes tables et colonnes (`seances.date_seance`, `seance_suppression`), aucune migration ; suite de RG-SEA-002 et RG-SEA-050).
  *Changer de jour* : seule la séance **facturée** est refusée (422 `SEANCE_FACTUREE_NON_MODIFIABLE`) ; l'absence de date, `null` et la
  chaîne vide valent « le jour du centre » (`HorlogeDuCentre`, jamais l'horloge de la JVM) ; la réponse est `{id, status, dateSeance}`.
  *Supprimer* : ordre des gardes reproduit (centre de la session 403, séance du centre 422 `SEANCE_INTROUVABLE`, motif de 5 à 500
  caractères 422, statut `FACTUREE` 422 `SEANCE_FACTUREE_NON_SUPPRIMABLE`) ; puis, tout ou rien dans une seule unité de travail : consommations
  imputées annulées par le Stock, administrations et volets effacés, séance supprimée, trace écrite dans `seance_suppression` ; la diffusion
  `SEANCE_SUPPRIMEE` part APRÈS la validation (un échec de diffusion ne défait jamais la suppression). **Corrigés, à confirmer avec les
  métiers** — (KB-SEA-49) le changement de jour n'écrit que `date_seance` : la facture, le forfait imposé, les signatures et la place sont
  conservés (le legacy effaçait `facture_id` d'une séance non facturée) ; (KB-SEA-50) **toute séance qui porte une facture est refusée à la
  suppression, quel que soit son statut** (422 `SEANCE_RATTACHEE_A_UNE_FACTURE_NON_SUPPRIMABLE` : annuler d'abord la facture) ; (KB-SEA-52)
  identifiant, centre ou JSON illisibles, corps absent, motif en tableau : 400 au lieu de 500, `centerId` absent de la suppression : 400
  « Centre requis », droits avant 400 ; (KB-SEA-56, partielle) date illisible ou nombre : 400 « Date de séance illisible » (aucune règle de
  calendrier ajoutée) ; deux écritures simultanées se suivent sous verrou de ligne, verrou non obtenu en 5 s : 409 `ECRITURE_CONCURRENTE`.
  **Reproduits, RISQUES À CONFIRMER avant la mise en production** — (KB-SEA-54) le jour d'une séance validée ou signée reste modifiable
  alors que le jour détermine la période de facturation ; (KB-SEA-55) le changement de jour contourne le verrou des jours passés
  (RG-SEA-053) sans trace de déverrouillage ; (KB-SEA-51) **aucun stock n'est restitué** à la suppression malgré les mots « restitution » du
  legacy (RG-SEA-050 est à lire ainsi), et les `bons_sortie` / `stock_movements` de la séance restent sans séance (aucune clé étrangère) ;
  (D10) une séance rattachée à une facture mais non `FACTUREE` peut changer de jour (et sortir de la période de sa facture) alors que sa
  suppression est refusée. **Écart gardé, à confirmer** — (D9) un échec de diffusion de `SEANCE_SUPPRIMEE` est journalisé et n'empêche
  pas la suppression (le legacy renvoyait une 500 après l'effacement). **Reproduits sans alerte** — (KB-SEA-53) la suppression ne regarde ni le jour, ni la signature du
  médecin, ni le patient ; droits, audit (`SEANCES_MODIFICATION`, `SEANCES_SUPPRESSION`), diffusion, libellés. Aucun montant n'est recalculé.
  *Source :* `ChangerLeJourDUneSeance`, `SupprimerUneSeance`, `Seance.changerDeJour`, `Seance.exigerSupprimable`, `MotifDeSuppression`,
  `SuppressionDeSeance`, `FactureId`, `SeancesJdbc`, `JournalDesSuppressionsJdbc`, `SaisiesDeSeanceFourniesLegacy`, `SeancesControleur`,
  `ErreursDeSeanceAdvice`.

- **RG-SEA-058** — **Journal du jour, calendrier du centre, volet médical et séances récentes repris dans le module `seance`** (slice
  SE7a, ADR 0094 ; mêmes URL, mêmes champs JSON, mêmes droits, mêmes tables `seances`, `patients` (lecture), `center_holiday`,
  `center_closure_day`, `volet_medical`, aucune migration ; suite de RG-SEA-026, 028 et 031). *Journal* (`GET /seances/journal`, `ADMIN`,
  `INFIRMIER`, `MEDECIN`, `SECRETAIRE`) : les patients ayant une séance ce jour-là, tous statuts, triés par nom puis prénom, et les articles
  sortis du stock ce jour-là (bons de sortie du Stock, pas les consommables déclarés). *Calendrier* (`GET /seances/calendar` mêmes profils ;
  `POST` et `DELETE /seances/calendar/holiday` et `/closure`, `ADMIN` seul) : jours fériés et fermetures exceptionnelles, deux tables,
  deux routes ; le calendrier **reste dans la Séance** (décision par délégation, à confirmer ; transfert à l'Organisation en résidu daté).
  *Volet médical* (`PUT /seances/{id}/volet-medical`, `ADMIN`, `MEDECIN`) : refus 422 des séances absentes, créées ou facturées ; la séance est
  lue sous verrou, deux saisies simultanées se suivent (409 `ECRITURE_CONCURRENTE` après 5 s). *Séances récentes*
  (`GET /seances/patient/{id}/recentes`) : au plus 10 séances STRICTEMENT antérieures au jour (par défaut le jour du centre, `HorlogeDuCentre`),
  `limit` ramené à 1..10. **Corrigés, à confirmer avec les métiers** — (KB-SEA-57) paramètre ou corps absent, illisible ou hors bornes (mois
  0 ou 13, journal sans date, identifiant illisible) : 400 `BAD_REQUEST` au lieu de 500, 403 avant 400 ; (KB-SEA-58, partielle) un second
  jour férié ou une seconde fermeture à la même date : 409 `JOUR_DEJA_AU_CALENDRIER` au lieu de 500, date absente ou libellé de plus de 255
  caractères : 400 ; centre absent d'un corps : 400 « Centre requis » ; une séance au statut illisible ne cite plus de nom de classe.
  **Reproduits, RISQUES À CONFIRMER avant la mise en production (médecins et DPO)** — (KB-SEA-59) le volet médical est **remplacé en entier** :
  un champ absent efface la valeur déjà saisie, la route reste ouverte sur une séance signée, `ADMIN` peut écrire des observations, aucun
  plafond de taille (colonnes `text`) ; (KB-SEA-60) les constantes des séances `CREE` (non validées) sont servies par les séances récentes.
  **Reproduits avec signalement** — (KB-SEA-58) le retrait d'un jour inexistant répond toujours 200 `{deleted:true}` ; aucun contrôle de
  cohérence avec les séances déjà posées ; l'an 1900 est accepté. **Reproduits sans alerte** — (KB-SEA-61) droits, libellés, audit générique
  des écritures (route et statut), aucune diffusion ni notification. Limite assumée : le golden d'audit n'a pas été rejoué contre le legacy
  retiré (le filtre d'audit est générique, non modifié).
  *Source :* `ConsulterLeJournalDuJour`, `ConsulterLeCalendrier`, `AjouterUnJourAuCalendrier`, `RetirerUnJourDuCalendrier`,
  `RenseignerLeVoletMedical`, `ConsulterLesSeancesRecentes`, `JourDuCalendrier`, `MoisDuCalendrier`, `VoletMedical`, `ObservationsMedicales`,
  `CalendrierDuCentreJdbc`, `VoletsMedicauxJdbc`, `PatientsDuJourJdbc`, `RoutesResiduellesControleur`, `ErreursDeSeanceAdvice`.

- **RG-SEA-059** — **Tableau de bord mensuel des séances, détail nominatif et exports repris dans le module `seance`** (slice SE7b,
  ADR 0095 ; mêmes URL `/seances/dashboard`, `/dashboard/details`, `/dashboard/export`, mêmes champs JSON, mêmes droits `ADMIN`,
  `INFIRMIER`, `MEDECIN`, `SECRETAIRE`, mêmes tables `seances`, `patients` (lecture), `center_holiday`, `center_closure_day`, aucune
  migration ; suite de RG-SEA-043 et 051, la garde de centre reste celle de RG-SEA-051). Le jour du centre vient de `HorlogeDuCentre`.
  Aucun montant n'est exposé. Les exports CSV, PDF et XLSX gardent les mêmes octets (CSV `text/plain` sans charset). **Corrigés, à
  confirmer avec les métiers** — (KB-SEA-63) année ou mois absent ou illisible, centre illisible, `kind` absent : 400 `BAD_REQUEST` au
  lieu de 500, 403 avant 400. **Reproduits, RISQUES À CONFIRMER avant la mise en production (métiers et DPO)** — (KB-SEA-66) le détail
  nominatif sert nom, prénom, identifiant et présence ou absence à tous les profils autorisés, et ses lectures ne sont PAS auditées
  (décision DPO requise) ; (KB-SEA-64) les compteurs ne se recoupent pas avec le détail et l'effectif du mois est contaminé par des
  patients d'un autre centre ; (KB-SEA-65) un patient peut être compté présent et absent le même jour ; (KB-SEA-62) les clés françaises
  de `expectedByWeekday` restent à 0, les comptes sont sur les clés anglaises. **Écart décidé, à confirmer** — les absences sont bornées
  au jour du fuseau de chaque centre (et non plus du fuseau par défaut). **Reproduits sans alerte** — (KB-SEA-67) tri du détail
  sensible à la casse, droits, libellés. Un échec de lecture du calendrier est avalé avec un WARN portant le seul identifiant du centre.
  **LEC-PAT (i), 2026-11-14, ADR 0176 : source des données, comportement inchangé** — la programmation hebdomadaire (jours de dialyse,
  état, sommeil, admission), le sexe et la naissance des présents, les identités du détail nominatif, les patients du journal du jour
  (code, nom, prénom) et le planning d'un scan ne sont plus lus par la Séance en SQL sur la table des patients mais par les lectures
  publiées de Patient et couverture (`ProgrammationDesPatients`, `LotsDePatients`) ; le tri nom puis prénom du journal reste celui de la
  base de Patient et couverture, les homonymes sont départagés par l'identifiant de séance. Les deux lectures par lot ne sont pas bornées
  au centre du patient : KB-SEA-61 et KB-SEA-64 restent reproduits, à confirmer métiers/DPO avant le 2026-12-15.
  **LEC-PAT (ii), 2026-11-14, ADR 0177 : source des données (ARGENT), comportement inchangé, montants identiques à l'unité près** — le
  forfait courant d'une séance (le forfait imposé sur la séance prime, sinon celui de la prise en charge qui couvre le jour : effectif
  d'abord, sinon demandé, la plus récente d'abord ; à défaut la plus récente qui porte un forfait, le statut de la prise en charge n'étant
  jamais regardé), les séances à facturer (fiche du centre : code, nom, prénom, état, numéro d'assurance, centre payeur ; ordre nom puis
  prénom, homonymes par date puis identifiant de séance ; une séance dont la fiche n'est pas du centre n'est pas facturable), la répartition
  des séances par caisse (centre payeur de la fiche du centre, caisse vide sinon : KB-SEA-83, KB-SEA-84) et les séances réalisées publiées
  à la Planification avec le nom et le prénom de la fiche du centre (KB-SEA-80) ne sont plus lus en SQL sur les tables du patient, de la
  prise en charge et du forfait, mais par `LotsDePatients` et `CouvertureDesPatients` (langage publié `patientcouverture.v1`). Aucun
  écart de parité (goldens existants inchangés, un golden d'ordre de facturation ajouté avant le changement).
  **LEC-PAT (iii), 2026-11-14, ADR 0178 : source des données, comportement inchangé** — l'historique des séances (filtre de texte sur le
  nom, le prénom ou le code du patient, tri par patient ou par code) ne joint plus la table des patients : la Séance filtre et ordonne ses
  séances, la fiche des patients DU CENTRE (texte, tri nom puis prénom ou code, collation de la base) vient de `LotsDePatients`
  (`rechercherDuCentre`). Une séance dont le patient est d'un autre centre ou a disparu reste absente ; les homonymes restent départagés par
  création décroissante puis identifiant de séance. Le total et les pages sont inchangés.
  *Source :* `ConsulterLeTableauDeBord`, `ConsulterLeDetailDuTableauDeBord`, `ExporterLeTableauDeBord`, `MoisDuTableauDeBord`,
  `SeancesAttendues`, `EffectifDuMois`, `FinDeLaPriseEnCharge`, `TrancheDAge`, `LigneDuDetail`, `FormatDExport`, `SeancesRealiseesJdbc`,
  `PatientsDuTableauDeBordJdbc`, `AbsencesDuCentreParLePilotage`, `ExportsDuTableauDeBordCsvPdfXlsx`, `TableauDeBordDesSeancesControleur`,
  `ErreursDeSeanceAdvice`.

- **RG-SEA-060** — **Lectures publiées de la Séance pour le Stock** (slice SE7c1, ADR 0096 ; aucune route, aucune migration, aucun
  montant, lecture seule). Le Stock ne lit plus la table `seances` : il demande à la Séance, par son langage publié, si une séance est
  **facturée** et quelle est sa **place** (code de la salle, code et libellé du créneau). Une séance est facturée quand son statut est
  `FACTUREE`, sans tenir compte de la casse (KB-SEA-70) ; une séance inconnue ou d'un autre centre n'est pas facturée et n'a pas de place
  (le Stock retombe alors sur la sortie « journée »). La salle et le créneau ne sont lus que dans le centre de la séance (sinon aucun,
  KB-SEA-69). Le Stock déduit la demi-journée du code ou du libellé du créneau (KB-SEA-68 : « matin », « après-midi », « midi »,
  « soir »). **Reproduit tel quel, RISQUES À CONFIRMER avant la mise en production (métiers, revue au plus tard le 2026-12-15)** :
  KB-SEA-68, 69 et 70.
  *Source :* `FacturationDesSeancesPubliee`, `PlacesDesSeancesPubliees`, `LecturesPublieesDesSeancesJdbc`,
  `StatutDesSeancesParLaSeance`, `PerimetreDesSeancesParLaSeance`.

- **RG-SEA-061** — **Écritures et lectures publiées de la Séance pour la Facturation (ARGENT)** (slice SE7c2, ADR 0097 ; aucune route,
  aucune migration, aucune clé étrangère ajoutée, aucun montant, prix, TVA ni arrondi recalculé : les valeurs financières sont copiées à
  l'identique). La Facturation n'écrit ni ne lit plus la table `seances` : elle passe par quatre ports publiés de la Séance. **Séances à
  facturer** : statut `VALIDEE` ou `SIGNEE` (casse exacte, KB-FAC-17), non rattachée à une facture, date dans la période (bornes
  incluses), patient du même centre ; tri nom, prénom, date. **Exclusion** : la séance au statut attendu et non facturée passe à `ABSENT`
  en mémorisant son statut ; **réinclusion** : elle retrouve ce statut ; **forfait imposé** : id, nom, prix, date et auteur de la surcharge
  sont écrits sur une séance `VALIDEE` ou `SIGNEE` non facturée, **sans écrire le code du forfait** (KB-SEA-47, reproduit). **Liaison** :
  chaque séance `VALIDEE` ou `SIGNEE` non facturée du centre passe à `FACTUREE` et porte l'identité de sa facture ; si une seule n'est plus
  éligible, toute l'émission est annulée (« Une ou plusieurs seances ne sont plus eligibles a la validation », 422) : l'émission reste dans
  UNE SEULE transaction, non scindée, sans compensation. **Comptages** : deux définitions de « séance facturée » conservées (tableau de
  bord : statut `FACTUREE` et date de séance dans le mois ; synthèse et répartitions : séances rattachées aux factures du mois ;
  **KB-SEA-75 CLOS le 2026-11-10 (lot L5b, ADR 0118) sans unifier** : les deux définitions ne mesurent pas la même chose (la date de la séance
  contre la facture du mois) et coïncident sur des données saines dont les séances sont du mois de leur facture ; un test du contrat fige les
  deux définitions, leur équivalence dans ce cas et leur écart quand une facture du mois porte une séance d'un mois antérieur).
  **Seul écart décidé (à confirmer métiers, KB-SEA-71)** : le port publié refuse désormais de lier une absence à une séance déjà rattachée
  à une facture (garde `facture_id IS NULL`, comme la réinclusion). C'est une **défense en profondeur sur le port**, observée au niveau du
  port (golden et contrat), **sans effet aujourd'hui sur le trafic HTTP** : l'exclusion d'une séance, seul appelant, exclut d'abord la
  séance, ce qui exige déjà qu'elle soit non facturée, puis lie l'absence dans la même transaction ; le refus visible par l'utilisateur
  reste celui de l'exclusion. **Corrigés le 2026-11-09 (lot L3, ADR 0114, à confirmer par les métiers avant le 2026-12-15)** :
  (KB-SEA-72) la **mémoire d'exclusion** n'est « présente » que si elle contient au moins un caractère hors espace, pour la lecture comme pour
  la liaison à une absence et la réinclusion, qui refusent une mémoire blanche ; (KB-SEA-73) l'**exclusion** d'une séance n'est acceptée par le
  port que si elle est exactement `VALIDEE` ou `SIGNEE` (le port redit la règle du domaine), même appelée avec son propre statut ; (KB-SEA-74)
  une séance ne se **lie** qu'à une facture qui existe et appartient au même centre (garde de l'écriture) ; en production PostgreSQL, une clé
  étrangère `seances.facture_id` vers `factures` (migration V54, `NOT VALID`) refuse tout nouvel identifiant orphelin et la suppression d'une
  facture encore liée, sans empêcher le démarrage sur des données sales ; (KB-SEA-82) en production, un CHECK de statut canonique
  (`UPPER(TRIM(statut))`, migration V55, `NOT VALID`) refuse tout statut écrit en minuscules ou avec un blanc ; les lecteurs restent en casse
  exacte (KB-FAC-17). Les deux contraintes se valident à la main après nettoyage (`VALIDATE CONSTRAINT`). **Clos** : KB-SEA-71 (déjà corrigé).
  **Reproduits, RISQUES À CONFIRMER avant la mise en production (métiers, DPO, DBA, revue au plus tard le 2026-12-15)** : KB-SEA-47 (code du
  forfait non écrit), KB-SEA-75 (deux définitions). Requêtes de détection : `verifications-production.md` sections 62 et 76.
  *Source :* `SeancesAFacturerPubliees`, `ExclusionsEtForfaitsDesSeancesPublies`, `LiaisonDesSeancesAUneFacturePubliee`,
  `SeancesDesFacturesPubliees`, `FacturationDesSeancesJdbc`, `LiaisonDesSeancesJdbc`, `SeancesFacturablesParLaSeance`,
  `SeancesDeFacturationParLaSeance`, `LiaisonDesSeancesParLaSeance`, `SeancesDesFacturesParLaSeance`.

- **RG-SEA-062** — **Lectures publiées de la Séance pour les Absences de patients** (slice SE7d, ADR 0098 ; aucune route, aucune
  migration, lecture seule, comportement inchangé). Les Absences ne lisent plus la table `seances` : elles demandent à la Séance, par son
  langage publié, les **séances réalisées** et le **lien d'exclusion**. Une séance est **réalisée** quand son statut est `VALIDEE`,
  `SIGNEE` ou `FACTUREE`, en **casse exacte et sans blanc** (`validee`, `VALIDEE ` ne comptent pas, KB-SEA-82) ; tout est borné au centre,
  bornes de dates incluses (des bornes inversées ne donnent rien). Quatre lectures : la **semaine** (une ligne par patient et par jour
  réalisé, dates croissantes puis patient à date égale, ordre déterministe : KB-SEA-77 corrigé), les **jours d'un patient** (jours
  distincts croissants, rattrapage), la **séance réalisée un jour** (déclaration, rattrapage, réconciliation de nuit) et les **patients
  ayant une séance réalisée un jour** (patients écartés de la détection de nuit). **Lien d'exclusion** : une absence vient d'une exclusion
  tant qu'une séance **du centre réellement exclue (statut `ABSENT`)** garde le lien vers elle (KB-SEA-81 corrigé : un lien résiduel resté sur
  une séance revenue à un autre statut ne bloque plus l'annulation de l'absence ; le lien reste en base, aucun nettoyage automatique) ; un lien
  porté par une séance d'un autre centre est ignoré. **Corrigés le 2026-11-10 (lot L5a, ADR 0117), à confirmer par les métiers au plus tard le
  2026-12-15** : KB-SEA-77, 81. **Reproduit sans correction** : KB-SEA-82. Requêtes de détection : `verifications-production.md` section 63.
  *Source :* `SeancesRealiseesPubliees`, `LiensDExclusionPublies`, `LecturesDesSeancesRealiseesJdbc`, `SeancesRealiseesParLaSeance`,
  `LiensDExclusionParLaSeance`, `SeancesRealiseesFournies`, `LiensDExclusionFournis`, `ExclusionsDeSeancesPubliees`, `PatientsAttendusJdbc`.

- **RG-SEA-063** — **Lecture publiée des séances réalisées avec leur place pour la Planification** (slice SE7d, ADR 0098 ; aucune route,
  aucune migration, lecture seule, comportement inchangé). La grille de la semaine du planning (et l'empreinte de l'optimisation) lit
  par le langage publié de la Séance les séances réalisées (statuts exacts, KB-SEA-82) de la période avec la **place figée à leur
  validation** (salle, créneau, générateur ; aucune si elle n'a pas été figée, RG-SEA-049) et le **nom et le prénom de la fiche** ; la
  Planification compose le nom « prénom nom ». Tri par **date, puis création (la plus ancienne créée d'abord, création inconnue en dernier), puis
  identifiant** : deux séances réalisées d'un même patient le même jour sortent dans un ordre déterministe et la grille garde la première, la
  plus ancienne créée (KB-SEA-78 corrigé le 2026-11-10, lot L5a, ADR 0117). Une séance réalisée n'est lue que si **la fiche du patient est
  dans le centre de la séance** : un patient dont la fiche est dans un autre centre reste invisible dans la grille (cloisonnement voulu ;
  **KB-SEA-80 CLOS le 2026-11-10, lot L5b, ADR 0118, sans changement de comportement** : aucune lecture d'une fiche d'un autre centre, et le
  dépôt ne contient aucun code de transfert qui ferait changer le centre d'une fiche ; détection §80 de `verifications-production.md`).
  **Reproduit sans correction, à confirmer par les métiers au plus tard le 2026-12-15** : KB-SEA-82.
  *Source :* `SeancesRealiseesAvecLeurPlacePubliees`, `LecturesDesSeancesRealiseesJdbc`, `SeancesRealiseesAvecLeurPlaceParLaSeance`,
  `SeancesRealiseesAvecLeurPlaceFournies`, `SeancesRealiseesPubliees` (Planification).

- **RG-SEA-064** — **Lecture publiée des constantes des séances pour le Dossier médical** (slice SE7d, ADR 0098 ; aucune route, aucune
  migration, lecture seule, comportement inchangé). L'historique des constantes du patient (`GET /api/v1/patients/{id}/constantes`)
  lit par le langage publié de la Séance **toutes les séances du patient dans le centre, quel que soit leur statut**, avec les sept
  mesures du **volet paramédical du même centre** (poids avant et après, tension avant et après, débit sang, ultrafiltration, durée ;
  toutes vides sans volet ou si le volet est rattaché à un autre centre) ; aucune lecture de la fiche du patient. Le total est lu avant
  la page. Ordre : date de séance décroissante, puis création décroissante, puis identifiant décroissant. Une séance **sans date de
  création** se classe **en premier** (`NULLS FIRST` écrit explicitement, identique sur H2 et PostgreSQL, ce qui conserve l'ordre servi en
  production : KB-SEA-79 corrigé le 2026-11-10, lot L5a, ADR 0117, à confirmer au plus tard le 2026-12-15). Mêmes mesures et mêmes
  destinataires `MEDECIN`, `ADMIN` (DPO : inchangés).
  *Source :* `ConstantesDesSeancesPubliees`, `ConstantesDesSeancesJdbc`, `HistoriqueDesConstantesPublie`.

- **RG-SEA-065** — **Lecture publiée des séances réalisées pour le pilotage de la Direction** (slice SE7e, ADR 0099 ; aucune route,
  aucune migration, lecture seule, comportement inchangé). La vue d'ensemble, la période précédente, l'évolution mensuelle, la
  répartition par caisse, le rapport PDF, l'instantané et le taux d'absentéisme de la Direction comptent les séances par le langage
  publié de la Séance. Une séance compte quand son statut est `VALIDEE`, `SIGNEE` ou `FACTUREE` en **casse exacte et sans blanc** et
  que sa date est dans la période, bornes incluses, pour les seuls centres de la société. **Un seul prédicat pour les trois vues**, celui de
  la séance elle-même (KB-DIR-05 et KB-SEA-83 corrigés le 2026-11-10, lot L5b, ADR 0118) : la vue d'ensemble, le taux d'absentéisme et la
  répartition par caisse comptent les mêmes séances ; une séance dont la fiche du patient est dans un autre centre reste comptée dans la
  caisse « sans code » du centre de la séance (la somme des caisses d'un centre égale sa vue d'ensemble). **Chaîne payeur bornée au centre**
  (KB-SEA-84 corrigé, lot L5b) : le centre payeur d'une fiche n'est suivi que s'il est du centre de la fiche, sinon la séance, le patient et
  le chiffre d'affaires (agence figée de la facture, borne `ag.center_id = f.center_id`) tombent sur la caisse vide ; à chiffre d'affaires égal,
  les lignes de caisse sont départagées par le code (ordre indépendant de la lecture). Agrégats seulement, aucune ligne de patient.
  **À confirmer par les métiers (Direction, Comptabilité) et le DPO au plus tard le 2026-12-15** : KB-DIR-05, KB-SEA-83, 84 corrigés (la ligne
  « caisse non renseignée » grossit, l'historique est recalculé en direct, les instantanés figés gardent l'ancienne valeur). Détection :
  `verifications-production.md` §64 et §80.
  *Source :* `SeancesRealiseesDesCentresPubliees`, `SeancesRealiseesDesCentresJdbc`, `DirectionDashboardQueryService`,
  `DirectionBreakdownQueryService`, `DirectionAbsencesQueryService`.

- **RG-SEA-066** — **Lecture publiée des séances pour les statistiques du patient** (slice SE7e, ADR 0099 ; aucune route, aucune migration,
  lecture seule). Les statistiques paramédicales (écran et export PDF) lisent par le langage publié de la Séance
  **les seules séances réalisées du patient dans le centre** (`VALIDEE`, `SIGNEE`, `FACTUREE` en casse exacte : une séance annulée, créée ou
  planifiée sort du compte et des moyennes, comme dans les autres lectures ; KB-SEA-85 b corrigé le 2026-11-10, lot L5b, ADR 0118), avec les
  mesures du **volet paramédical du centre de la séance seulement** (un volet posé sous un autre centre n'est plus lu : KB-SEA-85 a corrigé) ;
  le compte s'affiche **en entier** dans le PDF (« Seances: 3 », KB-SEA-85 c corrigé) : nombre de séances, moyennes des poids avant et
  après et de l'ultrafiltration réelle (saisie, sinon perte de poids x 1000 ; une moyenne sans mesure vaut `0.0`), évolution du poids avec
  le **poids sec en vigueur** le jour de la séance (dernière prescription du patient au centre portant un poids sec, la plus récemment
  modifiée à date égale) et évolution de la tension. Ordre par **date, puis création (la plus ancienne créée d'abord, création inconnue en
  dernier), puis identifiant** ; les clés des lignes de l'évolution du poids sont **toutes en minuscules** quelle que soit la base (KB-SEA-86
  corrigé le 2026-11-10, lot L5a, ADR 0117 : c'est le contrat servi en production, l'écran et le PDF lisent les deux casses). La précision des
  moyennes dépend de la base (KB-SEA-87, **close sans changement de code** : seules 10 décimales sont garanties, comparaison à tolérance dans
  les tests, le PDF arrondit à 2 décimales). **KB-SEA-85 corrigé, à confirmer par les métiers (infirmier référent, médecins) et le DPO au plus tard
  le 2026-12-15** (mêmes rôles `ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE` ; export `ADMIN` ; les moyennes ne portent plus sur les séances non
  réalisées, ni sur un volet d'un autre centre).
  *Source :* `StatistiquesDesSeancesPubliees`, `StatistiquesDesSeancesJdbc`, `SeancesDuPatientPubliees` (ex `PatientStatsQueryService`, PL4).

- **RG-SEA-067** — **Cahier de dialyse et usage d'un forfait : comptages publiés** (slice SE7e, ADR 0099 ; aucune route, aucune migration,
  lecture seule, comportement inchangé). Le refus `CAHIER_VIDE` compte par le langage publié de la Séance les séances du patient au
  centre, `VALIDEE`, `SIGNEE` ou `FACTUREE` en casse exacte, dans la période (par défaut du 1900-01-01 au 2999-12-31) ; le **contenu** du
  cahier reste lu par le **modèle de document du centre** (personnalisable, une requête embarquée ; un forfait imposé sans code recopié sur la
  séance imprime désormais le code et le libellé du forfait du référentiel du même centre, le prix restant celui de la surcharge : KB-SEA-88
  corrigé le 2026-11-10, lot L5a, ADR 0117), mise en page inchangée. La suppression d'un forfait du référentiel est refusée (`REFERENTIAL_IN_USE`) tant
  qu'une séance **de n'importe quel centre et de n'importe quel statut** l'impose (KB-SEA-91), compté par le même langage publié.
  **À confirmer au plus tard le 2026-12-15** (infirmier référent, pour le libellé du forfait imprimé) : KB-SEA-88 corrigé ; **KB-SEA-91 CLOS
  par documentation le 2026-11-10 (lot L5b, ADR 0118)** : le blocage sans borne de centre ni de statut est voulu (prudence : aucune référence
  orpheline), figé par le contrat `should_count_the_seances_imposing_a_package_in_any_centre_and_any_status`.
  *Source :* `SeancesDuCahierPubliees`, `ForfaitsImposesAuxSeancesPublies`, `SeancesDuCahierEtForfaitsImposesJdbc`,
  `ImprimerLeCahierDeDialyse` (l'ancien contrôle en SQL dynamique, `ReferentialAdminJdbcAdapter`, est retiré : ADR 0161).

- **RG-SEA-068** — **Séances reprises de l'ancien logiciel : écritures publiées** (slice SE7e, ADR 0099 ; aucune route, aucune migration,
  comportement inchangé). L'import des séances d'un lot de reprise et l'annulation du lot passent par le **seul** port publié qui écrit
  et supprime des séances hors de la Séance, réservé à la reprise. Une séance **déjà présente** le même jour pour le patient dans le
  centre du lot, **quel que soit son statut sauf annulée** (une séance `ANNULEE` n'est pas une séance tenue et ne bloque plus la reprise :
  KB-SEA-89 corrigé le 2026-11-10, lot L5a, ADR 0117), est conservée et la ligne n'est pas reprise ; sinon la
  séance est créée `FACTUREE` (facturée dans l'ancien logiciel) ou `SIGNEE`. Seuls la date, le statut et le patient sont acceptés. À
  l'annulation, un patient repris ne se supprime pas tant qu'il a une séance **dans n'importe quel centre** qui n'a pas été créée par le
  lot (KB-SEA-90) ; une séance reprise ne se supprime pas si elle est facturée ou complétée dans la plateforme ; la suppression ne vise
  que le centre du lot. **À confirmer au plus tard le 2026-12-15** : KB-SEA-89 corrigé ; **KB-SEA-90 CLOS par documentation le 2026-11-10
  (lot L5b, ADR 0118)** : règle voulue « bloquant sans borne de centre » (une séance d'un autre centre empêche la suppression du patient, sinon
  séance orpheline inter-centres puisque la suppression est bornée au centre du lot), figée par le contrat
  `should_list_the_seances_and_the_invoice_without_the_centre_and_remove_only_in_the_centre_of_the_batch`.
  *Source :* `SeancesDeLaReprisePubliees`, `SeancesDeLaRepriseJdbc`, `HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`.

- **RG-SEA-069** — **Volet paramédical : les constantes de la séance appartiennent à la Séance** (slice SE7f, ADR 0100 ; même route
  `PUT /api/v1/seances/{seanceId}/volet-paramedical`, mêmes rôles `ADMIN`, `INFIRMIER`, `SECRETAIRE`, mêmes champs, aucune migration ;
  comportement inchangé sauf les trois corrections du lot L4 ci-dessous). Le volet porte **dix mesures** : poids avant et après (kg), tension avant et après, durée (min), débit sang
  (ml/min), ultrafiltration (ml), anticoagulant, type de dialysat, incidents. La saisie **remplace le volet en entier** : une mesure absente
  du corps efface la valeur déjà saisie (RG-SEA-030). Un poids présent est **strictement positif et au plus 500 kg** (sinon 422
  `BUSINESS_ERROR`, « Le poids doit être strictement positif » / « Le poids dépasse la borne plausible (500 kg) ») ; une tension présente
  est lue « systolique/diastolique », la notation en cmHg (« 12/8 », « 11,5/7 ») est convertie en mmHg, les valeurs sont bornées de 20 à
  400 mmHg et la systolique est au moins égale à la diastolique (sinon 422 `BUSINESS_ERROR`, messages du legacy) ; une tension vide est
  gardée telle quelle ; durée, débit et ultrafiltration ne sont pas bornés. Ordre des refus : centre du corps (403 avant tout 400), séance
  inconnue du centre (400 « Seance introuvable »), séance **facturée** (422 `SEANCE_FACTUREE_NON_MODIFIABLE` ; `CREE`, `VALIDEE`,
  `SIGNEE` et le reliquat `ABSENT` sont acceptés), mesures, fiche du patient absente du centre (400). **Écart assumé sous concurrence, à confirmer par les métiers au plus tard
  le 2026-12-15** (ADR 0100, « Parité attendue ») : deux saisies simultanées de la même séance se suivent (séance lue sous verrou de ligne,
  la seconde attend la première puis écrit) ; si la première tient le verrou plus de 5 s, la seconde répond 409 `ECRITURE_CONCURRENTE`
  (relire puis recommencer), là où le legacy écrivait deux fois sans verrou (deux 200, la dernière écriture écrasait l'autre). La réponse rend `id`, `seanceId`, `updatedAt` ; deux diffusions suivent (volet enregistré
  pour les infirmiers et secrétaires du centre, saisie de l'infirmier de type `PARAMEDICAL` pour les médecins). Le volet est **effacé avec
  la séance** (RG-SEA-054) et relu par le détail et les séances récentes **par la séance** (L8 ; le volet médical : au centre).
  **Corrigé le 2026-11-10 (lot L4, ADR 0116), à confirmer par les métiers au plus tard le 2026-12-15** : (KB-SEA-93) la fiche du patient est
  vérifiée **avant** l'écriture : fiche hors du centre, 400 « Patient introuvable », **rien n'est écrit ni diffusé** (avant : le volet était écrit
  puis la réponse échouait) ; une mesure refusée (422) passe toujours avant ; (KB-SEA-94) le volet d'une séance est cherché **par la séance** :
  un volet posé sous un autre centre est **remplacé** (200) sans changer son centre, au lieu de bloquer toute saisie (500 `DATA_ACCESS_ERROR`) ;
  **depuis le lot L8 (ADR 0120)** le détail de la séance et les séances récentes relisent le volet paramédical **par la séance, comme la saisie**
  (la séance est déjà bornée au centre) : l'utilisateur revoit ce qu'il vient d'enregistrer, même si le volet est resté posé sous un autre centre ;
  le volet médical reste borné au centre ; la réparation du centre des volets reste une décision manuelle du responsable des données (jamais
  automatique, `verifications-production.md` §78, prérequis 78.1) ; (KB-SEA-99) un corps illisible ou absent, un champ mal typé (durée en texte ou hors
  entier) ou un identifiant de séance illisible répondent **400 `BAD_REQUEST`** sans message technique (avant : 500 `INTERNAL_ERROR`), le 403
  restant prioritaire pour un profil sans droit.
  *Source :* `VoletParamedical`, `MesuresDeSeance`, `Poids`, `TensionArterielle`, `Seance.exigerUnVoletParamedical`,
  `RenseignerLeVoletParamedical`, `VoletsParamedicauxJdbc`, `VoletParamedicalControleur`, `ErreursDuVoletParamedicalAdvice`.

- **RG-SEA-070** — **Lectures reprises de la plateforme par la Séance** (slice SE7f, ADR 0100 ; aucune route, aucune migration,
  comportement inchangé). Le planning du patient (jours de dialyse, sommeil, état et dates de la fiche AU CENTRE ; centre fermé = jour
  férié OU fermeture exceptionnelle à la date, additionnés : KB-SEA-95 reproduit), la place du patient à la validation (déplacement
  temporaire du jour, sinon place de la fiche publiée par la Planification ; vide sans salle ou créneau), le forfait courant (imposé, puis
  prise en charge couvrant le jour, effective avant demandée, puis la plus récente portant un forfait), l'historique des séances (jointure
  séances-fiches, filtres, tris, pagination, statuts illisibles exclus et journalisés), le générateur du patient (équipement GMAO du centre,
  hors supprimé), le référentiel des forfaits du centre et le **jour du centre** (fuseau `centers.fuseau_horaire`, défaut de la plateforme)
  sont lus par la Séance elle-même, requêtes **reprises mot pour mot** des fournisseurs retirés de la plateforme. Restent fournis par la
  plateforme : les fiches des patients, les diffusions, le rappel de régularisation et l'effacement des administrations de traitement.
  *Source :* `PlanningDesPatientsJdbc`, `PlaceDuPatientParLaPlanification` (déplacement du jour et place de la fiche par les langages publiés de
  la Planification, LEC-PLN-GMAO, ADR 0179), `ForfaitsDeSeanceJdbc`, `HistoriqueDesSeancesJdbc`, `GenerateursDesPatientsParLaGmao` (fiche du
  générateur par `FichesDesGenerateurs`, gmao.v1, ADR 0179), `ForfaitsDuCentreJdbc`, `JourDuCentreJdbc`, `SaisiesParLeLegacy`.

- **RG-SEA-071** — **Retrait du module legacy `bc-seance` et gardes des tables de la Séance** (slice SE7f, ADR 0100). Le module Maven
  `backend/bc-seance` et ses dix-sept utilisateurs de la plateforme sont retirés ; la **dernière créatinine** de l'évaluation KDIGO est lue
  par le langage publié du Dossier médical (`DerniereCreatininePubliee` : analyses du patient au centre, date de prélèvement décroissante,
  première créatinine non nulle ; **KB-SEA-96 (a) corrigé le 2026-11-10, lot L5a, ADR 0117, à confirmer par les médecins au plus tard le
  2026-12-15** : à date de prélèvement égale, la dernière saisie fait foi (création la plus récente, création inconnue en dernier, puis
  identifiant), au lieu de l'ordre du SGBD ; **(b) clos sans changement** : une créatinine à 0 reste retenue et le calcul de la fonction rénale la
  juge non évaluable (aucun stade faux), le refus du 0 à la saisie étant une décision médicale. Ancien texte : ordre non défini à date
  égale, une créatinine à 0 est retenue) et la **capacité théorique** des centres par le langage publié de la Planification
  (`CapaciteTheoriqueDesCentres` : un générateur de secours pour huit, capacité = postes actifs x séries x patients par poste et par série,
  seuil de proximité 90 %, même résultat). Les tables `seances`, `volet_paramedical` et `volet_medical` ne sont lues ou écrites hors de la
  Séance que par une **liste blanche nominative datée** (`TableDesSeancesTest`, 5 entrées depuis RP2 : le schéma H2, l'état Jasper du
  cahier et trois scripts de données ; l'annulation d'un lot de reprise lit « volet complété » par `SeancesDeLaReprisePubliees`, ADR 0170) ;
  `resultats_analyses` hors du Dossier médical de même (`TableDesAnalysesTest`, 5 entrées depuis RP2) ; les entités JPA sont détectées. **KB-SEA-49 est clos** (le code qui effaçait `facture_id` n'existe plus).
  *Source :* `KdigoGreffeQueryService`, `DerniereCreatinineJdbc`, `DirectionCapaciteQueryService`, `ConsulterLaCapaciteTheorique`,
  `CapaciteTheorique`, `TableDesSeancesTest`, `TableDesAnalysesTest`, `FrontieresDesContextesTest`.

- **RG-SEA-020** — La validation (`ADMIN`, `INFIRMIER`) fait passer la séance de `CREE` à `VALIDEE`, date la validation
  et enregistre la signature de l'infirmier. Une
  séance déjà validée ou signée n'est pas rejetée mais ne change pas de statut ; une séance facturée est refusée. Autre
  statut que `CREE` : « La seance n'est pas en statut CREE ».
- **RG-SEA-049** — À la validation, la séance **mémorise la place où elle a eu lieu** (salle, créneau, générateur) :
  le déplacement temporaire du patient pour ce jour s'il en a un (RG-PLN-096), sinon sa place habituelle ; rien si le
  patient n'est pas placé. Une place déjà mémorisée n'est jamais remplacée, même si la séance est revalidée (ajout de
  consommables) ou si la place ou les jours du patient changent ensuite. Les séances validées avant cette règle n'ont
  pas de place mémorisée (`SeancePlaceJdbcAdapter`, migration V26).
- **RG-SEA-021** — À la validation, les **consommables** utilisés sont sortis du stock automatiquement : chaque ligne
  doit désigner un article existant du centre et **actif**
  (« Article inactif »), la quantité est prélevée sur les lots disponibles par **FEFO** (lot le plus proche de la
  péremption d'abord) ; si le stock disponible est insuffisant,
  la validation est refusée avec la quantité manquante (« Stock insuffisant pour l'article … »). Un **bon de sortie**
  valorisé au PMP est créé (motif SEANCE), de façon atomique avec
  le changement de statut.
- **RG-SEA-022** — Modifier la quantité (positive) d'un consommable déclaré ou le retirer (`ADMIN`, `INFIRMIER`) **met à jour
  la consommation imputée par le Stock** sur la sortie pour séances du jour (le retrait fixe la quantité à zéro) : **aucun
  mouvement de stock, aucune restitution et aucune sélection de lots FEFO n'ont lieu à la déclaration** (KB-SEA-22, voir
  RG-SEA-054) ; le stock physique est décrémenté par la sortie pour séances (RG-STK, ADR 0045) et par la validation
  (RG-SEA-021). Interdit si la séance est facturée. Chaque changement notifie le centre.
- **RG-SEA-025** — **Déclaration d'un consommable sur une séance déjà validée** (`ADMIN`, `INFIRMIER` ;
  `POST /seances/{id}/consommables`) : la quantité est **imputée par le Stock** sur la sortie pour séances du jour (alertes
  `AUCUNE_PIECE`, `DEPASSEMENT_DE_LA_SORTIE`, `ARTICLE_HORS_PIECE`) ; elle s'ajoute à celle déjà déclarée pour l'article
  (cumul, KB-SEA-23) ; **aucun bon de sortie n'est créé ni mis à jour, aucun mouvement ni lot n'est touché** (KB-SEA-22). Le
  bon de sortie « SEANCE » unique de la séance n'est créé qu'à la validation (RG-SEA-021). Article actif exigé, quantité
  d'ajout strictement positive ; autorisé pour une séance `VALIDEE` ou `SIGNEE`, refusé si facturée (« La seance facturee ne
  peut plus etre modifiee ») ou non validée. Reprise dans le module `seance` : RG-SEA-054.
  Les administrations EPO/fer gardent leur propre bon numéroté (poste « ADMINISTRATION »). Chaque saisie notifie le
  médecin (RG-NOT-004).
- **RG-SEA-026** — **Poste infirmier** (écran « Séances » ; le médecin seul n'y a pas accès, il passe par son tableau de
  bord) : un seul écran pour scanner, choisir le patient dans la **file du jour** (séances du
  jour avec leur statut : à valider, validée, signée, facturée, absent) et saisir la séance en quatre étapes —
  constantes (avec le poids sec prescrit), consommables, anémie, remarques. Les constantes s'enregistrent à la sortie de
  chaque champ et les remarques après une courte pause de frappe, sans bouton « Enregistrer » ; une séance facturée est
  en lecture seule. Les consommables les plus sortis du jour dans le centre sont proposés en un toucher (+1 unité,
  RG-SEA-025). Une alerte non bloquante signale un générateur affecté dont l'état n'est pas « en service ». Les
  statistiques et le tableau des séances sont dans l' **historique** (RG-SEA-029), accessible depuis le poste. La
  secrétaire voit la file et scanne mais n'ouvre pas la saisie clinique. L'écran est responsive : une
  colonne (file puis séance) sur mobile, deux colonnes sur tablette, toutes les sections visibles sur PC.
  L'habillage suit la charte de l'application (thème actif, titres Fraunces, cartes vitrées, indicateurs du jour).
  Sur PC, un panneau de contexte rappelle les dernières séances du patient (RG-SEA-028). L'en-tête de la séance ouverte
  propose d'afficher et d'imprimer le **badge du patient avec son QR code** (le même qu'à la liste des patients).
  *Source :*
  `SeanceStationComponent`, `QrScannerComponent`.
- **RG-SEA-046** — **Validation oubliée** : une séance restée `CREE` après son jour n'est pas réalisée (RG-ABS-041) :
  la détection de la nuit compte le patient absent (« à qualifier ») et la perte est valorisée. La régularisation se
  fait **en deux temps** : (1) l' **administrateur** consulte, dans « À régulariser » du poste infirmier, les séances
  `CREE` des **7 derniers jours** (les plus anciennes d'abord, jusqu'à 50) et **déverrouille pour régularisation**
  (`POST /seances/{id}/deverrouiller-regularisation`, `ADMIN` seul) celles dont il est sûr qu'il s'agit d'un oubli ;
  seule une séance d'un **jour passé** restée `CREE` peut l'être (`SEANCE_DEVERROUILLAGE_INVALIDE`), l'action est
  idempotente et notifie les infirmiers (`SEANCE_DEVERROUILLEE`) ; (2) la séance déverrouillée devient visible de
  l' **infirmier** dans son bloc « À régulariser » (il ne voit que celles-là, jamais les autres) et il peut la valider.
  L'infirmier ne valide librement que les séances **du jour** : valider une séance d'un jour passé non déverrouillée est
  refusé par le serveur (`SEANCE_REGULARISATION_NON_DEVERROUILLEE`) ; l'administrateur valide toujours. La secrétaire
  ne voit pas le bloc. Un **rappel** est envoyé chaque jour à **07:00** (`RappelDesRegularisationsPlanifie`, après la
  détection des absences de 02:30, au jour du fuseau de chaque centre) à l'administrateur de chaque centre qui a de
  telles séances **pas encore déverrouillées** : nombre et date de la plus ancienne (notification
  `SEANCES_A_REGULARISER`, `ADMIN` seul ; rien n'est envoyé s'il n'y en a aucune ; **un seul rappel par centre et par
  jour**). Une fois validée, la séance compte comme réalisée et
  l'absence détectée à tort est annulée à la réconciliation suivante (RG-ABS-040, commentaire « Séance réalisée à
  cette date »). Au-delà de 7 jours, la séance reste consultable dans l'historique (RG-SEA-029).
- **RG-SEA-047** — **Navigation entre patients du poste infirmier** : après un scan réussi, le poste se positionne
  automatiquement sur la séance du patient scanné (étape « constantes »). Depuis une séance ouverte de la file du jour,
  les boutons « Patient précédent » / « Patient suivant » ouvrent le voisin dans l'ordre de la file sans repasser par la
  liste (désactivés aux extrémités, rang « n / total » affiché) ; ils sont masqués pour une séance hors file (ex. séance
  à régulariser) ou lorsqu'il n'y a qu'un patient. La saisie en cours est enregistrée avant le changement. *Source :*
  `SeanceStationComponent`, `PatientPagerComponent`.
- **RG-SEA-048** — **Séance hors planning** : le scan qui doit **créer** la séance du jour (aucune séance n'existe
  encore ce jour-là ; une séance existante est simplement renvoyée) vérifie que le patient est **attendu** à la date,
  avec les mêmes critères que la détection des absences (RG-ABS) : la date est un de ses **jours de dialyse**, le centre
  est **ouvert** (ni jour férié ni fermeture exceptionnelle), le patient n'est ni en sommeil, ni admis plus tard, ni
  sorti (transféré, guéri : attendu jusqu'au jour de sortie inclus ; décédé, greffé : jusqu'à la veille ; occasionnel ou
  vacancier : jusqu'à la fin du séjour incluse). Sinon, **rien n'est créé** et le serveur répond 422 avec le motif :
  `SEANCE_HORS_PLANNING_JOUR` (pas un jour de dialyse), `SEANCE_HORS_PLANNING_PATIENT` (patient non attendu) ou
  `SEANCE_CENTRE_FERME` (centre fermé). L' **infirmier** (et l'administrateur) peut alors **confirmer** en renvoyant le
  scan
  avec un motif — `RATTRAPAGE`, `URGENCE` ou `AUTRE` (précision libre obligatoire :
  `SEANCE_DEROGATION_PRECISION_REQUISE` ; motif absent ou inconnu : `SEANCE_DEROGATION_MOTIF_REQUIS`) : la séance est
  créée
  et validée, marquée **« hors planning »** avec son motif (poste infirmier, historique des séances). Un **jour de
  fermeture** n'est franchi que par l'**administrateur** (la confirmation d'un infirmier reste refusée par
  `SEANCE_CENTRE_FERME`). La **secrétaire** ne peut jamais confirmer : son scan hors planning est refusé avec les mêmes
  codes et l'écran lui demande de prévenir l'infirmier. La création manuelle d'une séance (`POST /seances`) n'est pas
  concernée. *Source :* `SeanceDomainService.verifierProgramme`, `ProgrammationSeance`, `SeancePlanningJdbcAdapter`,
  `HorsPlanningConfirmComponent`.
- **RG-SEA-029** — **Historique des séances** (`GET /seances`, tous profils de centre ; écran « Historique des
  séances ») : statistiques du mois (séances prévues, présences, absences, total, répartitions par sexe et par âge,
  détail et export) et **liste paginée** des séances du centre. La recherche, les filtres et le tri sont appliqués **par
  le serveur** sur toute la liste, jamais sur la seule page affichée : période (`from`, `to`, bornes incluses,
  début ≤ fin), statut (un ou plusieurs), texte libre sur le nom, le prénom ou le code du patient (tous les mots
  doivent correspondre), tri sur la date, le patient, le code, le statut ou la création (liste blanche ; toute autre
  colonne retombe sur la date décroissante, avec un départage stable). Réponse `{items, total, page, size}` ; `size`
  est borné à 500 et la liste est toujours limitée au centre demandé. L'écran est en consultation seule : la saisie se
  fait au poste infirmier (RG-SEA-026). *Point d'attention :* l'ancien écran permettait à l'`ADMIN` de modifier la
  **date** et le **forfait** d'une séance (RG-SEA-014 et suivantes) ; ces actions n'ont plus d'écran depuis la refonte
  (les points d'accès subsistent). *Source :* `SeanceDomainService.search`, `SeanceRepositoryAdapter.search`,
  `SeancesHistoriqueComponent`.
- **RG-SEA-027** — **Raccourcis de consommables du centre** : l'`ADMIN` choisit (au plus 12, sans doublon, articles
  actifs du centre, ordre de sélection conservé) les articles proposés en un toucher à l'infirmier
  (`PUT /seances/raccourcis-consommables`) ; la lecture est ouverte à `ADMIN`, `INFIRMIER` et `SECRETAIRE`. Sans
  raccourci configuré, l'écran propose les articles les plus sortis du jour (6 au plus). La liste est propre au centre
  (isolation stricte), mise en cache par centre et purgée à chaque modification. Un article inconnu, d'un autre centre ou **inactif** est refusé en 400 (`BAD_REQUEST`, KB-STK-56 corrigé ; c'était 422 pour l'inactif) ; de même pour l'ajout d'un consommable et pour la **validation de la séance** avec consommations (KB-STK-57). *Source :*
  `RaccourcisDeConsommables`, `ConsulterLesRaccourcis`, `RemplacerLesRaccourcis` (module `seance`, RG-SEA-054).
- **RG-SEA-028** — **Rappel des dernières séances** (`GET /seances/patient/{id}/recentes`, `ADMIN`, `INFIRMIER`,
  `MEDECIN`) : les 3 dernières séances du patient antérieures à la séance ouverte (10 au plus, de la plus récente à
  la plus ancienne) avec leurs constantes — poids avant/après, perte de poids, tension, durée, ultrafiltration.
  Limité au centre de la session. *Source :* `SeanceDomainService.recentByPatient`.
- **RG-SEA-023** — La date d'une sortie de stock rattachée à une séance est immuable
  (`SEANCE_STOCK_EXIT_DATE_IMMUTABLE`).
- **Point d'attention (RG-SEA-024)** — Valider à nouveau une séance déjà validée avec une liste de consommables retraite
  ces consommables (nouvelle sortie) : l'idempotence ne
  porte que sur le statut. L'interface n'expose pas ce cas ; à protéger côté serveur. L'ajout d'un consommable
  après validation passe par RG-SEA-025.

## 7.4 Volets de la séance

- **RG-SEA-030** — **Volet paramédical** (`ADMIN`, `INFIRMIER`, `SECRETAIRE`) : poids avant/après, tension avant/après,
  durée, débit sang, ultrafiltration, anticoagulant, type de
  dialysat, incidents. Les poids saisis respectent RG-TRV-032 (> 0, ≤ 500 kg). Une tension est normalisée au format
  `systolique/diastolique` en **mmHg** ; chaque valeur entre **20 et 400 mmHg**,
  systolique ≥ diastolique. La saisie en **centimètres de mercure** est acceptée et convertie : dès que la systolique
  saisie est ≤ 30, les deux valeurs sont multipliées par 10 (« 12/8 » → `120/80`, « 11,5/7 » → `115/70`) ; la virgule
  ou le point décimal sont tolérés. Enregistrement notifié ; interdit si facturée.
- **RG-SEA-031** — **Volet médical** (`ADMIN`, `MEDECIN`) : prescription, tolérance, examen clinique, résultats
  biologiques, ajustements thérapeutiques, conclusion. Il n'est
  accessible **qu'après validation infirmière** (« n'est accessible qu'apres validation infirmiere ») ; interdit si
  facturée.
- **RG-SEA-032** — **Signature médecin** (`ADMIN`, `MEDECIN`) : uniquement après validation infirmière, une seule fois
  (« deja signee par un medecin ») ; statut `SIGNEE`.
- **RG-SEA-033** — Forfait de la séance : par défaut celui de la prise en charge ; l'administrateur (`ADMIN` seul —
  l'infirmier ne le modifie pas) peut le **surcharger** par un forfait du
  centre actif (« Forfait introuvable pour le centre actif » sinon), avec enregistrement de l'auteur et de
  l'horodatage ; interdit si facturée. Reprise dans le module `seance` : RG-SEA-056.

## 7.5 Consultation, journal, tableau de bord et calendrier

- **RG-SEA-040** — La liste des séances est paginée et peut être filtrée par mois ; accès `ADMIN`, `INFIRMIER`,
  `MEDECIN`, `SECRETAIRE`.
- **RG-SEA-041** — Le **journal du jour** rassemble les patients dialysés à une date, avec leur statut, et le total des
  articles sortis ce jour-là par code article.
- **RG-SEA-042** — **Tableau de bord mensuel** : séances prévues = somme, sur les jours ouvrés **échus** du mois (hors
  fériés et fermetures exceptionnelles ; le futur n'est compté ni en présence ni en absence), des patients programmés ce
  jour de la semaine, hors patients « en sommeil », à partir de leur date d'admission et jusqu'à la libération de leur
  place (transfert, décès, greffe, guérison, fin de séjour : RG-PAT-032) ; **présences = séances réalisées** (statuts
  `VALIDEE`, `SIGNEE`, `FACTUREE`) du mois ; **absences = absences du suivi des absences (RG-ABS) du mois, hors
  annulées**, qu'elles soient déclarées ou détectées par le contrôle quotidien (plus de calcul « prévues − présences »,
  supprimé) ; **effectif du mois** = patients pris en charge sur la période (admis au plus tard le dernier jour du mois,
  place non libérée avant le 1er du mois, hors « en sommeil ») auxquels s'ajoutent ceux qui ont une séance réalisée
  dans le mois, qu'ils aient déjà dialysé ou non ; répartition par sexe (M/F/autre) et par tranche d'âge (0-17, 18-39,
  40-59, 60+, inconnu) de cet effectif (`effectifPatients`) ; détail par jour de la semaine ; exports PDF, XLSX et CSV.
- **RG-SEA-043** — Le détail du tableau de bord se demande pour `presence` (séances réalisées du mois) ou `absence`
  (absences du suivi du mois, hors annulées, avec leur statut) ; toute autre valeur est refusée
  (« kind must be 'presence' or 'absence' »).
- **RG-SEA-051** — **Garde de centre du tableau de bord** : le tableau de bord mensuel, son détail et son export
  (`/seances/dashboard`, `/dashboard/details`, `/dashboard/export`) n'acceptent que le centre de la session ; un autre
  centre est refusé en **403** `ACCESS_DENIED` (RG-TRV-002). Avant, un administrateur lisait le tableau de bord d'un
  autre centre en changeant l'identifiant (KB-ABS-05, corrigé). Le calendrier du centre (`/seances/calendar`) n'a pas
  encore cette garde.
- **RG-SEA-044** — **Calendrier du centre** : jours fériés (libellé) et jours de fermeture exceptionnelle (motif) par
  mois ; ajout et suppression réservés à `ADMIN`, bornés au centre.
  Ces jours bloquent les séances prévues, les absences automatiques et le planning de la semaine. **Point
  d'attention :** l'ajout utilise une instruction `MERGE … KEY` propre à H2 ; elle est à remplacer par une requête
  compatible PostgreSQL (AGENTS.md §10) avant la
  mise en production sur ce moteur, et l'unicité d'un jour férié n'est pas contrôlée.

## 7.6 Impression du cahier de dialyse

- **RG-SEA-045** — **Cahier de dialyse imprimable** (modèle de document `CAHIER_DIALYSE`, `GET
  /api/v1/cahier-dialyse/{patientId}/imprimer`, profils `ADMIN`, `SECRETAIRE`, `MEDECIN`, `INFIRMIER`, patient du centre
  courant uniquement) : **une page par séance validée, signée ou facturée** (jamais une séance seulement créée), de la
  plus récente à la plus ancienne comme à l'écran, éventuellement limitée à une période (`from`, `to` incluses). Chaque
  page reprend **toutes les informations du cahier** : date, statut et forfait de la séance (forfait modifié sur la
  séance, sinon forfait de la prise en charge couvrant la date, sinon dernier forfait connu) ; saisie infirmier (poids
  avant et après, TA avant et après, durée, débit sang, ultrafiltration, type de dialysat, anticoagulant, incidents) ;
  consommables sortis du stock avec quantité, unité, valorisation unitaire (PMP) et totale, et valorisation totale de la
  séance ; saisie médecin (néphropathie initiale, date de mise en dialyse, statuts des hépatites B et C, observation
  globale, conclusion du médecin, prescription en vigueur **à la date de la séance** — poids sec, EPO, fer — et volet
  médical de la séance : prescription, tolérance, examen clinique, résultats biologiques, ajustements, conclusion). Une
  information absente est imprimée « - ». Chaque page porte l'en-tête (logo, société, centre, identité du patient) et
  le pied de page (édition, pagination, centre, pied de page société) du centre. Refus : patient hors du centre
  (`PATIENT_INTROUVABLE`), aucune séance à imprimer (`CAHIER_VIDE`), début après la fin (`CAHIER_PERIODE_INVALIDE`).
- **RG-SEA-045a** — *Cahier de dialyse : reprise sur place PL5 (ADR 0144, 2026-11-12)* : la route est servie par `contexts/seance`
  (`CahierDeDialyseControleur`, cas d'usage `ImprimerLeCahierDeDialyse`) et rendue par Documents (`RenduDeDocument`, type `CAHIER_DIALYSE`) ;
  URL, droits, en-têtes, modèle de document et codes de refus (`CAHIER_VIDE` et `CAHIER_PERIODE_INVALIDE` en 422) **inchangés**. Ordre des
  gardes : centre de la session (403 `ACCESS_DENIED`), période, patient du centre, cahier vide. **KB-PL5-1 CORRIGÉ** : `patientId`,
  `centerId`, `from` ou `to` illisibles = 400 `BAD_REQUEST` (le 403 d'un profil sans droit passe avant), autre verbe HTTP = 405
  `METHOD_NOT_ALLOWED` (avant : 500). **KB-PL5-6e CORRIGÉ** : patient inconnu ou d'un autre centre = 404 `PATIENT_INTROUVABLE`, message « Patient
  introuvable » conservé (avant : 422). **KB-PL5-3 CORRIGÉ** : l'impression est journalisée sous l'action de lecture `CAHIER_DIALYSE_IMPRESSION`
  (« Cahier de dialyse imprimé », entité `patients`). **KB-PL5-4 CORRIGÉ** : un utilisateur dont le seul profil est `SECRETAIRE` n'imprime plus
  le volet médical (hépatites, néphropathie, observation, conclusions, prescription EPO/fer, volet médical de la séance) ; `ADMIN`, `MEDECIN` et
  `INFIRMIER` (seuls ou cumulés avec `SECRETAIRE`) le lisent ; `DIRECTION` et `SUPERADMIN` restent refusés (403 `ROLE_SCOPE`) ; à confirmer
  métiers/DPO avant le 2026-12-15. **KB-PL5-5 CORRIGÉ** : les mouvements `ENTREE` et `AJUSTEMENT` rattachés à la séance sont déduits des
  sorties (quantité et valorisation nettes par article et prix unitaire) ; à confirmer métiers. **KB-PL5-2 CORRIGÉ** : la ligne d'identité du
  patient est imprimée sur deux lignes au besoin. **KB-PL5-6 CORRIGÉ (b, c)** : « Prix : x DA » n'est imprimé que si le forfait a un prix ; poids
  avant et après, ultrafiltration et poids sec s'impriment avec la virgule. **KB-PL5-6 REPRODUIT (a, d)** (dette d'affichage). **Limite** : un modèle
  de document personnalisé par le centre n'a pas le masque du volet médical (question ouverte de l'ADR 0144).
  *Source :* `CahierDeDialyseControleur`, `ImprimerLeCahierDeDialyse`, `CahierRenduParDocuments`, `SessionLegacy`, `AuditActionResolver`,
  `cahier_dialyse.jrxml`.
