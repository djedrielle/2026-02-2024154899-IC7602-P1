package com.tec.dnsapi.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Con HTTPS activo, Spring solo abre el puerto seguro. Este conector agrega un puerto HTTP
 * (dns.http.port) para clientes que no pueden confiar en el certificado, como la DNS UI en el navegador.
 */
@Configuration
public class HttpConnectorConfig {

    @Bean
    @ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> extraHttpConnector(
            @Value("${dns.http.port:0}") int httpPort) {
        return factory -> {
            if (httpPort > 0) {
                Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connector.setScheme("http");
                connector.setPort(httpPort);
                factory.addAdditionalTomcatConnectors(connector);
            }
        };
    }
}
