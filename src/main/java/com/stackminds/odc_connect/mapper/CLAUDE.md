# CLAUDE.md — `mapper/` (entité ↔ DTO)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md`.

## Rôle

Convertir entre `model/` et `dto/`, **et rien d'autre**. Classes sans état, déterministes, sans I/O.

## Ce qu'on crée ici

`<Entite>Mapper.java` — un `@Component` par entité racine. Un mapper de sous-objet (`AddressMapper`) seulement s'il est
réutilisé par plusieurs mappers.

## Gabarit

```java
@Component
public class MemberMapper {

	public MemberResponse toResponse(Member member) {
		return new MemberResponse(
			member.getPublicId(),
			member.getEmail(),
			member.getDisplayName(),
			member.getStatus(),
			member.getCreatedAt());
	}

	public Member toEntity(CreateMemberRequest request) {
		return new Member(request.email(), request.displayName());
	}

	/** Applique un PATCH : les champs null ne changent rien. */
	public void applyUpdate(Member member, UpdateMemberRequest request) {
		if (request.displayName() != null) {
			member.rename(request.displayName());
		}
		if (request.status() != null) {
			member.changeStatus(request.status());
		}
	}
}
```

## Règles

- **Direction** : `toResponse(entité)`, `toEntity(CreateRequest)`, `applyUpdate(entité, UpdateRequest)`. `toEntity` appelle le
  **constructeur** de l'entité (qui fixe `publicId`, statut initial) ; `applyUpdate` appelle les **méthodes de comportement**
  de l'entité (pas de setters, il n'y en a pas).
- **Aucune règle métier** (unicité, transition de statut, droits) : si une `if` exprime une règle, elle va dans `service/`.
  Un mapper ne lève pas de `BusinessException`.
- **Aucun accès** à un repository, au contexte de sécurité, à l'horloge, ni à un autre service. Les références (ex. `roleId` →
  entité `Role`) sont **résolues par le service**, qui passe l'entité au mapper : `toEntity(request, role)`.
- **Toujours appelé dans la transaction du service** (`open-in-view=false`) : `toResponse` lit des champs/relations lazy.
  Pour une relation lazy, le repository la charge d'avance (`@EntityGraph`) ; sinon `LazyInitializationException`.
- **Null-safe** : une relation facultative absente donne `null` dans la réponse, pas une exception.
- **Listes** : `members.stream().map(mapper::toResponse).toList()` dans le service ; pour une page :
  `PageResponse.from(page.map(mapper::toResponse))`. Pas de méthode `toResponses` sauf besoin réel.
- **Pas de champ d'état** ni de `static` mutable ; injection par constructeur si un mapper en utilise un autre.
- Écrits **à la main**. MapStruct n'est pas installé ; l'ajouter exige de déclarer ses processeurs (et `lombok-mapstruct-binding`)
  dans `maven-compiler-plugin` (voir `docs/memoire/PIEGES.md`) et une décision datée.

## Interdits

- Dépendre de `service`, `controller`, `repository`, `security`.
- Modifier une entité en dehors de ses méthodes de comportement ; reconstruire `publicId` ou `createdAt`.
- Renvoyer l'entité elle-même, ou y laisser passer `id`.
- Mapper dans le contrôleur ou dans le repository.

## Tests

`mapper/<Entite>MapperTest` — **seulement** s'il y a de la logique (valeurs par défaut, null, listes, conversions). Un mapper qui
recopie des champs 1:1 n'a pas de test dédié : il est couvert par les tests de service et de contrôleur.

## Checklist « nouveau mapper »

1. DTO et entité existent. 2. `toResponse` ne laisse sortir aucun champ interne. 3. `applyUpdate` n'appelle que des méthodes
d'entité. 4. Aucun import de `service`/`repository`.
