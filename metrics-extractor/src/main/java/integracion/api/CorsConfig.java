package integracion.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//-----> Configura los permisos CORS para el frontend
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                //-----> Permite peticiones desde cualquier origen
                .allowedOrigins("*")
                //-----> Metodos HTTP permitidos
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                //-----> Permite todos los encabezados
                .allowedHeaders("*");
    }
}