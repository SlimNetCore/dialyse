/**
 * Génère la documentation fonctionnelle (HTML, PDF, DOCX) à partir du référentiel des règles de gestion
 * (docs/reference/regles-de-gestion). Ne jamais corriger les sorties à la main : corriger le référentiel puis relancer.
 *
 * Usage : node build-documentation-fonctionnelle.cjs   (modules requis : marked, docx, playwright-core ;
 * NODE_PATH peut pointer vers un dossier node_modules les contenant — voir README.md de ce dossier).
 */
const fs = require('fs');
const path = require('path');
const {marked} = require('marked');
const D = require('docx');

const ROOT = path.resolve(__dirname, '..', '..', '..');
const REF = path.join(ROOT, 'docs', 'reference', 'regles-de-gestion');
const OUT = path.resolve(__dirname, '..');
const IMG = path.join(OUT, 'img');
const VERSION = process.env.DOC_VERSION || '1.0';
const DATE = process.env.DOC_DATE || new Date().toISOString().slice(0, 10);
const TITLE = 'HemoDialyse — Documentation fonctionnelle et règles de gestion';

// Captures illustrant chaque partie (utilisées seulement si le fichier existe dans docs/client/img).
const SCREENS = {
    '03': [['organisation-societes.png', 'Administration des sociétés et de leurs centres']],
    '04': [['patients-liste.png', 'Liste des patients'], ['patient-fiche.png', 'Fiche patient']],
    '05': [['planning-semaine.png', 'Planning de la semaine'], ['planning-salles.png', 'Salles, générateurs et capacité']],
    '06': [['absences-patients.png', 'Suivi des absences des patients']],
    '07': [['cahier-dialyse.png', 'Cahier de dialyse']],
    '08': [['stock-dashboard.png', 'Tableau de bord du stock']],
    '09': [['gmao-fiche-equipement.png', 'Fiche équipement et aide à la réforme']],
    '10': [['infirmiers-presence.png', 'Présence des infirmiers et remplaçants']],
    '11': [['dossier-medical-anemie.png', 'Suivi de l\'anémie et cibles KDIGO']],
    '12': [['facturation.png', 'Facturation mensuelle']],
    '13': [['direction-dashboard.png', 'Tableau de bord de la direction'], ['direction-capacite.png', 'Capacité théorique par centre']],
};

const PROFILS = [
    ['ADMIN', 'Administrateur du centre', 'Paramétrage, utilisateurs, séances, facturation, GMAO, comptabilité, planification'],
    ['MEDECIN', 'Médecin', 'Dossier médical, prescriptions, validation et signature, cibles cliniques, greffe'],
    ['INFIRMIER', 'Infirmier', 'Séances, consommables, administrations, « mon planning », absences'],
    ['SECRETAIRE', 'Secrétaire', 'Accueil, patients, PEC, règlements, planning, absences'],
    ['PHARMACIEN', 'Pharmacien / magasinier', 'Stock : bons, lots, inventaire'],
    ['DIRECTION', 'Direction de société', 'Tableaux de bord consolidés et anonymes, instantanés, rapports'],
    ['SUPERADMIN', 'Propriétaire de la plateforme', 'Sociétés, centres, comptes d\'encadrement, licences, journal d\'audit'],
];

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

// ───────────────────────── Lecture du référentiel ─────────────────────────
function loadChapters() {
    const files = fs.readdirSync(REF).filter((f) => /^\d\d-.*\.md$/.test(f)).sort();
    return files.map((f) => {
        const raw = fs.readFileSync(path.join(REF, f), 'utf8').replace(/\r\n/g, '\n');
        const lines = raw.split('\n');
        const h1 = lines.find((l) => l.startsWith('# '));
        const m = h1.match(/^#\s+(\d\d)\s+—\s+(.*)$/);
        const body = lines.slice(lines.indexOf(h1) + 1).join('\n');
        return {num: m[1], key: f.replace('.md', ''), title: m[2].trim(), body};
    });
}

/** Découpe un corps Markdown en blocs : {type:'md', text} ou {type:'rule', id, attention, text}. */
function blocks(body) {
    const out = [];
    let pending = [];
    const flush = () => {
        const t = pending.join('\n').trim();
        if (t) out.push({type: 'md', text: t});
        pending = [];
    };
    const lines = body.split('\n');
    for (let i = 0; i < lines.length; i++) {
        const m = lines[i].match(/^- \*\*(.+?)\*\*\s*—?\s*(.*)$/);
        if (m && /RG-[A-Z]{3}-\d{3}/.test(m[1])) {
            flush();
            let text = m[2];
            while (i + 1 < lines.length && /^ {2,}\S/.test(lines[i + 1])) {
                i++;
                text += '\n' + lines[i].replace(/^ {2}/, '');
            }
            const id = m[1].match(/RG-[A-Z]{3}-\d{3}[a-z]?/)[0];
            out.push({type: 'rule', id, attention: /Point d'attention/.test(m[1] + ' ' + text), text: text.trim()});
        } else {
            pending.push(lines[i]);
        }
    }
    flush();
    return out;
}

const slug = (s) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

// ───────────────────────── HTML ─────────────────────────
function imgFigures(num) {
    return (SCREENS[num] || []).filter(([f]) => fs.existsSync(path.join(IMG, f))).map(([f, cap]) =>
        `<figure><img src="img/${f}" alt="${esc(cap)}"><figcaption>${esc(cap)}</figcaption></figure>`).join('\n');
}

function renderHtml(chapters, stats, attentions) {
    const toc = [];
    let main = '';
    for (const ch of chapters) {
        const id = 'p' + ch.num;
        toc.push(`<li><a href="#${id}">${parseInt(ch.num, 10)}. ${esc(ch.title)}</a></li>`);
        main += `<section class="part" id="${id}"><h1><span class="n">Partie ${parseInt(ch.num, 10)}</span>${esc(ch.title)}</h1>\n`;
        main += imgFigures(ch.num);
        for (const b of blocks(ch.body)) {
            if (b.type === 'md') main += marked.parse(b.text);
            else {
                main += `<div class="rule${b.attention ? ' attention' : ''}" id="${b.id}"><div class="rid">${b.id}${b.attention ? '<small>point d\'attention</small>' : ''}</div><div class="rtxt">${marked.parse(b.text)}</div></div>\n`;
            }
        }
        main += '</section>\n';
    }
    const att = attentions.map((a) => `<li><a href="#${a.id}"><b>${a.id}</b></a> — ${esc(a.text.replace(/[*`]/g, '').slice(0, 220))}…</li>`).join('\n');
    const profils = PROFILS.map(([c, n, d]) => `<tr><td><code>${c}</code></td><td>${n}</td><td>${d}</td></tr>`).join('');
    return `<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"><title>${TITLE}</title>
<style>
:root{--teal:#14766e;--dark:#0b4d47;--ink:#10222b;--muted:#5a6a74;--soft:#eef5f6;--line:#d7e3e8;--gold:#f2b544}
*{box-sizing:border-box}body{font-family:"Segoe UI",Inter,Arial,sans-serif;color:var(--ink);line-height:1.5;font-size:10.5pt;margin:0}
.cover{height:100vh;padding:60px;background:linear-gradient(140deg,var(--dark),var(--teal) 60%,#2b6f9e);color:#fff;display:flex;flex-direction:column;justify-content:center;page-break-after:always}
.cover h1{font-family:Georgia,serif;font-size:40pt;line-height:1.1;margin:0 0 20px}.cover h1 em{font-style:normal;color:var(--gold)}
.cover p{font-size:14pt;max-width:640px;opacity:.92}.cover .meta{margin-top:40px;font-size:11pt;opacity:.8}
.page{padding:0 8px}h1,h2,h3{font-family:Georgia,serif;color:var(--dark)}
h1{font-size:22pt;border-bottom:3px solid var(--teal);padding-bottom:8px;margin:0 0 14px}h1 .n{display:block;font-family:"Segoe UI",sans-serif;font-size:9pt;letter-spacing:.18em;text-transform:uppercase;color:var(--teal);margin-bottom:4px}
h2{font-size:14pt;margin:22px 0 8px;color:var(--teal);break-after:avoid}.part{page-break-before:always}
blockquote{margin:10px 0;padding:10px 14px;background:var(--soft);border-left:4px solid var(--teal);color:var(--muted);font-size:9.5pt}
.rule{display:grid;grid-template-columns:104px 1fr;gap:10px;border:1px solid var(--line);border-radius:6px;margin:6px 0;break-inside:avoid;background:#fff}
.rule .rid{background:var(--soft);padding:8px;font-weight:700;font-size:8.5pt;color:var(--dark);border-right:1px solid var(--line);border-radius:6px 0 0 6px}
.rule .rid small{display:block;color:#a15c00;font-weight:600;margin-top:3px}.rule .rtxt{padding:6px 10px 6px 0}.rule .rtxt p{margin:2px 0}.rule .rtxt ul{margin:4px 0;padding-left:18px}
.rule.attention{border-color:#e9c27a;background:#fff8e8}.rule.attention .rid{background:#fdebc4}
code{font-family:Consolas,monospace;font-size:8.8pt;background:#eef2f4;padding:0 3px;border-radius:3px}
table{border-collapse:collapse;width:100%;margin:10px 0;font-size:9pt;break-inside:auto}th,td{border:1px solid var(--line);padding:5px 7px;vertical-align:top;text-align:left}th{background:var(--soft)}
figure{margin:12px 0;break-inside:avoid}figure img{width:100%;border:1px solid var(--line);border-radius:6px}figcaption{font-size:9pt;color:var(--muted);text-align:center;margin-top:4px}
.toc li{margin:3px 0}.toc{columns:1;font-size:11pt}.stat{display:flex;gap:14px;margin:12px 0}.stat div{flex:1;background:var(--soft);border-radius:8px;padding:10px;text-align:center}.stat b{display:block;font-size:20pt;color:var(--teal)}
@page{size:A4;margin:18mm 14mm 18mm 14mm}@media print{.cover{height:261mm;padding:40px}}
</style></head><body>
<div class="cover"><div style="font-size:12pt;letter-spacing:.2em;text-transform:uppercase;color:var(--gold);font-weight:700">Document de référence</div>
<h1>HemoDialyse<br><em>Documentation fonctionnelle</em><br>et règles de gestion</h1>
<p>Tout ce que la plateforme sait faire pour gérer un centre d'hémodialyse — et chaque règle qu'elle applique, sans exception.</p>
<div class="meta">Version ${VERSION} · ${DATE}<br>${stats.rules} règles de gestion · ${stats.chapters} domaines · ${stats.codes} codes d'erreur métier</div></div>
<div class="page">
<section><h1><span class="n">Sommaire</span>Contenu du document</h1><ol class="toc"><li><a href="#intro">Présentation de la plateforme</a></li>${toc.join('')}<li><a href="#attentions">Annexe — Points d'attention connus</a></li></ol></section>
<section class="part" id="intro"><h1><span class="n">Introduction</span>Présentation de la plateforme</h1>
<p>HemoDialyse couvre <b>toute la gestion d'un centre d'hémodialyse</b> : accueil et dossier administratif des patients, prises en charge, planification des postes et du personnel, séances et cahier de dialyse, dossier médical et suivi clinique, stock et pharmacie, maintenance des équipements, facturation, règlements et comptabilité, pilotage de la direction. La plateforme est multi-centres et multi-sociétés, en français, arabe, kabyle et anglais.</p>
<p>Au-delà de la disponibilité de l'information, le système <b>surveille</b> (absences, observance des traitements, sous-effectif, péremptions, indisponibilité des équipements, licences), <b>propose</b> (meilleures places de dialyse classées, remplaçants, réforme d'un équipement, simulation de facturation) et <b>protège</b> (placement incohérent refusé, isolement automatique des patients à risque, gel du stock pendant un inventaire, anonymat de la direction, cloisonnement par centre).</p>
<div class="stat"><div><b>${stats.rules}</b>règles de gestion</div><div><b>${stats.chapters}</b>domaines</div><div><b>${stats.alerts}</b>surveillances automatiques</div><div><b>${stats.attention}</b>points d'attention</div></div>
<h2>Profils et périmètres</h2><table><tr><th>Profil</th><th>Rôle</th><th>Périmètre principal</th></tr>${profils}</table>
<h2>Comment lire les règles</h2><p>Chaque règle porte un identifiant stable <code>RG-XXX-nnn</code>, jamais renuméroté. Le code d'erreur renvoyé à l'utilisateur figure entre apostrophes inversées ; la source dans le code est citée. Un encadré <b>point d'attention</b> signale un écart connu entre l'intention métier et le comportement actuel : il est conservé tant qu'il n'est pas corrigé.</p></section>
${main}
<section class="part" id="attentions"><h1><span class="n">Annexe</span>Points d'attention connus</h1><p>Écarts constatés entre l'intention métier et le comportement actuel. Ils sont retirés du référentiel dès leur correction.</p><ul>${att}</ul></section>
</div></body></html>`;
}

// ───────────────────────── DOCX ─────────────────────────
const FONT = 'Calibri';
const W = 9638;

function runs(tokens, base = {}) {
    const out = [];
    for (const t of tokens || []) {
        if (t.type === 'strong') out.push(...runs(t.tokens, {...base, bold: true}));
        else if (t.type === 'em') out.push(...runs(t.tokens, {...base, italics: true}));
        else if (t.type === 'codespan') out.push(new D.TextRun({
            text: t.text,
            font: 'Consolas',
            size: 18,
            color: '0B4D47', ...base
        }));
        else if (t.type === 'link') out.push(...runs(t.tokens, base));
        else if (t.type === 'br') out.push(new D.TextRun({break: 1}));
        else if (t.tokens) out.push(...runs(t.tokens, base));
        else out.push(new D.TextRun({text: (t.text || t.raw || '').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'"), ...base}));
    }
    return out;
}

const border = {style: D.BorderStyle.SINGLE, size: 4, color: 'D7E3EA'};
const borders = {top: border, bottom: border, left: border, right: border};

function cellOf(width, children, fill) {
    return new D.TableCell({
        borders, width: {size: width, type: D.WidthType.DXA}, margins: {top: 60, bottom: 60, left: 100, right: 100},
        shading: fill ? {fill, type: D.ShadingType.CLEAR, color: 'auto'} : undefined, children,
    });
}

function tokensToBlocks(tokens, level = 0) {
    const out = [];
    for (const t of tokens) {
        if (t.type === 'paragraph' || t.type === 'text') {
            out.push(new D.Paragraph({children: runs(t.tokens || [t]), spacing: {after: 80}}));
        } else if (t.type === 'heading') {
            out.push(new D.Paragraph({
                heading: t.depth <= 2 ? D.HeadingLevel.HEADING_2 : D.HeadingLevel.HEADING_3,
                children: runs(t.tokens)
            }));
        } else if (t.type === 'list') {
            for (const it of t.items) {
                out.push(new D.Paragraph({
                    numbering: {reference: 'bul', level: Math.min(level, 1)},
                    children: runs((it.tokens.find((x) => x.type === 'text' || x.type === 'paragraph') || {}).tokens || []),
                    spacing: {after: 40}
                }));
                const nested = it.tokens.filter((x) => x.type === 'list');
                if (nested.length) out.push(...tokensToBlocks(nested, level + 1));
            }
        } else if (t.type === 'blockquote') {
            for (const p of tokensToBlocks(t.tokens)) out.push(p);
        } else if (t.type === 'table') {
            const n = t.header.length;
            const w = Math.floor(W / n);
            const widths = t.header.map((_, i) => (i === n - 1 ? W - w * (n - 1) : w));
            const rows = [new D.TableRow({
                tableHeader: true,
                children: t.header.map((h, i) => cellOf(widths[i], [new D.Paragraph({
                    children: runs(h.tokens, {
                        bold: true,
                        size: 18
                    })
                })], 'EAF3F4'))
            })];
            for (const r of t.rows) rows.push(new D.TableRow({children: r.map((c, i) => cellOf(widths[i], [new D.Paragraph({children: runs(c.tokens, {size: 18})})]))}));
            out.push(new D.Table({
                width: {size: W, type: D.WidthType.DXA},
                columnWidths: widths,
                rows
            }), new D.Paragraph({children: []}));
        }
    }
    return out;
}

function ruleTable(rules) {
    const idW = 1700;
    const rows = rules.map((r) => new D.TableRow({
        cantSplit: true,
        children: [
            cellOf(idW, [new D.Paragraph({
                children: [new D.TextRun({
                    text: r.id,
                    bold: true,
                    size: 17,
                    color: '0B4D47'
                })]
            }),
                ...(r.attention ? [new D.Paragraph({
                    children: [new D.TextRun({
                        text: "point d'attention",
                        size: 15,
                        color: 'A15C00',
                        bold: true
                    })]
                })] : [])], r.attention ? 'FDEBC4' : 'EAF3F4'),
            cellOf(W - idW, tokensToBlocks(marked.lexer(r.text)).map((x) => x), r.attention ? 'FFF8E8' : undefined),
        ],
    }));
    return [new D.Table({
        width: {size: W, type: D.WidthType.DXA},
        columnWidths: [idW, W - idW],
        rows
    }), new D.Paragraph({children: [], spacing: {after: 60}})];
}

function imgBlocks(num) {
    const out = [];
    for (const [f, cap] of SCREENS[num] || []) {
        const p = path.join(IMG, f);
        if (!fs.existsSync(p)) continue;
        const buf = fs.readFileSync(p);
        const w = buf.readUInt32BE(16), h = buf.readUInt32BE(20);
        const width = 600, height = Math.round((600 * h) / w);
        out.push(new D.Paragraph({
                alignment: D.AlignmentType.CENTER,
                keepNext: true,
                children: [new D.ImageRun({
                    type: 'png',
                    data: buf,
                    transformation: {width, height},
                    altText: {title: cap, description: cap, name: f}
                })]
            }),
            new D.Paragraph({
                alignment: D.AlignmentType.CENTER,
                spacing: {after: 160},
                children: [new D.TextRun({text: cap, italics: true, size: 18, color: '5A6A74'})]
            }));
    }
    return out;
}

function buildDocx(chapters, stats, attentions) {
    const children = [];
    children.push(new D.Paragraph({
            spacing: {before: 2800},
            children: [new D.TextRun({text: 'DOCUMENT DE RÉFÉRENCE', bold: true, size: 24, color: 'B87A00'})]
        }),
        new D.Paragraph({
            spacing: {before: 200},
            children: [new D.TextRun({text: 'HemoDialyse', bold: true, size: 84, color: '0B4D47', font: 'Georgia'})]
        }),
        new D.Paragraph({
            children: [new D.TextRun({
                text: 'Documentation fonctionnelle et règles de gestion',
                size: 44,
                color: '14766E',
                font: 'Georgia'
            })]
        }),
        new D.Paragraph({
            spacing: {before: 400},
            children: [new D.TextRun({
                text: "Tout ce que la plateforme sait faire pour gérer un centre d'hémodialyse — et chaque règle qu'elle applique, sans exception.",
                size: 26,
                color: '5A6A74'
            })]
        }),
        new D.Paragraph({
            spacing: {before: 600},
            children: [new D.TextRun({text: `Version ${VERSION} · ${DATE}`, size: 22})]
        }),
        new D.Paragraph({
            children: [new D.TextRun({
                text: `${stats.rules} règles de gestion · ${stats.chapters} domaines · ${stats.codes} codes d'erreur métier`,
                size: 22
            })]
        }),
        new D.Paragraph({children: [new D.PageBreak()]}),
        new D.Paragraph({heading: D.HeadingLevel.HEADING_1, children: [new D.TextRun('Sommaire')]}),
        new D.TableOfContents('Sommaire', {hyperlink: true, headingStyleRange: '1-2'}),
        new D.Paragraph({
            children: [new D.TextRun({
                text: '(Dans Word : clic droit sur le sommaire → Mettre à jour le champ.)',
                italics: true,
                size: 18,
                color: '5A6A74'
            })]
        }),
        new D.Paragraph({children: [new D.PageBreak()]}),
        new D.Paragraph({
            heading: D.HeadingLevel.HEADING_1,
            children: [new D.TextRun('Présentation de la plateforme')]
        }),
        new D.Paragraph({
            spacing: {after: 120},
            children: [new D.TextRun("HemoDialyse couvre toute la gestion d'un centre d'hémodialyse : accueil et dossier administratif des patients, prises en charge, planification des postes et du personnel, séances et cahier de dialyse, dossier médical et suivi clinique, stock et pharmacie, maintenance des équipements, facturation, règlements et comptabilité, pilotage de la direction. La plateforme est multi-centres et multi-sociétés, en français, arabe, kabyle et anglais.")]
        }),
        new D.Paragraph({
            spacing: {after: 120},
            children: [new D.TextRun("Au-delà de la disponibilité de l'information, le système surveille (absences, observance des traitements, sous-effectif, péremptions, indisponibilité des équipements, licences), propose (meilleures places de dialyse classées, remplaçants, réforme d'un équipement, simulation de facturation) et protège (placement incohérent refusé, isolement automatique des patients à risque, gel du stock pendant un inventaire, anonymat de la direction, cloisonnement par centre).")]
        }),
        new D.Paragraph({heading: D.HeadingLevel.HEADING_2, children: [new D.TextRun('Profils et périmètres')]}),
        new D.Table({
            width: {size: W, type: D.WidthType.DXA}, columnWidths: [1700, 2800, 5138], rows: [
                new D.TableRow({
                    tableHeader: true,
                    children: ['Profil', 'Rôle', 'Périmètre principal'].map((h, i) => cellOf([1700, 2800, 5138][i], [new D.Paragraph({
                        children: [new D.TextRun({
                            text: h,
                            bold: true,
                            size: 18
                        })]
                    })], 'EAF3F4'))
                }),
                ...PROFILS.map((p) => new D.TableRow({
                    children: p.map((c, i) => cellOf([1700, 2800, 5138][i], [new D.Paragraph({
                        children: [new D.TextRun({
                            text: c,
                            size: 18,
                            font: i === 0 ? 'Consolas' : FONT
                        })]
                    })]))
                })),
            ]
        }),
        new D.Paragraph({
            spacing: {before: 160},
            heading: D.HeadingLevel.HEADING_2,
            children: [new D.TextRun('Comment lire les règles')]
        }),
        new D.Paragraph({children: [new D.TextRun("Chaque règle porte un identifiant stable RG-XXX-nnn, jamais renuméroté. Le code d'erreur renvoyé à l'utilisateur est cité ; la source dans le code aussi. Un encadré « point d'attention » signale un écart connu entre l'intention métier et le comportement actuel : il est conservé tant qu'il n'est pas corrigé.")]}));

    for (const ch of chapters) {
        children.push(new D.Paragraph({children: [new D.PageBreak()]}),
            new D.Paragraph({
                heading: D.HeadingLevel.HEADING_1,
                children: [new D.TextRun(`${parseInt(ch.num, 10)}. ${ch.title}`)]
            }));
        children.push(...imgBlocks(ch.num));
        let group = [];
        const flushRules = () => {
            if (group.length) {
                children.push(...ruleTable(group));
                group = [];
            }
        };
        for (const b of blocks(ch.body)) {
            if (b.type === 'rule') group.push(b);
            else {
                flushRules();
                children.push(...tokensToBlocks(marked.lexer(b.text)));
            }
        }
        flushRules();
    }
    children.push(new D.Paragraph({children: [new D.PageBreak()]}),
        new D.Paragraph({
            heading: D.HeadingLevel.HEADING_1,
            children: [new D.TextRun("Annexe — Points d'attention connus")]
        }));
    children.push(...ruleTable(attentions));

    return new D.Document({
        creator: 'HemoDialyse', title: TITLE, description: `Version ${VERSION}`,
        features: {updateFields: true},
        styles: {
            default: {document: {run: {font: FONT, size: 21}}},
            paragraphStyles: [
                {
                    id: 'Heading1',
                    name: 'Heading 1',
                    basedOn: 'Normal',
                    next: 'Normal',
                    quickFormat: true,
                    run: {size: 40, bold: true, font: 'Georgia', color: '0B4D47'},
                    paragraph: {spacing: {before: 240, after: 200}, outlineLevel: 0}
                },
                {
                    id: 'Heading2',
                    name: 'Heading 2',
                    basedOn: 'Normal',
                    next: 'Normal',
                    quickFormat: true,
                    run: {size: 28, bold: true, font: 'Georgia', color: '14766E'},
                    paragraph: {spacing: {before: 240, after: 120}, outlineLevel: 1}
                },
                {
                    id: 'Heading3',
                    name: 'Heading 3',
                    basedOn: 'Normal',
                    next: 'Normal',
                    quickFormat: true,
                    run: {size: 24, bold: true, color: '14766E'},
                    paragraph: {spacing: {before: 160, after: 80}, outlineLevel: 2}
                },
            ],
        },
        numbering: {
            config: [{
                reference: 'bul', levels: [
                    {
                        level: 0,
                        format: D.LevelFormat.BULLET,
                        text: '•',
                        alignment: D.AlignmentType.LEFT,
                        style: {paragraph: {indent: {left: 360, hanging: 240}}}
                    },
                    {
                        level: 1,
                        format: D.LevelFormat.BULLET,
                        text: '–',
                        alignment: D.AlignmentType.LEFT,
                        style: {paragraph: {indent: {left: 720, hanging: 240}}}
                    }]
            }]
        },
        sections: [{
            properties: {
                page: {
                    size: {width: 11906, height: 16838},
                    margin: {top: 1134, right: 1134, bottom: 1134, left: 1134}
                }
            },
            footers: {
                default: new D.Footer({
                    children: [new D.Paragraph({
                        alignment: D.AlignmentType.CENTER,
                        children: [new D.TextRun({
                            text: `HemoDialyse — Documentation fonctionnelle v${VERSION} — page `,
                            size: 16,
                            color: '5A6A74'
                        }), new D.TextRun({children: [D.PageNumber.CURRENT], size: 16, color: '5A6A74'})]
                    })]
                })
            },
            children,
        }],
    });
}

// ───────────────────────── Principal ─────────────────────────
(async () => {
    const chapters = loadChapters();
    const all = chapters.flatMap((c) => blocks(c.body).filter((b) => b.type === 'rule'));
    const attentions = all.filter((r) => r.attention);
    const codes = new Set();
    for (const c of chapters) for (const m of c.body.matchAll(/`([A-Z][A-Z0-9_]*[A-Z0-9])`/g)) if (m[1].includes('_')) codes.add(m[1]);
    const alerts = (fs.readFileSync(path.join(REF, '15-notifications-alertes.md'), 'utf8').match(/^\| [A-Z].*\| (quotidien|à la demande|temps réel|à chaque|écriture|le 1er|contrôle|à l)/gm) || []).length || 15;
    const stats = {
        rules: all.length,
        chapters: chapters.length,
        attention: attentions.length,
        codes: codes.size,
        alerts
    };

    const html = renderHtml(chapters, stats, attentions);
    fs.writeFileSync(path.join(OUT, 'documentation-fonctionnelle.html'), html);
    console.log(`HTML : ${all.length} règles, ${attentions.length} points d'attention`);

    const buf = await D.Packer.toBuffer(buildDocx(chapters, stats, attentions));
    fs.writeFileSync(path.join(OUT, 'documentation-fonctionnelle.docx'), buf);
    console.log('DOCX : ' + buf.length + ' octets');

    const {chromium} = require('playwright-core');
    const browser = await chromium.launch();
    const page = await browser.newPage();
    await page.goto('file:///' + path.join(OUT, 'documentation-fonctionnelle.html').replace(/\\/g, '/'));
    await page.pdf({
        path: path.join(OUT, 'documentation-fonctionnelle.pdf'),
        format: 'A4',
        printBackground: true,
        displayHeaderFooter: true,
        headerTemplate: '<span></span>',
        footerTemplate: '<div style="font-size:8px;width:100%;text-align:center;color:#5a6a74">HemoDialyse — Documentation fonctionnelle v' + VERSION + ' — page <span class="pageNumber"></span> / <span class="totalPages"></span></div>',
        margin: {top: '16mm', bottom: '16mm', left: '13mm', right: '13mm'},
    });
    await browser.close();
    console.log('PDF généré');
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
