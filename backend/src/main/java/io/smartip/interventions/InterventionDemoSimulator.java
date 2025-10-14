package io.smartip.interventions;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.domain.InterventionEntity;
import io.smartip.domain.InterventionRepository;
import io.smartip.domain.InterventionStatus;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InterventionDemoSimulator {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterventionDemoSimulator.class);

    private static final double LAT_MIN = 43.3;
    private static final double LAT_MAX = 44.00;
    private static final double LON_MIN = 1.00;
    private static final double LON_MAX = 2.00;

    private static final String[] INTERVENTION_TYPES =
            {"Network inspection", "IoT maintenance", "Electrical repair", "HVAC calibration", "Safety audit"};
    private static final String[] PRIORITIES = {"Low", "Medium", "High"};
    private static final String[] AREAS = {
        "Capitole", "Blagnac", "Labege", "Purpan", "Cote Pavee", "Jolimont",
        "Colomiers", "Tournefeuille", "Balma", "L'Union", "Ramonville-Saint-Agne",
        "Saint-Orens-de-Gameville", "Muret", "Cugnaux", "Plaisance-du-Touch",
        "Fonsorbes", "Seysses", "Portet-sur-Garonne", "Roques", "Roquettes",
        "Pins-Justaret", "Pinsaguel", "Castelginest", "Aucamville", "Fenouillet",
        "Gratentour", "Saint-Jory", "Bruguières", "Launaguet", "Quint-Fonsegrives",
        "Escalquens", "Pechbusque", "Vieille-Toulouse", "Pechabou", "Aureville",
        "Eaunes", "Villeneuve-Tolosane", "Roquettes", "Cornebarrieu", "Pibrac",
        "Lespinasse", "Gagnac-sur-Garonne", "Beauzelle", "Daux", "Lévignac",
        "La Salvetat-Saint-Gilles", "Frouzins", "Lherm", "Le Fauga", "Vernet",
        "Auterive", "Cintegabelle", "Caraman", "Nailloux", "Villefranche-de-Lauragais",
        "Montgiscard", "Baziège", "Ayguesvives", "Donneville", "Avignonet-Lauragais",
        "Revel", "Sorèze", "Saint-Félix-Lauragais", "Castanet-Tolosan",
        "Pechbonnieu", "Saint-Alban", "Fonbeauzard", "Gragnague", "Garidech",
        "Montastruc-la-Conseillère", "Bessières", "Bazus", "Lapeyrouse-Fossat",
        "Verfeil", "Saint-Pierre-de-Lages", "Belberaud", "Belbèze-de-Lauragais",
        "Villate", "Lacroix-Falgarde", "Auzeville-Tolosane", "Pechbusque",
        "Vigoulet-Auzil", "Castanet", "Labarthe-sur-Lèze", "Eaunes", "Poucharramet",
        "Saint-Lys", "Fontenilles", "Lamasquère", "Bragayrac", "Bonrepos-sur-Aussonnelle",
        "Saint-Clar-de-Rivière", "Le Vernet", "Roqueserière", "Montberon",
        "Paulhac", "Buzet-sur-Tarn", "Villemur-sur-Tarn", "Fronton", "Castelnau-d'Estretefonds",
        "Bouloc", "Vacquiers", "La Magdelaine-sur-Tarn", "Bazus", "Saint-Rustice",
        "Grenade", "Ondes", "Merville", "Aussonne", "Seilh", "Cornebarrieu", "Pibrac"
    };


    private static final DateTimeFormatter REFERENCE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    static final String CREATE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS idx_interventions_created_at ON interventions(created_at DESC)";

    static final String PURGE_STALE_INTERVENTIONS_SQL =
            """
            WITH stale AS (
                SELECT id
                FROM interventions
                ORDER BY created_at DESC
                OFFSET ?
                LIMIT ?
            )
            DELETE FROM interventions
            WHERE id IN (SELECT id FROM stale)
            """;

    private final InterventionRepository interventionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final Clock clock;
    private final Random random;
    private final int maxRows;
    private final int batchSize;
    private final AtomicBoolean indexEnsured = new AtomicBoolean(false);

    @Autowired
    InterventionDemoSimulator(
            InterventionRepository interventionRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<Clock> clockProvider,
            @Value("${DEMO_MAX_ROWS:3000}") int maxRows,
            @Value("${DEMO_BATCH_DELETE:200}") int batchSize) {
        this(
                interventionRepository,
                userRepository,
                jdbcTemplate,
                clockProvider.getIfAvailable(Clock::systemUTC),
                new Random(),
                maxRows,
                batchSize);
    }

    InterventionDemoSimulator(
            InterventionRepository interventionRepository,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            Random random,
            int maxRows,
            int batchSize) {
        this.interventionRepository = Objects.requireNonNull(interventionRepository, "interventionRepository");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.random = random != null ? random : new Random();
        this.maxRows = Math.max(1, maxRows);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void runSimulation() {
        ensureCreatedAtIndex();

        int desiredInsertions = random.nextInt(3) + 1;
        Instant runTimestamp = Instant.now(clock);
        /* LOGGER.debug(
                "Demo simulator run triggered at {} (maxRows={}, batchSize={})",
                runTimestamp,
                maxRows,
                batchSize);
 */
        List<UserEntity> technicians = userRepository.findByRoleOrderByIdAsc(UserRole.TECH);
        List<Long> technicianIds = technicians.stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (technicianIds.isEmpty()) {
            LOGGER.warn("Demo simulator aborted run because no technicians with role TECH were found");
            return;
        }

        List<InterventionEntity> generated = new ArrayList<>(desiredInsertions);
        for (int i = 0; i < desiredInsertions; i++) {
            generated.add(buildSyntheticIntervention(runTimestamp, technicianIds));
        }

        List<InterventionEntity> saved = interventionRepository.saveAll(generated);

        long purged = purgeExcessRows();

 /*        LOGGER.debug(
                "Demo simulator inserted {} intervention(s) and purged {} outdated row(s)",
                saved.size(),
                purged); */
    }

    private void ensureCreatedAtIndex() {
        if (indexEnsured.compareAndSet(false, true)) {
            jdbcTemplate.execute(CREATE_INDEX_SQL);
        }
    }

    private InterventionEntity buildSyntheticIntervention(Instant referenceNow, List<Long> technicianIds) {
        Instant lowerBound = referenceNow.minusSeconds(30 * 60);
        Instant plannedAt = randomInstant(lowerBound, referenceNow);
        Instant createdAt = randomInstant(lowerBound, plannedAt);
        Instant updatedAt = randomInstant(createdAt, referenceNow);

        String type = pickRandom(INTERVENTION_TYPES);
        String priority = pickRandom(PRIORITIES);
        String area = pickRandom(AREAS);

        InterventionStatus status = pickRandomStatus();
        InterventionEntity entity = new InterventionEntity();
        entity.setReference(generateReference(createdAt));
        entity.setTitle(priority + " " + type + " - " + area);
        entity.setDescription(
                "Demo " + type + " scheduled in " + area + " with " + priority.toLowerCase() + " priority.");
        entity.setStatus(status);
        entity.setAssignmentMode(InterventionAssignmentMode.MANUAL);
        entity.setPlannedAt(plannedAt);
        applyStatusTimestamps(entity, status, plannedAt, updatedAt);
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);
        entity.setLatitude(randomCoordinate(LAT_MIN, LAT_MAX));
        entity.setLongitude(randomCoordinate(LON_MIN, LON_MAX));
        entity.setTechnician(selectTechnician(technicianIds));
        return entity;
    }

    private void applyStatusTimestamps(
            InterventionEntity entity, InterventionStatus status, Instant plannedAt, Instant upperBound) {
        if (status == InterventionStatus.SCHEDULED) {
            entity.setStartedAt(null);
            entity.setCompletedAt(null);
            entity.setValidatedAt(null);
            return;
        }

        Instant startedAt = randomInstant(plannedAt, upperBound);
        entity.setStartedAt(startedAt);

        if (status == InterventionStatus.IN_PROGRESS) {
            entity.setCompletedAt(null);
            entity.setValidatedAt(null);
            return;
        }

        Instant completedAt = randomInstant(startedAt, upperBound);
        entity.setCompletedAt(completedAt);

        if (status == InterventionStatus.COMPLETED) {
            entity.setValidatedAt(null);
            return;
        }

        Instant validatedAt = randomInstant(completedAt, upperBound);
        entity.setValidatedAt(validatedAt);
    }

    private BigDecimal randomCoordinate(double min, double max) {
        double value = random.nextDouble(min, max);
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private String generateReference(Instant createdAt) {
        String timestamp = REFERENCE_FORMATTER.format(createdAt);
        int suffix = random.nextInt(900) + 100;
        return "DEMO-" + timestamp + "-" + suffix;
    }

    private InterventionStatus pickRandomStatus() {
        InterventionStatus[] statuses = InterventionStatus.values();
        return statuses[random.nextInt(statuses.length)];
    }

    private String pickRandom(String[] candidates) {
        return candidates[random.nextInt(candidates.length)];
    }

    private Instant randomInstant(Instant startInclusive, Instant endInclusive) {
        long startMillis = startInclusive.toEpochMilli();
        long endMillis = endInclusive.toEpochMilli();
        if (startMillis >= endMillis) {
            return Instant.ofEpochMilli(startMillis);
        }
        long boundExclusive = endMillis == Long.MAX_VALUE ? endMillis : endMillis + 1;
        if (boundExclusive <= startMillis) {
            return Instant.ofEpochMilli(startMillis);
        }
        long randomMillis = random.nextLong(startMillis, boundExclusive);
        return Instant.ofEpochMilli(randomMillis);
    }

    private long purgeExcessRows() {
        long totalDeleted = 0;
        while (true) {
            int deleted = jdbcTemplate.update(PURGE_STALE_INTERVENTIONS_SQL, maxRows, batchSize);
            totalDeleted += deleted;
            if (deleted > 0) {
                /* LOGGER.debug("Demo simulator purge pass deleted {} row(s) beyond row cap {}", deleted, maxRows); */
            }
            if (deleted < batchSize) {
                return totalDeleted;
            }
        }
    }

    private UserEntity selectTechnician(List<Long> technicianIds) {
        if (technicianIds.isEmpty()) {
            return null;
        }
        int index = random.nextInt(technicianIds.size());
        Long technicianId = technicianIds.get(index);
        return userRepository.getReferenceById(technicianId);
    }
}
