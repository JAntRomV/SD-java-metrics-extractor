package integracion.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//-----> Punto de entrada de la app Spring Boot
@SpringBootApplication
public class MetricsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsApiApplication.class, args);
    }
}