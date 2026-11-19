const fs = require('fs');
const path = require('path');

const distDir = path.join(__dirname, 'dist');

console.log('🚀 Construction du dossier dist public (Menu Digital & Suivi Commande uniquement)...');

// 1. Nettoyer ou créer dist
if (fs.existsSync(distDir)) {
  fs.rmSync(distDir, { recursive: true, force: true });
}
fs.mkdirSync(distDir, { recursive: true });

// 2. Fonctions de copie
function copyFileSafe(src, dest) {
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
    console.log(`  ✓ Copié: ${path.basename(dest)}`);
  }
}

function copyDirRecursive(src, dest) {
  if (!fs.existsSync(src)) return;
  fs.mkdirSync(dest, { recursive: true });
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDirRecursive(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

// 3. Servir UNIQUEMENT le Portail Public Menu Digital & Suivi Client (AUCUN panneau admin / DB exposé)
const portalHtmlSrc = path.join(__dirname, 'public', 'portal.html');
copyFileSafe(portalHtmlSrc, path.join(distDir, 'index.html'));
copyFileSafe(portalHtmlSrc, path.join(distDir, 'portal.html'));

// 3b. Copier CSS, JS et Manifest du portail
const portalAssets = ['portal.css', 'portal.js', 'manifest.webmanifest'];
for (const file of portalAssets) {
  const srcPublic = path.join(__dirname, 'public', file);
  const srcRoot = path.join(__dirname, file);
  copyFileSafe(fs.existsSync(srcPublic) ? srcPublic : srcRoot, path.join(distDir, file));
}

// 4. Copier logo.png
const logoSource = fs.existsSync(path.join(__dirname, 'public', 'logo.png'))
  ? path.join(__dirname, 'public', 'logo.png')
  : path.join(__dirname, 'logo.png');
copyFileSafe(logoSource, path.join(distDir, 'logo.png'));

// 5. Copier les assets (dont public/assets/presets/*)
if (fs.existsSync(path.join(__dirname, 'public', 'assets'))) {
  copyDirRecursive(path.join(__dirname, 'public', 'assets'), path.join(distDir, 'assets'));
  console.log('  ✓ Dossier public/assets copié dans dist/assets');
} else if (fs.existsSync(path.join(__dirname, 'assets'))) {
  copyDirRecursive(path.join(__dirname, 'assets'), path.join(distDir, 'assets'));
  console.log('  ✓ Dossier assets copié dans dist/assets');
}

// 6. Fichier de redirections Netlify (_redirects) : toute route renvoie sur le menu public
const redirectsContent = `/menu        /portal.html  200
/menu/*      /portal.html  200
/orders      /portal.html  200
/suivi       /portal.html  200
/iphone      /portal.html  200
/portal      /portal.html  200
/*           /index.html   200
`;
fs.writeFileSync(path.join(distDir, '_redirects'), redirectsContent);
console.log('  ✓ Fichier _redirects généré (100% public, sans panneau admin)');

// 7. netlify.toml statique pour dist (sans section [build] pour éviter ENOENT package.json)
const distNetlifyToml = `[[redirects]]
  from = "/menu"
  to = "/portal.html"
  status = 200

[[redirects]]
  from = "/menu/*"
  to = "/portal.html"
  status = 200

[[redirects]]
  from = "/orders"
  to = "/portal.html"
  status = 200

[[redirects]]
  from = "/suivi"
  to = "/portal.html"
  status = 200

[[redirects]]
  from = "/iphone"
  to = "/portal.html"
  status = 200

[[redirects]]
  from = "/portal"
  to = "/portal.html"
  status = 200

[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200

[[headers]]
  for = "/*"
  [headers.values]
    X-Frame-Options = "SAMEORIGIN"
    X-XSS-Protection = "1; mode=block"
    X-Content-Type-Options = "nosniff"
    Referrer-Policy = "strict-origin-when-cross-origin"
`;
fs.writeFileSync(path.join(distDir, 'netlify.toml'), distNetlifyToml);
console.log('  ✓ netlify.toml statique généré dans dist');

console.log('🎉 Dossier dist public créé avec succès !');
