## FEATURE:

Migrate current navigation code in mobile app to use `go_router` package

## EXAMPLES:

```dart
enum AppRoute {
  outerInner("inner");

  final String path;

  const AppRoute(this.path);
}

final GoRouter appRouter = GoRouter(
  initialLocation: '/',
  errorBuilder: (context, state) =>
      MyErrorPage(
        text: "Error Page: ${state.error?.toString() ?? 'Unknown Error'}",
      ),
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) =>
          MyHomePage(
            callbacks: {
              "simple": () {
                // go will replace the navigation stack with all routes defined for this URL
                // it this example go /outer/inner wil replace stack with pages for /outer and /outer/inner
                // push will add a new page to the stack like in Navigator.push
                // context.go('/simple');
                context.push('/simple');
              },
              "simple with param": () {
                context.push('/simple/Hello%20World');
              },
              "simple with param and extra": () {
                context.push('/simple/extra', extra: {'text': 'Hello World'});
              },
              "simple with query": () {
                context.push('/simple/query?text=Hello%20World');
              },
              "outer": () {
                context.go('/outer');
              },
              "outer inner": () {
                // context.go('/outer/inner');
                context.goNamed(AppRoute.outerInner.name);
              },
              "error": () {
                // This will trigger an error page
                context.push('/error');
              },
            },
          ),
    ),
    GoRoute(path: '/simple', builder: (context, state) => const MySimplePage()),
    GoRoute(
      path: '/simple/:text',
      builder: (context, state) {
        final text = state.pathParameters['text']!;
        return MySimplePageWithParam(text: text);
      },
    ),
    GoRoute(
      path: '/simple/extra',
      builder: (context, state) {
        final text = state.pathParameters['text']!;
        return MySimplePageWithParam(text: text);
      },
    ),
    GoRoute(
      path: '/simple/query',
      builder: (context, state) {
        final text = state.uri.queryParameters['text'] ?? 'Default Text';
        return MySimplePageWithParam(text: text);
      },
    ),
    GoRoute(
      path: "/outer",
      builder: (context, state) =>
          MyHomePage(
            callbacks: {
              "back to home": () {
                // This will replace the current page with the home page
                context.go('/');
              },
              "inner": () {
                // Because we are using enum I can't use it here as it is relative path
                // I would nee to use goNamed
                context.go('/outer/inner');
              },
            },
          ),
      routes: [
        GoRoute(
          path: AppRoute.outerInner.path,
          builder: (context, state) => MySimplePage(),
          name: AppRoute.outerInner.name,
        ),
      ],
    ),
  ],
);
```

## DOCUMENTATION:

- [go_router documentation](https://pub.dev/packages/go_router)

## OTHER CONSIDERATIONS:

- create `routes.dart` in core folder to define all routes (global appRouter variable)
- use nested routes
- current screens should become:
    - recipe list: `/recipes`
    - recipe details: `/recipes/:id`
    - import: `/recipes/import`
    - create recipe: `/recipes/create`
- use enums for route names
- update `mobile/CLAUDE.md` with new rules for `go_router`