package dev.wallpaperhub.account.grpc;

import dev.wallpaperhub.platform.security.JwtValidation;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * contracts/proto/account/v1/account.proto: every call carries a Keycloak access token from the Client Credentials flow in the
 * {@code authorization: Bearer <token>} metadata entry, validated like the token of an HTTP request (the same {@link JwtDecoder}:
 * signature against the JWKS, issuer, audience, expiry, {@code typ=Bearer}) and holding the realm role {@code internal}.
 *
 * <p>UNAUTHENTICATED: no token or a token that is not valid. PERMISSION_DENIED: a valid token without the role (a user's token, not a
 * service account's). UNAVAILABLE: the signing keys cannot be fetched, so no token can be judged. The token is never logged.
 */
public final class InternalCallInterceptor implements ServerInterceptor {

    static final String INTERNAL_AUTHORITY = "ROLE_internal";

    private static final Logger LOG = LoggerFactory.getLogger(InternalCallInterceptor.class);
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final String BEARER = "bearer ";

    private final JwtDecoder decoder;
    private final Converter<Jwt, AbstractAuthenticationToken> converter = JwtValidation.converter();

    public InternalCallInterceptor(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String header = headers.get(AUTHORIZATION);
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(BEARER)) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("bearer token required"));
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(header.substring(BEARER.length()).strip());
        } catch (BadJwtException e) {
            return reject(call, Status.UNAUTHENTICATED.withDescription("token not valid"));
        } catch (JwtException e) {
            LOG.warn("a token could not be judged: {}", e.getClass().getSimpleName());
            return reject(call, Status.UNAVAILABLE.withDescription("tokens cannot be validated now"));
        }
        AbstractAuthenticationToken authentication = converter.convert(jwt);
        boolean internal = authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> INTERNAL_AUTHORITY.equals(a.getAuthority()));
        if (!internal) {
            return reject(call, Status.PERMISSION_DENIED.withDescription("role internal required"));
        }
        return next.startCall(call, headers);
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> reject(ServerCall<ReqT, RespT> call, Status status) {
        call.close(status, new Metadata());
        return new ServerCall.Listener<>() {};
    }
}
