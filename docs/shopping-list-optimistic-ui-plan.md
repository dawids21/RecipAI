# Shopping List Optimistic UI Implementation Plan

## Goal

Build an optimistic UI system for adding and removing items from shopping lists that:

- **Immediately reflects user actions** in the UI without waiting for API responses
- **Queues operations** (ADD and DELETE) for asynchronous processing with the backend
- **Handles conflicts gracefully** by fetching the latest version and re-applying pending operations
- **Continues syncing in the background** even when the user navigates away from the detail screen
- **Provides visual feedback** with a non-blocking sync indicator in the AppBar
- **Parses user input** to extract quantity, unit, and name using regex pattern matching
- **Maintains data consistency** using version-based optimistic concurrency control (ETags)

### Success Criteria

- User can add items by typing in a text field; items appear immediately in the list
- User can delete items with an 'X' button; items disappear immediately from the list
- Operations sync with backend in the order they were created, one at a time
- 412 Conflict errors trigger a fetch, discard the conflicted operation, and continue processing remaining operations
- Sync indicator appears in AppBar only when operations are pending
- Polling continues every 10 seconds when no operations are being processed
- System works correctly when user switches between multiple shopping lists
- All existing tests pass

## Context

### Documentation and References

**Project Documentation** (must read):

- `/home/dawid/Projects/RecipAI/docs/prd.md` - Product overview and user stories
- `/home/dawid/Projects/RecipAI/docs/mobile/architecture.md` - Repository-Service-View pattern, AsyncValue usage
- `/home/dawid/Projects/RecipAI/docs/mobile/ui.md` - UI component specifications, shopping list detail screen
  requirements
- `/home/dawid/Projects/RecipAI/docs/backend/api.md` - API endpoint specifications (POST item, DELETE item)

**Flutter Documentation**:

- ValueNotifier/ValueListenable: https://api.flutter.dev/flutter/foundation/ValueNotifier-class.html
- Timer.periodic: https://api.flutter.dev/flutter/dart-async/Timer/Timer.periodic.html
- FocusNode: https://api.flutter.dev/flutter/widgets/FocusNode-class.html
- TextField: https://api.flutter.dev/flutter/material/TextField-class.html

**Dart Documentation**:

- RegExp: https://api.dart.dev/stable/dart-core/RegExp-class.html
- UUID generation: https://pub.dev/packages/uuid

**HTTP Package**:

- Custom headers: https://pub.dev/documentation/http/latest/http/BaseRequest/headers.html

### Current Codebase Tree

```
mobile/
├── lib/
│   ├── core/
│   │   ├── async_value.dart          # AsyncLoading, AsyncData, AsyncError sealed class
│   │   ├── theme.dart                 # AppSpacing constants, Material Design 3 theme
│   │   └── routes.dart                # AppRoute enum, type-safe navigation
│   └── features/
│       └── shopping_list/
│           ├── shopping_list.dart                    # ShoppingList model (id, name)
│           ├── shopping_list_detail.dart             # ShoppingListDetail model (id, name, items, role)
│           ├── shopping_list_item.dart               # ShoppingListItem model (id, name, quantity, unit, checked, position, version)
│           ├── shopping_list_repository.dart         # Repository: API calls with http.Client
│           ├── shopping_list_list_service.dart       # Service: Manages list of shopping lists
│           ├── shopping_list_detail_service.dart     # Service: Manages single shopping list detail (current: load, rename, delete)
│           ├── shopping_list_list_screen.dart        # Screen: List of all shopping lists
│           ├── shopping_list_detail_screen.dart      # Screen: Shopping list detail with items (current: static display)
│           ├── shopping_list_rename_dialog.dart      # Dialog: Rename shopping list
│           └── shopping_list_setup.dart              # DI: GetIt registration
└── test/
    └── widget_test.dart                              # Test structure and mock patterns
```

### Desired Codebase Tree

```
mobile/
├── lib/
│   ├── core/
│   │   ├── async_value.dart
│   │   ├── theme.dart
│   │   └── routes.dart
│   └── features/
│       └── shopping_list/
│           ├── shopping_list.dart
│           ├── shopping_list_detail.dart
│           ├── shopping_list_item.dart
│           ├── shopping_list_repository.dart         # MODIFY: Add createItem, deleteItem methods
│           ├── shopping_list_list_service.dart
│           ├── shopping_list_detail_service.dart     # MODIFY: Add optimistic updates, polling timer, sync coordination
│           ├── shopping_list_sync_manager.dart       # CREATE: Managing operation queues for all lists
│           ├── shopping_list_operation.dart          # CREATE: Operation sealed class (AddItemOperation/DeleteItemOperation)
│           ├── shopping_list_list_screen.dart
│           ├── shopping_list_detail_screen.dart      # MODIFY: Add ItemInputWidget, ShoppingListItemWidget, sync indicator
│           ├── shopping_list_item_widget.dart        # CREATE: Item display/edit widget
│           ├── item_input_widget.dart                # CREATE: Reusable input widget with parsing
│           ├── shopping_list_rename_dialog.dart
│           └── shopping_list_setup.dart              # MODIFY: Register ShoppingListSyncManager singleton
└── test/
    └── widget_test.dart
```

### Known Gotchas of Our Codebase and Library Quirks

**Codebase Conventions**:

1. **Lazy Singleton Reset**: Always check `getIt.isRegistered<T>()` before calling `resetLazySingleton()` to avoid
   errors if service wasn't instantiated
2. **Context Safety**: Always check `mounted` before using `BuildContext` after async operations (especially for
   SnackBars)
3. **AsyncValue Pattern**: Wrap repository calls in `AsyncValue.guardAsync(() async { ... })` for consistent error
   handling
4. **Boolean Flags**: Use boolean flags (`_isProcessing`) in services to prevent concurrent operations
5. **Service Coordination**: Services can call other services to maintain consistency (e.g., detail service → list
   service)
6. **Data Model Immutability**: Models use final fields with no `copyWith` methods; create new instances instead
7. **ETag Header**: Must pass version as string in `If-Match` header for DELETE operations (not as integer)
8. **Error Types**: Repositories throw generic `Exception` (not custom exception types)

**Flutter Framework**:

1. **ValueNotifier**: Changes only trigger listeners if new value differs by `==`; for list mutations, assign a new list
   instance
2. **Timer Lifecycle**: Must cancel timers in dispose to prevent memory leaks
3. **FocusNode**: Must be disposed properly; can use `WidgetsBinding.instance.addPostFrameCallback` for delayed focus
4. **TextField Controller**: Must dispose controller in widget's dispose method

**API & Version Management**:

1. **Position Auto-Calculation**: API automatically assigns position when creating items (always appended at end)
2. **Version Starts at 0**: New items have `version: 0`, incremented on each update
3. **412 Precondition Failed**: Occurs when `If-Match` header doesn't match current item version
4. **Quantity Precision**: API accepts double for quantity, can be null
5. **Unit Optional**: Unit field is nullable, can be omitted in request

**Regex Parsing**:

1. **Decimal Separator**: Support both dot (.) and comma (,) as decimal separators for international users
2. **Leading/Trailing Whitespace**: Always trim input before parsing
3. **Optional Quantity/Unit**: Pattern must handle items with only name (e.g., "bread")

## Implementation Plan

### High-Level Architecture

The optimistic UI system follows a **three-layer operation queue architecture**:

1. **UI Layer** (ShoppingListDetailScreen): Applies optimistic updates immediately, queues operations
2. **Service Layer** (ShoppingListDetailService): Manages state, coordinates sync and polling
3. **Sync Layer** (ShoppingListSyncManager): Processes operation queues sequentially for all shopping lists

**Data Flow**:

```
User Action → Optimistic UI Update → Queue Operation → Sync Manager → API Call
                                                              ↓
                                        Success: Update versions, continue
                                                              ↓
                                        412 Conflict: Fetch latest, re-apply queue
                                                              ↓
                                        Other Error: Show snackbar, continue
```

**Polling & Sync Coordination**:

- Polling runs every 10 seconds when queue is empty
- Polling and syncing are mutually exclusive (can't happen at same time)
- Polling result: Replace list, re-apply items with pending operations

### Tasks

```
Task 1: Create ShoppingListOperation model
  Action: CREATE
  File: lib/features/shopping_list/shopping_list_operation.dart
  Changes:
    - [ ] Create sealed class ShoppingListOperation with:
          - String id (UUID for this operation instance)
          - String itemId (UUID - either real or temporary)
    - [ ] Create AddItemOperation extends ShoppingListOperation with:
          - String itemName
          - double? itemQuantity
          - String? itemUnit
    - [ ] Create DeleteItemOperation extends ShoppingListOperation with:
          - int itemVersion
    - [ ] Add toJson/fromJson for potential future persistence
    - [ ] Follow existing model pattern from shopping_list_item.dart

Task 2: Create ShoppingListSyncManager
  Action: CREATE
  File: lib/features/shopping_list/shopping_list_sync_manager.dart
  Changes:
    - [ ] Create regular class (will be registered as singleton in DI)
    - [ ] Maintain Map<String, List<ShoppingListOperation>> _operationQueues (key: shopping list ID)
    - [ ] Maintain Map<String, bool> _isSyncing (key: shopping list ID)
    - [ ] Expose ValueNotifier<bool> for each list's sync status via method: ValueNotifier<bool> getSyncStatusNotifier(String listId)
    - [ ] Method: void queueOperation(String listId, ShoppingListOperation operation)
    - [ ] Method: Future<void> processQueue(String listId, ShoppingListRepository repository, String? idToken, Function(String listId) onConflict, Function(String message) onError)
    - [ ] Process operations sequentially (await each before starting next)
    - [ ] Handle 412 conflict: call onConflict callback to trigger re-fetch
    - [ ] Handle other errors: call onError callback, continue processing
    - [ ] Update sync status notifier based on queue state
    - [ ] Use boolean flag pattern from existing services to prevent concurrent processing

Task 3: Add repository methods for item operations
  Action: MODIFY
  File: lib/features/shopping_list/shopping_list_repository.dart
  Changes:
    - [ ] Add method: Future<ShoppingListItem> createItem(String listId, String name, double? quantity, String? unit, String? idToken)
          - POST to /shopping-lists/{listId}/item
          - Body: {"name": name, "quantity": quantity, "unit": unit}
          - Return ShoppingListItem.fromJson(response)
          - Handle 201 Created success
          - Handle errors: 400, 401, 403, 404
    - [ ] Add method: Future<void> deleteItem(String listId, String itemId, int version, String? idToken)
          - DELETE to /shopping-lists/{listId}/item/{itemId}
          - Header: If-Match: version.toString()
          - Handle 204 No Content success
          - Handle errors: 401, 403, 404, 412 (throw specific exceptions for each)
    - [ ] Follow existing pattern from renameShoppingList method
    - [ ] Use _getAuthHeaders() helper for consistent header management

Task 4: Create ItemInputWidget for parsing user input
  Action: CREATE
  File: lib/features/shopping_list/item_input_widget.dart
  Changes:
    - [ ] Create StatefulWidget with parameters:
          - ShoppingListItem? initialItem (null for add mode, item for edit mode)
          - ValueChanged<ItemInputResult?> onSave callback
          - VoidCallback? onCancel (for edit mode)
          - bool autofocus (default true)
    - [ ] Create ItemInputResult class: {String name, double? quantity, String? unit}
    - [ ] Use TextEditingController initialized with formatted text from initialItem
    - [ ] Use FocusNode to detect focus loss
    - [ ] Regex pattern: ^\s*(\d+[\.,]?\d*)\s*([a-zA-Z]+)?\s+(.+)$
          - Group 1: quantity (replace comma with dot)
          - Group 2: unit (optional)
          - Group 3: name
    - [ ] Parse on focus loss: _focusNode.addListener(() { if (!_focusNode.hasFocus) _parseAndNotify(); })
    - [ ] If parse fails (no match), treat entire text as name
    - [ ] Trim all whitespace from parsed components
    - [ ] Call onSave with result (or null if empty)
    - [ ] Follow pattern from ingredient_input_widget.dart for structure
    - [ ] Use theme values: theme.textTheme, theme.colorScheme
    - [ ] Dispose controller and focus node properly

Task 5: Create ShoppingListItemWidget for display/edit
  Action: CREATE
  File: lib/features/shopping_list/shopping_list_item_widget.dart
  Changes:
    - [ ] Create StatefulWidget with parameters:
          - ShoppingListItem item
          - ValueChanged<ItemInputResult> onEdit callback
          - VoidCallback onDelete callback
    - [ ] Maintain bool _isEditing state (default false)
    - [ ] In view mode:
          - Display Row with checkbox icon (item.checked), item text, delete button
          - Format text: "quantity unit name" (follow existing pattern from shopping_list_detail_screen.dart lines 220-240)
          - OnTap: Set _isEditing = true
          - Delete button: IconButton with Icons.close, calls onDelete
    - [ ] In edit mode:
          - Display ItemInputWidget with initialItem
          - onSave: Call onEdit callback, set _isEditing = false
          - onCancel: Set _isEditing = false without callback
    - [ ] Use theme.textTheme.bodyLarge for text
    - [ ] Use AppSpacing constants for padding
    - [ ] Strikethrough and reduced opacity for checked items: TextStyle(decoration: TextDecoration.lineThrough, color: color.withOpacity(0.6))

Task 6: Enhance ShoppingListDetailService with optimistic updates
  Action: MODIFY
  File: lib/features/shopping_list/shopping_list_detail_service.dart
  Changes:
    - [ ] Add dependency: ShoppingListSyncManager (injected via constructor)
    - [ ] Add Timer? _pollingTimer field
    - [ ] Add method: void startPolling(String listId)
          - Create Timer.periodic(Duration(seconds: 10), (_) => _pollIfNotSyncing())
          - Store in _pollingTimer
    - [ ] Add method: void stopPolling()
          - Cancel _pollingTimer
          - Set _pollingTimer = null
    - [ ] Add method: Future<void> _pollIfNotSyncing()
          - Get current list ID from _shoppingListDetail.value (if AsyncData)
          - Check if sync manager is syncing for that list ID
          - If not syncing, call loadShoppingListDetail() without showing loading state
          - After load, re-apply pending operations (items with operations in sync manager queue)
    - [ ] Add method: void addItemOptimistically(String name, double? quantity, String? unit)
          - Generate temporary UUID for item
          - Create ShoppingListItem with temporary ID, position = max + 1, version = 0, checked = false
          - Add item to current state's items list (create new list instance!)
          - Update _shoppingListDetail.value with new AsyncData
          - Create ADD operation and queue via syncManager.queueOperation()
          - Trigger processQueue() with conflict and error callbacks
    - [ ] Add method: void deleteItemOptimistically(ShoppingListItem item)
          - Remove item from current state's items list (create new list instance!)
          - Update _shoppingListDetail.value with new AsyncData
          - Create DELETE operation and queue via syncManager.queueOperation()
          - Trigger processQueue() with conflict and error callbacks
    - [ ] Add method: Future<void> _handleConflict(String listId)
          - Load fresh data from API
          - Discard conflicted operation (already removed from queue by sync manager)
          - Re-apply all remaining pending operations optimistically
          - Show SnackBar via callback to screen
    - [ ] Add method: void updateItemVersion(String tempId, String realId, int version)
          - Replace temporary ID with real ID in current state
          - Update version in current state
          - Update itemId in pending operations in sync manager queue
    - [ ] Modify dispose() to call stopPolling()
    - [ ] Follow existing boolean flag pattern for preventing concurrent operations

Task 7: Update ShoppingListDetailScreen with new widgets
  Action: MODIFY
  File: lib/features/shopping_list/shopping_list_detail_screen.dart
  Changes:
    - [ ] Add ValueListenableBuilder for sync status (from syncManager.getSyncStatusNotifier(listId))
    - [ ] Add CircularProgressIndicator in AppBar actions when syncing
          - Use pattern: if (isSyncing) Padding(padding: EdgeInsets.all(16), child: SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2)))
    - [ ] Replace current item display with ListView.builder of ShoppingListItemWidget
    - [ ] Add persistent ItemInputWidget at bottom (above or below item list)
          - onSave: Call service.addItemOptimistically()
          - Clear input after save by using key to rebuild widget
    - [ ] Pass onEdit callback to ShoppingListItemWidget (not implemented yet, leave empty for now)
    - [ ] Pass onDelete callback to ShoppingListItemWidget: service.deleteItemOptimistically()
    - [ ] Call service.startPolling(listId) in initState
    - [ ] Call service.stopPolling() in dispose
    - [ ] Add conflict handler callback to service (shows SnackBar with "List was updated by another user")
    - [ ] Add error handler callback to service (shows SnackBar with error message)
    - [ ] Ensure all SnackBar calls check mounted before showing
    - [ ] Use AppSpacing constants for layout spacing
    - [ ] Follow existing AsyncValue.when() pattern for loading/data/error states

Task 8: Register ShoppingListSyncManager in DI
  Action: MODIFY
  File: lib/features/shopping_list/shopping_list_setup.dart
  Changes:
    - [ ] Register ShoppingListSyncManager as singleton (before services)
    - [ ] Pass syncManager instance to ShoppingListDetailService constructor
    - [ ] Update ShoppingListDetailService constructor to accept syncManager parameter
    - [ ] Follow existing registration pattern from setupShoppingList()

Task 9: Handle temporary ID replacement after successful ADD
  Action: MODIFY
  File: lib/features/shopping_list/shopping_list_sync_manager.dart
  Changes:
    - [ ] In processQueue, after successful createItem call:
          - Get real ID and version from response
          - Call service method to update item in state (need callback parameter)
          - Update itemId in any remaining operations in queue that reference temp ID
    - [ ] Add callback parameter to processQueue: Function(String tempId, String realId, int version) onItemCreated
    - [ ] Invoke callback with temp ID, real ID, and version from API response
```

### Per Task Pseudocode

#### Task 2: ShoppingListSyncManager Core Logic

```dart
class ShoppingListSyncManager {
  final Map<String, List<ShoppingListOperation>> _queues = {};
  final Map<String, bool> _isSyncing = {};
  final Map<String, ValueNotifier<bool>> _syncNotifiers = {};

  ValueNotifier<bool> getSyncStatusNotifier(String listId) {
    return _syncNotifiers.putIfAbsent(listId, () => ValueNotifier(false));
  }

  void queueOperation(String listId, ShoppingListOperation operation) {
    _queues.putIfAbsent(listId, () => []).add(operation);
    _updateSyncStatus(listId);
  }

  Future<void> processQueue(String listId,
      ShoppingListRepository repository,
      String? idToken,
      Function(String tempId, String realId, int version) onItemCreated,
      Function(String listId) onConflict,
      Function(String message) onError,) async {
    if (_isSyncing[listId] == true) return; // Already processing

    _isSyncing[listId] = true;
    _updateSyncStatus(listId);

    while (_queues[listId]?.isNotEmpty ?? false) {
      final operation = _queues[listId]!.first;

      try {
        switch (operation) {
          case AddItemOperation add:
            final response = await repository.createItem(
              listId,
              add.itemName,
              add.itemQuantity,
              add.itemUnit,
              idToken,
            );
            // Replace temp ID with real ID
            onItemCreated(add.itemId, response.id, response.version);
            _replaceItemIdInQueue(listId, add.itemId, response.id);
          case DeleteItemOperation delete:
            await repository.deleteItem(
              listId,
              delete.itemId,
              delete.itemVersion,
              idToken,
            );
        }

        _queues[listId]!.removeAt(0); // Success - remove from queue

      } catch (e) {
        if (e.toString().contains('412')) {
          _queues[listId]!.removeAt(0); // Discard conflicted operation
          onConflict(listId);
          // Continue with remaining operations after conflict handler finishes
        } else {
          _queues[listId]!.removeAt(0); // Remove failed operation
          onError('Failed to sync: $e');
        }
      }
    }

    _isSyncing[listId] = false;
    _updateSyncStatus(listId);
  }

  void _updateSyncStatus(String listId) {
    final isProcessing = (_queues[listId]?.isNotEmpty ?? false);
    getSyncStatusNotifier(listId).value = isProcessing;
  }

  void _replaceItemIdInQueue(String listId, String oldId, String newId) {
    for (var i = 0; i < (_queues[listId]?.length ?? 0); i++) {
      final operation = _queues[listId]![i];
      if (operation.itemId == oldId) {
        // Reconstruct operation with new ID
        if (operation is AddItemOperation) {
          _queues[listId]![i] = AddItemOperation(
            id: operation.id,
            itemId: newId,
            itemName: operation.itemName,
            itemQuantity: operation.itemQuantity,
            itemUnit: operation.itemUnit,
          );
        } else if (operation is DeleteItemOperation) {
          _queues[listId]![i] = DeleteItemOperation(
            id: operation.id,
            itemId: newId,
            itemVersion: operation.itemVersion,
          );
        }
      }
    }
  }
}
```

#### Task 4: ItemInputWidget Parsing Logic

```dart
class ItemInputWidget extends StatefulWidget {
  // ... widget definition
}

class _ItemInputWidgetState extends State<ItemInputWidget> {
  late TextEditingController _controller;
  late FocusNode _focusNode;
  final _pattern = RegExp(r'^\s*(\d+[\.,]?\d*)\s*([a-zA-Z]+)?\s+(.+)$');

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: _formatInitialItem());
    _focusNode = FocusNode();
    _focusNode.addListener(_onFocusChange);
  }

  String _formatInitialItem() {
    if (widget.initialItem == null) return '';
    final item = widget.initialItem!;
    if (item.quantity != null) {
      final qtyStr = item.quantity! == item.quantity!.toInt()
          ? item.quantity!.toInt().toString()
          : item.quantity!.toString();
      if (item.unit != null) {
        return '$qtyStr ${item.unit} ${item.name}';
      }
      return '$qtyStr ${item.name}';
    }
    return item.name;
  }

  void _onFocusChange() {
    if (!_focusNode.hasFocus) {
      _parseAndNotify();
    }
  }

  void _parseAndNotify() {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      widget.onSave(null);
      return;
    }

    final match = _pattern.firstMatch(text);
    if (match != null) {
      final quantityStr = match.group(1)!.replaceAll(',', '.');
      final quantity = double.tryParse(quantityStr);
      final unit = match.group(2)?.trim();
      final name = match.group(3)!.trim();
      widget.onSave(ItemInputResult(name: name, quantity: quantity, unit: unit));
    } else {
      // No match - entire text is the name
      widget.onSave(ItemInputResult(name: text, quantity: null, unit: null));
    }
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: _controller,
      focusNode: _focusNode,
      autofocus: widget.autofocus,
      decoration: InputDecoration(
        labelText: 'Add item',
        hintText: 'e.g., 2 liters milk',
      ),
    );
  }

  @override
  void dispose() {
    _focusNode.removeListener(_onFocusChange);
    _focusNode.dispose();
    _controller.dispose();
    super.dispose();
  }
}
```

#### Task 6: Service Optimistic Add/Delete

```dart
void addItemOptimistically(String name, double? quantity, String? unit) async {
  final currentState = _state.value;
  if (currentState is! AsyncData<ShoppingListDetail>) return;

  final detail = currentState.value;
  final tempId = Uuid().v4(); // Generate temporary UUID
  final maxPosition = detail.items.isEmpty
      ? 0.0
      : detail.items.map((i) => i.position).reduce((a, b) => a > b ? a : b);

  final newItem = ShoppingListItem(
    id: tempId,
    name: name,
    quantity: quantity,
    unit: unit,
    checked: false,
    position: maxPosition + 1.0,
    version: 0,
  );

  // Optimistic update - create new list instance!
  final updatedItems = [...detail.items, newItem];
  final updatedDetail = ShoppingListDetail(
    id: detail.id,
    name: detail.name,
    items: updatedItems,
    role: detail.role,
  );
  _state.value = AsyncData(updatedDetail);

  // Queue operation
  final operation = AddItemOperation(
    id: Uuid().v4(),
    itemId: tempId,
    itemName: name,
    itemQuantity: quantity,
    itemUnit: unit,
  );

  _syncManager.queueOperation(detail.id, operation);

  // Start processing
  _syncManager.processQueue(
    detail.id,
    _shoppingListRepository,
    await _authService.getIdToken(),
    _onItemCreated,
    _handleConflict,
    _handleError,
  );
}

void deleteItemOptimistically(ShoppingListItem item) async {
  final currentState = _state.value;
  if (currentState is! AsyncData<ShoppingListDetail>) return;

  final detail = currentState.value;

  // Optimistic update - create new list instance!
  final updatedItems = detail.items.where((i) => i.id != item.id).toList();
  final updatedDetail = ShoppingListDetail(
    id: detail.id,
    name: detail.name,
    items: updatedItems,
    role: detail.role,
  );
  _state.value = AsyncData(updatedDetail);

  // Queue operation
  final operation = DeleteItemOperation(
    id: Uuid().v4(),
    itemId: item.id,
    itemVersion: item.version,
  );

  _syncManager.queueOperation(detail.id, operation);

  // Start processing
  _syncManager.processQueue(
    detail.id,
    _shoppingListRepository,
    await _authService.getIdToken(),
    _onItemCreated,
    _handleConflict,
    _handleError,
  );
}

void _onItemCreated(String tempId, String realId, int version) {
  final currentState = _state.value;
  if (currentState is! AsyncData<ShoppingListDetail>) return;

  final detail = currentState.value;
  final updatedItems = detail.items.map((item) {
    if (item.id == tempId) {
      return ShoppingListItem(
        id: realId,
        name: item.name,
        quantity: item.quantity,
        unit: item.unit,
        checked: item.checked,
        position: item.position,
        version: version,
      );
    }
    return item;
  }).toList();

  final updatedDetail = ShoppingListDetail(
    id: detail.id,
    name: detail.name,
    items: updatedItems,
    role: detail.role,
  );
  _state.value = AsyncData(updatedDetail);
}

Future<void> _handleConflict(String listId) async {
  // Fetch fresh data
  await loadShoppingListDetail();

  // Re-apply pending operations would happen naturally since
  // the items in the queue are still there and will be processed

  // Note: Need to pass callback to screen for SnackBar
  _onConflictCallback?.call();
}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd /home/dawid/Projects/RecipAI/mobile
flutter analyze

# Expected: No issues found!
# If errors: READ the error message carefully and fix the issue
```

### Unit Tests

```bash
# Run and iterate until passing:
cd /home/dawid/Projects/RecipAI/mobile
flutter test

# If failing:
# 1. Read the error message to understand what failed
# 2. Identify the root cause (logic error, missing mock, incorrect assertion)
# 3. Fix the code (never just adjust mocks to make tests pass)
# 4. Re-run tests
# 5. Repeat until all tests pass
```

### Manual Testing Checklist

After implementation, manually test:

1. Add item "2 liters milk" → verify appears immediately, syncs with backend
2. Add item "bread" → verify appears immediately, syncs with backend
3. Delete item → verify disappears immediately, syncs with backend
4. Add item while offline → verify appears, shows sync indicator, syncs when back online
5. Navigate away during sync → verify sync continues in background
6. Create conflict (modify item in another client) → verify shows snackbar, re-fetches, continues
7. Switch between multiple shopping lists → verify each list syncs independently

## Integration Points

### API Changes

- **New Endpoints Used**:
    - POST /shopping-lists/{shopping_list_id}/item
    - DELETE /shopping-lists/{shopping_list_id}/item/{id}
- **Headers Required**:
    - If-Match: {version} for DELETE operations
- **Response Handling**:
    - 201 Created → Extract item with real ID and version
    - 204 No Content → Operation succeeded
    - 412 Precondition Failed → Conflict detected, trigger re-fetch

### Data Flow

- **ShoppingListDetailScreen** ↔ **ShoppingListDetailService** ↔ **ShoppingListSyncManager** ↔ **ShoppingListRepository
  ** ↔ **Backend API**
- **Polling**: Service → Repository → Update state → Re-apply pending operations
- **Conflict Resolution**: Sync Manager detects 412 → Service re-fetches → Service re-applies queue items

### State Management

- **ValueNotifier** for shopping list detail state (existing pattern)
- **ValueNotifier** for sync status per list (new)
- **Timer** for polling every 10 seconds (new)

## Documentation

### Files to Update

1. **`/home/dawid/Projects/RecipAI/docs/mobile/mobile.md`**
    - Add section on ShoppingListSyncManager singleton
    - Document optimistic UI pattern
    - Explain operation queue system
    - Add ItemInputWidget to reusable components list

2. **`/home/dawid/Projects/RecipAI/docs/mobile/ui.md`**
    - Update Shopping List Detail Screen section with:
        - ItemInputWidget component description
        - ShoppingListItemWidget component description
        - Sync indicator in AppBar
        - Optimistic update behavior
    - Update Primary Actions to include parsing behavior

## Final Validation Checklist

- [ ] Correct syntax (flutter analyze passes)
- [ ] Correct style (follows existing conventions)
- [ ] All tests pass (flutter test succeeds)
- [ ] Manual test successful (can add and delete items optimistically)
- [ ] Conflict handling works (412 error triggers re-fetch and continues)
- [ ] Sync indicator appears/disappears correctly
- [ ] Polling works (fetches every 10 seconds when not syncing)
- [ ] Multi-list support works (can switch between lists)
- [ ] Error cases handled gracefully (network errors show snackbar, continue processing)
- [ ] Logs are informative but not verbose
- [ ] Documentation updated (mobile.md, ui.md)
- [ ] No memory leaks (timers cancelled, listeners removed)
- [ ] Context safety (mounted checks before SnackBars)
- [ ] Temporary IDs replaced with real IDs after successful ADD

## Additional Notes

### External Dependencies to Add

Add to `pubspec.yaml`:

```yaml
dependencies:
  uuid: ^4.0.0  # For generating temporary UUIDs
```

Run after adding:

```bash
cd /home/dawid/Projects/RecipAI/mobile
flutter pub get
```

### Parsing Examples Reference

Valid inputs and expected parsing:

- "2 eggs" → {name: "eggs", quantity: 2.0, unit: null}
- "2.5 liters milk" → {name: "milk", quantity: 2.5, unit: "liters"}
- "2,5 kg flour" → {name: "flour", quantity: 2.5, unit: "kg"}
- "bread" → {name: "bread", quantity: null, unit: null}
- "  3.5 lb chicken  " → {name: "chicken", quantity: 3.5, unit: "lb"}
- "" → null (empty input)

### Future Enhancements (Out of Scope)

These features are explicitly excluded from this implementation:

- Edit operation (UPDATE)
- Reorder operation
- Check/uncheck operation sync (remains local only)
- Persistent queue (SharedPreferences)
- Retry button for failed operations
- Visual distinction for pending items
- Offline detection and queueing

---

**Plan Confidence Score: 9/10**

This plan provides comprehensive context for one-pass implementation:

- ✅ All necessary context from codebase included with file references
- ✅ Specific patterns to follow identified with line numbers
- ✅ Complete pseudocode for complex components
- ✅ Validation commands are executable
- ✅ Error handling documented with specific status codes
- ✅ Integration points clearly specified
- ✅ Documentation update requirements listed

**Slight deduction** because:

- Testing strategy could be more comprehensive (missing integration test details)
- Some edge cases may emerge during implementation (e.g., rapid user actions, race conditions)

The plan should enable successful implementation with minimal clarification needed.