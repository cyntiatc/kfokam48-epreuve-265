# KFOKAM48 — Présences et relectures

Épreuve pratique KFOKAM48 — Tedjou Nguimzi Cyntia, matricule 265.

Application web qui accompagne une séance de cours de bout en bout :

1. le **formateur** ouvre une session et obtient un **code de présence** valable 15 minutes ;
2. chaque **étudiant** émarge avec ce code ;
3. chaque étudiant **dépose le lien** de son exercice ;
4. le système **attribue au hasard** l'exercice d'un pair à **deux** étudiants présents, qui le **notent (0 à 20) et le commentent** — jamais leur propre exercice. La note de l'exercice est la moyenne des deux notes ; tant qu'une seule est rendue, elle est affichée comme **provisoire** (évolution de l'Étape 3) ;
5. le formateur suit l'ensemble sur un **tableau récapitulatif** par promotion, puis **clôture** la session, ce qui rend les notes définitives.

Les exigences, règles de gestion et arbitrages sont décrits dans le [cahier des charges](docs/CAHIER_DES_CHARGES.md).

---

## Architecture et technologies

| Couche | Technologies |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Spring Web, Spring Data JPA, Bean Validation), Flyway, Maven |
| Base de données | PostgreSQL 17, lancé par Docker Compose (port local `5435` → `5432` du conteneur) |
| Frontend | React 19, Vite 8, CSS personnalisé (sans bibliothèque de composants) |
| Contrat d'API | OpenAPI 3.0.3 : [`api/contrat.yaml`](api/contrat.yaml), source de vérité |
| Documentation | Swagger UI servi par le backend : http://localhost:8080/swagger-ui.html |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL réel), validation des réponses par le contrat |

```
kfokam48-epreuve-265/
├── api/contrat.yaml             Contrat OpenAPI (routes, schémas, codes d'erreur)
├── backend/                     API Spring Boot
│   └── src/main/
│       ├── java/com/kfokam48/epreuve/
│       │   ├── domain/          Entités JPA et cycles de vie (session, présence, exercice, relecture)
│       │   ├── service/         Règles de gestion (attribution des relectures, clôture…)
│       │   ├── repository/      Accès aux données (Spring Data JPA, requête SQL du tableau)
│       │   ├── web/             Contrôleurs REST, DTO, format d'erreur unique
│       │   └── config/          Horloge, tirage au sort, CORS
│       └── resources/db/migration/   Migrations Flyway V1 à V5
├── frontend/                    Application React (Vite)
├── docs/                        Cahier des charges, journal de bord, diagrammes D1 à D4
└── docker-compose.yml           PostgreSQL 17
```

Le backend suit une architecture en couches : contrôleur, service, dépôt. Le schéma de la base est géré **uniquement** par Flyway.

---

## Démarrage rapide

### Prérequis

| Outil | Version |
|---|---|
| Java (JDK) | 21 |
| Maven | 3.9 ou plus (le dépôt ne contient pas de Maven Wrapper) |
| Node.js | 20.19+ ou 22.12+ (exigé par Vite 8) |
| Docker Desktop | avec Docker Compose |

### 1. Base de données

```bash
docker compose up -d
```

PostgreSQL 17 écoute sur `localhost:5435`, base `kfokam48`, utilisateur et mot de passe `kfokam48` (développement uniquement).

### 2. Backend (port 8080)

```bash
cd backend
mvn spring-boot:run
```

Au premier démarrage, Flyway applique les migrations V1 à V5 : schéma, données de démonstration, puis passage à la double relecture (V5). Sur une base existante, seule V5 est appliquée ; elle conserve toutes les relectures.

### 3. Frontend (port 5173)

```bash
cd frontend
npm install
npm run dev
```

Ouvrir http://localhost:5173. En développement, Vite relaie les appels `/api` vers le backend : le navigateur reste sur une seule origine.

### Données de démonstration

| Promotion | Étudiants (identifiants) |
|---|---|
| 1 — L3 GL | 1 Alice, 2 Brice, 3 Carine, 4 David, 5 Estelle |
| 2 — M1 Software Engineering | 6 Fabrice, 7 Grace |

Parcours type dans l'interface :

1. **Espace Formateur** : ouvrir une session pour la promotion `1` et noter le code affiché.
2. **Espace Étudiant** : émarger avec ce code pour les étudiants `1` puis `2`.
3. **Dépôt d'exercice** : déposer un lien pour l'étudiant `1` ; l'étudiant `2` est aussitôt désigné relecteur.
4. **Relecture** : avec l'identifiant `2`, afficher ses relectures, ouvrir l'exercice, le noter.
5. **Tableau** : consulter la promotion `1`, puis clôturer la session depuis l'Espace Formateur.

### Configuration

| Variable ou propriété | Rôle | Valeur par défaut |
|---|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | identifiants PostgreSQL | `kfokam48` / `kfokam48` |
| `app.cors.origines-autorisees` | origines autorisées à appeler l'API directement | `http://localhost:5173,http://127.0.0.1:5173` |

---

## Routes de l'API

Toutes les erreurs suivent un **format unique** : `{ "code": "...", "message": "..." }`.

### Les 5 routes imposées par le contrat

| Méthode | Route | Rôle | Réponses |
|---|---|---|---|
| `POST` | `/api/sessions` | Ouvrir une session et obtenir le code de présence | 201, 400 |
| `POST` | `/api/presences` | Émarger avec le code | 201, 400, 409, 410 |
| `POST` | `/api/exercices` | Déposer le lien de son exercice | 201, 400, 409 |
| `POST` | `/api/relectures/{id}` | Rendre une relecture, ou la modifier avant la clôture | 200, 400, 403, 409 |
| `GET` | `/api/tableau?promotionId=` | Tableau récapitulatif d'une promotion | 200, 400, 404 |

L'**attribution des relectures** n'a pas de route : c'est un traitement interne, déclenché à chaque dépôt d'exercice et à chaque nouvel émargement. Chaque exercice reçoit deux relecteurs distincts, tirés au hasard parmi les étudiants présents les moins chargés, jamais l'auteur ; s'il n'y en a qu'un de disponible, le second est attribué au prochain émargement.

### Les 2 routes d'extension

Ajoutées au contrat et signalées comme extensions (hypothèse H3 du cahier des charges), car le contrat imposé ne permettait ni de clôturer une session, ni à un relecteur de retrouver ses relectures.

| Méthode | Route | Rôle | Réponses |
|---|---|---|---|
| `GET` | `/api/relectures?relecteurId=` | Relectures attribuées à un relecteur, avec le lien de chaque exercice | 200, 400 |
| `POST` | `/api/sessions/{id}/cloture` | Clôturer une session : notes définitives, code refusé | 200, 400, 409 |

Le détail des corps, des schémas et des codes d'erreur se trouve dans [`api/contrat.yaml`](api/contrat.yaml). Il est aussi consultable et testable dans Swagger UI : http://localhost:8080/swagger-ui.html (backend démarré).

---

## Tests et qualité

```bash
cd backend
mvn clean test
```

| Type | Ce qui est vérifié |
|---|---|
| Unitaires (Mockito) | règles de gestion des services : validité du code, attribution équilibrée, notes, clôture… |
| Web (MockMvc) | statuts HTTP, corps JSON et format d'erreur de chaque contrôleur |
| Intégration (Testcontainers) | parcours complets sur un vrai PostgreSQL 17, avec les migrations Flyway et les contraintes de la base |
| Conformité au contrat | chaque statut de chaque route est provoqué, et la réponse est **validée par `api/contrat.yaml` lui-même** |

Les tests d'intégration démarrent PostgreSQL dans Docker : **Docker Desktop doit être lancé**. Sans Docker, ils sont ignorés (« skipped »), pas réussis.

**Dernière exécution complète vérifiée :** 143 tests, 0 échec, 0 ignoré, dont 40 d'intégration (y compris la migration V5 sur une base peuplée, et les tests de concurrence des émargements et des relectures).

Le frontend n'a pas de tests automatisés. `npm run build` vérifie qu'il compile.

---

## Limites connues

- **Pas d'authentification** (H4) : les identifiants d'étudiant sont transmis dans les requêtes.
- **Dépôt après clôture** : un exercice déposé après la clôture d'une session est encore accepté ; la règle reste à trancher.
- **Performance** : l'objectif de ENF4 (moins de 500 ms pour 100 étudiants) n'a pas été mesuré.

---

## Documentation

| Document | Contenu |
|---|---|
| [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md) | Contexte, exigences EF et ENF, règles de gestion, contradictions et hypothèses, backlog |
| [`docs/JOURNAL.md`](docs/JOURNAL.md) | Journal de bord : ce qui a été fait, bloqué, et le rôle de l'IA, étape par étape |
| [`docs/diagrammes/`](docs/diagrammes/) | D1 cas d'utilisation, D2 modèle de données, D3 séquence « Marquer sa présence », D4 états d'un exercice |
| [`api/contrat.yaml`](api/contrat.yaml) | Contrat OpenAPI 3.0.3 |
