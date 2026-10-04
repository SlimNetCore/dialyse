/**
 * Génère la maquette commerciale (HTML, PDF, DOCX) à partir des captures réelles de l'application (docs/client/img)
 * et des chiffres du référentiel des règles de gestion. Les captures sont produites par l'instance de démonstration ;
 * une capture absente est simplement omise. Usage : node build-maquette.cjs (modules : docx, playwright-core).
 */
const fs = require('fs');
const path = require('path');
const D = require('docx');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const REF = path.join(ROOT, 'docs', 'reference', 'regles-de-gestion');
const OUT = path.resolve(__dirname, '..');
const IMG = path.join(OUT, 'img');
const VERSION = process.env.DOC_VERSION || '1.0';

const ruleCount = fs.readdirSync(REF).filter((f) => /^\d\d-.*\.md$/.test(f))
    .reduce((n, f) => n + (fs.readFileSync(path.join(REF, f), 'utf8').match(/^- \*\*[^*]*RG-[A-Z]{3}-\d{3}/gm) || []).length, 0);

const has = (f) => fs.existsSync(path.join(IMG, f + '.png'));

const MODULES = [
    ['Patients & admissions', 'Dossier administratif, assurés, attestations de droits, prises en charge'],
    ['Planification', 'Postes, salles, créneaux, isolement, capacité, propositions classées'],
    ['Séances & cahier de dialyse', 'Validation infirmière, signature médicale, consommables, volets'],
    ['Absences des patients', 'Détection automatique, qualification, valorisation du manque à gagner'],
    ['Personnel soignant', 'Roulement, présence, absences, remplaçants, charge de travail'],
    ['Dossier médical', 'Antécédents, allergies, sérologies, examens, ordonnances, prescriptions'],
    ['Anémie & greffe', 'Observance EPO/fer, cibles KDIGO, bilan pré-greffe, donneurs vivants'],
    ['Stock & pharmacie', 'Lots, FEFO, PMP, inventaire, péremptions, groupes d\'articles'],
    ['GMAO', 'Parc, interventions, coûts, indisponibilité, aide à la réforme'],
    ['Facturation & règlements', 'Simulation, TVA, numérotation, soldes, trop-perçus'],
    ['Comptabilité', 'Écritures équilibrées, clôture de période, export'],
    ['Direction multi-centres', 'Tableaux de bord anonymes, alertes, capacité, rapports mensuels'],
];

const SLIDES = [
    {kind: 'cover'},
    {
        kind: 'grid',
        eyebrow: 'Couverture fonctionnelle',
        title: 'Toute la gestion du centre, du premier accueil à la direction de société.',
        lead: 'Douze domaines métier dans un seul outil, sans ressaisie : ce qui est saisi une fois nourrit la planification, la facturation, la comptabilité et le pilotage.'
    },
    {
        kind: 'pillars',
        eyebrow: 'Au-delà de l\'information',
        title: 'Un système qui surveille, propose et vous aide à décider.',
        pillars: [
            ['Il surveille', ['Séances prévues non réalisées détectées chaque nuit', 'Observance des traitements EPO / fer contrôlée chaque jour', 'Sous-effectif d\'infirmiers signalé 14 jours à l\'avance', 'Péremptions, ruptures et seuils de stock', 'Indisponibilité des générateurs, interventions et plans en retard', 'Licences et sécurité des accès']],
            ['Il propose', ['Les meilleures places de dialyse, classées de 0 à 100', 'Les remplaçants d\'infirmiers, classés et expliqués', 'La réforme d\'un équipement quand la maintenance dépasse 60 % du prix d\'achat', 'La simulation de facturation avant validation', 'Les cibles cliniques KDIGO face à chaque résultat']],
            ['Il protège', ['Un placement incohérent est refusé avant d\'être enregistré', 'Un patient devenu à risque infectieux est replacé en isolement', 'Le stock est gelé pendant un inventaire', 'Les séances facturées ne peuvent plus être modifiées', 'La direction ne voit jamais un patient : effectifs < 5 masqués']],
        ]
    },
    {
        kind: 'feature',
        eyebrow: 'Patients & prises en charge',
        title: 'Un dossier administratif complet, un patient facturable ou non en un coup d\'œil.',
        lead: 'Assuré ou ayant droit, attestation de droits, prise en charge validée : le système sait si une séance peut être facturée avant qu\'elle ait lieu.',
        bullets: ['Numéro d\'assurance unique par centre, ayants droit et historique d\'affectation', 'Un patient non vacancier ne peut être pris en charge sans attestation valide', 'Statut « non facturable » visible dès la liste', 'Placement (salle, créneau, générateur, jours) contrôlé par la planification'],
        shots: ['patients-liste', 'patient-fiche']
    },
    {
        kind: 'feature',
        eyebrow: 'Planification intelligente',
        title: 'Qui dialyse où, quel jour, sur quel générateur — sans conflit.',
        lead: 'Le planning réel de la semaine détecte les conflits ; l\'aide au placement propose les meilleures places et explique son classement.',
        bullets: ['Un générateur sert un seul patient par jour et par créneau', 'Patients à risque infectieux : salle d\'isolement uniquement, jamais mélangés', 'Générateur double, salle surchargée, jour fermé : détectés et signalés', 'Capacité théorique du centre calculée (secours 1 pour 8, séries, patients par poste)'],
        shots: ['planning-semaine', 'planning-salles']
    },
    {
        kind: 'feature',
        eyebrow: 'Absences des patients',
        title: 'Chaque absence est détectée, qualifiée et valorisée.',
        lead: 'Le contrôle nocturne repère les séances prévues non réalisées. Le manque à gagner est calculé au forfait de la prise en charge, avec la TVA du jour.',
        bullets: ['Motifs, justification, rattrapage ou annulation avec commentaire', 'Alerte quand une absence attend sa qualification depuis plus de 3 jours', 'Clôture automatique quand la période est facturée', 'Vue direction : taux d\'absentéisme et part du chiffre d\'affaires'],
        shots: ['absences-patients']
    },
    {
        kind: 'feature',
        eyebrow: 'Séances & cahier de dialyse',
        title: 'Des séances saisies en quelques gestes, tracées jusqu\'à la facture.',
        lead: 'Création par scan du QR du patient, validation infirmière, signature médicale, consommables sortis du stock au plus proche de la péremption.',
        bullets: ['Volet paramédical : poids, tension, débit, ultrafiltration, incidents', 'Volet médical accessible seulement après validation infirmière', 'Consommables sortis automatiquement (FEFO) et valorisés au PMP', 'Avertissement si le générateur du patient est indisponible'],
        shots: ['cahier-dialyse', 'seances']
    },
    {
        kind: 'feature',
        eyebrow: 'Personnel soignant',
        title: 'La bonne équipe, au bon créneau, avant que le problème n\'arrive.',
        lead: 'Le ratio de sécurité fixe l\'effectif requis ; chaque case du planning est couverte ou en sous-effectif.',
        bullets: ['Roulement, absences, remplaçants et salle d\'isolement réservée aux infirmiers habilités', 'Remplaçants proposés et classés (salle connue, charge, jour libre)', 'Notification quotidienne de l\'administration en cas de sous-effectif prévisible', 'Chaque infirmier consulte « son planning » et déclare ses absences'],
        shots: ['infirmiers-presence', 'infirmiers-charge']
    },
    {
        kind: 'feature',
        eyebrow: 'Dossier médical',
        title: 'Un dossier clinique structuré, codé et partout accessible.',
        lead: 'Antécédents CIM-10, allergies, sérologies, examens et biologie LOINC, ordonnances signées : chaque donnée est contrôlée à la saisie.',
        bullets: ['Sérologie positive : conduite à tenir obligatoire et replacement automatique en isolement', 'Ordonnance signée = contenu verrouillé', 'Machine à états des demandes d\'examen', 'Export interopérable FHIR R4'],
        shots: ['dossier-medical-synthese', 'dossier-medical-serologies']
    },
    {
        kind: 'feature',
        eyebrow: 'Anémie & greffe',
        title: 'Le traitement de l\'anémie sans faille, la greffe préparée dès la dialyse.',
        lead: 'Chaque résultat est comparé aux cibles KDIGO ; l\'observance des traitements est vérifiée tous les jours ; le bilan pré-greffe suit un parcours encadré.',
        bullets: ['Cibles KDIGO : hémoglobine, ferritine, CST, Kt/V, phosphore, calcium, PTH, albumine', 'Retard d\'observance constaté ou rappel d\'échéance, alerte au médecin', 'Risque immunologique (PRA), DFG CKD-EPI, alertes sérologiques', 'Checklist de 23 étapes, décisions de RCP, donneurs vivants, dossier PDF'],
        shots: ['dossier-medical-anemie', 'dossier-medical-greffe']
    },
    {
        kind: 'feature', eyebrow: 'Stock & pharmacie', title: 'Du lot au patient, de la réception à l\'inventaire.',
        lead: 'Prix moyen pondéré recalculé en cascade, sorties au plus proche de la péremption, inventaire qui gèle les mouvements.',
        bullets: ['Traçabilité complète d\'un lot : fournisseur, séances, patients', 'Alertes de péremption, rupture et seuil', 'Inventaire avec feuille de comptage Excel et justification de chaque écart', 'Kits de consommables valorisés pour la direction'],
        shots: ['stock-dashboard', 'stock-inventaires']
    },
    {
        kind: 'feature',
        eyebrow: 'GMAO — aide à la décision',
        title: 'Savoir quand réparer, quand réformer, et ce que la panne vous coûte.',
        lead: 'Coût de possession, indisponibilité et ratio de maintenance par équipement : le système recommande l\'étude de la réforme.',
        bullets: ['Interventions préventives et curatives, pièces jointes, coûts par ligne', 'Valorisation automatique du temps de l\'intervenant', 'Réforme recommandée au-delà de 60 % du prix d\'acquisition', 'Un patient affecté à un générateur en panne est signalé à l\'équipe et à la direction'],
        shots: ['gmao-fiche-equipement', 'gmao-dashboard']
    },
    {
        kind: 'feature',
        eyebrow: 'Facturation, règlements, comptabilité',
        title: 'De la séance validée à l\'écriture comptable, sans ressaisie.',
        lead: 'La simulation montre chaque facture avant validation ; les règlements et les écritures suivent automatiquement.',
        bullets: ['Numérotation configurable, TVA datée, regroupement par forfait', 'Séances exclues ou forfait ajusté avant validation', 'Soldes, restes à payer et trop-perçus, export Excel / PDF', 'Écritures de ventes et de trésorerie équilibrées, clôture de période'],
        shots: ['facturation', 'reglement']
    },
    {
        kind: 'feature',
        eyebrow: 'Direction de société',
        title: 'Tous les centres sur un écran, à jour à la seconde, sans jamais voir un patient.',
        lead: 'Indicateurs consolidés par centre, comparaison à la période précédente, alertes et capacité théorique — anonymes par construction.',
        bullets: ['Finances, clinique KDIGO, stock, GMAO, absences, capacité', 'Alertes critiques et avertissements, historique des alertes', 'Instantanés mensuels figés et rapports PDF', 'Mise à jour en temps réel'],
        shots: ['direction-dashboard', 'direction-capacite']
    },
    {
        kind: 'feature', eyebrow: 'Sécurité & conformité', title: 'Chaque accès contrôlé, chaque action tracée.',
        lead: 'Cloisonnement strict par centre, double authentification, journal d\'audit, licences signées.',
        bullets: ['Jetons en cookies HttpOnly, rotation et détection de réutilisation', 'Double authentification TOTP avec codes de secours', 'Journal d\'audit « qui a fait quoi », consultations du dossier médical comprises', 'Rôles et périmètres restreints (médecin, infirmier, direction, propriétaire)'],
        shots: ['admin-audit', 'organisation-licences']
    },
    {
        kind: 'feature',
        eyebrow: 'Multi-centres, multilingue, mobile',
        title: 'Sur tous vos sites, dans votre langue, sur tous vos écrans.',
        lead: 'Français, arabe (de droite à gauche), kabyle et anglais ; interface responsive du téléphone au grand écran.',
        bullets: ['Un centre = un cloisonnement : aucune donnée ne traverse les centres', 'Sociétés, centres et comptes d\'encadrement administrés par l\'éditeur', 'Mode clair / sombre, accessibilité tactile'],
        shots: ['mobile-dashboard', 'mobile-planning']
    },
    {
        kind: 'feature',
        eyebrow: 'Mise en route',
        title: 'Opérationnel simplement : reprise de vos données et documents à votre image.',
        lead: 'Reprise d\'un logiciel existant par lots rejouables et annulables ; modèles de documents personnalisables en toute sécurité.',
        bullets: ['Import par fichiers avec vérification à blanc et compte rendu ligne à ligne', 'Rapprochement au lieu de doublons, reprise annulable tant qu\'elle n\'a pas servi', 'Onze documents imprimables avec logo, en-tête et pied de page de votre société', 'Référentiels importables (forfaits, salles, caisses, transporteurs…)'],
        shots: ['admin-reprise', 'modeles-document']
    },
    {kind: 'numbers'},
    {kind: 'closing'},
];

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function shotsHtml(shots) {
    const list = (shots || []).filter(has);
    const mobile = list.length && list.every((s) => s.startsWith('mobile'));
    return list.length ? `<div class="shots n${list.length}${mobile ? ' mobile' : ''}">${list.map((s, i) => `<div class="frame f${i + 1}"><div class="bar"><i></i><i></i><i></i></div><img src="img/${s}.png" alt="${esc(s)}"></div>`).join('')}</div>` : '';
}

function slideHtml(s, i) {
    if (s.kind === 'cover') {
        return `<section class="slide dark cover"><div class="brand">HemoDialyse</div><h1>Toute la gestion de votre centre d'hémodialyse.<br><em>Et un système qui surveille, propose et aide à décider.</em></h1><p class="lead">Du premier accueil du patient au tableau de bord de la direction : une seule plateforme, des règles de gestion appliquées sans exception.</p><div class="meta">Plaquette commerciale · version ${VERSION}</div></section>`;
    }
    if (s.kind === 'grid') {
        return `<section class="slide"><div class="eyebrow">${esc(s.eyebrow)}</div><h2>${esc(s.title)}</h2><p class="lead">${esc(s.lead)}</p><div class="grid">${MODULES.map(([t, d]) => `<div class="tile"><b>${esc(t)}</b><span>${esc(d)}</span></div>`).join('')}</div></section>`;
    }
    if (s.kind === 'pillars') {
        return `<section class="slide soft"><div class="eyebrow">${esc(s.eyebrow)}</div><h2>${esc(s.title)}</h2><div class="pillars">${s.pillars.map(([t, l]) => `<div class="pillar"><h3>${esc(t)}</h3><ul>${l.map((x) => `<li>${esc(x)}</li>`).join('')}</ul></div>`).join('')}</div></section>`;
    }
    if (s.kind === 'numbers') {
        const n = [[ruleCount, 'règles de gestion documentées'], ['15', 'domaines métier'], ['4', 'langues : fr, ar, kab, en'], ['8', 'surveillances automatiques planifiées'], ['11', 'documents imprimables personnalisables'], ['5', 'bornes de sécurité : MFA, audit, licences, cloisonnement, anonymat']];
        return `<section class="slide dark"><div class="eyebrow">En chiffres</div><h2>Une couverture que l'on peut vérifier, règle par règle.</h2><div class="numbers">${n.map(([v, l]) => `<div><b>${v}</b><span>${esc(l)}</span></div>`).join('')}</div><p class="lead">Chaque règle est identifiée, sourcée et contrôlée automatiquement : le référentiel est mis à jour à chaque évolution.</p></section>`;
    }
    if (s.kind === 'closing') {
        return `<section class="slide dark cover"><h2 style="margin:auto">Voyons ensemble ce que HemoDialyse peut faire pour votre centre.</h2><div class="meta" style="margin:0 auto">Démonstration sur vos propres cas d'usage — reprise de vos données comprise.</div></section>`;
    }
    return `<section class="slide"><div class="eyebrow">${esc(s.eyebrow)}</div><h2>${esc(s.title)}</h2><p class="lead">${esc(s.lead)}</p><div class="body"><ul class="points">${s.bullets.map((b) => `<li>${esc(b)}</li>`).join('')}</ul>${shotsHtml(s.shots)}</div></section>`;
}

const CSS = `:root{--teal:#14766e;--dark:#0b4d47;--blue:#2b6f9e;--ink:#10222b;--muted:#5a6a74;--soft:#eef5f6;--line:#d7e3e8;--gold:#f2b544}
*{box-sizing:border-box}html{-webkit-print-color-adjust:exact;print-color-adjust:exact}body{margin:0;font-family:"Segoe UI",Inter,Arial,sans-serif;color:var(--ink);background:#dfe7ea}
.slide{width:1280px;height:720px;padding:44px 56px;margin:0 auto 12px;background:#fff;position:relative;overflow:hidden;display:flex;flex-direction:column;page-break-after:always;break-after:page}
.slide.dark{background:linear-gradient(140deg,var(--dark),var(--teal) 60%,var(--blue));color:#fff}.slide.soft{background:var(--soft)}
.eyebrow{text-transform:uppercase;letter-spacing:.18em;font-size:12px;font-weight:700;color:var(--teal);margin-bottom:8px}.dark .eyebrow{color:var(--gold)}
h1,h2,h3{font-family:Georgia,"Palatino Linotype",serif;margin:0;line-height:1.15}h2{font-size:32px;margin-bottom:10px;max-width:1100px}
.lead{font-size:16px;color:var(--muted);max-width:980px;margin:0 0 14px}.dark .lead{color:rgba(255,255,255,.9)}
.cover{justify-content:center}.brand{font-family:Georgia,serif;font-size:26px;font-weight:700;margin-bottom:34px}.cover h1{font-size:46px;max-width:1080px;margin-bottom:20px}.cover h1 em{font-style:normal;color:var(--gold);font-size:34px;display:block;margin-top:14px}.cover .lead{font-size:20px}.meta{margin-top:30px;opacity:.8;font-size:14px}
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:6px}.tile{background:var(--soft);border:1px solid var(--line);border-radius:10px;padding:14px;min-height:110px}.tile b{display:block;color:var(--dark);font-size:15px;margin-bottom:5px}.tile span{font-size:12.5px;color:var(--muted);line-height:1.4}
.pillars{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:10px}.pillar{background:#fff;border:1px solid var(--line);border-top:5px solid var(--teal);border-radius:10px;padding:18px}.pillar h3{color:var(--teal);font-size:24px;margin-bottom:10px}.pillar li{font-size:14px;line-height:1.4;margin:0 0 8px}.pillar ul{margin:0;padding-left:18px}
.body{display:grid;grid-template-columns:330px 1fr;gap:26px;flex:1;min-height:0}.points{margin:0;padding-left:20px;font-size:15px;line-height:1.45}.points li{margin-bottom:12px}
.shots{position:relative;min-height:0;height:470px}.frame{position:absolute;border:1px solid var(--line);border-radius:8px;overflow:hidden;box-shadow:0 10px 30px rgba(16,34,43,.22);background:#fff}
.frame.f1{top:0;right:0;width:720px}.shots.n1 .frame.f1{width:790px}.frame.f2{bottom:0;left:6px;width:430px}
.bar{background:#e8eef1;height:16px;display:flex;gap:5px;align-items:center;padding-left:8px}.bar i{width:7px;height:7px;border-radius:50%;background:#b8c6cc}.frame img{display:block;width:100%}
.shots.mobile .frame{width:228px!important;top:0;bottom:auto}.shots.mobile .frame.f1{right:auto;left:130px}.shots.mobile .frame.f2{left:400px}.shots.mobile .frame img{max-height:462px;object-fit:cover;object-position:top}.numbers{display:grid;grid-template-columns:repeat(3,1fr);gap:22px;margin:14px 0}.numbers div{background:rgba(255,255,255,.12);border-radius:12px;padding:20px}.numbers b{font-family:Georgia,serif;font-size:54px;color:var(--gold);display:block}.numbers span{font-size:15px}
@page{size:1280px 720px;margin:0}@media print{body{background:#fff}.slide{margin:0}}`;

function html() {
    return `<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><title>HemoDialyse — Plaquette commerciale</title><style>${CSS}</style></head><body>${SLIDES.map(slideHtml).join('\n')}</body></html>`;
}

// ───────────────────────── DOCX (une page paysage par diapositive) ─────────────────────────
function pngSize(buf) {
    return [buf.readUInt32BE(16), buf.readUInt32BE(20)];
}

function docx() {
    const W = 15000; // largeur utile (A4 paysage, marges 0,8")
    const children = [];
    const para = (text, o = {}) => new D.Paragraph({
        spacing: {after: o.after ?? 100},
        alignment: o.align,
        children: [new D.TextRun({text, size: o.size || 22, bold: o.bold, color: o.color, font: o.font})]
    });
    SLIDES.forEach((s, i) => {
        if (i) children.push(new D.Paragraph({children: [new D.PageBreak()]}));
        if (s.kind === 'cover') {
            children.push(para('HemoDialyse', {size: 56, bold: true, color: '0B4D47', font: 'Georgia', after: 300}),
                para('Toute la gestion de votre centre d\'hémodialyse.', {
                    size: 52,
                    bold: true,
                    font: 'Georgia',
                    color: '0B4D47'
                }),
                para('Et un système qui surveille, propose et aide à décider.', {
                    size: 40,
                    color: 'B87A00',
                    font: 'Georgia',
                    after: 300
                }),
                para('Du premier accueil du patient au tableau de bord de la direction : une seule plateforme, des règles de gestion appliquées sans exception.', {
                    size: 26,
                    color: '5A6A74'
                }),
                para(`Plaquette commerciale · version ${VERSION}`, {size: 20, color: '5A6A74'}));
        } else if (s.kind === 'grid') {
            children.push(para(s.eyebrow.toUpperCase(), {
                size: 18,
                bold: true,
                color: '14766E'
            }), para(s.title, {size: 40, bold: true, font: 'Georgia', color: '0B4D47'}), para(s.lead, {
                size: 24,
                color: '5A6A74'
            }));
            const b = {style: D.BorderStyle.SINGLE, size: 4, color: 'D7E3EA'};
            const rows = [];
            for (let r = 0; r < MODULES.length; r += 3) {
                rows.push(new D.TableRow({
                    children: MODULES.slice(r, r + 3).map(([t, d]) => new D.TableCell({
                        borders: {
                            top: b,
                            bottom: b,
                            left: b,
                            right: b
                        },
                        width: {size: 5000, type: D.WidthType.DXA},
                        margins: {top: 100, bottom: 100, left: 140, right: 140},
                        shading: {fill: 'EEF5F6', type: D.ShadingType.CLEAR, color: 'auto'},
                        children: [new D.Paragraph({
                            children: [new D.TextRun({
                                text: t,
                                bold: true,
                                size: 22,
                                color: '0B4D47'
                            })]
                        }), new D.Paragraph({children: [new D.TextRun({text: d, size: 18, color: '5A6A74'})]})]
                    }))
                }));
            }
            children.push(new D.Table({
                width: {size: W, type: D.WidthType.DXA},
                columnWidths: [5000, 5000, 5000],
                rows
            }));
        } else if (s.kind === 'pillars') {
            children.push(para(s.eyebrow.toUpperCase(), {
                size: 18,
                bold: true,
                color: '14766E'
            }), para(s.title, {size: 40, bold: true, font: 'Georgia', color: '0B4D47'}));
            for (const [t, l] of s.pillars) {
                children.push(para(t, {size: 30, bold: true, font: 'Georgia', color: '14766E', after: 40}));
                l.forEach((x) => children.push(new D.Paragraph({
                    numbering: {reference: 'b', level: 0},
                    spacing: {after: 30},
                    children: [new D.TextRun({text: x, size: 21})]
                })));
            }
        } else if (s.kind === 'numbers') {
            children.push(para('EN CHIFFRES', {
                size: 18,
                bold: true,
                color: '14766E'
            }), para('Une couverture que l\'on peut vérifier, règle par règle.', {
                size: 40,
                bold: true,
                font: 'Georgia',
                color: '0B4D47'
            }));
            [[ruleCount, 'règles de gestion documentées'], ['15', 'domaines métier'], ['4', 'langues : français, arabe, kabyle, anglais'], ['8', 'surveillances automatiques planifiées'], ['11', 'documents imprimables personnalisables'], ['5', 'bornes de sécurité : MFA, audit, licences, cloisonnement, anonymat']]
                .forEach(([v, l]) => children.push(new D.Paragraph({
                    spacing: {after: 80},
                    children: [new D.TextRun({
                        text: String(v) + '  ',
                        size: 44,
                        bold: true,
                        color: 'B87A00',
                        font: 'Georgia'
                    }), new D.TextRun({text: l, size: 24})]
                })));
        } else if (s.kind === 'closing') {
            children.push(para('Voyons ensemble ce que HemoDialyse peut faire pour votre centre.', {
                size: 48,
                bold: true,
                font: 'Georgia',
                color: '0B4D47',
                after: 300
            }), para('Démonstration sur vos propres cas d\'usage — reprise de vos données comprise.', {
                size: 26,
                color: '5A6A74'
            }));
        } else {
            children.push(para(s.eyebrow.toUpperCase(), {
                size: 18,
                bold: true,
                color: '14766E',
                after: 40
            }), para(s.title, {size: 38, bold: true, font: 'Georgia', color: '0B4D47'}), para(s.lead, {
                size: 22,
                color: '5A6A74'
            }));
            s.bullets.forEach((x) => children.push(new D.Paragraph({
                numbering: {reference: 'b', level: 0},
                spacing: {after: 40},
                children: [new D.TextRun({text: x, size: 21})]
            })));
            const list = (s.shots || []).filter(has);
            const mw = list.length > 1 ? 480 : 700;
            const cells = list.map((f) => {
                const buf = fs.readFileSync(path.join(IMG, f + '.png'));
                const [w, h] = pngSize(buf);
                const width = mw, height = Math.min(Math.round((mw * h) / w), 330);
                return new D.TableCell({
                    borders: {
                        top: {style: D.BorderStyle.NONE},
                        bottom: {style: D.BorderStyle.NONE},
                        left: {style: D.BorderStyle.NONE},
                        right: {style: D.BorderStyle.NONE}
                    },
                    width: {size: Math.floor(W / list.length), type: D.WidthType.DXA},
                    children: [new D.Paragraph({
                        alignment: D.AlignmentType.CENTER,
                        children: [new D.ImageRun({
                            type: 'png',
                            data: buf,
                            transformation: {width, height: Math.round((mw * h) / w) > 330 ? 330 : height},
                            altText: {title: f, description: f, name: f}
                        })]
                    })]
                });
            });
            if (cells.length) children.push(new D.Table({
                width: {size: W, type: D.WidthType.DXA},
                columnWidths: list.map(() => Math.floor(W / list.length)),
                rows: [new D.TableRow({children: cells})]
            }));
        }
    });
    return new D.Document({
        creator: 'HemoDialyse', title: 'HemoDialyse — Plaquette commerciale',
        numbering: {
            config: [{
                reference: 'b',
                levels: [{
                    level: 0,
                    format: D.LevelFormat.BULLET,
                    text: '•',
                    alignment: D.AlignmentType.LEFT,
                    style: {paragraph: {indent: {left: 360, hanging: 240}}}
                }]
            }]
        },
        styles: {default: {document: {run: {font: 'Calibri'}}}},
        sections: [{
            properties: {
                page: {
                    size: {width: 11906, height: 16838, orientation: D.PageOrientation.LANDSCAPE},
                    margin: {top: 800, right: 800, bottom: 800, left: 800}
                }
            }, children
        }],
    });
}

(async () => {
    fs.writeFileSync(path.join(OUT, 'maquette-commerciale.html'), html());
    fs.writeFileSync(path.join(OUT, 'maquette-commerciale.docx'), await D.Packer.toBuffer(docx()));
    const {chromium} = require('playwright-core');
    const browser = await chromium.launch();
    const page = await browser.newPage();
    await page.goto('file:///' + path.join(OUT, 'maquette-commerciale.html').replace(/\\/g, '/'));
    await page.waitForTimeout(500);
    await page.pdf({
        path: path.join(OUT, 'maquette-commerciale.pdf'),
        width: '1280px',
        height: '720px',
        printBackground: true,
        margin: {top: 0, right: 0, bottom: 0, left: 0}
    });
    await browser.close();
    console.log(`Maquette générée : ${SLIDES.length} diapositives, ${ruleCount} règles`);
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
