# 2026-11-17 — Adaptation de `docs/` et `CLAUDE.md` depuis `app-service`

Entrée : archives `app-service` (NestJS, hexagonal, 21 entrées de journal, 21 décisions) et `odc-connect-backend` (Spring Boot 4.1.2),
`CLAUDE.md` du workspace CIE et capture de l'arborescence cible (`config, controller, dto, exception, mapper, model, repository,
security, service, tests`).

## Fait

- Analyse du code d'`odc-connect-backend` : un seul fichier métier-adjacent (`SecurityConfig`), config Docker/profils, un test.
- `CLAUDE.md` réécrit pour un dépôt unique Spring Boot (le `CLAUDE.md` d'origine décrivait un workspace multi-dépôts CIE).
- `docs/instruction/` : 5 fichiers réécrits pour Java/Spring (architecture en couches, nomenclature, qualité, tests, seeders
  via Flyway). Principes conservés : dépendances vers l'intérieur, le domaine parle en codes, `id` interne vs `publicId`,
  tests autorisés/interdits, séparation référence/démo, idempotence.
- `docs/memoire/` : `ETAT.md`, `PIEGES.md` (17 pièges tirés du code réel), décisions 0001-0004, cette entrée.
- Non repris volontairement : l'historique `app-service` (portages, trésorerie, bureau, etc.), spécifique au club CIE.

## Non fait

- Aucune modification du code, du `pom.xml` ni des dossiers `src/` (dossiers de couches non créés).
- Rien n'a été compilé ni exécuté (pas de `.mvn/wrapper/`, pas de Docker dans l'environnement d'analyse).

## Points à valider avec l'équipe

- Tests sous `src/test` (et non `src/main/.../tests`) ; `publicId` en UUID ; routes `/api/<ressources>` ; erreurs en `ProblemDetail`.

## Ajout (même jour) — `CLAUDE.md` par couche

- Analyse de `ODC_connect.zip` (clone brut) : écarts de package et de `pom.xml` consignés dans `PIEGES.md` / `ETAT.md`.
- 11 `CLAUDE.md` de couche livrés par patch (nouveaux fichiers seulement) ; `CLAUDE.md` racine complété (carte des fichiers de couche).
- Correction de `nomenclature-java.md` : l'exemple de policy `MemberEmailUniquePolicy` impliquait une I/O (contraire à la règle) ;
  remplacé par `MemberStatusTransitionPolicy`. L'unicité se vérifie dans le service + contrainte `UNIQUE` en base.
- Gabarits de code **non compilés** (Maven Central inaccessible depuis l'environnement d'analyse) : à valider au premier usage.
