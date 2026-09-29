package com.tec.dnsapi.config;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("HttpConnectorConfig")
class HttpConnectorConfigTest {

    @Test
    @DisplayName("con dns.http.port > 0 agrega un conector HTTP en ese puerto")
    void addsHttpConnector() {
        TomcatServletWebServerFactory factory = mock(TomcatServletWebServerFactory.class);

        new HttpConnectorConfig().extraHttpConnector(8080).customize(factory);

        ArgumentCaptor<Connector> captor = ArgumentCaptor.forClass(Connector.class);
        verify(factory).addAdditionalTomcatConnectors(captor.capture());
        assertThat(captor.getValue().getPort()).isEqualTo(8080);
        assertThat(captor.getValue().getScheme()).isEqualTo("http");
    }

    @Test
    @DisplayName("con dns.http.port = 0 no agrega ningún conector (solo HTTPS)")
    void noConnectorWhenDisabled() {
        TomcatServletWebServerFactory factory = mock(TomcatServletWebServerFactory.class);

        new HttpConnectorConfig().extraHttpConnector(0).customize(factory);

        verify(factory, never()).addAdditionalTomcatConnectors(any(Connector[].class));
    }
}
