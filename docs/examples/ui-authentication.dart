import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_auth_test/firebase_options.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:google_sign_in/google_sign_in.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  await GoogleSignIn.instance.initialize();
  runApp(const MyApp());
}

class AuthChangeNotifier extends ChangeNotifier {
  String _user = "Guest";

  String get user => _user;

  void login(String username) {
    _user = username;
    notifyListeners();
  }

  void logout() {
    _user = "Guest";
    notifyListeners();
  }
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final AuthChangeNotifier _authChangeNotifier = AuthChangeNotifier();

  @override
  void initState() {
    FirebaseAuth.instance.userChanges().listen(
      _handleUserChanges,
      onError: _handleUserChangesError,
    );
    super.initState();
  }

  Future<void> _handleUserChanges(User? user) async {
    if (user != null) {
      print('User token: ${await user.getIdToken()}');
      _authChangeNotifier.login(user.displayName ?? "Unknown");
    } else {
      _authChangeNotifier.logout();
    }
  }

  Future<void> _handleUserChangesError(Object error) async {
    print(error);
  }

  Future<void> _signIn() async {
    final GoogleSignInAccount googleSignInAccount = await GoogleSignIn.instance
        .authenticate();
    final GoogleSignInAuthentication googleSignInAuthentication =
        googleSignInAccount.authentication;
    final OAuthCredential credential = GoogleAuthProvider.credential(
      idToken: googleSignInAuthentication.idToken,
    );
    await FirebaseAuth.instance.signInWithCredential(credential);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme
            .of(context)
            .colorScheme
            .inversePrimary,
        title: Text('Flutter Firebase Auth Demo'),
      ),
      body: ListenableBuilder(
        listenable: _authChangeNotifier,
        builder: (BuildContext context, Widget? child) =>
            Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [Text('Hello ${_authChangeNotifier.user}!')],
              ),
            ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _signIn,
        tooltip: 'Login',
        child: const Icon(Icons.login),
      ),
    );
  }
}
