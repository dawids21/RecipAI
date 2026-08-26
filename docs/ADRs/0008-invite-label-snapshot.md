# ADR-0008: A pending invite carries a display label snapshotted at invite time

**Date:** 2026-08-26
**Status:** proposed
**Related ADRs:** [ADR-0007](0007-shared-permissions-module.md)

## Context

Sharing a resource in RecipAI creates a pending invite that grants no access until the invitee accepts. The
invitee discovers their invites from a single in-app surface listing everything waiting for them across all
shareable resource types — recipes, recipes collections, shopping lists and meal plans.

That surface has to tell them what they are being asked to accept. "You have been invited to a recipe" is not
an answerable prompt; the invitee needs the resource's name and who sent it before they can decide. So each
listed invite needs a human-readable title.

This collides with the boundary the `permissions` module is built on (ADR-0007): the module treats resource
types as opaque keys and holds no domain knowledge. It cannot read a recipe's title, because knowing that a
recipe has a title — and where to find it — is exactly the knowledge the boundary excludes.

A further constraint narrows the options. While an invite is pending, the resource must be genuinely
unreadable by the invitee: absent from every list, and not fetchable. Any mechanism that resolves the title
by reading the resource on the invitee's behalf has to carve an exception into that rule.

## Decision

The module that creates an invite supplies a **display label** at creation time, and the `permissions`
module stores it as an opaque string it never interprets. The invitee's list renders that stored label.

The label is a snapshot. It is not refreshed while the invite is pending, and it is not reconciled against
the resource. If the resource is renamed before the invite is answered, the invitee sees the name the
resource had when they were invited. Accepting grants access, at which point every surface shows the current
name, so the stale value never outlives the invite.

An invite is always created with a label; the inviting module is responsible for supplying a sensible one,
including a fallback when the resource has no meaningful name.

## Alternatives considered

- **Resolve names in the `permissions` module with a query across the resource tables** — a single
  view-style query fetching the current name per resource type, kept in one maintained place. Always fresh,
  and the domain knowledge is confined to one query rather than scattered. Rejected because it puts
  table-level knowledge of all four resource types inside a module whose entire value rests on having none,
  re-creating the coupling through SQL rather than through code.
- **Return opaque references and let the client resolve names** — the invitee's app fans out per resource
  type to fetch titles. Always fresh and keeps the module clean, but it requires a lookup that works for
  someone with no access to the resource, which means punching a "a pending invitee may read the title" hole
  in the unreadability rule this feature exists to establish.
- **A registered naming capability the `permissions` module calls back into** — each module registers how
  to name its resources. Keeps names fresh with no read hole, but inverts the dependency direction the facade
  convention sets and hides the coupling in startup wiring.

## Consequences

- The `permissions` module stays genuinely domain-free: it stores a string and renders nothing about it.
- The invitee's list is answerable from one query with no fan-out, no joins across domain tables, and no
  per-type special casing.
- **Labels can go stale.** A resource renamed while an invite is pending shows the invitee the old name. This
  is cosmetic, bounded by the life of the invite, and accepted deliberately.
- Every path that creates an invite must supply a label, including any future resource type. A missing or
  empty label degrades the invitee's surface rather than failing it, so the fallback is the inviting module's
  responsibility.
- If stale labels later prove to matter, refreshing them is an additive change — the stored label can become
  a cache the inviting module updates on rename — without revisiting the boundary.
