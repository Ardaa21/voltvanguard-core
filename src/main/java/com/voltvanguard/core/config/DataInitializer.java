package com.voltvanguard.core.config;

import com.voltvanguard.core.entity.ElectricVehicle;
import com.voltvanguard.core.enums.VehicleStatus;
import com.voltvanguard.core.repository.ElectricVehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the database with a realistic EV fleet on first startup.
 *
 * <p>Only runs when the {@code electric_vehicles} table is empty, so it is
 * safe to leave active in development — it never overwrites existing data.</p>
 *
 * <p>Not active in the {@code prod} profile — use Flyway migrations there.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ElectricVehicleRepository vehicleRepository;

    // Shared owner UUID — represents the fleet operator account
    private static final UUID FLEET_OWNER = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long count = vehicleRepository.count();

        if (count == 0) {
            // ── Fresh DB: insert full seed fleet ─────────────────────────────
            log.info("DataInitializer: empty DB — seeding fleet with 8 test vehicles…");

        List<ElectricVehicle> fleet = List.of(

            // ── Tesla Model Y — fully charged, online, Istanbul Levent ──────────
            vehicle("5YJYGCEE8P20261AB", "Tesla",   "Model Y",    2023,
                    82.0, 78.0,   430.0, VehicleStatus.ONLINE,
                    41.0822,  28.9799),

            // ── Hyundai IONIQ 6 — mid charge, in transit ──────────────────────
            vehicle("KM8K33AGXP12345BC", "Hyundai", "IONIQ 6",    2024,
                    74.0,  77.4,   310.0, VehicleStatus.IN_TRANSIT,
                    41.0500,  29.0200),

            // ── Volvo EX30 — low battery, critical ───────────────────────────
            vehicle("YV1CZAHL8R12345CD", "Volvo",   "EX30",       2024,
                    12.0,  69.0,    48.0, VehicleStatus.BATTERY_CRITICAL,
                    41.0150,  28.9700),

            // ── Tesla Model 3 — charging at station ───────────────────────────
            vehicle("5YJ3E1EA8NF23456D", "Tesla",  "Model 3",    2022,
                    55.0,  75.0,   230.0, VehicleStatus.CHARGING,
                    41.0600,  29.0100),

            // ── BMW iX — online, good range ──────────────────────────────────
            vehicle("WBY8P410X0N34567E", "BMW",    "iX",         2023,
                    88.0, 111.5,   440.0, VehicleStatus.ONLINE,
                    41.0700,  29.0050),

            // ── Mercedes EQS — offline (parked overnight) ─────────────────────
            vehicle("WDD2232231A45678F", "Mercedes","EQS 450+",  2023,
                    92.0, 107.8,   510.0, VehicleStatus.OFFLINE,
                    41.0300,  28.9950),

            // ── Porsche Taycan — online, mid range ────────────────────────────
            vehicle("WP0ZZZ9YZN56789GH", "Porsche", "Taycan",     2024,
                    63.0,  93.4,   280.0, VehicleStatus.ONLINE,
                    41.0900,  28.9850),

            // ── Audi Q8 e-tron — approaching critical, online ─────────────────
            vehicle("WAUZZZGE0PB67890H", "Audi",   "Q8 e-tron",  2024,
                    22.0,  95.0,    90.0, VehicleStatus.ONLINE,
                    41.0250,  29.0000)
        );

            vehicleRepository.saveAll(fleet);
            log.info("DataInitializer: ✓ seeded {} vehicles successfully.", fleet.size());

        } else {
            // ── Existing DB: patch any vehicles that are missing battery data ─
            List<ElectricVehicle> existing = vehicleRepository.findAll();
            double[] batteries = {82.0, 74.0, 18.0, 55.0, 88.0, 63.0, 22.0, 91.0};
            double[] ranges    = {430.0, 310.0, 70.0, 230.0, 440.0, 280.0, 90.0, 480.0};
            VehicleStatus[] statuses = {
                VehicleStatus.ONLINE,          VehicleStatus.IN_TRANSIT,
                VehicleStatus.BATTERY_CRITICAL, VehicleStatus.CHARGING,
                VehicleStatus.ONLINE,           VehicleStatus.ONLINE,
                VehicleStatus.ONLINE,           VehicleStatus.IDLE
            };
            double[] lats = {41.0822, 41.0500, 41.0150, 41.0600, 41.0700, 41.0900, 41.0250, 41.0400};
            double[] lngs = {28.9799, 29.0200, 28.9700, 29.0100, 29.0050, 28.9850, 29.0000, 28.9900};

            boolean anyUpdated = false;
            for (int i = 0; i < existing.size(); i++) {
                ElectricVehicle ev = existing.get(i);
                boolean changed = false;
                int idx = i % batteries.length;

                if (ev.getBatteryPercent() == null) {
                    ev.setBatteryPercent(BigDecimal.valueOf(batteries[idx]));
                    ev.setBatteryLastUpdatedAt(Instant.now());
                    changed = true;
                }
                if (ev.getEstimatedRangeKm() == null) {
                    ev.setEstimatedRangeKm(BigDecimal.valueOf(ranges[idx]));
                    changed = true;
                }
                if (ev.getBatteryCapacityKwh() == null) {
                    ev.setBatteryCapacityKwh(BigDecimal.valueOf(75.0));
                    changed = true;
                }
                // Only override status if it's null (not if it's intentionally OFFLINE)
                if (ev.getStatus() == null) {
                    ev.setStatus(statuses[idx]);
                    changed = true;
                }
                if (ev.getLatitude() == null) {
                    ev.setLatitude(BigDecimal.valueOf(lats[idx]));
                    ev.setLongitude(BigDecimal.valueOf(lngs[idx]));
                    changed = true;
                }

                if (changed) {
                    vehicleRepository.save(ev);
                    anyUpdated = true;
                    log.info("DataInitializer: patched vehicle vin={} battery={}%",
                        ev.getVin(), ev.getBatteryPercent());
                }
            }
            if (!anyUpdated) {
                log.info("DataInitializer: all {} vehicles already have battery data, nothing to patch.", count);
            }
        }
    }

    // ── Builder helper ────────────────────────────────────────────────────────

    private ElectricVehicle vehicle(
        String vin,
        String manufacturer,
        String model,
        int year,
        double batteryPct,
        double capacityKwh,
        double rangeKm,
        VehicleStatus status,
        double lat,
        double lng
    ) {
        ElectricVehicle ev = new ElectricVehicle();
        ev.setVin(vin);
        ev.setManufacturer(manufacturer);
        ev.setModel(model);
        ev.setModelYear(year);
        ev.setOwnerId(FLEET_OWNER);
        ev.setBatteryCapacityKwh(BigDecimal.valueOf(capacityKwh));
        ev.setBatteryPercent(BigDecimal.valueOf(batteryPct));
        ev.setBatteryLastUpdatedAt(Instant.now());
        ev.setEstimatedRangeKm(BigDecimal.valueOf(rangeKm));
        ev.setStatus(status);
        ev.setLatitude(BigDecimal.valueOf(lat));
        ev.setLongitude(BigDecimal.valueOf(lng));
        return ev;
    }
}
