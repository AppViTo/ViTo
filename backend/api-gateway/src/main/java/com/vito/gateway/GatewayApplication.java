package com.vito.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Las rutas se definen en application.yml (y se sobreescriben por perfil en
 * application-dev.yml / application-docker.yml), no en código: así cada entorno
 * apunta a sus propios hosts sin recompilar.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
