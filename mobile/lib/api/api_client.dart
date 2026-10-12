import 'dart:async';
import 'dart:typed_data';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:path_provider/path_provider.dart';

/// 서버 주소. 빌드할 때 --dart-define=API_BASE=... 로 바꿀 수 있다.
const apiBase = String.fromEnvironment('API_BASE', defaultValue: 'https://52-79-61-201.sslip.io');

/// 서버가 돌려준 오류 ({ code, message, fieldErrors })
class ApiException implements Exception {
  ApiException(this.status, this.code, this.message);

  final int status;
  final String? code;
  final String message;

  @override
  String toString() => message;
}

/// 백엔드 호출을 한곳에서 처리한다 (웹의 api/client.js 와 같은 규칙).
/// - 액세스 토큰은 메모리에만 둔다.
/// - 리프레시 토큰은 서버가 HttpOnly 쿠키로 내려주므로 쿠키 저장소가 보관하고 인증 경로에만 다시 실어 보낸다.
/// - 401 이 오면 한 번만 토큰을 다시 받아 재시도한다.
class ApiClient {
  ApiClient._(this._dio);

  final Dio _dio;
  String? _accessToken;
  Future<void>? _refreshing;

  /// 리프레시까지 실패했을 때(다시 로그인해야 할 때) 불린다
  void Function()? onSessionExpired;

  static Future<ApiClient> create() async {
    final dio = Dio(BaseOptions(
      baseUrl: apiBase,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30),
      // 상태 코드는 직접 본다 (4xx 도 응답 본문의 code · message 를 읽어야 한다)
      validateStatus: (_) => true,
    ));
    final dir = await getApplicationSupportDirectory();
    dio.interceptors.add(CookieManager(PersistCookieJar(storage: FileStorage('${dir.path}/cookies'))));
    return ApiClient._(dio);
  }

  bool get hasToken => _accessToken != null;

  /// 사진처럼 토큰이 필요한 주소를 직접 받을 때 쓰는 헤더
  Map<String, String> get authHeaders => _accessToken == null ? {} : {'Authorization': 'Bearer $_accessToken'};

  Future<dynamic> get(String path, {bool auth = true}) => _send('GET', path, auth: auth);

  Future<dynamic> post(String path, {Object? body, bool auth = true}) => _send('POST', path, body: body, auth: auth);

  Future<dynamic> put(String path, {Object? body}) => _send('PUT', path, body: body);

  Future<dynamic> delete(String path) => _send('DELETE', path);

  /// 사진 한 장 올리기 (multipart 의 file 칸)
  Future<dynamic> upload(String path, Uint8List bytes, String filename) {
    return _send('POST', path, bodyBuilder: () {
      return FormData.fromMap({'file': MultipartFile.fromBytes(bytes, filename: filename)});
    });
  }

  /// 토큰을 붙여 사진 바이트를 받는다 (참가자만 볼 수 있는 인증 사진 등)
  Future<Uint8List> bytes(String path) async {
    Future<Response<List<int>>> send() => _dio.get<List<int>>(path,
        options: Options(responseType: ResponseType.bytes, headers: authHeaders));
    var res = await send();
    if (res.statusCode == 401) {
      await refresh();
      res = await send();
    }
    if (res.statusCode != 200 || res.data == null) {
      throw ApiException(res.statusCode ?? 0, null, '사진을 불러오지 못했어요.');
    }
    return Uint8List.fromList(res.data!);
  }

  Future<void> login(String email, String password) async {
    final data = await _send('POST', '/api/auth/login', body: {'email': email, 'password': password}, auth: false);
    _accessToken = data['accessToken'] as String;
  }

  Future<void> logout() async {
    try {
      await _send('POST', '/api/auth/logout', auth: false);
    } finally {
      _accessToken = null;
    }
  }

  /// 리프레시 토큰은 1회용(회전)이라 동시에 두 번 보내면 한쪽이 실패한다 → 진행 중인 요청을 같이 기다린다
  Future<void> refresh() {
    return _refreshing ??= () async {
      try {
        final data = await _send('POST', '/api/auth/refresh', auth: false);
        _accessToken = data['accessToken'] as String;
      } catch (_) {
        _accessToken = null;
        rethrow;
      } finally {
        _refreshing = null;
      }
    }();
  }

  Future<dynamic> _send(String method, String path,
      {Object? body, Object Function()? bodyBuilder, bool auth = true}) async {
    // FormData 는 한 번 보내면 다시 못 써서 재시도 때 새로 만든다
    Future<Response<dynamic>> send() {
      return _dio.request<dynamic>(path,
          data: bodyBuilder != null ? bodyBuilder() : body,
          options: Options(method: method, headers: auth ? authHeaders : null));
    }

    Response<dynamic> res;
    try {
      res = await send();
      if (res.statusCode == 401 && auth) {
        try {
          await refresh();
        } catch (_) {
          onSessionExpired?.call();
          throw ApiException(401, 'UNAUTHORIZED', '다시 로그인해 주세요.');
        }
        res = await send();
      }
    } on DioException {
      throw ApiException(0, 'NETWORK', '서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요.');
    }

    final status = res.statusCode ?? 0;
    if (status >= 200 && status < 300) {
      return status == 204 ? null : res.data;
    }
    final data = res.data;
    if (data is Map) {
      throw ApiException(status, data['code'] as String?, (data['message'] as String?) ?? '요청을 처리하지 못했어요.');
    }
    throw ApiException(status, null, '요청을 처리하지 못했어요.');
  }
}
