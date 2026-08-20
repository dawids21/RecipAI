# Worked curl examples

Copy-paste starting points for the flows that are awkward to guess. The authoritative
endpoint contract — every parameter, status code and rule — lives in
`docs/backend/modules/*/api.md`; read that when you need more than a starting point.

Throughout, `$T` names the caller (who is `$T@local.test`) and `$B` is the base URL:

```bash
T=alice                          # the caller is T@local.test
B=http://localhost:8080
AUTH="Authorization: Bearer $T"
JSON="Content-Type: application/json"
```

Every example pipes through `jq 'del(.trace)'` because dev-profile error bodies embed a
full Java stack trace — see SKILL.md, "Reading responses".

## Recipes — `docs/backend/modules/recipes/api.md`

Create. `data` is the only non-obvious part: `ingredients[].quantity`/`unit` are optional
(use `comment` for "to taste"), and `servingSize` defaults to 1.

```bash
curl -sS -X POST "$B/recipes" -H "$AUTH" -H "$JSON" -d '{
  "name": "Pizza",
  "data": {
    "ingredients": [
      {"name": "flour", "quantity": 300, "unit": "g"},
      {"name": "salt", "comment": "to taste"}
    ],
    "instructions": [{"step": "Make dough"}, {"step": "Bake"}],
    "servingSize": 4
  }
}' | jq 'del(.trace)'
```

Capture the id for follow-up calls:

```bash
RID=$(curl -sS -X POST "$B/recipes" -H "$AUTH" -H "$JSON" -d @recipe.json | jq -r .id)
```

List, filtered three ways:

```bash
curl -sS -H "$AUTH" "$B/recipes" | jq
curl -sS -H "$AUTH" "$B/recipes?collectionId=$CID" | jq
curl -sS -H "$AUTH" "$B/recipes?unassigned=true" | jq
```

`PUT /recipes/$RID` takes the same body as create. `DELETE /recipes/$RID` returns 204.

## Collections — same api.md

```bash
CID=$(curl -sS -X POST "$B/collections" -H "$AUTH" -H "$JSON" \
  -d '{"name":"Italian"}' | jq -r .id)
```

Assign a recipe by including `"recipesCollectionId": "$CID"` in the recipe create/update body.

## Shopping lists — `docs/backend/modules/shopping-lists/api.md`

Item writes are version-gated (first-action-wins). Create does *not* take `baseVersion`;
update and delete both do, and a stale one returns **412** whose body is the current item.

```bash
SID=$(curl -sS -X POST "$B/shopping-lists" -H "$AUTH" -H "$JSON" \
  -d '{"name":"Groceries"}' | jq -r .id)

ITEM=$(curl -sS -X POST "$B/shopping-lists/$SID/items" -H "$AUTH" -H "$JSON" \
  -d '{"name":"Milk","quantity":2.0,"unit":"liters","position":1.0}')
IID=$(echo "$ITEM" | jq -r .id); VER=$(echo "$ITEM" | jq -r .version)

curl -sS -X PUT "$B/shopping-lists/$SID/items/$IID" -H "$AUTH" -H "$JSON" \
  -d "{\"baseVersion\":$VER,\"name\":\"Milk\",\"quantity\":2.0,\"unit\":\"liters\",\"checked\":true,\"position\":1.0}" | jq

curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE \
  "$B/shopping-lists/$SID/items/$IID?baseVersion=$((VER+1))" -H "$AUTH"
```

To *prove* the 412 path, reuse a `baseVersion` you already spent — the second write loses.

## Meal plans — `docs/backend/modules/planning/api.md`

`color` must match `#RRGGBB`. An entry carries either `recipeId` (then `servingSize` is
required) or `placeholderText` (then it must be absent) — never both, never neither.

```bash
PID=$(curl -sS -X POST "$B/meal-plans" -H "$AUTH" -H "$JSON" \
  -d '{"name":"Week 1","color":"#FF5733"}' | jq -r .id)

curl -sS -X POST "$B/meal-plans/$PID/entries" -H "$AUTH" -H "$JSON" \
  -d "{\"date\":\"2026-02-01\",\"recipeId\":\"$RID\",\"servingSize\":4}" | jq
```

Calendar requires all three parameters; `planIds` is comma-separated and may be empty
(which returns `{}`), and the range cannot exceed 3 months:

```bash
curl -sS -H "$AUTH" \
  "$B/meal-plans/calendar?startDate=2026-02-01&endDate=2026-02-28&planIds=$PID" | jq
```

Generate shopping-list items from planned meals (quantities scale by
`entry.servingSize / recipe.servingSize`; placeholders are ignored):

```bash
curl -sS -X POST "$B/meal-plans/generate-shopping-list" -H "$AUTH" -H "$JSON" \
  -d "{\"planIds\":[\"$PID\"],\"selectedDates\":[\"2026-02-01\"]}" | jq
```

## Sharing — a full round-trip

Share/unshare bodies are `{"email": "..."}`, validated with `@Email`, so the target is the
**full** address — while the recipient authenticates with the **bare** name (SKILL.md,
"Identity"). Sharing grants EDITOR; only the owner may delete.

```bash
ALICE="Authorization: Bearer alice"; BOB="Authorization: Bearer bob"

curl -sS -o /dev/null -w 'before=%{http_code}\n' -H "$BOB" "$B/recipes/$RID"   # 403
curl -sS -o /dev/null -w 'share=%{http_code}\n' -X POST "$B/recipes/$RID/share" \
  -H "$ALICE" -H "$JSON" -d '{"email":"bob@local.test"}'
curl -sS -H "$ALICE" "$B/recipes/$RID/shared_users" | jq -c
curl -sS -o /dev/null -w 'after=%{http_code}\n' -H "$BOB" "$B/recipes/$RID"    # 200

curl -sS -o /dev/null -w 'unshare=%{http_code}\n' -X POST "$B/recipes/$RID/unshare" \
  -H "$ALICE" -H "$JSON" -d '{"email":"bob@local.test"}'
```

Collections, shopping lists and meal plans share the same shape at
`/collections/{id}`, `/shopping-lists/{id}` and `/meal-plans/{id}`.

## Cleanup

There is no bulk reset. Delete what you created, newest first, or accept that the rows stay
until the postgres container is removed:

```bash
for id in $(curl -sS -H "$AUTH" "$B/recipes" | jq -r '.[].id'); do
  curl -sS -o /dev/null -X DELETE "$B/recipes/$id" -H "$AUTH"
done
```
