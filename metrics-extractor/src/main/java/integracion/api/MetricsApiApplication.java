package integracion.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//-----> Punto de entrada de la aplicacion Spring Boot
@SpringBootApplication
public class MetricsApiApplication {

    //-----> Metodo principal para arrancar la API
    public static void main(String[] args) {
        SpringApplication.run(MetricsApiApplication.class, args);
    }
}