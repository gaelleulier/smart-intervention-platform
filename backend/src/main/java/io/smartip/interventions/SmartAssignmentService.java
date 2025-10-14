package io.smartip.interventions;

import io.smartip.dashboard.DashboardService;
import io.smartip.dashboard.TechnicianLoadSnapshot;
import io.smartip.interventions.dto.SmartAssignmentCandidate;
import io.smartip.interventions.dto.SmartAssignmentRequest;
import io.smartip.interventions.dto.SmartAssignmentResponse;
import io.smartip.domain.InterventionEntity;
import io.smartip.domain.InterventionRepository;
import io.smartip.domain.InterventionStatus;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmartAssignmentService {

    private static final double DEFAULT_DISTANCE_SCORE = 0.5;
    private static final double DEFAULT_SKILL_SCORE = 0.5;

    private final UserRepository userRepository;
    private final InterventionRepository interventionRepository;
    private final DashboardService dashboardService;

    public SmartAssignmentService(
            UserRepository userRepository,
            InterventionRepository interventionRepository,
            DashboardService dashboardService) {
        this.userRepository = userRepository;
        this.interventionRepository = interventionRepository;
        this.dashboardService = dashboardService;
    }

    @Transactional(readOnly = true)
    public SmartAssignmentResponse recommendTechnician(SmartAssignmentRequest request) {
        List<UserEntity> technicians = userRepository.findByRoleOrderByIdAsc(UserRole.TECH);
        if (technicians.isEmpty()) {
            throw new IllegalStateException("No technicians available for assignment");
        }

        Map<Long, TechnicianLoadSnapshot> loadByTechnician = dashboardService.getAllTechnicianLoadSnapshots().stream()
                .collect(Collectors.toMap(TechnicianLoadSnapshot::technicianId, snapshot -> snapshot));

        List<String> tokens = extractTokens(request.title(), request.description());
        boolean hasLocation = request.latitude() != null && request.longitude() != null;
        boolean hasTokens = !tokens.isEmpty();

        List<CandidateMetrics> metrics = new ArrayList<>();
        for (UserEntity technician : technicians) {
            long technicianId = technician.getId();
            TechnicianLoadSnapshot loadSnapshot = loadByTechnician.get(technicianId);
            long openAssignments = loadSnapshot != null ? loadSnapshot.openCount() : 0;

            Double distanceKm = hasLocation
                    ? computeDistanceToTechnician(technicianId, request.latitude(), request.longitude())
                    : null;

            long skillMatches = hasTokens ? countSkillMatches(technicianId, tokens) : 0;

            metrics.add(new CandidateMetrics(technician, openAssignments, distanceKm, skillMatches));
        }

        double maxOpen = metrics.stream().mapToLong(CandidateMetrics::openAssignments).max().orElse(0);
        double maxSkill = metrics.stream().mapToLong(CandidateMetrics::skillMatches).max().orElse(0);
        double maxDistance = metrics.stream()
                .map(CandidateMetrics::distanceKm)
                .filter(distance -> distance != null && distance > 0)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);

        double distanceWeight = hasLocation ? 0.4 : 0.25;
        double skillWeight = hasTokens ? 0.25 : 0.2;
        double workloadWeight = 1.0 - distanceWeight - skillWeight;

        List<SmartAssignmentCandidate> candidates = new ArrayList<>();
        for (CandidateMetrics metric : metrics) {
            double workloadRatio = maxOpen <= 0 ? 0.0 : (double) metric.openAssignments() / maxOpen;
            double workloadScore = maxOpen <= 0 ? 1.0 : 1.0 - Math.min(1.0, workloadRatio);
            workloadScore = clamp(workloadScore, 0, 1);

            double distanceScore;
            if (!hasLocation || metric.distanceKm() == null) {
                distanceScore = DEFAULT_DISTANCE_SCORE;
            } else if (maxDistance <= 0) {
                distanceScore = 0.6;
            } else {
                distanceScore = 1.0 - Math.min(1.0, metric.distanceKm() / maxDistance);
            }
            distanceScore = clamp(distanceScore, 0, 1);

            double skillScore;
            if (!hasTokens) {
                skillScore = DEFAULT_SKILL_SCORE;
            } else if (maxSkill <= 0) {
                skillScore = 0.5;
            } else {
                skillScore = Math.min(1.0, (double) metric.skillMatches() / maxSkill);
            }
            skillScore = clamp(skillScore, 0, 1);

            double overallScore = (distanceScore * distanceWeight)
                    + (workloadScore * workloadWeight)
                    + (skillScore * skillWeight);

            candidates.add(new SmartAssignmentCandidate(
                    metric.technician().getId(),
                    metric.technician().getFullName(),
                    metric.technician().getEmail(),
                    round(overallScore, 3),
                    round(workloadScore, 3),
                    round(distanceScore, 3),
                    round(skillScore, 3),
                    metric.distanceKm(),
                    metric.openAssignments(),
                    metric.skillMatches()));
        }

        candidates.sort(Comparator.comparingDouble(SmartAssignmentCandidate::overallScore).reversed());
        SmartAssignmentCandidate recommended = candidates.get(0);
        List<SmartAssignmentCandidate> alternatives = candidates.stream()
                .skip(1)
                .limit(3)
                .collect(Collectors.toList());

        String rationale = buildRationale(recommended, hasLocation, hasTokens);
        return new SmartAssignmentResponse(recommended, alternatives, rationale, Instant.now());
    }

    private double computeDistanceToTechnician(Long technicianId, Double latitude, Double longitude) {
        return interventionRepository
                .findFirstByTechnician_IdAndLatitudeIsNotNullAndLongitudeIsNotNullOrderByUpdatedAtDesc(technicianId)
                .map(entity -> haversine(
                        latitude,
                        longitude,
                        entity.getLatitude().doubleValue(),
                        entity.getLongitude().doubleValue()))
                .orElse(null);
    }

    private long countSkillMatches(Long technicianId, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0;
        }
        Set<String> keywordSet = keywords.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Collection<InterventionStatus> statuses = EnumSet.of(InterventionStatus.COMPLETED, InterventionStatus.VALIDATED);
        List<InterventionEntity> history =
                interventionRepository.findTop20ByTechnician_IdAndStatusInOrderByUpdatedAtDesc(technicianId, statuses);
        long matches = 0;
        for (InterventionEntity intervention : history) {
            String content = (intervention.getTitle() + " " + Optional.ofNullable(intervention.getDescription()).orElse(""))
                    .toLowerCase(Locale.ROOT);
            for (String token : keywordSet) {
                if (content.contains(token)) {
                    matches++;
                    break;
                }
            }
        }
        return matches;
    }

    private List<String> extractTokens(String title, String description) {
        String combined = ((title != null ? title : "") + " " + (description != null ? description : ""))
                .toLowerCase(Locale.ROOT);
        String[] rawTokens = combined.split("[^a-z0-9]+");
        Set<String> blacklist = Set.of("les", "des", "une", "pour", "avec", "dans", "chez", "and", "the", "aux", "sur", "par");
        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            if (token.length() < 3) {
                continue;
            }
            if (blacklist.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return round(earthRadiusKm * c, 2);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    private String buildRationale(SmartAssignmentCandidate candidate, boolean hasLocation, boolean hasTokens) {
        StringBuilder builder = new StringBuilder("Technicien recommandé: ").append(candidate.fullName()).append(". ");
        builder.append(String.format(Locale.FRENCH, "Score global %.1f%%. ", candidate.overallScore() * 100));
        builder.append(String.format(
                Locale.FRENCH, "Charge actuelle: %d intervention(s) ouverte(s). ", candidate.openAssignments()));
        if (hasLocation && candidate.distanceKm() != null) {
            builder.append(String.format(Locale.FRENCH, "Distance estimée: %.1f km. ", candidate.distanceKm()));
        }
        if (hasTokens) {
            builder.append(String.format(
                    Locale.FRENCH, "Historique similaire: %d intervention(s) correspondante(s). ", candidate.matchingHistory()));
        }
        return builder.toString().trim();
    }

    private record CandidateMetrics(
            UserEntity technician, long openAssignments, Double distanceKm, long skillMatches) {}
}
