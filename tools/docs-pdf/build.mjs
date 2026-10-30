// Compila un Markdown de docs/pdf/src a PDF: portada + índice + cuerpo, con número de página.
// Uso: node tools/docs-pdf/build.mjs docs/pdf/src/<doc>.md [más.md ...]
// Salida: docs/pdf/out/<doc>.pdf (y <doc>.html para revisar el maquetado).
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { marked } from 'marked';
import puppeteer from 'puppeteer-core';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '..', '..');
const outDir = join(repo, 'docs', 'pdf', 'out');

const BROWSERS = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean);

// Front matter mínimo "clave: valor" entre líneas ---.
function parseFrontMatter(text) {
  const m = text.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/);
  if (!m) return { meta: {}, body: text };
  const meta = {};
  for (const line of m[1].split(/\r?\n/)) {
    const i = line.indexOf(':');
    if (i > 0) meta[line.slice(0, i).trim()] = line.slice(i + 1).trim().replace(/^["']|["']$/g, '');
  }
  return { meta, body: text.slice(m[0].length) };
}

const escape = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const slug = (s) => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/<[^>]+>/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

function render(srcPath) {
  const { meta, body } = parseFrontMatter(readFileSync(srcPath, 'utf8'));
  const toc = [];
  const used = new Map();
  const renderer = new marked.Renderer();
  renderer.heading = function ({ tokens, depth }) {
    const text = this.parser.parseInline(tokens);
    let id = slug(text) || 'seccion';
    const n = used.get(id) ?? 0;
    used.set(id, n + 1);
    if (n) id += `-${n}`;
    if (depth === 2 || depth === 3) toc.push({ depth, text, id });
    return `<h${depth} id="${id}">${text}</h${depth}>\n`;
  };
  const content = marked.parse(body, { renderer, gfm: true });

  const title = meta.title ?? basename(srcPath, '.md');
  const tocHtml = meta.toc === 'false' || !toc.length ? '' : `
    <nav class="toc"><h2 class="toc-title">Contenido</h2><ol>
      ${toc.map((t) => `<li class="l${t.depth}"><a href="#${t.id}">${t.text}</a></li>`).join('\n')}
    </ol></nav>`;
  const field = (k, label) => (meta[k] ? `<div><span>${label}</span>${escape(meta[k])}</div>` : '');

  const html = `<!doctype html><html lang="es"><head><meta charset="utf-8">
<title>${escape(title)}</title>
<base href="${pathToFileURL(dirname(srcPath)).href}/">
<style>${readFileSync(join(here, 'style.css'), 'utf8')}</style></head><body>
<section class="cover">
  <div class="brand">ViTo · VibeTogether</div>
  <h1>${escape(title)}</h1>
  ${meta.subtitle ? `<p class="subtitle">${escape(meta.subtitle)}</p>` : ''}
  <div class="meta">
    ${field('version', 'Versión')}${field('date', 'Fecha')}${field('audience', 'Audiencia')}
    ${field('author', 'Autor')}${field('status', 'Estado')}
  </div>
</section>
${tocHtml}
<main>${content}</main>
</body></html>`;
  return { html, title };
}

async function main() {
  const inputs = process.argv.slice(2);
  if (!inputs.length) {
    console.error('Uso: node tools/docs-pdf/build.mjs docs/pdf/src/<doc>.md');
    process.exit(2);
  }
  const executablePath = BROWSERS.find((p) => existsSync(p));
  if (!executablePath) throw new Error('No se encontró Chrome/Edge; define CHROME_PATH.');
  mkdirSync(outDir, { recursive: true });

  const browser = await puppeteer.launch({ executablePath, headless: true, args: ['--allow-file-access-from-files'] });
  try {
    for (const input of inputs) {
      const src = resolve(input);
      const name = basename(src, '.md');
      const { html, title } = render(src);
      const htmlPath = join(outDir, `${name}.html`);
      writeFileSync(htmlPath, html);

      const page = await browser.newPage();
      await page.goto(pathToFileURL(htmlPath).href, { waitUntil: 'networkidle0' });
      const pdfPath = join(outDir, `${name}.pdf`);
      await page.pdf({
        path: pdfPath,
        format: 'A4',
        printBackground: true,
        displayHeaderFooter: true,
        margin: { top: '22mm', bottom: '20mm', left: '18mm', right: '18mm' },
        headerTemplate: `<div style="font-size:8px;color:#888;width:100%;padding:0 18mm;">${escape(title)}</div>`,
        footerTemplate: '<div style="font-size:8px;color:#888;width:100%;padding:0 18mm;text-align:right;">'
          + '<span class="pageNumber"></span> / <span class="totalPages"></span></div>',
      });
      await page.close();
      console.log(`OK  ${input} → ${pdfPath}`);
    }
  } finally {
    await browser.close();
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
