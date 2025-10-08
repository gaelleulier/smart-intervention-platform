package io.smartip.dashboard;

import io.smartip.dashboard.dto.DashboardSummaryResponse;
import io.smartip.dashboard.dto.InterventionMapMarker;
import io.smartip.dashboard.dto.StatusTrendPoint;
import io.smartip.dashboard.dto.TechnicianLoadResponse;
import io.smartip.domain.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AnalyticsAggregationService aggregationService;

    public DashboardController(DashboardService dashboardService, AnalyticsAggregationService aggregationService) {
        this.dashboardService = dashboardService;
        this.aggregationService = aggregationService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','TECH')")
    public DashboardSummaryResponse getSummary(
            Authentication authentication,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UserRole role = resolveRole(authentication);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        return dashboardService.getSummary(targetDate, authentication.getName(), role);
    }

    @GetMapping("/status-trends")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','TECH')")
    public List<StatusTrendPoint> getStatusTrends(
            Authentication authentication,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UserRole role = resolveRole(authentication);
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(13);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Parameter 'from' must be before 'to'");
        }
        return dashboardService.getStatusTrends(start, end, authentication.getName(), role);
    }

    @GetMapping("/technician-load")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','TECH')")
    public List<TechnicianLoadResponse> getTechnicianLoad(Authentication authentication) {
        UserRole role = resolveRole(authentication);
        String email = authentication.getName();
        return dashboardService.getTechnicianLoad(email, role);
    }

    @GetMapping("/map")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','TECH')")
    public List<InterventionMapMarker> getMap(
            Authentication authentication,
            @RequestParam(value = "status", required = false) List<String> statuses,
            @RequestParam(value = "limit", required = false) @Positive @Max(1000) Integer limit) {
        UserRole role = resolveRole(authentication);
        boolean precise = role == UserRole.ADMIN;
        int desiredLimit = limit != null ? limit : 0;
        return dashboardService.getMapMarkers(statuses, precise, desiredLimit, authentication.getName(), role);
    }

    private UserRole resolveRole(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .map(UserRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role for user"));
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    public void refreshAnalytics() {
        aggregationService.refreshAnalytics();
    }
}
