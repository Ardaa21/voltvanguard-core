"""
Application settings loaded from environment variables / .env file.

Uses Pydantic BaseSettings so every field is:
  - Type-validated at startup (bad config → crash early, not silently)
  - Overridable via environment variables (12-factor app)
  - Documented with Field descriptions (shows up in --help / docs)
"""
from __future__ import annotations

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class KafkaSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="KAFKA_", extra="ignore")

    bootstrap_servers: str   = Field("localhost:9092",          description="Comma-separated Kafka broker addresses")
    consumer_group:    str   = Field("voltvanguard-ai-agent-group", description="Consumer group ID")
    telemetry_topic:   str   = Field("telemetry.raw",           description="Source topic for raw EV telemetry")
    alerts_topic:      str   = Field("vehicle.alerts",          description="Destination topic for vehicle alerts")
    dlq_topic:         str   = Field("telemetry.raw.DLT",       description="Dead-letter queue for unprocessable messages")

    # Polling
    poll_timeout_seconds:   float = Field(1.0,  description="Max seconds to block in poll()")
    max_poll_records:       int   = Field(500,  description="Max messages returned per poll")
    session_timeout_ms:     int   = Field(30_000)
    heartbeat_interval_ms:  int   = Field(10_000)

    # Retry / DLQ
    max_processing_retries: int   = Field(3, description="Retries before routing to DLQ")


class BackendSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="BACKEND_", extra="ignore")

    base_url:        str   = Field("http://localhost:8080/api/v1")
    timeout_seconds: float = Field(10.0)
    max_retries:     int   = Field(3)


class OpenAISettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="OPENAI_", extra="ignore")

    api_key:          str   = Field("",           description="OpenAI API key (required if LLM_ENABLED=true)")
    model:            str   = Field("gpt-4o-mini")
    max_tokens:       int   = Field(512)
    temperature:      float = Field(0.1)
    timeout_seconds:  float = Field(15.0)

    @field_validator("temperature")
    @classmethod
    def clamp_temperature(cls, v: float) -> float:
        return max(0.0, min(2.0, v))


class DecisionSettings(BaseSettings):
    model_config = SettingsConfigDict(extra="ignore")

    battery_critical_threshold:   float = Field(15.0, description="% — immediate charging required")
    battery_low_threshold:        float = Field(25.0, description="% — high urgency")
    battery_proactive_threshold:  float = Field(35.0, description="% — ask LLM for nuanced decision")

    station_search_radius_km: float = Field(30.0)
    station_max_results:      int   = Field(5)

    llm_enabled:                  bool  = Field(True)
    reservation_cooldown_minutes: int   = Field(30, description="Min minutes between reservations per vehicle")


class LogSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="LOG_", extra="ignore")

    level:          str  = Field("INFO")
    file:           str  = Field("logs/route-optimizer.log")
    rotation:       str  = Field("50 MB")
    retention:      str  = Field("7 days")
    serialize_json: bool = Field(False, description="Enable JSON log serialization for ELK/Loki")

    @field_validator("level")
    @classmethod
    def validate_level(cls, v: str) -> str:
        allowed = {"DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"}
        upper = v.upper()
        if upper not in allowed:
            raise ValueError(f"LOG_LEVEL must be one of {allowed}")
        return upper


class Settings(BaseSettings):
    """Root settings — aggregates all sub-configs."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    agent_id: str = Field("route-optimizer-1", description="Unique replica identifier")

    kafka:    KafkaSettings    = Field(default_factory=KafkaSettings)
    backend:  BackendSettings  = Field(default_factory=BackendSettings)
    openai:   OpenAISettings   = Field(default_factory=OpenAISettings)
    decision: DecisionSettings = Field(default_factory=DecisionSettings)
    log:      LogSettings      = Field(default_factory=LogSettings)


# ── Singleton ──────────────────────────────────────────────────────────────────
# Import `settings` everywhere; never call Settings() more than once.
settings = Settings()
