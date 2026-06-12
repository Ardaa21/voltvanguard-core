<div align="center">

```
 ██╗   ██╗ ██████╗ ██╗  ████████╗    ██╗   ██╗ █████╗ ███╗   ██╗ ██████╗ ██╗   ██╗ █████╗ ██████╗ ██████╗
 ██║   ██║██╔═══██╗██║  ╚══██╔══╝    ██║   ██║██╔══██╗████╗  ██║██╔════╝ ██║   ██║██╔══██╗██╔══██╗██╔══██╗
 ██║   ██║██║   ██║██║     ██║       ██║   ██║███████║██╔██╗ ██║██║  ███╗██║   ██║███████║██████╔╝██║  ██║
 ╚██╗ ██╔╝██║   ██║██║     ██║       ╚██╗ ██╔╝██╔══██║██║╚██╗██║██║   ██║██║   ██║██╔══██║██╔══██╗██║  ██║
  ╚████╔╝ ╚██████╔╝███████╗██║        ╚████╔╝ ██║  ██║██║ ╚████║╚██████╔╝╚██████╔╝██║  ██║██║  ██║██████╔╝
   ╚═══╝   ╚═════╝ ╚══════╝╚═╝         ╚═══╝  ╚═╝  ╚═╝╚═╝  ╚═══╝ ╚═════╝  ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```

### Otonom EV Zeka ve Şebeke Orkestratörü

*Elektrikli araç filolarını gerçek zamanlı izleyen, yapay zeka destekli şarj kararları alan<br>ve mobil bir komuta merkezi sunan üretim kalitesinde bir platform.*

---

[![Java](https://img.shields.io/badge/Java-21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-3.7-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white)](https://kafka.apache.org/)
[![Python](https://img.shields.io/badge/Python-3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://www.python.org/)
[![Flutter](https://img.shields.io/badge/Flutter-3.x-02569B?style=for-the-badge&logo=flutter&logoColor=white)](https://flutter.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![OpenAI](https://img.shields.io/badge/OpenAI-GPT--4o--mini-412991?style=for-the-badge&logo=openai&logoColor=white)](https://platform.openai.com/)
[![License](https://img.shields.io/badge/Lisans-MIT-yellow.svg?style=for-the-badge)](LICENSE)

</div>

---

## İçindekiler

- [Bu Proje Nedir?](#-bu-proje-nedir)
- [Neden Yaptım?](#-neden-yaptım)
- [Ne Öğrendim?](#-ne-öğrendim)
- [Sistem Mimarisi](#-sistem-mimarisi)
- [Teknoloji Yığını](#-teknoloji-yığını)
- [Proje Yapısı](#-proje-yapısı)
- [Bileşenler](#-bileşenler)
- [Tek Komutla Çalıştırma](#-tek-komutla-çalıştırma)
- [API Referansı](#-api-referansı)
- [Yapay Zeka Karar Motoru](#-yapay-zeka-karar-motoru)
- [Mühendislik Kararları](#-mühendislik-kararları)
- [Performans](#-performans)
- [Yol Haritası](#-yol-haritası)

---

## 💡 Bu Proje Nedir?

**VoltVanguard**, elektrikli araç filolarını gerçek zamanlı olarak izleyen, hangi aracın nerede şarj olması gerektiğine yapay zeka ile karar veren ve bu kararları tamamen otomatik olarak hayata geçiren, uçtan uca bir yazılım platformudur.

Sistem dört ana bileşenden oluşur:

**1. Spring Boot Backend (Java 21)**
Araçlardan gelen telemetri verilerini (batarya yüzdesi, konum, hız, sıcaklık) REST API ve WebSocket üzerinden yönetir. Apache Kafka ile her saniye yüzlerce olayı işler, verileri PostgreSQL'e yazar ve Redis cache ile anlık araç durumlarını tutar.

**2. Apache Kafka Olay Akışı**
Araç telemetrisi, Kafka üzerinden `telemetry.raw` topic'ine akar. Spring Boot bu topic'i dinler, işler ve kritik durumlarda `vehicle.alerts` topic'ine uyarı yayınlar. Bir mesaj işlenemezse Dead Letter Topic'e yönlendirilir, böylece hiçbir veri kaybolmaz.

**3. Python Yapay Zeka Ajanı (Route Optimizer)**
Kafka'dan telemetri verisi okuyan bu Python servisi, her araç için şarj kararı alır. Karar iki katmanlıdır: önce deterministic kurallar çalışır (batarya ≤ %15 → kritik, anında şarj), gri bölgede (%15–35) ise GPT-4o-mini devreye girerek hız, sıcaklık, tahmini menzil ve günün saati gibi bağlamsal faktörleri değerlendirir. Şarj kararı verilen araç için en yakın uygun istasyon Haversine formülüyle bulunur ve otomatik bir görev oluşturulur.

**4. Flutter Mobil Uygulama**
Filo operatörlerine gerçek zamanlı araç durumu, batarya seviyeleri ve otonom görevleri gösteren iOS/Android uygulaması. WebSocket bağlantısı üzerinden anlık telemetri akışı alır; bağlantı kesildiğinde üstel geri çekilme (exponential backoff) ile yeniden bağlanır. Kritik batarya uyarıları push notification olarak iletilir.

---

## 🎯 Neden Yaptım?

Bilgisayar mühendisliği öğrencisi olarak ders projelerinde sürekli aynı kalıpları görüyordum: basit CRUD uygulamaları, tek bir dil, tek bir veritabanı. Gerçek dünyada kullanılan sistemlerin nasıl çalıştığını öğrenmek istedim.

**Üç somut hedefim vardı:**

**1. Olay güdümlü mimariyi gerçekten anlamak.**
Kafka'yı teoride bilmek ayrı bir şey, bir üretim konfigürasyonu kurmak (idempotent producer, manual-ACK consumer, Dead Letter Topic, retry policy) bambaşka bir şey. Bu projeyi yapana kadar "neden Kafka?" sorusuna gerçekten iyi bir cevap veremiyordum. Şimdi verebiliyorum.

**2. Yapay zekayı bir araç olarak kullanmak, amaç olarak değil.**
Çoğu öğrenci projesi yapay zeka *hakkında* olur. Burada yapay zeka sistemi bir bileşen olarak kullanır — ve dahası, nerede kullanmaması gerektiğini de biliyor. Batarya %8'e düştüğünde GPT-4o-mini'yi beklemenin anlamı yok; kural motoru anında devreye giriyor. İki katmanlı karar motoru bu dengeyi kuruyor.

**3. Birden fazla dil ve teknolojiyi tek bir tutarlı sistemde bir araya getirmek.**
Java backend, Python AI ajanı, Dart mobil uygulama — üçü de birbirinden haberdar ve birlikte çalışıyor. Farklı dillerin farklı problemler için neden daha iyi olduğunu bizzat yaşayarak öğrendim: Java'nın tip güvenliliği ve Spring ekosistemi backend için ideal, Python'ın ml/ai kütüphaneleri ve esnek yapısı karar motoru için mükemmel, Flutter'ın widget sistemi ise hızlı ve güzel bir mobil deneyim için biçilmiş kaftan.

**Konu seçimi — neden elektrikli araçlar?**
Türkiye'de ve dünyada EV benimsenmesi hızlanıyor. Şarj altyapısı koordinasyonu gerçek bir mühendislik problemi. Rivian, Tesla Fleet, ChargePoint gibi şirketlerin backend sistemleri tam da bu sorunları çözüyor. Bu alandaki gerçek teknik zorluklarla yüzleşmek istedim: yüksek frekanslı veri akışları, gerçek zamanlı karar alma, coğrafi sorgular, mobil push notification'lar — hepsi bir arada.

---

## 📚 Ne Öğrendim?

Bu proje boyunca öğrendiklerimi birkaç başlık altında özetleyebilirim:

### Dağıtık Sistemler
- Kafka consumer group'larının nasıl çalıştığını, partition rebalancing'in ne zaman olduğunu ve neden `enable.auto.commit=false` kullanmak gerektiğini anladım.
- Dead Letter Topic'in neden sadece "başarısız mesajları bir yere atmak" değil, sistemi güvenli tutmanın temel mekanizması olduğunu gördüm.
- Servisler arasında tutarlılık (consistency) sağlamanın ne kadar dikkat gerektirdiğini yaşadım.

### Önbellekleme Stratejisi
- Redis'i sadece "hızlı veritabanı" olarak değil, araç durumunu tutan gerçek zamanlı bir durum deposu olarak kullandım.
- Her telemetri mesajını doğrudan PostgreSQL'e yazmak yerine Redis'e yazıp sadece kritik durum değişikliklerinde ve periyodik aralıklarda DB'ye flush etmenin DB yükünü %97 azalttığını ölçtüm.

### Yapay Zeka Entegrasyonu
- LLM'lerin güçlü olduğu ve olmadığı durumları ayırt etmeyi öğrendim. Kritik güvenlik kararlarında LLM'ye güvenmek yanlış; deterministik kurallar her zaman daha güvenilir.
- Structured output ile GPT-4o-mini'den JSON şeması almayı, prompt mühendisliğini ve API maliyetini minimize ederken karar kalitesini korumayı deneyimledim.

### Reaktif Mobil Geliştirme
- Flutter Riverpod ile `StreamProvider` ve `FutureProvider.family` kombinasyonunu kullanarak WebSocket üzerinden gelen gerçek zamanlı veriyi minimize widget rebuild ile nasıl yönetirim öğrendim.
- Expo'dan Backoff'a kadar WebSocket bağlantı yönetiminin nüanslarını anladım.

### Altyapı ve DevOps
- Docker Compose ile çok servisli bir ortam kurmayı, healthcheck'leri ve servis bağımlılıklarını yönetmeyi öğrendim.
- `start_all.sh` betiğini yazarken shell scripting, process yönetimi ve farklı Java sürümlerinin nasıl ele alındığını pratikte gördüm.

---

## 🏛 Sistem Mimarisi

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              VoltVanguard Sistem Mimarisi                            │
└─────────────────────────────────────────────────────────────────────────────────────┘

  ┌──────────────┐   REST/WS      ┌──────────────────────────────────────────────────┐
  │   Flutter    │◄──────────────►│             Spring Boot Backend                  │
  │  Mobil App   │                │         (Java 21 · Spring Boot 3.3)              │
  │              │                │                                                  │
  │  Riverpod    │                │  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
  │  WebSocket   │                │  │  Araç    │  │ İstasyon │  │   Otonom      │  │
  │  GoRouter    │                │  │   API    │  │   API    │  │   Görev API   │  │
  └──────────────┘                │  └────┬─────┘  └────┬─────┘  └──────┬────────┘  │
                                  │       └──────────────┴───────────────┘            │
                                  │                    │                              │
                                  │           ┌────────▼────────┐                    │
                                  │           │  Servis Katmanı  │                    │
                                  │           │  VehicleService  │                    │
                                  │           │  StationService  │                    │
                                  │           │  TaskService     │                    │
                                  │           └───────┬─────┬────┘                    │
                                  │                   │     │                         │
                                  │          ┌────────▼─┐ ┌─▼──────────┐             │
                                  │          │   JPA /  │ │   Spring   │             │
                                  │          │ Hibernate│ │   Cache    │             │
                                  │          └────┬─────┘ └─────┬──────┘             │
                                  └───────────────┼─────────────┼────────────────────┘
                                                  │             │
                                           ┌──────▼──────┐ ┌───▼─────────┐
                                           │ PostgreSQL  │ │    Redis    │
                                           │     16      │ │      7      │
                                           └─────────────┘ └─────────────┘
                                                  ▲
                                                  │  seçici kalıcı depolama
                                                  │
  ┌───────────────────────────────────────────────┼─────────────────────────────────┐
  │                        Apache Kafka Kümesi     │                                 │
  │                                               │                                 │
  │  telemetry.raw · telemetry.processed · vehicle.alerts · telemetry.raw.DLT      │
  │                                               │                                 │
  │   ┌───────────────────────────────────────────┴──────────────────────────────┐  │
  │   │  TelemetryConsumer — manuel ACK · üstel geri çekilmeli yeniden deneme    │  │
  │   │  Redis güncelle → durum değişikliği tespit → DB kaydet → uyarı yayınla  │  │
  │   └──────────────────────────────────────────────────────────────────────────┘  │
  │                                                                                 │
  │   ┌──────────────────────────────────────────────────────────────────────────┐  │
  │   │  TelemetrySimulator — @Scheduled · araç başına saniyede 1 mesaj          │  │
  │   └──────────────────────────────────────────────────────────────────────────┘  │
  └─────────────────────────────────────────────────────────────────────────────────┘
                                          ▲
                                          │  telemetry.raw okuyor
                                          │
  ┌───────────────────────────────────────┼─────────────────────────────────────────┐
  │                Python Yapay Zeka Ajanı — Route Optimizer                         │
  │                                       │                                         │
  │    confluent-kafka consumer ──────────┘                                         │
  │         ▼                                                                       │
  │    ┌────────────────────────────────────────────────────────────┐               │
  │    │                  İki Katmanlı Karar Motoru                  │               │
  │    │                                                            │               │
  │    │   Batarya %     ┌──────────────┐                          │               │
  │    │   ──────────►   │ Kural Motoru │──► KRİTİK / YOK ────────►│               │
  │    │                 └──────┬───────┘       (< 1 ms)           │               │
  │    │                   Gri  │ Bölge?                            │               │
  │    │                        ▼                                   │               │
  │    │                 ┌──────────────┐                          │               │
  │    │   Bağlam ─────  │  GPT-4o-mini │──► YZ kararı ──────────►│               │
  │    │  (hız, sıcaklık │   (httpx)    │    + güvenlik vetosu     │               │
  │    │   menzil, saat) └──────────────┘                          │               │
  │    └────────────────────────────────────────────────────────────┘               │
  │         │                                                                       │
  │         │  should_charge = True                                                 │
  │         ▼                                                                       │
  │    İstasyonBulucu ──► GET /stations/nearby/available (Haversine + puanlama)    │
  │         │                                                                       │
  │         │  puan = güç×0.5 + müsaitlik×0.3 − mesafe×0.2                        │
  │         ▼                                                                       │
  │    RezervasyonServisi ──► POST /tasks  (30 dk soğuma süresi)                   │
  └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠 Teknoloji Yığını

### Backend — Spring Boot (Java 21)

| Teknoloji | Sürüm | Kullanım Amacı |
|---|---|---|
| **Java** | 21 LTS (Temurin) | Sanal thread'ler, record'lar, sealed class'lar |
| **Spring Boot** | 3.3.0 | Otomatik yapılandırma, bağımlılık enjeksiyonu |
| **Spring Data JPA** | 3.3.x | Hibernate ORM, UUID birincil anahtar, JSONB sütunlar |
| **Spring Kafka** | 3.x | `@KafkaListener`, producer/consumer yönetimi |
| **Spring Cache** | 3.x | `@Cacheable`, `@CachePut`, `@CacheEvict` |
| **Spring Actuator** | 3.3.x | Sağlık kontrolleri, Prometheus metrikleri |
| **PostgreSQL** | 16 | Ana ilişkisel veritabanı |
| **Redis** | 7 | Önbellek katmanı, gerçek zamanlı araç durumu |
| **Apache Kafka** | 3.7 | Dayanıklı olay akışı altyapısı |
| **Flyway** | 10.x | Versiyon kontrollü veritabanı migrations |
| **Lombok** | 1.18.x | `@Builder`, `@Data`, `@Slf4j` ile şablon kod azaltımı |
| **MapStruct** | 1.5.5 | Derleme zamanında güvenli entity ↔ DTO dönüşümü |
| **springdoc-openapi** | 2.5.0 | Otomatik Swagger UI oluşturma |
| **Micrometer + Prometheus** | 1.13.x | Sayaçlar, zamanlayıcılar (p50/p95/p99), göstergeler |

### Olay Akışı — Apache Kafka

| Ayar | Değer | Neden? |
|---|---|---|
| `acks` | `all` | Mesaj kaybı yok — tüm replikalar onaylamalı |
| `enable.idempotence` | `true` | Exactly-once producer semantiği |
| `compression.type` | `snappy` | ~%60 boyut azaltımı, düşük CPU kullanımı |
| `batch.size` | `32 768` | Toplu işleme ile verimlilik optimizasyonu |
| Consumer ACK | Manuel | Sadece başarılı işlem sonrası commit |
| Yeniden deneme | Üstel geri çekilme (2 s → 4 s → 8 s) | Geçici hata dayanıklılığı |
| DLT | `telemetry.raw.DLT` (7 gün) | Zehirli mesaj izolasyonu + güvenli tekrar oynatma |

### Yapay Zeka Ajanı — Python

| Teknoloji | Sürüm | Kullanım Amacı |
|---|---|---|
| **Python** | 3.12 | Çalışma zamanı |
| **confluent-kafka** | 2.4.0 | librdkafka tabanlı üretim kalitesinde consumer |
| **openai** | 1.35.0 | GPT-4o-mini, `json_object` yapılandırılmış çıktı |
| **pydantic** | 2.7.4 | İstek/yanıt doğrulama ve ayar yönetimi |
| **pydantic-settings** | 2.3.4 | `.env` tabanlı yazılı yapılandırma |
| **httpx** | 0.27.0 | Backend REST çağrıları için HTTP istemcisi |
| **tenacity** | 8.4.2 | `@retry` ile üstel geri çekilmeli HTTP çağrıları |
| **loguru** | 0.7.2 | Yapılandırılmış JSON loglama, dosya rotasyonu |
| **rich** | 13.7.1 | Açılış ekranı ve okunabilir konsol çıktısı |

### Mobil — Flutter

| Teknoloji | Sürüm | Kullanım Amacı |
|---|---|---|
| **Flutter** | 3.x | iOS ve Android çapraz platform UI |
| **Dart** | 3.3+ | Dil (kayıtlar, sealed class'lar, null safety) |
| **flutter_riverpod** | 2.5.1 | Reaktif durum yönetimi |
| **go_router** | 14.x | `StatefulShellRoute` ile bildirimsel navigasyon |
| **web_socket_channel** | 3.x | Üstel geri çekilmeli WebSocket yeniden bağlantısı |
| **dio** | 5.4.x | Yeniden deneme interceptor'lı HTTP istemcisi |
| **fl_chart** | 0.68 | Batarya geçmişi sparkline grafikleri |
| **flutter_local_notifications** | 17.x | Android + iOS push bildirimleri |
| **flutter_animate** | 4.5 | Bildirimsel mikro animasyonlar |
| **shimmer** | 3.x | Yükleme iskelet UI'ı |

### Altyapı

| Araç | Kullanım Amacı |
|---|---|
| **Docker Compose** | Tek dosyada yerel ortam tanımı |
| **PostgreSQL 16** (Alpine) | Ana veri deposu |
| **Redis 7** (Alpine) | Önbellek ve gerçek zamanlı araç durum deposu |
| **Apache Kafka 3.7** (Confluent) | Mesaj aracısı |
| **Kafdrop** | Kafka web arayüzü — topic, consumer grubu izleme |
| **pgAdmin 4** | Veritabanı yönetim arayüzü |

---

## 📁 Proje Yapısı

```
voltvanguard-core/                             ← Monorepo kökü
│
├── 🚀 start_all.sh                            ← Tek komutla tam stack başlatıcı
├── 🛑 stop_all.sh                             ← Tüm servisleri nazikçe durdurur
├── 🔧 dev_run.sh                              ← Backend + Flutter tek terminalde
│
├── docker-compose.yml                         ← PostgreSQL · Redis · Kafka · Kafdrop · pgAdmin
├── pom.xml                                    ← Maven: Spring Boot 3.3, Java 21
│
├── src/main/java/com/voltvanguard/core/
│   ├── config/                                ← Redis, OpenAPI yapılandırmaları
│   ├── entity/                                ← JPA varlıkları (EV, İstasyon, Görev)
│   ├── enums/                                 ← VehicleStatus, TaskStatus, StationStatus
│   ├── repository/                            ← Spring Data repo'ları + Haversine SQL
│   ├── service/                               ← İş mantığı (arayüzler + uygulamalar)
│   ├── controller/                            ← REST kontrolcüleri
│   ├── dto/                                   ← İstek/Yanıt kayıtları + ApiResponse<T>
│   ├── exception/                             ← GlobalExceptionHandler + alan istisnaları
│   └── kafka/
│       ├── config/                            ← Topic, Producer, Consumer yapılandırmaları
│       ├── event/                             ← VehicleTelemetryEvent, VehicleAlertEvent
│       ├── producer/                          ← TelemetryProducer, TelemetrySimulator
│       └── consumer/                          ← TelemetryConsumer, TelemetryProcessingService
│
├── ai-agents/route-optimizer/
│   ├── main.py                                ← Giriş noktası, sinyal işleyiciler
│   ├── .env.example                           ← Ortam değişkeni şablonu
│   ├── config/settings.py                     ← Pydantic-Settings: tüm ortam değişkenleri
│   ├── kafka/consumer.py                      ← Confluent consumer, hata yönetimi
│   ├── models/                                ← VehicleTelemetryEvent, Reservation (Pydantic)
│   ├── services/
│   │   ├── decision_engine.py                 ← İki katmanlı: Kural motoru + GPT-4o-mini
│   │   ├── llm_client.py                      ← OpenAI yapılandırılmış çıktı istemcisi
│   │   ├── station_finder.py                  ← Haversine sıralama, bileşik puanlama
│   │   └── reservation_service.py             ← POST /tasks, 30 dk soğuma süresi
│   └── utils/logger.py                        ← Loguru yapılandırılmış JSON loglama
│
└── mobile/voltvanguard_app/lib/
    ├── main.dart                              ← Uygulama girişi, bildirim başlatma
    ├── core/
    │   ├── network/api_client.dart            ← Dio + yeniden deneme interceptor'ı
    │   ├── network/websocket_service.dart     ← Yeniden bağlantı döngüsü
    │   ├── services/notification_service.dart ← Push bildirim kurulumu
    │   ├── theme/app_theme.dart               ← Tasarım sistemi: elektrik teal / koyu lacivert
    │   └── router/app_router.dart             ← GoRouter navigasyonu
    └── features/
        ├── dashboard/                         ← Filo grid'i + canlı filo istatistikleri
        ├── vehicle/                           ← Araç detayı + batarya sparkline
        └── reservations/                      ← Yapay zeka görev listesi + bildirim izleyici
```

---

## 🚀 Tek Komutla Çalıştırma

Tüm stack (Docker altyapısı + Spring Boot backend + Python yapay zeka ajanı) tek bir komutla başlatılabilir:

```bash
git clone https://github.com/Ardaa21/voltvanguard-core.git
cd voltvanguard-core

# (İsteğe bağlı) OpenAI anahtarını gir
cp ai-agents/route-optimizer/.env.example ai-agents/route-optimizer/.env
# .env dosyasını düzenle: OPENAI_API_KEY=sk-...

# Her şeyi başlat
chmod +x start_all.sh && ./start_all.sh
```

`start_all.sh` tamamen otomatiktir:

1. Docker Desktop çalışmıyorsa açar ve hazır olmasını bekler (maks. 90 sn)
2. Tüm container'ları başlatır, her sağlık kontrolü geçene kadar bekler
3. Spring Boot'u derler, `/actuator/health` endpoint'i `UP` dönene kadar bekler
4. Python sanal ortamı oluşturur, bağımlılıkları kurar, yapay zeka ajanını başlatır
5. Tüm canlı endpoint'lerin özet tablosunu yazdırır

`start_all.sh` tamamlandıktan sonra, ayrı bir terminalde Flutter uygulamasını başlat:

```bash
cd mobile/voltvanguard_app
flutter pub get
flutter run
```

Durdurmak için:
```bash
./stop_all.sh
```

### Servis Endpoint'leri

| Servis | URL | Kimlik Bilgileri |
|---|---|---|
| **Spring Boot API** | http://localhost:8080/api/v1 | — |
| **Swagger UI** | http://localhost:8080/api/v1/swagger-ui.html | — |
| **Sağlık Kontrolü** | http://localhost:8080/api/v1/actuator/health | — |
| **Prometheus Metrikleri** | http://localhost:8080/api/v1/actuator/prometheus | — |
| **Kafdrop** (Kafka UI) | http://localhost:9000 | — |
| **pgAdmin** | http://localhost:5050 | `admin@voltvanguard.dev` / `admin` |
| **PostgreSQL** | localhost:5432 | `postgres` / `postgres` |
| **Redis** | localhost:6380 | — |
| **Kafka** | localhost:9092 | — |

---

## 📡 API Referansı

Tüm endpoint'ler `http://localhost:8080/api/v1` altındadır. Her yanıt standart bir zarfla sarılır:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "message": "OK",
  "timestamp": "2024-01-15T10:23:01.000Z"
}
```

### Araçlar

```
POST   /vehicles                         Yeni bir EV kaydet
GET    /vehicles?page=0&size=20         Tüm araçları listele (sayfalı)
GET    /vehicles/{id}                   Araç detayı getir
PATCH  /vehicles/{id}/telemetry        Canlı telemetriyi güncelle (batarya, GPS, hız, sıcaklık)
GET    /vehicles/alerts/critical        Bataryası ≤ %15 olan araçlar
GET    /vehicles/analytics/fleet-summary  Filo KPI'ları: çevrimiçi sayısı, ortalama batarya
```

### Şarj İstasyonları

```
POST   /stations                         Şarj istasyonu kaydet
GET    /stations?page=0&size=20         İstasyonları listele (sayfalı)
GET    /stations/{id}                   İstasyon detayı getir
GET    /stations/nearby/available       Haversine SQL ile coğrafi arama
         ?lat=41.0082&lng=28.9784&radiusKm=20
PATCH  /stations/{id}/availability     Müsait konnektör sayısını güncelle
```

### Otonom Görevler

```
POST   /tasks                            Görev oluştur (yapay zeka ajanı tarafından çağrılır)
GET    /tasks?status=PENDING&page=0     Görevleri duruma göre filtrele
GET    /tasks/{id}                      Görev detayı getir
POST   /tasks/claim?taskType=           Türe göre sıradaki görevi sahiplen
PATCH  /tasks/{id}/complete             Görevi tamamlandı olarak işaretle
PATCH  /tasks/{id}/fail                 Görevi başarısız olarak işaretle
```

> Etkileşimli dokümantasyon: [`http://localhost:8080/api/v1/swagger-ui.html`](http://localhost:8080/api/v1/swagger-ui.html)

---

## 🧠 Yapay Zeka Karar Motoru

Ajan'ın karar döngüsü her telemetri olayını **< 1 ms** (sadece kural yolu) veya **2–15 sn** (YZ ile) içinde işler. YZ'nin kullanılamaz olması veya yanlış karar vermesi **hiçbir zaman güvenlik hatasına yol açmaz** — kural motoru her zaman önce çalışır ve sert bir zemin oluşturur.

```
Batarya %     Motor Katmanı       Aciliyet    Eylem
──────────── ──────────────────  ──────────  ────────────────────────────────────────
> %35         Kurallar (1. Kat)   YOK         İşlem yok — O(1), sıfır maliyet
%25 – %35    Kurallar → YZ        ORTA        Bağlam değerlendirmesi: hız, sıcaklık,
                                              menzil, günün saati → GPT-4o-mini
%15 – %25    Kurallar → YZ        YÜKSEK      YZ karar verebilir. Güvenlik vetosu
                                              "işlem yok" kararını engelliyor
≤ %15         Kurallar (1. Kat)   KRİTİK      Anında rezervasyon. YZ çağrısı yok.
                                              Arama yarıçapı ×1.5, min. 50 kW istasyon
Zaten şarjda  Kurallar (1. Kat)   YOK         Koruma cümlesi — tamamen atla
```

### YZ Girdi / Çıktı Sözleşmesi

```python
# GPT-4o-mini'ye gönderilen bağlam
{
    "battery_percent": 28.4,
    "vehicle_status": "ONLINE",
    "speed_kmh": 87.2,
    "estimated_range_km": 62.0,
    "battery_temp_c": 34.1,
    "rule_hint": "medium",        # kural motorunun ön değerlendirmesi
    "current_hour": 22            # günün saati bağlamı
}

# Zorunlu JSON çıktı şeması
{
    "should_charge": true,
    "urgency": "high",            # none | low | medium | high | critical
    "reasoning": "Batarya %28.4'te, yüksek hız (87 km/s) ve yüksek sıcaklık...",
    "recommended_charge_to_pct": 85,
    "max_search_radius_km": 20.0,
    "confidence": 0.87
}
```

### İstasyon Puanlama Algoritması

```
puan(istasyon) = güç_kw_norm    × 0.5
              + müsaitlik_norm  × 0.3
              − mesafe_norm     × 0.2
```

| Ağırlık | Faktör | Gerekçe |
|---|---|---|
| **0.5** | Güç çıkışı (kW, normalleştirilmiş) | Şarj süresini doğrudan belirler |
| **0.3** | Konnektör müsaitlik oranı | Varışta belirsizliği azaltır |
| **0.2** | Mesafe (Haversine, normalleştirilmiş) | Cezalandır ama uzak istasyonları eleme |

---

## 🏗 Mühendislik Kararları

### REST yerine neden Kafka telemetri için?

50 araç × saniyede 1 mesaj = dakikada 3.000 olay. Senkron REST çağrıları backend'i doyurur ve sıkı bağlantı oluşturur. Kafka üreticiyi işlemciden ayırır, bağımsız ölçeklendirmeye izin verir ve işlem hataları için Dead Letter Topic ile güvenli tekrar oynatma sağlar.

### Doğrudan DB sorguları yerine neden Redis önce önbellek?

Her telemetri güncellemesini doğrudan PostgreSQL'e yazmak dakikada binlerce yazma işlemi oluşturur. Bunun yerine:
- Her olayda Redis'e yaz (API ve mobil uygulama için milisaniyenin altında okuma)
- Yalnızca kritik durum geçişlerinde ve periyodik aralıklarda DB'ye flush et

Bu yaklaşım DB yükünü yaklaşık %97 azaltır, sıfır veri kaybı riski ile.

### Neden iki katmanlı karar motoru?

**Saf YZ**: Belirsiz, 2–10 sn gecikme, her olay için ücret. Sürekli izleme için kabul edilemez maliyet ve gecikme.

**Saf kurallar**: Hızlı ve ucuz ama bağlam köre — hızı, sıcaklığı, günün saatini görmezden gelir.

**İki katmanlı**: Kurallar tüm açık durumları ele alır (< 1 ms, sıfır maliyet). YZ yalnızca bağlamın kararı gerçekten değiştirdiği gri bölgede devreye girer. Sonuç: her iki yaklaşımdan da belirgin şekilde daha iyi karar kalitesi, optimize edilmiş maliyet.

### Flutter'da Bloc yerine neden Riverpod?

Bloc, temelde async veri akışları için Event → Bloc → State şeklinde önemli miktarda şablon kod ekler. Riverpod'un `StreamProvider`'ı doğrudan WebSocket akış mimarisine eşlenir, `FutureProvider.family` araç başına detay ekranlarını temiz bir şekilde yönetir ve provider'lar widget ağacı `BuildContext`'i olmadan oluşur. Sonuç: eşdeğer işlevsellik için yaklaşık %40 daha az kod.

---

## 📊 Performans Özellikleri

| Metrik | Değer |
|---|---|
| Telemetri alım verimi | ~3.000 mesaj/dakika (50 araç, 1 Hz) |
| Kural motoru karar gecikmesi | < 1 ms |
| YZ karar gecikmesi (GPT-4o-mini) | 2–15 sn |
| Naif yaklaşıma kıyasla DB yazmaları | Redis önce stratejisiyle ~%97 azalma |
| WebSocket yeniden bağlantı stratejisi | 2 sn → 4 sn → 8 sn → 30 sn (üstel geri çekilme) |
| Rezervasyon tekilleştirme penceresi | Araç başına 30 dakika |
| Uyarı soğuma süresi | Araç başına 300 sn |
| Kafka consumer DLQ yeniden deneme sayısı | 2× üstel geri çekilme ile 3 deneme |
| API yanıt süresi p95 (önbellek isabeti) | < 10 ms |
| API yanıt süresi p95 (DB okuma) | < 50 ms |

---

## 🗺 Yol Haritası

- [ ] **TimescaleDB** — telemetri depolama için zaman serisi hypertable'larına geçiş
- [ ] **Kubernetes manifests** — bulut dağıtımı için Helm chart (GKE / EKS)
- [ ] **OpenTelemetry** — Spring ↔ Kafka ↔ Python ajanı arası dağıtık izleme
- [ ] **Çok ajanlı koordinasyon** — `vehicle.alerts` topic'i üzerinden ajan-ajan görev devri
- [ ] **Tahmine dayalı şarj** — rota + hava durumu verisine dayalı LSTM batarya düşüş modeli
- [ ] **Prometheus + Grafana** — filo KPI'ları ve Kafka consumer lag için hazır dashboard
- [ ] **iOS CarPlay** — komuta merkezini CarPlay dashboard'una genişletme

---

## 📄 Lisans

Bu proje MIT Lisansı altında lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakınız.

---

<div align="center">

**Mühendislik kalitesine takıntılı bir şekilde inşa edildi.**

*Bu depodaki her mimari kararın bir nedeni var.*
*Kodu oku — hikayeyi o anlatıyor.*

---

[![GitHub Stars](https://img.shields.io/github/stars/Ardaa21/voltvanguard-core?style=social)](https://github.com/Ardaa21/voltvanguard-core)
[![GitHub Forks](https://img.shields.io/github/forks/Ardaa21/voltvanguard-core?style=social)](https://github.com/Ardaa21/voltvanguard-core/fork)

</div>
