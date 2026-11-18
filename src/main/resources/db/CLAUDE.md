# CLAUDE.md — `db/` (migrations Flyway et seeds)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/nomenclature-java.md` (§ Base de données)
> et `docs/instruction/seeder-gestion.md`.

## Rôle

Le **schéma appartient à Flyway** (`ddl-auto: validate`). Tout changement de modèle = un fichier SQL ici, dans le **même commit**
que l'entité. Sans migration, l'application ne démarre pas.

## Arborescence (à créer au besoin)

```
db/
  migration/                # schéma + données de référence — TOUS les environnements
    V1__create_members.sql
  seed/dev/                 # données de démo — dev et test UNIQUEMENT (R__NNN_seed_<nom>.sql)
```
`db/seed/dev` n'est déclaré que dans `application-dev.yaml` (`spring.flyway.locations`). Voir `seeder-gestion.md`.

> Ce `CLAUDE.md` est dans `src/main/resources` : il sera copié dans le jar. C'est inoffensif (il est dans `db/`, hors de la
> location Flyway `db/migration`). Pour l'exclure : ajouter `<excludes><exclude>**/CLAUDE.md</exclude></excludes>` à la `<resource>` du `pom.xml`.

## Nommage

- Versionnées : `V<n>__<description_snake_case>.sql` (**deux** `_`), `n` entier croissant sans trou volontaire :
  `V1__create_members.sql`, `V2__add_member_status.sql`. Une migration = un changement cohérent.
- Répétables (seeds de démo) : `R__<NNN>_seed_<nom>.sql`.
- **Jamais modifier une migration déjà appliquée** (échec de checksum au démarrage) : ajouter `V<n+1>`.
  Faute avant tout déploiement partagé : seulement avec l'accord explicite de l'équipe (base locale à recréer).

## Gabarit de création de table

```sql
CREATE TABLE members (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id     UUID         NOT NULL DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_members_public_id UNIQUE (public_id),
    CONSTRAINT uk_members_email     UNIQUE (email),
    CONSTRAINT ck_members_status    CHECK (status IN ('PENDING', 'ACTIVE', 'ARCHIVED'))
);
```

Clé étrangère et index :
```sql
ALTER TABLE member_roles
    ADD CONSTRAINT fk_member_roles_member FOREIGN KEY (member_id) REFERENCES members (id);
CREATE INDEX idx_member_roles_member_id ON member_roles (member_id);
```

## Règles

- **Alignement entité ↔ SQL** (Hibernate `validate` le vérifie) : mêmes noms (`snake_case`), mêmes longueurs, même nullabilité.
  Types : `String`→`VARCHAR(n)` · `UUID`→`UUID` · `Instant`→`TIMESTAMPTZ` · `LocalDate`→`DATE` · `BigDecimal`→`NUMERIC(p,s)` ·
  `boolean`→`BOOLEAN` · enum `STRING`→`VARCHAR(n)` + `CHECK` · `@Version`→`BIGINT NOT NULL DEFAULT 0`.
- **Table au pluriel** `snake_case` ; PK `id BIGINT GENERATED ALWAYS AS IDENTITY` ; `public_id UUID NOT NULL UNIQUE` ;
  `created_at`/`updated_at` `TIMESTAMPTZ` (maintenus par Hibernate, pas de trigger).
- **Contraintes nommées** : `uk_<table>_<col>`, `fk_<table>_<ref>`, `ck_<table>_<col>`, `idx_<table>_<col>` (jamais de nom auto).
  Index sur **chaque clé étrangère** et sur les colonnes filtrées/triées.
- **Changements destructifs en deux temps** (expand/contract) : d'abord ajouter (colonne nullable ou avec défaut) et déployer, puis,
  dans une migration ultérieure, renseigner/rendre `NOT NULL`/supprimer. Ajouter une colonne `NOT NULL` à une table non vide exige
  un `DEFAULT` ou un remplissage dans la même migration.
- **Idempotence non requise** pour les `V` (Flyway les joue une fois) ; **requise** pour les `R__` (`INSERT … ON CONFLICT DO NOTHING`).
- **Données de référence nécessaires en prod** (rôles, statuts) : migration `V` (`INSERT`). Démo/fictif : `seed/dev` seulement,
  e-mails en `@example.invalid`, UUID littéraux fixes, **aucun mot de passe ni compte** (l'identité vit chez le fournisseur OAuth2).
- Une migration s'exécute dans une transaction (PostgreSQL) : échec = rollback et arrêt du démarrage. Pas de `CREATE INDEX
  CONCURRENTLY` dans une migration transactionnelle sans le désactiver explicitement (décision datée).
- SQL en minuscules/majuscules cohérentes (mots-clés en MAJUSCULES), indentation 4 espaces, un `;` par instruction.
- Pas de dépendance à `public` implicite si un schéma dédié est adopté plus tard (décision datée).

## Interdits

- Modifier une `V` appliquée ; `DROP TABLE`/`DROP COLUMN` sans plan de reprise écrit dans `docs/memoire/decisions/`.
- `TRUNCATE`/`DELETE` massifs dans une migration de schéma ; seed de démo dans `migration/`.
- Mot de passe, secret ou donnée personnelle réelle dans un fichier SQL.
- Laisser Hibernate créer/modifier le schéma (`ddl-auto` ≠ `validate`).

## Checklist « nouvelle migration »

1. Numéro = dernier + 1. 2. Nom `V<n>__<verbe>_<objet>.sql`. 3. Contraintes et index nommés. 4. Entité alignée (même commit).
5. Test d'intégration repository (Testcontainers) qui l'exerce. 6. `docs/memoire/ETAT.md` mis à jour.
