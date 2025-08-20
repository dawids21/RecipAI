## FEATURE:

I want to use ChangeNotifier for the recipes list in the UI instead of a StatefulWidget.
This will allow to update the list from anywhere in the app.
Create `recipe_list_model.dart` in the `mobile/lib/features/recipe/` directory.
Define a `RecipeListModel` class that extends `ChangeNotifier` and manages the list of recipes.
Define a `InheritedRecipeListModel` class that extends `InheritedNotifier<RecipeListModel>` to provide the model to the
widget tree.
Add lazy loading for the recipes list, so that it only fetches recipes when the list is displayed (use provided below).
In the model add method to refresh the recipes list, which can be called from anywhere in the app.
Add call to the refresh method in the following files:

- `create_recipe_screen.dart` - after creating
- `edit_recipe_screen.dart` - after editing
- `recipe_detail_screen.dart` - after deleting

## EXAMPLES:

```dart
import 'package:flutter/material.dart';

void main() => runApp(const MyApp());

class CounterModel extends ChangeNotifier {
  int _countApiValue = 0; // to simulate changes from other place using API
  late Future<int> _count = _initCount();

  Future<int> get count => _count;

  Future<int> _initCount() {
    print("initCount");
    return Future.delayed(const Duration(seconds: 5), () {
      print('Fetched count - Initial: $_countApiValue');
      return _countApiValue;
    });
  }

  void increment() {
    print("Increment called - current value: $_countApiValue");
    _countApiValue++;
    _count = Future.delayed(const Duration(seconds: 4), () {
      print('Fetched count - After increment: $_countApiValue');
      return _countApiValue;
    });
    print("About to notify listeners");
    notifyListeners();
    print("Listeners notified");
  }
}

class CounterInheritedModel extends InheritedNotifier<CounterModel> {
  const CounterInheritedModel({
    super.key,
    super.notifier,
    required super.child,
  });

  static CounterModel of(BuildContext context) {
    final CounterInheritedModel? result = context
        .dependOnInheritedWidgetOfExactType<CounterInheritedModel>();
    assert(result != null, 'No CounterInheritedModel found in context');
    return result!.notifier!;
  }
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final counterModel = CounterModel();

  @override
  Widget build(BuildContext context) {
    return CounterInheritedModel(
      notifier: counterModel,
      child: MaterialApp(
        title: 'Flutter Demo',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(colorSchemeSeed: Colors.blue),
        home: const MyHomePage(title: 'Flutter Demo Home Page'),
      ),
    );
  }
}

class MyHomePage extends StatefulWidget {
  final String title;

  const MyHomePage({super.key, required this.title});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool isCounterVisible = false;

  void toggleCounter() {
    setState(() {
      isCounterVisible = !isCounterVisible;
    });
  }

  @override
  Widget build(BuildContext context) {
    final counterModel = CounterInheritedModel.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('You have pushed the button this many times:'),
            if (isCounterVisible)
              FutureBuilder<int>(
                future: counterModel.count,
                builder: (BuildContext context, AsyncSnapshot<int> snapshot) {
                  print(
                    "FutureBuilder rebuilding - hasData: ${snapshot.hasData}, connectionState: ${snapshot
                        .connectionState}",
                  );

                  List<Widget> children;
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    children = const <Widget>[
                      SizedBox(
                        width: 60,
                        height: 60,
                        child: CircularProgressIndicator(),
                      ),
                      Padding(
                        padding: EdgeInsets.only(top: 16),
                        child: Text('Awaiting result...'),
                      ),
                    ];
                  } else {
                    children = <Widget>[
                      const Icon(
                        Icons.check_circle_outline,
                        color: Colors.green,
                        size: 60,
                      ),
                      Padding(
                        padding: const EdgeInsets.only(top: 16),
                        child: Text('${snapshot.data}'),
                      ),
                    ];
                  }
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: children,
                    ),
                  );
                },
              ),
            ElevatedButton(
              onPressed: toggleCounter,
              child: const Text('Toggle counter'),
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: counterModel.increment,
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
  }
}
```

## DOCUMENTATION:

- `docs/` - App related documentation

## OTHER CONSIDERATIONS:

- Don't use any third party package for state management.
- Use ChangeNotifier and InheritedNotifier
- Create the ChangeNotifier object in the main.dart file and pass it using InheritedNotifier
