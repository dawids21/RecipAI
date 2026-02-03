import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:recipai_mobile/shared/extensions.dart';

import '../../core/app_config.dart';
import 'meal_plan.dart';
import 'meal_plan_calendar_data.dart';

class MealPlanRepository {
  final http.Client _client = http.Client();
  final String _baseUrl = AppConfig.apiBaseUrl;

  MealPlanRepository();

  Map<String, String> _getAuthHeaders(String? idToken) {
    return {
      'Content-Type': 'application/json',
      if (idToken != null) 'Authorization': 'Bearer $idToken',
    };
  }

  Future<MealPlanCalendarData> fetchCalendar({
    required DateTime startDate,
    required DateTime endDate,
    required List<String> planIds,
    required String? idToken,
  }) async {
    final headers = _getAuthHeaders(idToken);

    final queryParameters = <String, String>{
      'startDate': startDate.toIso8601DateString(),
      'endDate': endDate.toIso8601DateString(),
      'planIds': planIds.join(','),
    };

    final uri = Uri.parse(
      '$_baseUrl/meal-plans/calendar',
    ).replace(queryParameters: queryParameters);

    final response = await _client.get(uri, headers: headers);

    if (response.statusCode == 200) {
      final Map<String, dynamic> json = jsonDecode(response.body);
      return MealPlanCalendarData.fromJson(json);
    } else if (response.statusCode == 400) {
      throw Exception('Invalid request parameters');
    } else if (response.statusCode == 401) {
      throw Exception('Unauthorized: Please log in again');
    } else {
      throw Exception('Failed to load calendar: ${response.statusCode}');
    }
  }

  Future<List<MealPlan>> fetchMealPlans({required String? idToken}) async {
    final headers = _getAuthHeaders(idToken);
    final uri = Uri.parse('$_baseUrl/meal-plans');

    final response = await _client.get(uri, headers: headers);

    if (response.statusCode == 200) {
      final List<dynamic> jsonList = jsonDecode(response.body);
      return jsonList.map((json) => MealPlan.fromJson(json)).toList();
    } else if (response.statusCode == 401) {
      throw Exception('Unauthorized: Please log in again');
    } else {
      throw Exception('Failed to load meal plans: ${response.statusCode}');
    }
  }

  Future<MealPlan> createMealPlan({
    required String name,
    required Color color,
    required String? idToken,
  }) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.post(
      Uri.parse('$_baseUrl/meal-plans'),
      headers: headers,
      body: jsonEncode({
        'name': name,
        'color': color.toHexString(),
      }),
    );

    if (response.statusCode == 201) {
      final Map<String, dynamic> json = jsonDecode(response.body);
      return MealPlan.fromJson(json);
    } else if (response.statusCode == 400) {
      throw Exception('Invalid plan data');
    } else if (response.statusCode == 401) {
      throw Exception('Unauthorized: Please log in again');
    } else if (response.statusCode == 409) {
      throw Exception('Plan limit exceeded');
    } else {
      throw Exception('Failed to create meal plan: ${response.statusCode}');
    }
  }

  Future<MealPlan> updateMealPlan({
    required String id,
    required String name,
    required Color color,
    required String? idToken,
  }) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.put(
      Uri.parse('$_baseUrl/meal-plans/$id'),
      headers: headers,
      body: jsonEncode({
        'name': name,
        'color': color.toHexString(),
      }),
    );

    if (response.statusCode == 200) {
      final Map<String, dynamic> json = jsonDecode(response.body);
      return MealPlan.fromJson(json);
    } else if (response.statusCode == 400) {
      throw Exception('Invalid plan data');
    } else if (response.statusCode == 401) {
      throw Exception('Unauthorized: Please log in again');
    } else if (response.statusCode == 403) {
      throw Exception('You do not have permission to edit this plan');
    } else if (response.statusCode == 404) {
      throw Exception('Plan not found');
    } else {
      throw Exception('Failed to update meal plan: ${response.statusCode}');
    }
  }

  Future<void> deleteMealPlan({
    required String id,
    required String? idToken,
  }) async {
    final headers = _getAuthHeaders(idToken);
    final response = await _client.delete(
      Uri.parse('$_baseUrl/meal-plans/$id'),
      headers: headers,
    );

    if (response.statusCode == 204) {
      return;
    } else if (response.statusCode == 401) {
      throw Exception('Unauthorized: Please log in again');
    } else if (response.statusCode == 403) {
      throw Exception('You do not have permission to delete this plan');
    } else if (response.statusCode == 404) {
      throw Exception('Plan not found');
    } else {
      throw Exception('Failed to delete meal plan: ${response.statusCode}');
    }
  }
}
