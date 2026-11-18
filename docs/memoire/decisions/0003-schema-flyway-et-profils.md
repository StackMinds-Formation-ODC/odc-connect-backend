# 0003 — Schéma géré par Flyway, profils `dev` / `prod` (constat + règles)

Date : 2026-11-17 · Statut : **constat** (config) + **règles adoptées** (à valider)

## Constat

- `spring.jpa.hibernate.ddl-auto: validate` : Hibernate ne crée ni ne modifie rien ; le commentaire de `application.yaml`
  renvoie à `src/main/resources/db/migration`.
- Starters `flyway` + `flyway-database-postgresql` ; PostgreSQL 17 (compose et Testcontainers).
- Profil `dev` (`application-dev.yaml`) : valeurs pratiques, SQL affiché, logs DEBUG. Profil `prod` : tout en variables
  d'environnement, échec volontaire si une variable manque.
- Sélection du profil : `SPRING_PROFILES_ACTIVE` (`dev` dans `app`, `prod` dans `app-prod` et `Dockerfile`).

## Règles adoptées

1. Tout changement de modèle = une migration `V<n>__<description>.sql` dans `db/migration`, dans le **même commit** que l'entité.
2. Une migration appliquée n'est jamais modifiée.
3. Données de référence en migrations versionnées ; données de démo en `R__` sous `db/seed/dev`, déclarées uniquement dans
   `application-dev.yaml` (`spring.flyway.locations`) — voir `docs/instruction/seeder-gestion.md`.
4. Identifiants : clé interne `id BIGINT GENERATED ALWAYS AS IDENTITY` jamais exposée ; identifiant externe `public_id UUID`
   unique. *(Reprise du principe `id`/`publicId` d'`app-service`, mais en UUID natif plutôt qu'en nanoid, pour éviter une
   dépendance supplémentaire — à confirmer.)*
5. Aucun secret versionné ; `.env` ignoré, `.env.example` à jour quand une variable est ajoutée.
