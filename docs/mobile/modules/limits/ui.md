# Limits — UI

## Widgets

- Limit Counter (`limit_counter.dart`) - Small caption reading `used / limit noun` (e.g. "3 / 5 recipes"), followed
  by "(resets in 12h)" when the balance carries a countdown, shown in seconds, minutes, hours or days. It appears once
  both the count and the quota have loaded, in either order (see `module.md` → Services and State).

## Behaviour

How quotas and counts load, and the fail-open rule every surface follows, are in `module.md` → Services and State.

## Limited Surfaces

Each surface shows the counter and disables its action at the quota; each is described on its own feature's `ui.md`.

| Surface                                 | Disabled at the quota      |
|-----------------------------------------|----------------------------|
| Create Recipe Screen                    | Save                       |
| Recipes Collection Create Dialog        | Create                     |
| Shopping List Create Dialog             | Create                     |
| Plan Form Dialog (create mode only)     | Create                     |
| URL and Image Extraction Screens        | Extract                    |
| Shopping List Detail Screen             | The add-item field         |
