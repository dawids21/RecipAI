import 'dart:async';
import 'dart:io';

import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';

/// Writes log records to a rotating file so testers can share the full activity
/// trace. A single active file (`recipai.log`) is capped at ~1 MB; when it
/// exceeds the cap it is rotated to `recipai.log.1` (one backup kept) and a
/// fresh active file is started — ≈2 MB on disk worst case.
///
/// Writes are serialized through a single-future queue so concurrent log calls
/// cannot interleave or corrupt lines.
class AppLogSink {
  static const String _fileName = 'recipai.log';
  static const String _backupFileName = 'recipai.log.1';
  static const int _maxFileSizeBytes = 1024 * 1024; // ~1 MB

  File? _file;
  Future<void> _writeQueue = Future<void>.value();
  bool _initialized = false;

  /// Opens the log file for append. Idempotent — safe to call more than once.
  Future<void> init() async {
    if (_initialized) return;
    final dir = await getApplicationSupportDirectory();
    _file = File('${dir.path}/$_fileName');
    await _file!.create(recursive: true);
    _initialized = true;
  }

  /// Formats one record and appends it. Triggers rotation when the active file
  /// exceeds the size cap. Never throws into the logging pipeline.
  void handle(LogRecord record) {
    final buffer = StringBuffer()
      ..write(record.time.toIso8601String())
      ..write('  ')
      ..write(record.level.name)
      ..write('  ')
      ..write(record.loggerName)
      ..write('  ')
      ..writeln(record.message);

    if (record.error != null) {
      buffer.writeln('  error: ${record.error}');
    }
    if (record.stackTrace != null) {
      buffer.writeln(record.stackTrace.toString());
    }

    _enqueue(buffer.toString());
  }

  /// Flushes pending writes and returns the active log file for sharing.
  Future<File> currentLogFileForSharing() async {
    await init();
    await _writeQueue;
    return _file!;
  }

  void _enqueue(String line) {
    _writeQueue = _writeQueue.then((_) => _append(line));
  }

  Future<void> _append(String line) async {
    final file = _file;
    if (file == null) return;
    try {
      await file.writeAsString(line, mode: FileMode.append, flush: false);
      await _rotateIfNeeded(file);
    } catch (_) {
      // Logging must never crash the app — drop the line on I/O failure.
    }
  }

  Future<void> _rotateIfNeeded(File file) async {
    if (await file.length() < _maxFileSizeBytes) return;

    final backup = File('${file.parent.path}/$_backupFileName');
    if (await backup.exists()) {
      await backup.delete();
    }
    await file.rename(backup.path);
    _file = File('${file.parent.path}/$_fileName');
    await _file!.create(recursive: true);
  }
}
