# CLAUDE.md — `service/` (logique métier)

> Instruction de projet : **lecture seule pour les agents IA**. Complète `docs/instruction/architecture-couches-spring.md` et `qualite-de-code.md`.

## Rôle

Porter les règles métier et **ouvrir les transactions**. C'est le seul endroit qui orchestre repositories, mappers et policies.
Entrée : `*Request` + identifiants. Sortie : `*Response`. **Jamais** d'entité en entrée ni en sortie, **aucun** type HTTP.

## Ce qu'on crée ici

| Fichier | Contenu |
|---|---|
| `<Fonctionnalite>Service.java` | `@Service`, une par fonctionnalité (`MemberService`) |
| `policy/<Regle>Policy.java` | règle **pure** sur des objets déjà chargés (`MemberStatusTransitionPolicy`) |

Pas d'interface `XxxService` + `XxxServiceImpl` tant qu'il n'existe pas deux implémentations.

## Gabarit

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)               // lecture par défaut
public class MemberService {

	private final MemberRepository repository;
	private final MemberMapper mapper;
	private final MemberStatusTransitionPolicy transitions;

	@Transactional                             // écriture : on surcharge
	public MemberResponse create(CreateMemberRequest request) {
		if (repository.existsByEmailIgnoreCase(request.email())) {
			throw new BusinessException(ErrorCode.MEMBER_EMAIL_ALREADY_USED,
				"Un membre avec l'e-mail " + request.email() + " existe déjà");
		}
		Member saved = repository.save(mapper.toEntity(request));
		return mapper.toResponse(saved);       // mapping DANS la transaction
	}

	public MemberResponse getByPublicId(UUID publicId) {
		return mapper.toResponse(load(publicId));
	}

	public PageResponse<MemberResponse> list(Pageable pageable) {
		return PageResponse.from(repository.findAll(pageable).map(mapper::toResponse));
	}

	@Transactional
	public MemberResponse update(UUID publicId, UpdateMemberRequest request) {
		Member member = load(publicId);
		if (request.status() != null) {
			transitions.check(member.getStatus(), request.status());   // policy pure
		}
		mapper.applyUpdate(member, request);   // pas de save() : l'entité est gérée, flush au commit
		return mapper.toResponse(member);
	}

	@Transactional
	public void delete(UUID publicId) {
		repository.delete(load(publicId));
	}

	private Member load(UUID publicId) {
		return repository.findByPublicId(publicId).orElseThrow(() ->
			new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "Membre introuvable : " + publicId));
	}
}

@Component
public class MemberStatusTransitionPolicy {
	public void check(MemberStatus from, MemberStatus to) {   // aucune I/O
		if (from == MemberStatus.ARCHIVED && to != MemberStatus.ARCHIVED) {
			throw new BusinessException(ErrorCode.MEMBER_INVALID_STATUS_TRANSITION,
				"Un membre archivé ne peut plus changer de statut");
		}
	}
}
```

## Règles

**Déroulé d'une opération** : valider (règles métier) → charger → policies → écrire → mapper vers `*Response`.
Garde d'abord (clauses de garde, retours anticipés), jamais d'`if` imbriqués profonds.

**Transactions**
- Classe annotée `@Transactional(readOnly = true)`, méthodes d'écriture `@Transactional`. Jamais sur contrôleur/repository.
- Pas d'appel `this.autreMethodeTransactionnelle()` pour changer le mode (le proxy est contourné) ; extraire dans un autre bean.
- Entité chargée **dans** la transaction = suivie par Hibernate : modifier puis laisser le commit écrire ; `save()` seulement pour
  une entité **nouvelle**.
- Ne pas faire d'appel réseau lent (HTTP externe, e-mail) dans une transaction longue : déclencher après le commit
  (`@TransactionalEventListener(phase = AFTER_COMMIT)`) ou en dehors.

**Erreurs**
- Règle violée → `BusinessException(ErrorCode.X, "message français")`. Jamais `ResponseStatusException`, `HttpStatus`,
  `RuntimeException` nue, ni `catch (Exception)`.
- **Unicité** : vérifier avant (`existsBy…`) pour un message clair **et** garder la contrainte `UNIQUE` en base comme filet
  (deux requêtes simultanées passent la vérification) ; `DataIntegrityViolationException` est traduite en 409 par le handler.
- Ressource inconnue → `*_NOT_FOUND` (404), jamais `null` ni `Optional.get()`.

**Policies (`policy/`)** : méthodes **pures** — entrées = objets déjà chargés / valeurs, sorties = rien ou exception. Pas de
repository, pas d'horloge cachée (passer `Instant now` en paramètre ou injecter un `Clock`). Extraire une policy quand une
règle est réutilisée par deux services ou rend une méthode illisible ; sinon la laisser en méthode privée.

**Dépendances**
- Un service peut en appeler un autre (sens unique, **jamais de cycle**). Pour *lire* une entité d'une autre fonctionnalité,
  préférer son repository.
- Utilisateur courant : injecter `security/CurrentUser` (bean), ne pas le recevoir du contrôleur sauf besoin de test.
- Intégrations externes (e-mail, HTTP) : un service dédié (`NotificationService`) qui encapsule `JavaMailSender`/client, injecté ici.

**Taille** : ≤ 300 lignes. Au-delà, scinder par cas d'usage (`MemberQueryService` / `MemberCommandService`) ou extraire une policy.

**Journalisation** : `@Slf4j`, `log.info` pour un événement métier marquant (création, suppression, changement de statut), avec
l'identifiant public ; jamais d'e-mail complet, de jeton ni de donnée personnelle superflue.

## Interdits

- Import `org.springframework.http.*`, `jakarta.servlet.*`, `controller`, `*Response` construit à la main (passer par le mapper).
- Retourner/recevoir une entité ; `@Transactional` sur méthode privée ; état mutable dans un champ du service (beans singletons).
- Requête SQL/JPQL ici (elle est dans le repository) ; `Thread.sleep`.

## Tests

`service/<X>ServiceTest` : Mockito (`@ExtendWith(MockitoExtension.class)`), seulement **create/update** et leurs règles/codes
d'erreur ; `service/policy/<X>PolicyTest` : cas limites. Pas de test unitaire pour get/list/delete triviaux. Voir
`src/test/.../CLAUDE.md`.

## Checklist « nouveau service »

1. Repository + mapper + DTO prêts. 2. `ErrorCode` ajoutés (et leur statut dans `GlobalExceptionHandler`). 3. Méthodes avec la
bonne annotation transactionnelle. 4. Mapping dans la transaction. 5. Tests des règles.
