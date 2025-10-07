package io.smartip.interventions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smartip.domain.InterventionAssignmentMode;
import io.smartip.domain.InterventionEntity;
import io.smartip.domain.InterventionStatus;
import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRole;
import io.smartip.interventions.dto.InterventionResponse;
import io.smartip.interventions.dto.UpdateInterventionStatusRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class InterventionControllerSecurityTest {

    @Mock
    private InterventionService interventionService;

    @InjectMocks
    private InterventionController controller;

    @Test
    void technicianCannotValidateIntervention() {
        when(interventionService.getIntervention(42L))
                .thenReturn(intervention(42L, InterventionStatus.COMPLETED, technician(7L, "tech@example.com")));

        Authentication authentication = authenticatedUser("tech@example.com", "ROLE_TECH");

        assertThatThrownBy(() -> controller.updateStatus(
                        42L,
                        new UpdateInterventionStatusRequest(InterventionStatus.VALIDATED),
                        authentication))
                .isInstanceOf(InterventionAccessDeniedException.class);

        verify(interventionService, never()).updateStatus(anyLong(), any());
    }

    @Test
    void technicianCanAdvanceToInProgressWhenAssigned() {
        when(interventionService.getIntervention(10L))
                .thenReturn(intervention(10L, InterventionStatus.SCHEDULED, technician(3L, "tech@example.com")));
        when(interventionService.updateStatus(10L, InterventionStatus.IN_PROGRESS))
                .thenReturn(intervention(10L, InterventionStatus.IN_PROGRESS, technician(3L, "tech@example.com")));

        Authentication authentication = authenticatedUser("tech@example.com", "ROLE_TECH");

        InterventionResponse response = controller.updateStatus(
                10L,
                new UpdateInterventionStatusRequest(InterventionStatus.IN_PROGRESS),
                authentication);

        assertThat(response.status()).isEqualTo(InterventionStatus.IN_PROGRESS);
        verify(interventionService).updateStatus(10L, InterventionStatus.IN_PROGRESS);
    }

    @Test
    void dispatcherCanValidateIntervention() {
        when(interventionService.updateStatus(9L, InterventionStatus.VALIDATED))
                .thenReturn(intervention(9L, InterventionStatus.VALIDATED, technician(2L, "tech@example.com")));

        Authentication authentication = authenticatedUser("dispatch@example.com", "ROLE_DISPATCHER");

        InterventionResponse response = controller.updateStatus(
                9L,
                new UpdateInterventionStatusRequest(InterventionStatus.VALIDATED),
                authentication);

        assertThat(response.status()).isEqualTo(InterventionStatus.VALIDATED);
        verify(interventionService).updateStatus(9L, InterventionStatus.VALIDATED);
        verify(interventionService, never()).getIntervention(9L);
    }

    @Test
    void technicianCannotProgressUnassignedIntervention() {
        when(interventionService.getIntervention(55L))
                .thenReturn(intervention(55L, InterventionStatus.SCHEDULED, null));

        Authentication authentication = authenticatedUser("tech@example.com", "ROLE_TECH");

        assertThatThrownBy(() -> controller.updateStatus(
                        55L,
                        new UpdateInterventionStatusRequest(InterventionStatus.IN_PROGRESS),
                        authentication))
                .isInstanceOf(InterventionAccessDeniedException.class);

        verify(interventionService, never()).updateStatus(anyLong(), any());
    }

    private Authentication authenticatedUser(String username, String... authorities) {
        TestingAuthenticationToken token = new TestingAuthenticationToken(username, "password", authorities);
        token.setAuthenticated(true);
        return token;
    }

    private InterventionEntity intervention(Long id, InterventionStatus status, UserEntity technician) {
        InterventionEntity entity = new InterventionEntity();
        entity.setId(id);
        entity.setReference("INT-" + id);
        entity.setTitle("Intervention " + id);
        entity.setDescription("Test");
        entity.setStatus(status);
        entity.setAssignmentMode(InterventionAssignmentMode.MANUAL);
        entity.setPlannedAt(Instant.parse("2025-01-01T08:00:00Z"));
        entity.setStartedAt(null);
        entity.setCompletedAt(null);
        entity.setValidatedAt(null);
        entity.setCreatedAt(Instant.parse("2024-12-31T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2024-12-31T10:00:00Z"));
        entity.setTechnician(technician);
        return entity;
    }

    private UserEntity technician(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("Tech " + id);
        user.setRole(UserRole.TECH);
        user.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        user.setPasswordHash("hash");
        return user;
    }
}
