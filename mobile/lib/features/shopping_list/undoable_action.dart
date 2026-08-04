import 'local_shopping_list_item.dart';

sealed class UndoableAction {
  const UndoableAction();

  int get itemCount;
}

/// Items removed by a delete; carries the full pre-state needed to re-create them.
class DeletedItemsUndo extends UndoableAction {
  final List<LocalShoppingListItem> items;
  const DeletedItemsUndo(this.items);

  @override
  int get itemCount => items.length;
}

/// Items switched from checked to unchecked; only the ids are needed, since
/// uncheckAll never touches an already-unchecked item.
class UncheckedItemsUndo extends UndoableAction {
  final List<String> localIds;
  const UncheckedItemsUndo(this.localIds);

  @override
  int get itemCount => localIds.length;
}
