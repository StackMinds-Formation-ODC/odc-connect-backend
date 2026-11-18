---
description: Données de départ (référence vs démo) via Flyway — déclenchement, idempotence, séparation des environnements
alwaysApply: true
---

# Règles pour les données de départ (« seeders »)

Adapté de l'ancienne instruction NestJS (runner maison + `_seed_history`). Ici **Flyway fait le travail** : sa table
`flyway_schema_history` remplace `_seed_history`, et le choix des *locations* par profil remplace le manifeste `environments`.
Aucun runner à écrire.

## 1. Deux catégories, deux emplacements

| Catégorie | Exemples | Emplacement | Environnements |
|-----------|----------|-------------|----------------|
| **Données de référence** (stables, nécessaires partout) | rôles métier, statuts, catégories | migrations **versionnées** `src/main/resources/db/migration/V<n>__seed_<nom>.sql` | tous, y compris prod |
| **Données de démo / fake** | membres fictifs, jeux d'essai | migrations **répétables** `src/main/resources/db/seed/dev/R__<NNN>_seed_<nom>.sql` | `dev` et `test` **uniquement** |

La règle « jamais de démo en production » est garantie **par configuration** : `db/seed/dev` n'est déclaré que dans les
profils `dev`/`test`.

```yaml
# application-dev.yaml  (à ajouter avec le premier seed de démo)
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/seed/dev
```

`application-prod.yaml` ne déclare **pas** `spring.flyway.locations` (valeur par défaut : `classpath:db/migration`).

## 2. Déclenchement : auto en dev, jamais en prod

- **dev** : les `R__` s'exécutent au démarrage, après les migrations versionnées (Flyway applique les répétables en dernier,
  dans l'ordre alphabétique de leur description) et se rejouent seulement si leur contenu change (checksum).
- **prod / staging** : seules les migrations de `db/migration` s'exécutent. Aucune donnée de démo, aucun déclenchement caché.
- **tests** : le conteneur Testcontainers démarre vide ; par défaut `db/migration` seul. Un test qui a besoin de démo l'active
  explicitement (`@TestPropertySource(properties = "spring.flyway.locations=...")`), jamais par effet de bord.

## 3. Idempotence — la règle la plus importante

Un seed rejoué ne doit **jamais planter ni dupliquer**.

```sql
-- R__020_seed_members.sql
INSERT INTO members (public_id, email, display_name)
VALUES ('11111111-1111-4111-8111-111111111111', 'awa.demo@example.invalid', 'Awa Démo')
ON CONFLICT (email) DO NOTHING;
```

- `INSERT … ON CONFLICT … DO NOTHING/UPDATE` sur une **clé métier stable** (e-mail, code), pas sur un `id` auto-incrémenté.
- `public_id` fixes et déterministes dans les seeds (UUID littéraux) : reproductibles, référençables entre seeds.
- Pas de `TRUNCATE`/`DELETE` dans un seed normal. Une remise à zéro se fait en recréant le volume Docker
  (`docker compose --profile dev down -v`), pas dans un script.
- Une migration **versionnée** de référence ne se modifie jamais après application : on ajoute `V<n+1>__…`.

## 4. Un seed = une responsabilité, ordre explicite

- Un fichier = une entité ou un petit groupe cohérent (`roles`, `members`, `activities`), pas un script monolithique.
- Numéroter par pas de 10 (`R__010_seed_roles.sql`, `R__020_seed_members.sql`, `R__030_…`) pour pouvoir insérer ensuite.
  L'ordre d'exécution des répétables suit cette description : un seed ne doit dépendre que de numéros **inférieurs**.
- Commentaire en tête de fichier : objectif et dépendances.

## 5. Données sûres

- Données fictives uniquement, adresses en `@example.invalid`. **Jamais de vraies données personnelles**, jamais de dump de
  production non anonymisé (RGPD).
- Pas de génération aléatoire dans les seeds (pas de Faker dans le `pom.xml`) : jeux **déterministes** et réalistes
  (accents, Unicode, valeurs nulles, volumes suffisants pour tester la pagination).
- **Pas de comptes ni de mots de passe en base** : l'authentification est déléguée à un fournisseur OAuth2 externe (JWT).
  Les utilisateurs de test se créent côté fournisseur (ex. Keycloak), jamais dans un seed ; au plus un `external_subject`
  fictif si une table de profils le référence.
- Aucun secret dans un seed, même en dev.

## 6. Transactions, robustesse, performance

- Flyway exécute chaque migration dans une transaction (PostgreSQL) : échec = rollback de ce fichier et arrêt du démarrage.
  Ne pas contourner (`executeInTransaction=false`) sans raison écrite dans une décision.
- Insertions **en lot** (`INSERT … VALUES (…), (…)`), pas de boucle ligne à ligne.
- Garder les seeds de démo petits (démarrage rapide en dev et en CI) ; un gros jeu éventuel va dans une location séparée
  (`db/seed/perf`) activée à la demande par propriété.

## 7. CI/CD

- La CI lance les tests avec Testcontainers : migrations `db/migration` seules (pas de démo), schéma validé par
  `ddl-auto: validate`.
- Un test d'intégration peut valider l'idempotence : appliquer `db/migration` + `db/seed/dev`, puis `flyway.repair/migrate`
  à nouveau, sans doublon ni erreur.

## 8. Documentation

- Un `README.md` dans `src/main/resources/db/seed/dev/` : comment ajouter un seed, convention de numérotation, règle
  auto (dev) / absent (prod).
- Tout changement notable (nouveau seed, nouvelle location) est reporté dans `docs/memoire/ETAT.md`.

## Résumé

| Règle | Pourquoi |
|---|---|
| Référence = `V` versionné ; démo = `R__` dans `db/seed/dev` | Séparer ce qui va en prod de ce qui n'y va pas |
| `locations` par profil | Interdire la démo en prod par configuration, pas par convention |
| Auto en dev, rien d'implicite ailleurs | Pas de seed accidentel en staging/prod |
| Idempotent (`ON CONFLICT`) | Rejouable sans risque |
| `flyway_schema_history` | Ne rejouer que ce qui a changé |
| UUID fixes, pas d'aléatoire | Données reproductibles |
| Pas de compte/mot de passe en base | L'identité vit chez le fournisseur OAuth2 |
| Un fichier = une entité, pas de 10 | Ordre fiable, lisibilité |
