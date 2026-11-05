# Konab POS — Application Mobile & Panel Admin Web Indépendant

Solution de caisse et de gestion commerciale conçue pour les commerces et PME en Afrique francophone (100% hors-ligne avec synchronisation Cloud Supabase).

---

## 🚀 Déploiement du Panel Admin sur Vercel (1-Clic)

Le Panel Admin est un tableau de bord web statique complet (`public/admin.html` et `public/index.html`) pré-configuré pour un déploiement instantané sur **Vercel** via `vercel.json`.

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/konabdigital378-prog/POS-UNIV)

### Étapes manuelles de déploiement sur Vercel :

1. Connectez-vous sur [Vercel.com](https://vercel.com) (avec votre compte GitHub).
2. Cliquez sur **"Add New..."** > **"Project"**.
3. Importez le dépôt : `konabdigital378-prog/POS-UNIV`.
4. Vercel détecte automatiquement `vercel.json` et configure :
   * **Output Directory** : `public`
   * **Routes** : Redirection automatique de `/` et `/admin` vers `admin.html`.
5. *(Optionnel)* Dans la section **Environment Variables**, ajoutez vos clés Supabase :
   * `SUPABASE_URL` = `https://votre-projet.supabase.co`
   * `SUPABASE_ANON_KEY` = `votre-cle-anon-publique`
6. Cliquez sur **Deploy** !
7. Votre Panel Admin est immédiatement en ligne sur une URL du type :
   `https://pos-univ.vercel.app` (avec certificat HTTPS gratuit).

---

## 🛠️ Structure du Projet

```
├── public/
│   ├── admin.html          # Panel Admin Web complet (Tailwind CSS, Chart.js, Supabase JS)
│   ├── index.html          # Point d'entrée web Vercel
│   └── assets/             # Logos et icônes
├── app/                    # Application Android native (Kotlin / Jetpack Compose / Room)
├── vercel.json             # Configuration de routage et de sécurité pour Vercel
├── package.json            # Déclaration du projet web pour la détection Vercel
└── .env.example            # Variables d'environnement pour Supabase
```

---

## 📱 Fonctionnalités du Panel Admin Web

* **Tableau de Bord Exécutif** : Chiffre d'affaires en direct, marge brute estimée, solde de caisse et graphiques sur 7 jours.
* **Gestion des Stocks** : Catalogue, alertes de rupture, calcul de marges et export CSV.
* **Historique des Ventes** : Consultation des tickets et réimpression de factures.
* **Carnet de Crédit Clients** : Suivi des dettes et relance directe par WhatsApp en 1 clic.
* **Contrôle de Caisse** : Suivi des sessions, entrées/sorties d'espèces et écarts de caisse.
* **Synchronisation Supabase** : Partage en direct des 11 tables avec l'application mobile.
