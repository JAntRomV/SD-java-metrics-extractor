package integracion.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//-----> Le da permiso al frontend (desplegado en Vercel) para poder pedirle
//-----> datos a esta API, aunque esten en dominios distintos (CORS).
//-----> Sin esta clase, el navegador bloquea las peticiones desde la pagina
//-----> web hacia esta API, aunque la API funcione perfectamente bien.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // ⚠️ "*" permite CUALQUIER pagina web mientras se hacen las
                // pruebas. Cuando ya tengan la URL final de Vercel, hay que
                // cambiar el "*" por esa URL exacta (ver Paso 4 mas abajo).
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}