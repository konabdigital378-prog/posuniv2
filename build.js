const fs = require('fs');
const path = require('path');

const distDir = path.join(__dirname, 'dist');

console.log('🚀 Construction du dossier dist pour Netlify...');

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

// 3. Copier index.html et admin.html
const sourceHtml = fs.existsSync(path.join(__dirname, 'public', 'index.html'))
  ? path.join(__dirname, 'public', 'index.html')
  : path.join(__dirname, 'index.html');

copyFileSafe(sourceHtml, path.join(distDir, 'index.html'));
copyFileSafe(sourceHtml, path.join(distDir, 'admin.html'));

// 4. Copier logo.png
const logoSource = fs.existsSync(path.join(__dirname, 'public', 'logo.png'))
  ? path.join(__dirname, 'public', 'logo.png')
  : path.join(__dirname, 'logo.png');
copyFileSafe(logoSource, path.join(distDir, 'logo.png'));

// 5. Copier les assets
if (fs.existsSync(path.join(__dirname, 'public', 'assets'))) {
  copyDirRecursive(path.join(__dirname, 'public', 'assets'), path.join(distDir, 'assets'));
  console.log('  ✓ Dossier public/assets copié dans dist/assets');
} else if (fs.existsSync(path.join(__dirname, 'assets'))) {
  copyDirRecursive(path.join(__dirname, 'assets'), path.join(distDir, 'assets'));
  console.log('  ✓ Dossier assets copié dans dist/assets');
}

// 6. Fichier de redirections Netlify (_redirects)
const redirectsContent = `/admin       /index.html   200
/admin.html  /index.html   200
/*           /index.html   200
`;
fs.writeFileSync(path.join(distDir, '_redirects'), redirectsContent);
console.log('  ✓ Fichier _redirects généré pour Netlify');

// 7. netlify.toml
if (fs.existsSync(path.join(__dirname, 'netlify.toml'))) {
  fs.copyFileSync(path.join(__dirname, 'netlify.toml'), path.join(distDir, 'netlify.toml'));
  console.log('  ✓ netlify.toml copié dans dist');
}

console.log('🎉 Dossier dist créé avec succès pour Netlify !');
