package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a JWT whose account is deactivated or gone, so that deactivation takes effect on the account's next request
 * rather than when its token expires (FR-SEC-active-account).
 *
 * <p>A token is otherwise self-contained, so this one read is what stands between a deactivated account and the rest
 * of its two hours. The {@link DisabledException} reaches {@link CustomBearerTokenAuthenticationEntryPoint}, which
 * answers 401, and the SPA redirects to login. A token with no usable {@code userId} claim names no account, so it is
 * refused the same way.
 */
@Component
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(JwtAuthenticationConverter jwtAuthenticationConverter, UserRepository userRepository) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // The claim is numeric when this application issues the token; anything else names no account
        boolean active = jwt.getClaim("userId") instanceof Number userId
                && this.userRepository.findEnabledById(userId.intValue()).orElse(false);
        if (!active) {
            throw new DisabledException("This account is not active.");
        }
        return this.jwtAuthenticationConverter.convert(jwt);
    }

}
