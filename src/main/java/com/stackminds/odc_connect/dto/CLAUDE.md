# CLAUDE.md — `dto/` (contrat d'API)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md`.

## Rôle

Définir **exactement** ce qui entre et sort de l'API, indépendamment du schéma de base. Un DTO est un `record` immuable.
Dépend de Jakarta Validation et de types JDK ; **pas** de `model/` (sauf un enum métier partagé, voir plus bas).

## Ce qu'on crée ici

| Fichier | Usage | Validation |
|---|---|---|
| `Create<Entite>Request.java` | corps d'un `POST` | oui, stricte |
| `Update<Entite>Request.java` | corps d'un `PATCH`/`PUT` | oui |
| `<Entite>Response.java` | réponse détaillée | non |
| `<Entite>SummaryResponse.java` | élément de liste, si le détail est lourd | non |
| `<Nom>Filter.java` / `<Nom>Query.java` | paramètres de recherche | oui |
| `PageResponse.java` | enveloppe de pagination (une seule, partagée) | non |

## Gabarits

```java
public record CreateMemberRequest(
	@NotBlank(message = "L'e-mail est obligatoire")
	@Email(message = "L'e-mail est invalide")
	@Size(max = 254, message = "L'e-mail dépasse 254 caractères")
	String email,

	@NotBlank(message = "Le nom affiché est obligatoire")
	@Size(max = 120, message = "Le nom affiché dépasse 120 caractères")
	String displayName
) {}

// PATCH : tous les champs facultatifs ; null = « ne pas modifier ».
public record UpdateMemberRequest(
	@Size(min = 1, max = 120, message = "Le nom affiché doit faire 1 à 120 caractères")
	String displayName,
	MemberStatus status
) {}

public record MemberResponse(
	UUID publicId,        // jamais l'id interne
	String email,
	String displayName,
	MemberStatus status,
	Instant createdAt
) {}

public record PageResponse<T>(
	List<T> content, int page, int size, long totalElements, int totalPages
) {
	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
			page.getTotalElements(), page.getTotalPages());
	}
}
```

## Règles

- **`record`**, jamais de classe Lombok pour un DTO. Pas de logique : au plus un constructeur compact qui **normalise**
  (`email = email == null ? null : email.strip().toLowerCase()`), jamais de règle métier.
- **Validation syntaxique ici, validation métier dans `service/`.** Ici : obligatoire, longueur, format, borne, motif. Là-bas :
  unicité, existence d'une référence, transitions d'état, droits.
- **Message de validation en français**, explicite, dans l'annotation (`message = "…"`). Les longueurs reprennent celles de la
  colonne SQL.
- `@Valid` sur les DTO imbriqués et sur les éléments de liste (`List<@Valid LineRequest>`). Limiter la taille des listes
  (`@Size(max = …)`) : on ne laisse pas un client envoyer 10⁶ éléments.
- **Entrée vs sortie** : un `*Request` ne contient jamais `id`, `publicId`, `createdAt` ni un champ que le serveur calcule.
  Un `*Response` ne contient jamais `id` interne, secret, hash, champ technique (`version`, flags internes) ni entité.
- **Références à d'autres ressources** : par `publicId` (`UUID roleId`), jamais par `id` interne ni par objet complet.
- **Types** : dates/horodatages `Instant` (ISO-8601 UTC) ou `LocalDate` ; montants `BigDecimal` (+ `@Digits`, `@PositiveOrZero`) ;
  énumérations en nom de constante ; identifiants `UUID` ; booléens `boolean` / `Boolean` (nullable si « non précisé » compte).
- **PATCH vs PUT** : `PATCH` = champs facultatifs (null ignoré). Si « effacer un champ » doit être possible, ne pas le déduire de
  `null` : prévoir une opération dédiée. `PUT` = remplacement complet, tous les champs obligatoires.
- **Pagination** : toujours renvoyer `PageResponse<…Response>` ; ne jamais renvoyer un `Page`/`PageImpl` Spring (sérialisation
  JSON instable, expose des détails internes).
- **Enum métier** : réutiliser l'enum de `model/enums` dans le DTO est toléré (c'est le contrat). Si le contrat d'API doit
  différer du modèle, créer un enum dans `dto/`.
- Un DTO par usage : ne pas réutiliser `CreateXRequest` pour la mise à jour « parce qu'il est presque pareil ».
- Pas de `@JsonProperty` sauf nécessité (le nom Java **est** le nom JSON, en `camelCase`).

## Interdits

- Entité (`model`) comme champ ; `id` interne ; `Page`/`Pageable` Spring dans un `*Response`.
- Annotation `jakarta.persistence.*` ; accès à un repository ; code qui lit l'utilisateur courant.
- `Map<String,Object>` ou `JsonNode` comme corps : le contrat doit être typé.
- Valeurs par défaut cachées dans le DTO : elles se décident dans le service.

## Tests

`dto/<Nom>Test` (unitaire, sans Spring) : construire un `Validator` (`Validation.buildDefaultValidatorFactory()`), vérifier les
champs obligatoires, bornes, formats, et le constructeur compact. Un cas valide + un cas par contrainte.

## Checklist « nouveau DTO »

1. Écrire les `*Request` en reprenant les contraintes de la migration. 2. `*Response` sans champ interne.
3. Messages en français. 4. Test de validation. 5. Vérifier le mapper correspondant (`mapper/CLAUDE.md`).
