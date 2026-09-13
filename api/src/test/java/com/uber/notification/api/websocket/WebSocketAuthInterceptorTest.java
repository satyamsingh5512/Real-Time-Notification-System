package com.uber.notification.api.websocket;

import com.uber.notification.api.security.JwtService;
import com.uber.notification.domain.model.RoleName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies WebSocket handshake authentication: a valid ?token=&lt;jwt&gt; attaches the
 * userId to session attributes; missing/invalid tokens reject with 401.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private WebSocketHandler wsHandler;

    private ServletServerHttpRequest request(String queryString) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setScheme("ws");
        servletRequest.setServerName("localhost");
        servletRequest.setRequestURI("/ws/notifications");
        servletRequest.setQueryString(queryString);
        return new ServletServerHttpRequest(servletRequest);
    }

    @Test
    void validTokenAttachesUserId() {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseToken("valid-jwt")).thenReturn(
                new JwtService.AuthenticatedPrincipal(userId, "a@example.com", Set.of(RoleName.USER)));

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        boolean result = new WebSocketAuthInterceptor(jwtService)
                .beforeHandshake(request("token=valid-jwt"), response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(userId.toString());
    }

    @Test
    void missingTokenRejectsWith401() {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        boolean result = new WebSocketAuthInterceptor(jwtService)
                .beforeHandshake(request(null), response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenRejectsWith401() {
        when(jwtService.parseToken("bad-jwt")).thenThrow(new RuntimeException("invalid"));

        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        boolean result = new WebSocketAuthInterceptor(jwtService)
                .beforeHandshake(request("token=bad-jwt"), response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
