package dinamica;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    //-----> @TempDir crea una carpeta falsa en tu Ubuntu que se borra solita cuando las pruebas terminan.
    @TempDir
    Path carpetaTemporal; 

    private File subCarpetaProyecto;

    @BeforeEach
    void setUp() {
        //-----> Prepara una ruta simulada antes de que empiece cada prueba unitaria
        subCarpetaProyecto = carpetaTemporal.resolve("proyecto-prueba").toFile();
    }

    //=======================================================================
    // 1. PRUEBAS PARA COMPILADORPROYECTO
    //=======================================================================

    @Test
    void testCompilar_RutaNoExiste_DebeRetornarFalso() throws Exception {
        //-----> Intenta compilar una ruta inventada para comprobar que el escudo de seguridad funciona
        CompiladorProyecto.ResultadoCompilacion resultado = 
            CompiladorProyecto.compilar("/ruta/falsa/que/no/existe/en/este/equipo");
        
        assertFalse(resultado.exitoso, "-----> Debe fallar porque la carpeta no existe de verdad");
        assertTrue(resultado.mensaje.contains("La carpeta del proyecto no existe"));
    }

    @Test
    void testCompilar_CarpetaVaciaSinBuildTools_DebeRetornarFalso() throws Exception {
        //-----> Crea la carpeta física pero no le mete ni pom.xml ni build.gradle
        assertTrue(subCarpetaProyecto.mkdir());

        CompiladorProyecto.ResultadoCompilacion resultado = 
            CompiladorProyecto.compilar(subCarpetaProyecto.getAbsolutePath());

        assertFalse(resultado.exitoso, "-----> Debe fallar porque no sabe cómo compilar una carpeta vacía");
        assertTrue(resultado.mensaje.contains("No se encontro pom.xml ni build.gradle"));
    }

    //=======================================================================
    // 2. PRUEBAS PARA ESCANEADORMETODOS
    //=======================================================================

    @Test
    void testEscaneador_RutaInvalida_DebeLanzarExcepcion() {
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        
        //-----> Valida que el escaneador arroje un error controlado si la carpeta de clases .class no existe
        assertThrows(IllegalArgumentException.class, () -> {
            escaner.escanear("/target/classes/falso/inexistente");
        }, "-----> Debe lanzar un error si la ruta de compilación no existe");
    }

    @Test
    void testMetodoObjetivo_FormatoTexto_DebeSerCorrecto() {
        //-----> Valida que la etiqueta del catálogo junte la clase y el método usando un gato '#'
        EscaneadorMetodos.MetodoObjetivo met = 
            new EscaneadorMetodos.MetodoObjetivo("org.keycloak.Autenticacion", "validarToken");
        
        assertEquals("org.keycloak.Autenticacion#validarToken", met.comoTexto(), 
            "-----> El formato de texto generado debe ser Clase#metodo obligatoriamente");
    }

    @Test
    void testGuardarCatalogo_FlujoCompleto() throws Exception {
        EscaneadorMetodos escaner = new EscaneadorMetodos();
        File archivoCatalogo = carpetaTemporal.resolve("catalogo_salida.txt").toFile();

        //-----> Crea una lista simulada con dos métodos de ejemplo
        List<EscaneadorMetodos.MetodoObjetivo> listaFalsa = new ArrayList<>();
        listaFalsa.add(new EscaneadorMetodos.MetodoObjetivo("com.test.ClaseA", "init"));
        listaFalsa.add(new EscaneadorMetodos.MetodoObjetivo("com.test.ClaseB", "run"));

        //-----> Comprueba que la escritura en disco se complete sin trabarse ni dar errores
        assertDoesNotThrow(() -> {
            escaner.guardarCatalogo(listaFalsa, archivoCatalogo.getAbsolutePath());
        }, "-----> El guardado del archivo no debe arrojar ninguna excepción");

        assertTrue(archivoCatalogo.exists(), "-----> El archivo físico en Ubuntu debió crearse con éxito");
    }

    //=======================================================================
    // 3. PRUEBAS PARA RESULTADODINAMICO
    //=======================================================================

    @Test
    void testResultadoDinamico_GuardadoYUnionCorrecta() {
        //-----> Creamos un objeto molde simulando los resultados que arrojó el cronómetro de JMH
        ResultadoDinamico resultado = new ResultadoDinamico(
                "MiClase.java",
                "org.keycloak.MiClase",
                "evaluarCodigo",
                245.8,
                3.5,
                "ns",
                2048.0
        );

        //-----> Verificamos que todas las variables de rendimiento se guarden y lean exactamente como entraron
        assertEquals("MiClase.java", resultado.getFileName());
        assertEquals("org.keycloak.MiClase", resultado.getClassName());
        assertEquals("evaluarCodigo", resultado.getMethodName());
        assertEquals(245.8, resultado.getTiempoScore());
        assertEquals(3.5, resultado.getTiempoError());
        assertEquals("ns", resultado.getUnidadTiempo());
        assertEquals(2048.0, resultado.getMemoriaAsignadaBytes());

        //-----> Comprobar que getLlaveUnion() arme la misma estructura exacta que tu módulo estático.
        assertEquals("org.keycloak.MiClase#evaluarCodigo", resultado.getLlaveUnion(),
                "-----> ¡La llave de unión Clase#metodo no coincide con la del análisis estático!");
    }
}