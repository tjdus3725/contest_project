import json
import math
import os
import threading
import unittest
from http.server import ThreadingHTTPServer
from unittest.mock import patch
from urllib.request import Request, urlopen
from urllib.error import HTTPError

import server


class GeometryTest(unittest.TestCase):
    def test_segment_distance_not_vertex_distance(self):
        points = [{"lat": 37., "lon": 127.}, {"lat": 37., "lon": 127.2}]
        offset, progress = server.offset_from_route({"lat": 37.001, "lon": 127.1}, points)
        self.assertLess(offset, 120)
        self.assertGreater(progress, 8000)

    def test_coordinates_reject_nan_inf_and_invalid_range(self):
        for lat in (math.nan, math.inf, 91):
            with self.assertRaises(ValueError):
                server.point({"lat": lat, "lon": 127})

    def test_samples_capped_and_endpoints_included(self):
        points = [{"lat": 35., "lon": 127.}, {"lat": 38., "lon": 127.}]
        samples, limited = server.sample_route(points)
        self.assertEqual(8, len(samples)); self.assertTrue(limited)
        self.assertEqual(points[0], samples[0]); self.assertEqual(points[-1], samples[-1])

    def test_zero_length_route(self):
        p = {"lat": 37., "lon": 127.}
        samples, _ = server.sample_route([p, p])
        self.assertEqual([p, p], samples)

    def test_provider_longitude_latitude_order_and_road_geometry(self):
        payload = {"routes": [{"result_code": 0, "summary": {"distance": 100, "duration": 20}, "sections": [{"roads": [{"vertexes": [127., 37., 127.01, 37.01]}]}]}]}
        with patch.object(server, "kakao", return_value=payload) as api:
            result = server.directions({"lat": 37., "lon": 127.}, {"lat": 37.01, "lon": 127.01})
        self.assertEqual({"lat": 37., "lon": 127.}, result["points"][0])
        self.assertIn("origin=127.0%2C37.0", api.call_args.args[0])
        self.assertIn("priority=DISTANCE", api.call_args.args[0])


class HttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.http = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()
        cls.url = "http://127.0.0.1:" + str(cls.http.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown(); cls.http.server_close()

    def post(self, path, data, headers=None):
        return urlopen(Request(self.url + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json", **(headers or {})}), timeout=3)

    def test_token_required_for_api_calls(self):
        with patch.dict(os.environ, {"DONGHAENG_TOKEN": "test-secret"}):
            with self.assertRaises(HTTPError) as error:
                self.post("/search", {"query": "휴게소"})
            self.assertEqual(401, error.exception.code)

    def test_places_are_deduplicated_and_outside_corridor_removed(self):
        points = [{"lat": 37., "lon": 127.}, {"lat": 37., "lon": 127.2}]
        places = [{"id": "1", "name": "실제 응답 fixture", "lat": 37.001, "lon": 127.1}, {"id": "2", "name": "먼 장소", "lat": 38., "lon": 127.1}]
        with patch.dict(os.environ, {"DONGHAENG_TOKEN": ""}), patch.object(server, "search", return_value=places):
            with self.post("/near-route", {"query": "휴게소", "points": points}) as response:
                data = json.load(response)
        self.assertEqual(["1"], [p["id"] for p in data["places"]])
        self.assertNotIn("facilities", data["places"][0])

    def test_no_api_key_returns_error_not_fake_data(self):
        with patch.dict(os.environ, {"DONGHAENG_TOKEN": "", "KAKAO_REST_API_KEY": ""}):
            with self.assertRaises(HTTPError) as error:
                self.post("/search", {"query": "휴게소"})
        self.assertEqual(400, error.exception.code)

    def test_bad_coordinates_and_missing_route_are_rejected(self):
        with patch.dict(os.environ, {"DONGHAENG_TOKEN": ""}):
            for body in ({"points": []}, {"points": [{"lat": 100, "lon": 127}, {"lat": 37, "lon": 127}]}):
                with self.assertRaises(HTTPError) as error:
                    self.post("/near-route", body)
                self.assertEqual(400, error.exception.code)

    def test_map_page_does_not_expose_rest_key(self):
        with patch.dict(os.environ, {"KAKAO_JAVASCRIPT_KEY": "public-javascript-test-key", "KAKAO_REST_API_KEY": "REST-SECRET"}):
            with urlopen(self.url + "/map") as response:
                html = response.read().decode()
        self.assertIn("public-javascript-test-key", html)
        self.assertNotIn("REST-SECRET", html)
        self.assertNotIn("__MAP_KEY__", html)


if __name__ == "__main__":
    unittest.main()
