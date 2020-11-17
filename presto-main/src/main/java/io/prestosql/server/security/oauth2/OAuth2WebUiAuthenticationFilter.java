/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.server.security.oauth2;

import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.prestosql.server.security.UserMapping;
import io.prestosql.server.security.UserMappingException;
import io.prestosql.server.ui.WebUiAuthenticationFilter;
import io.prestosql.spi.security.BasicPrincipal;
import io.prestosql.spi.security.Identity;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Response;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.prestosql.server.ServletSecurityUtils.sendWwwAuthenticate;
import static io.prestosql.server.ServletSecurityUtils.setAuthenticatedIdentity;
import static io.prestosql.server.security.oauth2.OAuth2Resource.OAUTH2_COOKIE;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.Response.Status.FOUND;

public class OAuth2WebUiAuthenticationFilter
        implements WebUiAuthenticationFilter
{
    private static final Logger LOG = Logger.get(OAuth2WebUiAuthenticationFilter.class);

    private final OAuth2Service service;
    private final UserMapping userMapping;

    @Inject
    public OAuth2WebUiAuthenticationFilter(OAuth2Service service, OAuth2Config oauth2Config)
    {
        this.service = requireNonNull(service, "service is null");
        requireNonNull(oauth2Config, "oauth2Config is null");
        this.userMapping = UserMapping.createUserMapping(oauth2Config.getUserMappingPattern(), oauth2Config.getUserMappingFile());
    }

    @Override
    public void filter(ContainerRequestContext request)
    {
        Optional<String> principal = getAccessToken(request).map(token -> token.getBody().getSubject());
        if (principal.isEmpty()) {
            needAuthentication(request);
            return;
        }
        try {
            setAuthenticatedIdentity(request, Identity.forUser(userMapping.mapUser(principal.get()))
                    .withPrincipal(new BasicPrincipal(principal.get()))
                    .build());
        }
        catch (UserMappingException e) {
            sendWwwAuthenticate(request, firstNonNull(e.getMessage(), "Unauthorized"), ImmutableList.of(e.getMessage()));
        }
    }

    private Optional<Jws<Claims>> getAccessToken(ContainerRequestContext request)
    {
        Stream<String> accessTokenSources = Stream.concat(Stream.concat(
                getTokenFromCookie(request),
                getTokenFromHeader(request)),
                getTokenFromQueryParam(request));
        return accessTokenSources
                .filter(not(String::isBlank))
                .map(token -> {
                    try {
                        return Optional.ofNullable(service.parseClaimsJws(token));
                    }
                    catch (JwtException | IllegalArgumentException e) {
                        LOG.debug("Unable to parse JWT token: " + e.getMessage(), e);
                        return Optional.<Jws<Claims>>empty();
                    }
                })
                .findFirst()
                .flatMap(Function.identity());
    }

    private void needAuthentication(ContainerRequestContext request)
    {
        request.abortWith(
                Response.status(FOUND)
                        .location(URI.create(service.startChallenge(request.getUriInfo().getBaseUri()).getAuthorizationUrl()))
                        .build());
    }

    private static Stream<String> getTokenFromCookie(ContainerRequestContext request)
    {
        return Stream.ofNullable(request.getCookies().get(OAUTH2_COOKIE))
                .map(Cookie::getValue);
    }

    private static Stream<String> getTokenFromHeader(ContainerRequestContext request)
    {
        return Stream.ofNullable(request.getHeaders().get(AUTHORIZATION))
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring("Bearer ".length()));
    }

    private static Stream<String> getTokenFromQueryParam(ContainerRequestContext request)
    {
        return Stream.ofNullable(request.getUriInfo().getQueryParameters().get("access_token"))
                .flatMap(Collection::stream)
                .filter(Objects::nonNull);
    }
}
