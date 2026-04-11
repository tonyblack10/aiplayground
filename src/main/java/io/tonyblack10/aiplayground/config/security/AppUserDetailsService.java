package io.tonyblack10.aiplayground.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Reactive user-details service that resolves credentials and roles from
 * application properties.
 *
 * <p>The {@link #loadUser(String)} method is the designated extension point:
 * replace its body to plug in any authentication source (database, LDAP,
 * external identity provider, etc.) without changing the surrounding
 * security configuration.
 */
@Service
public class AppUserDetailsService implements ReactiveUserDetailsService {

    private final UserSecurityProperties properties;

    public AppUserDetailsService(UserSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return loadUser(username);
    }

    // -------------------------------------------------------------------------
    // Extension point — replace this method with the desired authentication source
    // -------------------------------------------------------------------------

    /**
     * Loads a user by username.
     *
     * <p><b>TODO:</b> Replace the body of this method with the desired
     * authentication logic (e.g. database lookup, LDAP bind, external IdP call).
     * Role resolution via {@link UserSecurityProperties#rolesForUser(String)}
     * must remain, or be replaced with an equivalent that honours the
     * {@code app.user.permissions} configuration.
     *
     * @param username the username supplied by the login form
     * @return a {@link Mono} emitting the resolved {@link UserDetails}, or
     *         {@link Mono#error} with {@link UsernameNotFoundException} when
     *         the user does not exist
     */
    private Mono<UserDetails> loadUser(String username) {
        String encodedPassword = properties.getAccounts().get(username);
        if (encodedPassword == null) {
            return Mono.error(new UsernameNotFoundException("User not found: " + username));
        }

        List<GrantedAuthority> authorities = properties.rolesForUser(username).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        UserDetails userDetails = User.withUsername(username)
                .password(encodedPassword)
                .authorities(authorities)
                .build();

        return Mono.just(userDetails);
    }
}
