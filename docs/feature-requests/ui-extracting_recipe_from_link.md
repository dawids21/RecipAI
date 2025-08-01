## FEATURE:

Implement a feature in the mobile application called "import". It should add a screen that allows users to import recipe
from a web page.
New screen should have an input on the top for the URL, web view below it to display the web page and FAB to import the
recipe.
User can open new screen using a FAB button in the recipe list screen.
After importing the recipe user should be taken back to the recipe list screen and the imported recipe should be added
to the list.
To extract the recipe from the web page, first copy the HTML content of the page using provided example and then use
extracting endpoint from the API.

## EXAMPLES:

### Extracting text from a web page

```dart
class SimpleWebView extends StatefulWidget {
  const SimpleWebView({super.key});

  @override
  State<SimpleWebView> createState() => _SimpleWebViewState();
}

class _SimpleWebViewState extends State<SimpleWebView> {
  late final WebViewController _controller;
  bool isLoading = false;
  String? _lastProcessedUrl;

  @override
  void initState() {
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..loadRequest(Uri.parse('https://flutter.dev'))
      ..addJavaScriptChannel(
        "TestChannel",
        onMessageReceived: (message) {
          print(message.message);
        },
      )
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            if (request.url.contains('blocked.com')) {
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
          onProgress: (prog) {
            setState(() {
              isLoading = prog != 100;
            });
          },
          //there is more
          onPageFinished: (url) {
            if (_lastProcessedUrl == url) {
              return;
            }
            _lastProcessedUrl = url;
            print('Finished loading: $url');
            _controller.runJavaScript("""
              if (document.readyState === 'complete' || document.readyState === 'interactive') {
                if (window.TestChannel) {
                  window.TestChannel.postMessage(document.title || 'No title');
                }
              } else {
                document.addEventListener('DOMContentLoaded', function() {
                  if (window.TestChannel) {
                    window.TestChannel.postMessage(document.title || 'No title');
                  }
                });
              }
            """);
          },
        ),
      );
    // _controller = WebViewController()
    //   ..loadHtmlString('<h1>Hello, Flutter!</h1>');
    super.initState();
  }

  @override
  void dispose() {
    _controller.removeJavaScriptChannel("TestChannel");
    super.dispose();
  }

  void goBack() async {
    if (await _controller.canGoBack()) {
      _controller.goBack();
    }
  }

  void goForward() async {
    if (await _controller.canGoForward()) {
      _controller.goForward();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Text('Test WebView'),
        SizedBox(height: 800, child: WebViewWidget(controller: _controller)),
        if (isLoading) const CircularProgressIndicator(),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            IconButton(onPressed: goBack, icon: const Icon(Icons.arrow_back)),
            IconButton(
              onPressed: goForward,
              icon: const Icon(Icons.arrow_forward),
            ),
          ],
        ),
      ],
    );
  }
}
```

## DOCUMENTATION:

- `docs/prd.md` - Product Requirements Document (PRD)
- `docs/backend/api.md` - API documentation for the backend service
- `docs/mobile/ui.md` - UI design specifications
- `docs/mobile/mobile.md` - Mobile application architecture

## OTHER CONSIDERATIONS:

- don't copy example one-to-one, adapt it to your needs
- create new feature folder `features/import/`