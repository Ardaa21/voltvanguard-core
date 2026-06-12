/// Concrete task repository — polls /tasks and wraps in a polling stream.
library;

import 'dart:async';

import '../../../../core/constants/api_constants.dart';
import '../../../../core/network/api_client.dart';
import '../models/task_model.dart';

abstract interface class TaskRepository {
  Future<List<TaskModel>> getActiveTasks({int page, int size});
  Future<TaskModel>       getTaskById(String taskId);

  /// Polls the backend every [interval] and emits fresh task lists.
  Stream<List<TaskModel>> watchActiveTasks({Duration interval});
}

class TaskRepositoryImpl implements TaskRepository {
  TaskRepositoryImpl({required ApiClient apiClient}) : _api = apiClient;

  final ApiClient _api;

  @override
  Future<List<TaskModel>> getActiveTasks({
    int page = 0,
    int size  = ApiConstants.defaultPageSize,
  }) async {
    // Spring Boot returns Page<T>: {"content": [...], "totalPages": N, ...}
    final envelope = await _api.get<Map<String, dynamic>>(
      ApiConstants.tasks,
      queryParameters: {
        'page':   page,
        'size':   size,
        'status': 'PENDING,IN_PROGRESS',
        'sort':   'priority',
      },
    );
    final content = (envelope?['content'] as List<dynamic>?) ?? [];
    return content
        .map((e) => TaskModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<TaskModel> getTaskById(String taskId) async {
    final data = await _api.get<Map<String, dynamic>>(
      ApiConstants.taskById.replaceFirst('{id}', taskId),
    );
    return TaskModel.fromJson(data!);
  }

  @override
  Stream<List<TaskModel>> watchActiveTasks({
    Duration interval = const Duration(seconds: 10),
  }) async* {
    while (true) {
      try {
        yield await getActiveTasks();
      } catch (_) {
        // Surface errors as empty list; caller can show stale UI
        yield [];
      }
      await Future<void>.delayed(interval);
    }
  }
}
