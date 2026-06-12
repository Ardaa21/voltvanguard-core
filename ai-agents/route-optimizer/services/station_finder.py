"""
Finds the optimal available charging station near a vehicle's location
by querying the VoltVanguard backend REST API.

Selection strategy (in order of priority):
  1. Available connectors (must have at least 1)
  2. Power output (higher kW → faster charge → better for urgent cases)
  3. Distance (prefer closer stations, but don't sacrifice power severely)

The finder returns a ranked list; the caller picks from the top.
"""
from __future__ import annotations

import math
from typing import Optional

import httpx
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
    before_sleep_log,
)

from config.settings import settings
from models.reservation import ApiResponse, StationResponse
from utils.logger import get_logger

_log = get_logger("services.station_finder")


# ── Haversine ─────────────────────────────────────────────────────────────────

def _haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Great-circle distance between two GPS coordinates in kilometres."""
    R = 6_371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi  = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ── Station Finder ────────────────────────────────────────────────────────────

class StationFinder:
    """HTTP client for the /stations/nearby/available endpoint."""

    def __init__(self) -> None:
        self._client = httpx.Client(
            base_url=settings.backend.base_url,
            timeout=settings.backend.timeout_seconds,
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        _log.info("StationFinder initialised", base_url=settings.backend.base_url)

    # ── Public ────────────────────────────────────────────────────────────────

    def find_best(
        self,
        latitude:         float,
        longitude:        float,
        radius_km:        Optional[float] = None,
        require_min_power_kw: Optional[float] = None,
    ) -> Optional[StationResponse]:
        """
        Returns the single best station for an immediate charge.
        Returns None if no station found within radius or on API error.
        """
        radius = radius_km or settings.decision.station_search_radius_km
        candidates = self._fetch_nearby(latitude, longitude, radius)

        if not candidates:
            _log.warning(
                "No available stations found",
                lat=latitude, lng=longitude, radius_km=radius,
            )
            return None

        if require_min_power_kw:
            candidates = [s for s in candidates if float(s.power_kw) >= require_min_power_kw]
            if not candidates:
                _log.warning(
                    "No station meets minimum power requirement",
                    min_power_kw=require_min_power_kw,
                )
                return None

        ranked = self._rank_stations(candidates, latitude, longitude)
        best = ranked[0]

        dist = _haversine_km(latitude, longitude, float(best.latitude), float(best.longitude))
        _log.info(
            "Best station selected",
            station_id=str(best.id),
            station_name=best.name,
            distance_km=round(dist, 2),
            power_kw=float(best.power_kw),
            available_connectors=best.available_connectors,
        )
        return best

    def find_all_nearby(
        self,
        latitude:  float,
        longitude: float,
        radius_km: Optional[float] = None,
    ) -> list[StationResponse]:
        """Returns all available stations within radius, ranked by score."""
        radius = radius_km or settings.decision.station_search_radius_km
        candidates = self._fetch_nearby(latitude, longitude, radius)
        return self._rank_stations(candidates, latitude, longitude)

    def close(self) -> None:
        self._client.close()

    # ── Private: HTTP ─────────────────────────────────────────────────────────

    @retry(
        retry=retry_if_exception_type((httpx.TransportError, httpx.TimeoutException)),
        stop=stop_after_attempt(settings.backend.max_retries),
        wait=wait_exponential(multiplier=1, min=1, max=8),
        before_sleep=before_sleep_log(_log, "WARNING"),  # type: ignore[arg-type]
    )
    def _fetch_nearby(
        self,
        latitude:  float,
        longitude: float,
        radius_km: float,
    ) -> list[StationResponse]:
        """
        GET /stations/nearby/available?lat=&lng=&radiusKm=
        Retried automatically on transient network errors.
        """
        try:
            resp = self._client.get(
                "/stations/nearby/available",
                params={"lat": latitude, "lng": longitude, "radiusKm": radius_km},
            )
            resp.raise_for_status()

            envelope = ApiResponse.model_validate(resp.json())

            if not envelope.success or envelope.data is None:
                _log.warning(
                    "Backend returned unsuccessful response",
                    error=envelope.error,
                )
                return []

            stations = [StationResponse.model_validate(s) for s in envelope.data]
            _log.debug(
                "Station search result",
                count=len(stations),
                lat=latitude, lng=longitude, radius_km=radius_km,
            )
            return stations

        except httpx.HTTPStatusError as exc:
            _log.error(
                "Backend HTTP error",
                status_code=exc.response.status_code,
                url=str(exc.request.url),
            )
            return []
        except Exception as exc:
            _log.error("Station fetch failed", error=str(exc), exc_info=True)
            return []

    # ── Private: Ranking ─────────────────────────────────────────────────────

    @staticmethod
    def _rank_stations(
        stations:  list[StationResponse],
        ref_lat:   float,
        ref_lng:   float,
    ) -> list[StationResponse]:
        """
        Composite score = power_kw_norm * 0.5 + availability_norm * 0.3 - distance_norm * 0.2

        Rationale: power output is the most important factor (directly impacts wait time),
        followed by connector availability (reduces uncertainty), then proximity.
        """
        if not stations:
            return []

        max_power = max(float(s.power_kw) for s in stations) or 1.0
        max_dist  = max(
            _haversine_km(ref_lat, ref_lng, float(s.latitude), float(s.longitude))
            for s in stations
        ) or 1.0

        def score(s: StationResponse) -> float:
            dist   = _haversine_km(ref_lat, ref_lng, float(s.latitude), float(s.longitude))
            power_norm  = float(s.power_kw) / max_power
            avail_norm  = s.available_connectors / max(s.total_connectors, 1)
            dist_norm   = dist / max_dist
            return power_norm * 0.5 + avail_norm * 0.3 - dist_norm * 0.2

        return sorted(stations, key=score, reverse=True)
