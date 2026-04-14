package io.tonyblack10.aiplayground.config.security;

import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive authentication manager that validates credentials and resolves
 * granted authorities.
 *
 * <p>The {@link #authenticateUser(String, String)} method is the designated
 * extension point: replace its body to plug in any authentication source
 * (database, LDAP, external identity provider, etc.) without touching the
 * surrounding security configuration.
 */
@Component
public class AppAuthenticationManager implements ReactiveAuthenticationManager {

    private final UserSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AppAuthenticationManager(UserSecurityProperties properties,
                                    PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String username = authentication.getName();
        String rawPassword = authentication.getCredentials().toString();
        return authenticateUser(username, rawPassword);
    }

    // -------------------------------------------------------------------------
    // Extension point — replace this method with the desired authentication source
    // -------------------------------------------------------------------------

    /**
     * Authenticates a user by username and raw password.
     *
     * <p><b>TODO:</b> Replace the body of this method with the desired
     * authentication logic (e.g. database lookup, LDAP bind, external IdP call).
     * Authority resolution via {@link UserSecurityProperties#rolesForUser(String)}
     * must remain, or be replaced with an equivalent that honours the
     * {@code app.user.permissions} configuration.
     *
     * @param username    the username supplied by the login form
     * @param rawPassword the plain-text password supplied by the login form
     * @return a {@link Mono} emitting a fully authenticated {@link Authentication}
     *         token, or {@link Mono#error} with {@link BadCredentialsException}
     *         on failure
     */
    private Mono<Authentication> authenticateUser(String username, String rawPassword) {
        String encodedPassword = properties.getAccounts().get(username);
        if (encodedPassword == null || !passwordEncoder.matches(rawPassword, encodedPassword)) {
            return Mono.error(new BadCredentialsException("Credenciais inválidas"));
        }

        List<GrantedAuthority> authorities = properties.rolesForUser(username).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        return Mono.just(new UsernamePasswordAuthenticationToken(username, null, authorities));
    }
}
