package io.smartip.security;

import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import io.smartip.users.UserService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final UserService userService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenService jwtTokenService,
            JwtProperties jwtProperties,
            UserRepository userRepository,
            UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        UserEntity user = userRepository
                .findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow();
        String token = jwtTokenService.generateToken(user);
        ResponseCookie cookie = buildCookie(token, jwtProperties.expiration());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new LoginResponse(token, user.getEmail(), user.getRole()));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        userService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
        ResponseCookie deleteCookie = buildCookie("", Duration.ZERO);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, deleteCookie.toString()).build();
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserRole role = resolveRole(authentication);
        return ResponseEntity.ok(new SessionResponse(authentication.getName(), role));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie deleteCookie = buildCookie("", Duration.ZERO);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, deleteCookie.toString()).build();
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(jwtProperties.cookieName(), value)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax");
        if (jwtProperties.cookieSecure()) {
            builder.secure(true);
        }
        if (maxAge != null) {
            builder.maxAge(maxAge);
        } else {
            builder.maxAge(Duration.ZERO);
        }
        return builder.build();
    }

    private UserRole resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5))
                .map(role -> role.toUpperCase(Locale.ROOT))
                .map(UserRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role for user"));
    }
}
