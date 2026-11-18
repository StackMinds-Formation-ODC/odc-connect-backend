# 0001 — Architecture en couches Spring Boot (et non hexagonale)

Date : 2026-11-17 · Statut : **convention adoptée, à valider par l'équipe** · Détail : [journal](../journal/2026-11-17-adaptation-docs-odc-connect.md)

## Contexte

Les instructions héritées de `app-service` (NestJS) décrivaient une architecture hexagonale (`domain/application/adapter/
infrastructure`, ports in/out, tokens d'injection). `odc-connect-backend` est un projet Spring Boot dont la structure cible,
fournie par l'équipe, est **à plat par type de composant** :
`config, controller, dto, exception, mapper, model, repository, security, service`, plus `tests`.

## Décisions

1. **Adopter ce découpage tel quel** sous `com.stackminds.odc_connect`. Pas de ports/adaptateurs ni d'interfaces
   systématiques : Spring Data fournit déjà l'abstraction de persistance, et un service est concret tant qu'il n'a pas deux
   implémentations (YAGNI).
2. **Règle de dépendance** : `controller -> service -> repository -> model` ; `dto/mapper/exception/security/config`
   transverses et jamais dépendants de `service`/`controller`. Aucun type HTTP hors `controller` et `exception`.
3. **Garder l'esprit de l'hexagonal** : « le domaine parle en codes » (`ErrorCode` levé par les services, traduit en statut
   HTTP uniquement par `GlobalExceptionHandler`) ; l'entité ne fuit jamais (le service renvoie des `*Response`).
4. **Le mapping entité -> DTO se fait dans le service**, dans la transaction : `spring.jpa.open-in-view=false` est configuré.
5. **`config/` vs `security/`** : `config/` = beans `@Configuration` (dont `SecurityConfig`, déjà présent) ; `security/` =
   briques réutilisables (conversion des rôles du JWT, utilisateur courant, constantes de rôles).
6. **Les tests vivent sous `src/test/java`**, même découpage par couche — pas sous `src/main`, malgré le dossier `tests` vu
   sous `src/main/java` dans le projet de référence : ils seraient empaquetés dans le jar et sans accès aux dépendances de
   scope `test`.
7. **Démarrer à plat**, sous-dossiers par fonctionnalité (même nom dans toutes les couches) au-delà de ~10 fichiers par couche.
8. **Pas de MapStruct pour l'instant** (non installé ; ajout subordonné à la configuration des processeurs, cf. `PIEGES.md`).

## Conséquences

- Les 5 fichiers de `docs/instruction/` et `CLAUDE.md` reflètent cette architecture.
- Aucune modification de code n'a été faite : les dossiers de couches seront créés avec la première fonctionnalité.
