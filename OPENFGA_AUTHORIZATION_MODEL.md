# OpenFGA Authorization Model

This project uses a split model:

- Spring Authorization Server + external IDPs handle authentication (who the user is)
- OpenFGA handles authorization (what the user can do) dynamically

## Responsibility Split

- Authentication (Spring Auth + IDP):
  - Login, MFA, OIDC federation
  - Token issuance (JWT/access token)
  - Identity claims (`sub`, `email`, `iss`, tenant/company context)
- Authorization (OpenFGA):
  - Role and permission checks
  - Resource-level and relationship-based access
  - Dynamic changes without token re-issuance

## Recommended Runtime Flow

1. User authenticates with Spring Auth via internal login or external IDP.
2. Spring issues token with stable identity context (user, tenant/company, registration id).
3. API receives request and validates JWT.
4. API calls OpenFGA `check` (or `list-objects`) for the requested action/resource.
5. API allows or denies based on OpenFGA response.

## Example OpenFGA Model (IKEA B2B)

```fga
model
  schema 1.1

type user

type company
  relations
    define member: [user]
    define admin: [user]

type resource
  relations
    define owner_company: [company]
    define can_view: member from owner_company or admin from owner_company
    define can_edit: admin from owner_company
```

## Example Tuples

```text
company:acme#member@user:alice
company:acme#admin@user:bob
resource:order-123#owner_company@company:acme
resource:order-999#owner_company@company:prolog
```

## Example Checks

- Can Alice view order-123?
  - `check(user:user:alice, relation:can_view, object:resource:order-123)` -> true
- Can Alice edit order-123?
  - `check(user:user:alice, relation:can_edit, object:resource:order-123)` -> false
- Can Bob edit order-123?
  - `check(user:user:bob, relation:can_edit, object:resource:order-123)` -> true

## Integration Notes for Spring Services

- Keep coarse scopes in JWT (optional), but enforce final access with OpenFGA.
- Do not hardcode authorization decisions in controllers.
- Add an authorization service adapter, for example `OpenFgaAuthorizationService`.
- Cache only short-lived check results if needed; prefer correctness over stale grants.
- For write operations, run OpenFGA checks in the request path.

## Admin and Data Lifecycle

- Company onboarding:
  - Register company/IDP in Spring data model.
  - Create OpenFGA tuples linking users -> company roles.
- Resource onboarding:
  - Write `resource#owner_company@company` tuples.
- Access changes:
  - Update tuples in OpenFGA (effective immediately).

## Why This Split

- Authentication remains centralized in Spring Auth and existing IDP federation logic.
- Authorization becomes dynamic, relationship-based, and auditable in OpenFGA.
- B2B tenant/resource permissions can be managed without changing token issuance logic.

