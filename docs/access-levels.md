# CLEAR Access Levels

## Purpose

CLEAR uses an implicit home-jurisdiction access model with explicit overrides.

Each Contact keeps a home jurisdiction in the Contact table through `jurisdictionId`. That home jurisdiction is the user's default jurisdiction when they sign in. The `ContactJurisdictionAccess` table stores only overrides. It does not store every access relationship.

Admin users are a special case. They have full access to all jurisdictions and are not represented in `ContactJurisdictionAccess`.

## Effective Access Rules

For a non-admin user, the effective role for a jurisdiction is resolved in this order:

1. If a `ContactJurisdictionAccess` row exists for that contact and jurisdiction, use that role.
2. Otherwise, if the jurisdiction matches `Contact.jurisdictionId`, treat the user as `VIEWER`.
3. Otherwise, treat the user as `NOT_AUTHORIZED`.

This means the override table is used to expand, change, or block access relative to the default home-jurisdiction relationship.

## Role Definitions

### PRIMARY_REPORTER

- Can view and edit EntryForInterop data.
- Default user interaction starts in edit/update mode.
- This role is the primary reminder-email target for that jurisdiction.
- Only one primary reporter override should exist for a jurisdiction at a time.

### SECONDARY_REPORTER

- Can view and edit EntryForInterop data.
- Default user interaction starts in edit/update mode.
- This role is an explicit override only.

### VIEWER

- Can view and edit EntryForInterop data.
- Default user interaction starts in view-first mode.
- This role is usually implicit for the user's home jurisdiction.
- It can also be stored explicitly as an override for another jurisdiction when needed.

### NOT_AUTHORIZED

- Cannot view or edit EntryForInterop data for that jurisdiction.
- Can be used to block a user from their home jurisdiction or any other jurisdiction.

## Interaction Defaults

The difference between reporter roles and viewer is the initial mode presented in the UI, not whether editing is allowed.

| Effective Role | Can View | Can Edit | Default UI Mode |
| --- | --- | --- | --- |
| PRIMARY_REPORTER | Yes | Yes | Edit |
| SECONDARY_REPORTER | Yes | Yes | Edit |
| VIEWER | Yes | Yes | View |
| NOT_AUTHORIZED | No | No | None |
| Admin | Yes | Yes | Edit |

## Reminder Emails

Reminder emails should target the `PRIMARY_REPORTER` override for the jurisdiction.

If no primary reporter override exists, there is currently no automatic fallback to secondary reporter or viewer.

## Why Overrides Only

The override-only model keeps the data smaller and easier to maintain.

- Most users only need access to their home jurisdiction.
- Home-jurisdiction access is already implied by the Contact record.
- Overrides are only needed for exceptions:
  - cross-jurisdiction access,
  - blocking a home-jurisdiction user,
  - explicitly marking reminder ownership.

## Automatic Schema Creation

No manual database migration script is required for this feature.

The application uses Hibernate automatic schema update behavior through the existing configuration in `hibernate.cfg.xml`. Once the new entity is registered there, the table should be created automatically in environments where Hibernate schema updates are enabled.