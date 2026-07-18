class LocalShoppingListItem {
  final String localId;
  final String? serverId;
  final String listId;
  final String name;
  final double? quantity;
  final String? unit;
  final bool checked;
  final double position;
  final int? lastAckedVersion;
  final bool dirty;
  final bool failed;
  final bool pendingDelete;

  const LocalShoppingListItem({
    required this.localId,
    required this.serverId,
    required this.listId,
    required this.name,
    required this.quantity,
    required this.unit,
    required this.checked,
    required this.position,
    required this.lastAckedVersion,
    required this.dirty,
    required this.failed,
    required this.pendingDelete,
  });

  LocalShoppingListItem copyWith({
    String? localId,
    Object? serverId = _sentinel,
    String? listId,
    String? name,
    Object? quantity = _sentinel,
    Object? unit = _sentinel,
    bool? checked,
    double? position,
    Object? lastAckedVersion = _sentinel,
    bool? dirty,
    bool? failed,
    bool? pendingDelete,
  }) {
    return LocalShoppingListItem(
      localId: localId ?? this.localId,
      serverId: identical(serverId, _sentinel)
          ? this.serverId
          : serverId as String?,
      listId: listId ?? this.listId,
      name: name ?? this.name,
      quantity: identical(quantity, _sentinel)
          ? this.quantity
          : quantity as double?,
      unit: identical(unit, _sentinel) ? this.unit : unit as String?,
      checked: checked ?? this.checked,
      position: position ?? this.position,
      lastAckedVersion: identical(lastAckedVersion, _sentinel)
          ? this.lastAckedVersion
          : lastAckedVersion as int?,
      dirty: dirty ?? this.dirty,
      failed: failed ?? this.failed,
      pendingDelete: pendingDelete ?? this.pendingDelete,
    );
  }

  static const _sentinel = Object();

  factory LocalShoppingListItem.fromMap(Map<String, dynamic> map) {
    return LocalShoppingListItem(
      localId: map['local_id'] as String,
      serverId: map['server_id'] as String?,
      listId: map['list_id'] as String,
      name: map['name'] as String,
      quantity: map['quantity'] as double?,
      unit: map['unit'] as String?,
      checked: (map['checked'] as int) != 0,
      position: map['position'] as double,
      lastAckedVersion: map['last_acked_version'] as int?,
      dirty: (map['dirty'] as int) != 0,
      failed: (map['failed'] as int) != 0,
      pendingDelete: (map['pending_delete'] as int) != 0,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'local_id': localId,
      'server_id': serverId,
      'list_id': listId,
      'name': name,
      'quantity': quantity,
      'unit': unit,
      'checked': checked ? 1 : 0,
      'position': position,
      'last_acked_version': lastAckedVersion,
      'dirty': dirty ? 1 : 0,
      'failed': failed ? 1 : 0,
      'pending_delete': pendingDelete ? 1 : 0,
    };
  }

  /// Orders by (position, localId) — stable tiebreaker when positions collide.
  int compareTo(LocalShoppingListItem other) {
    final positionCmp = position.compareTo(other.position);
    if (positionCmp != 0) return positionCmp;
    return localId.compareTo(other.localId);
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is LocalShoppingListItem &&
          localId == other.localId &&
          serverId == other.serverId &&
          listId == other.listId &&
          name == other.name &&
          quantity == other.quantity &&
          unit == other.unit &&
          checked == other.checked &&
          position == other.position &&
          lastAckedVersion == other.lastAckedVersion &&
          dirty == other.dirty &&
          failed == other.failed &&
          pendingDelete == other.pendingDelete;

  @override
  int get hashCode => localId.hashCode;

  @override
  String toString() =>
      'LocalShoppingListItem(localId: $localId, name: $name, checked: $checked, '
      'position: $position, dirty: $dirty, pendingDelete: $pendingDelete)';
}
