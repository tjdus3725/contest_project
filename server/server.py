"""Local development proxy. REST keys stay here, never in the APK or map page.
Public deployment requires HTTPS, DONGHAENG_TOKEN and infrastructure rate limits.
"""
import hmac
import json
import math
import os
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen


def point(value):
    if not isinstance(value, dict):
        raise ValueError("좌표 형식이 잘못되었습니다")
    lat, lon = float(value["lat"]), float(value["lon"])
    if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise ValueError("좌표 범위를 확인하세요")
    return {"lat": lat, "lon": lon}


def distance(a, b):
    lat = math.radians((a["lat"] + b["lat"]) / 2)
    return math.hypot((a["lon"] - b["lon"]) * math.cos(lat), a["lat"] - b["lat"]) * 111195


def offset_from_route(p, points):
    # Local projection around candidate; distance to every segment, not just route vertices.
    scale_x = 111195 * math.cos(math.radians(p["lat"]))
    best, progress, total = float("inf"), 0., 0.
    for a, b in zip(points, points[1:]):
        ax, ay = (a["lon"] - p["lon"]) * scale_x, (a["lat"] - p["lat"]) * 111195
        bx, by = (b["lon"] - p["lon"]) * scale_x, (b["lat"] - p["lat"]) * 111195
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        t = max(0., min(1., -(ax * dx + ay * dy) / length2)) if length2 else 0.
        d = math.hypot(ax + t * dx, ay + t * dy)
        segment = math.sqrt(length2)
        if d < best:
            best, progress = d, total + segment * t
        total += segment
    return best, progress


def sample_route(points, spacing=10000., limit=8):
    """At most eight searches; gaps are disclosed, not presented as exhaustive coverage."""
    accumulated = [0.]
    for a, b in zip(points, points[1:]):
        accumulated.append(accumulated[-1] + distance(a, b))
    total = accumulated[-1]
    count = min(limit, max(2, math.ceil(total / spacing) + 1))
    output = []
    for n in range(count):
        target = total * n / (count - 1)
        i = next((i for i in range(1, len(points)) if accumulated[i] >= target), len(points) - 1)
        span = accumulated[i] - accumulated[i-1]
        t = (target - accumulated[i-1]) / span if span else 0
        output.append({k: points[i-1][k] + t * (points[i][k] - points[i-1][k]) for k in ("lat", "lon")})
    return output, count == limit and total > spacing * (limit-1)


def kakao(url, key):
    if not key:
        raise ValueError("서버에 카카오 REST API 키를 설정하세요")
    req = Request(url, headers={"Authorization": "KakaoAK " + key, "Accept": "application/json"})
    try:
        with urlopen(req, timeout=12) as response:
            return json.loads(response.read(8_000_000))
    except HTTPError as error:
        # Do not forward provider bodies, keys or request URLs to clients/logs.
        raise ValueError(f"카카오 API 응답 {error.code}: 키·사용 설정·쿼터를 확인하세요") from None
    except (URLError, TimeoutError):
        raise ValueError("카카오 API 연결 실패 또는 시간 초과") from None


def search(query, center=None):
    params = {"query": str(query)[:100], "size": 15, "page": 1}
    if not params["query"].strip():
        raise ValueError("장소 검색어를 입력하세요")
    if center:
        params.update(x=center["lon"], y=center["lat"], radius=3000, sort="distance")
    result = kakao("https://dapi.kakao.com/v2/local/search/keyword.json?" + urlencode(params), os.getenv("KAKAO_REST_API_KEY", ""))
    places = []
    for item in result.get("documents", []):
        p = point({"lat": item["y"], "lon": item["x"]})
        places.append(dict(p, id=item["id"], name=item["place_name"], address=item.get("road_address_name") or item.get("address_name", ""),
                           phone=item.get("phone", ""), category=item.get("category_name", ""), url=item.get("place_url", "")))
    return places


def directions(origin, destination, priority="DISTANCE", waypoint=None):
    if priority not in ("DISTANCE", "TIME", "RECOMMEND"):
        raise ValueError("경로 우선순위 오류")
    params = {"origin": f'{origin["lon"]},{origin["lat"]}', "destination": f'{destination["lon"]},{destination["lat"]}',
              "priority": priority, "summary": "false", "alternatives": "false"}
    if waypoint:
        params["waypoints"] = f'{waypoint["lon"]},{waypoint["lat"]}'
    result = kakao("https://apis-navi.kakaomobility.com/v1/directions?" + urlencode(params), os.getenv("KAKAO_MOBILITY_REST_API_KEY") or os.getenv("KAKAO_REST_API_KEY", ""))
    routes = result.get("routes", [])
    if not routes or routes[0].get("result_code") != 0:
        raise ValueError("주행 가능한 경로를 찾지 못했습니다")
    route = routes[0]
    points = []
    for section in route.get("sections", []):
        for road in section.get("roads", []):
            vertices = road.get("vertexes", [])
            for i in range(0, len(vertices)-1, 2):
                p = point({"lon": vertices[i], "lat": vertices[i+1]})
                if not points or p != points[-1]:
                    points.append(p)
    if len(points) < 2:
        raise ValueError("표시할 도로 경로가 없습니다")
    summary = route["summary"]
    return {"points": points, "distance": summary["distance"], "duration": summary["duration"], "priority": priority}


class Handler(BaseHTTPRequestHandler):
    semaphore = threading.BoundedSemaphore(4)

    def log_message(self, *_):
        pass  # Location, query, token and keys must not be logged.

    def respond(self, code, data, mime="application/json; charset=utf-8"):
        body = data.encode() if isinstance(data, str) else json.dumps(data, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", mime)
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            return self.respond(200, {"ok": True, "mapConfigured": bool(os.getenv("KAKAO_JAVASCRIPT_KEY")), "searchConfigured": bool(os.getenv("KAKAO_REST_API_KEY"))})
        if self.path == "/map":
            key = os.getenv("KAKAO_JAVASCRIPT_KEY", "")
            if not re.fullmatch(r"[A-Za-z0-9_-]{10,100}", key):
                return self.respond(503, "지도 키 미설정: 서버의 KAKAO_JAVASCRIPT_KEY를 설정하세요", "text/plain; charset=utf-8")
            return self.respond(200, Path(__file__).with_name("map.html").read_text(encoding="utf-8").replace("__MAP_KEY__", key), "text/html; charset=utf-8")
        self.respond(404, {"error": "없는 경로"})

    def do_POST(self):
        token = os.getenv("DONGHAENG_TOKEN", "")
        if token and not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token):
            return self.respond(401, {"error": "서버 연결 토큰을 확인하세요"})
        if self.headers.get("Content-Type", "").split(";")[0] != "application/json":
            return self.respond(415, {"error": "JSON 요청만 허용됩니다"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 500_000:
                return self.respond(413, {"error": "요청 크기 제한"})
            data = json.loads(self.rfile.read(length))
            if not isinstance(data, dict):
                raise ValueError("요청 형식 오류")
            if not self.semaphore.acquire(blocking=False):
                return self.respond(429, {"error": "다른 요청 처리 중입니다. 잠시 후 다시 시도하세요"})
            try:
                if self.path == "/search":
                    result = {"places": search(data.get("query", ""), point(data["center"]) if data.get("center") else None)}
                elif self.path == "/route":
                    result = directions(point(data["origin"]), point(data["destination"]), data.get("priority", "DISTANCE"), point(data["waypoint"]) if data.get("waypoint") else None)
                elif self.path == "/near-route":
                    raw = data.get("points", [])
                    if not 2 <= len(raw) <= 20000:
                        raise ValueError("경로를 먼저 조회하세요")
                    points = [point(p) for p in raw]
                    centers, limited = sample_route(points)
                    found = {}
                    for center in centers:
                        for place in search(data.get("query", "휴게소"), center):
                            offset, progress = offset_from_route(place, points)
                            if offset <= 3000:
                                found[place["id"]] = dict(place, offset=round(offset), progress=round(progress))
                    result = {"places": sorted(found.values(), key=lambda p: p["progress"])[:30],
                              "coverageLimited": limited, "note": "경로 주변 최대 8개 지점·각 15개 검색. 전체 시설 목록이 아니며 실제 도로 접근 방향과 우회는 별도 확인이 필요합니다."}
                else:
                    return self.respond(404, {"error": "없는 경로"})
                self.respond(200, result)
            finally:
                self.semaphore.release()
        except (ValueError, KeyError, TypeError, OverflowError):
            # Safe errors only; never echo input secrets.
            self.respond(400, {"error": "요청 또는 카카오 API 처리 실패. 서버 키·API 사용 설정·좌표·검색어·쿼터를 확인하세요."})
        except Exception:
            self.respond(502, {"error": "외부 서비스 응답을 처리하지 못했습니다"})


if __name__ == "__main__":
    host = os.getenv("DONGHAENG_HOST", "127.0.0.1")
    if host not in ("127.0.0.1", "localhost") and not os.getenv("DONGHAENG_TOKEN"):
        raise SystemExit("외부 공개 전 DONGHAENG_TOKEN과 HTTPS 역방향 프록시가 필요합니다")
    print("Donghaeng development proxy: http://localhost:8787 (REST keys are not logged)")
    ThreadingHTTPServer((host, 8787), Handler).serve_forever()
