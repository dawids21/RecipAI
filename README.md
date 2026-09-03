# RecipAI

A personal cooking assistant: save recipes, import them from links or photos with AI, plan meals on
a calendar, and turn those plans into shopping lists — with any of it shareable with other users.

A monorepo of two independent applications:

- **`backend/`** — Spring Boot REST API (Java 26, PostgreSQL, Spring AI, OAuth2/JWT)
- **`mobile/`** — Flutter app for Android (Firebase Google Sign-In)

## Features

- **Recipes** — create manually, or extract from a web page or an image; organise into collections
- **Meal planning** — plan recipes or placeholders on a calendar, and generate a shopping list from a plan
- **Shopping lists** — add items manually or from a recipe; duplicates merge by quantity
- **Sharing** — recipes, meal plans, and shopping lists share via an invite the recipient accepts
- **Usage limits** — per-user quotas on the resources each module creates

## Documentation

Start at [`docs/INDEX.md`](docs/INDEX.md), which indexes the product, architecture, per-module, and
coding-standard documents. Running the project locally is covered in
[`docs/project/local-development.md`](docs/project/local-development.md).

## License

[MIT](LICENSE)
