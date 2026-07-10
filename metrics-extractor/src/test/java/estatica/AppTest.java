package estatica;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

//-----> Prueba de extremo a extremo: corre App igual que lo haría Maven, pero con carpetas temporales
public class AppTest {

    @Test
    //-----> Verifica que al analizar un proyecto de prueba se generen todos los archivos esperados
    void analizaProyectoDePruebaYGeneraLosArchivosEsperados(@TempDir Path carpetaTemporal) throws IOException {

        //-----> Arma una carpeta de código fuente con un proyecto de prueba adentro
        Path carpetaProyectos  = carpetaTemporal.resolve("codigoFuente");
        Path carpetaResultados = carpetaTemporal.resolve("resultados");
        Path proyectoDemo      = carpetaProyectos.resolve("ProyectoDemo");
        Files.createDirectories(proyectoDemo);

        //-----> Clase Java de prueba con un método simple para poder verificar sus métricas
        String codigoDePrueba =
                "package demo;\n" +
                "public class Calculadora {\n" +
                "    public int sumar(int a, int b) {\n" +
                "        if (a > 0) {\n" +
                "            return a + b;\n" +
                "        }\n" +
                "        return b;\n" +
                "    }\n" +
                "}\n";

        Files.writeString(proyectoDemo.resolve("Calculadora.java"), codigoDePrueba);

        //-----> Corre App igual que lo haría Maven, pero apuntando a las carpetas temporales
        App.main(new String[] { carpetaProyectos.toString(), carpetaResultados.toString() });

        //-----> Verifica que sí se haya creado la carpeta de resultados del proyecto
        File carpetaProyectoResultado = new File(carpetaResultados.toFile(), "ProyectoDemo");
        assertTrue(carpetaProyectoResultado.exists(), "Debería crearse la carpeta de resultados del proyecto");

        //-----> Verifica que se haya generado el JSON de métricas de la clase
        File jsonMetricas = new File(carpetaProyectoResultado, "CalculadoraMetricas.json");
        assertTrue(jsonMetricas.exists(), "Debería generarse el JSON de métricas");

        //-----> Verifica que el JSON tenga el nombre del método esperado adentro
        String contenidoJson = Files.readString(jsonMetricas.toPath());
        assertTrue(contenidoJson.contains("\"metodo\": \"sumar\""), "El JSON debe contener el método sumar");

        //-----> Verifica que se haya generado el CSV de métricas
        File csvMetricas = new File(carpetaProyectoResultado, "CalculadoraMetricas.csv");
        assertTrue(csvMetricas.exists(), "Debería generarse el CSV de métricas");

        //-----> Verifica que se haya generado el JSON de caminos del árbol
        File jsonCaminos = new File(carpetaProyectoResultado, "Calculadora_caminos.json");
        assertTrue(jsonCaminos.exists(), "Debería generarse el JSON de caminos del árbol");

        //-----> Verifica que se haya generado el JSON de Code2Seq
        File jsonCode2Seq = new File(carpetaProyectoResultado, "Calculadora_code2seq.json");
        assertTrue(jsonCode2Seq.exists(), "Debería generarse el JSON de Code2Seq");
    }

    @Test
    //-----> Verifica que un método anidado adentro de otro saque sus propias métricas por separado
    void separaLasMetricasDeUnMetodoAnidado(@TempDir Path carpetaTemporal) throws IOException {

        //-----> Arma un proyecto de prueba con un método que tiene otro método anidado adentro
        Path carpetaProyectos  = carpetaTemporal.resolve("codigoFuente");
        Path carpetaResultados = carpetaTemporal.resolve("resultados");
        Path proyectoDemo      = carpetaProyectos.resolve("ProyectoAnidado");
        Files.createDirectories(proyectoDemo);

        //-----> "externo" tiene adentro una clase anónima con su propio método "run" (el anidado)
        String codigoConMetodoAnidado =
                "package demo;\n" +
                "public class ConAnidado {\n" +
                "    public void externo() {\n" +
                "        Runnable r = new Runnable() {\n" +
                "            public void run() {\n" +
                "                int x = 1;\n" +
                "                int y = 2;\n" +
                "                int z = x + y;\n" +
                "            }\n" +
                "        };\n" +
                "        r.run();\n" +
                "    }\n" +
                "}\n";

        Files.writeString(proyectoDemo.resolve("ConAnidado.java"), codigoConMetodoAnidado);

        App.main(new String[] { carpetaProyectos.toString(), carpetaResultados.toString() });

        File carpetaProyectoResultado = new File(carpetaResultados.toFile(), "ProyectoAnidado");
        File jsonMetricas = new File(carpetaProyectoResultado, "ConAnidadoMetricas.json");
        assertTrue(jsonMetricas.exists(), "Debería generarse el JSON de métricas");

        String contenidoJson = Files.readString(jsonMetricas.toPath());

        //-----> Deben aparecer los dos métodos por separado: el de afuera y el anidado
        assertTrue(contenidoJson.contains("\"metodo\": \"externo\""), "Debe aparecer el método externo");
        assertTrue(contenidoJson.contains("\"metodo\": \"run\""), "Debe aparecer el método anidado por separado");
    }

    @Test
    //-----> Verifica que el resumen de consola sí cuente el método anidado, no solo el de afuera
    void elResumenDeConsolaCuentaElMetodoAnidado(@TempDir Path carpetaTemporal) throws IOException {

        Path carpetaProyectos  = carpetaTemporal.resolve("codigoFuente");
        Path carpetaResultados = carpetaTemporal.resolve("resultados");
        Path proyectoDemo      = carpetaProyectos.resolve("ProyectoAnidado");
        Files.createDirectories(proyectoDemo);

        //-----> Esta clase tiene 2 métodos en realidad: "externo" y el "run" anidado adentro
        String codigoConMetodoAnidado =
                "package demo;\n" +
                "public class ConAnidado {\n" +
                "    public void externo() {\n" +
                "        Runnable r = new Runnable() {\n" +
                "            public void run() {\n" +
                "                int x = 1;\n" +
                "            }\n" +
                "        };\n" +
                "        r.run();\n" +
                "    }\n" +
                "}\n";

        Files.writeString(proyectoDemo.resolve("ConAnidado.java"), codigoConMetodoAnidado);

        //-----> Captura lo que se imprime en consola para poder revisarlo después
        java.io.ByteArrayOutputStream salidaCapturada = new java.io.ByteArrayOutputStream();
        java.io.PrintStream salidaOriginal = System.out;
        System.setOut(new java.io.PrintStream(salidaCapturada));

        try {
            App.main(new String[] { carpetaProyectos.toString(), carpetaResultados.toString() });
        } finally {
            System.setOut(salidaOriginal);
        }

        String textoConsola = salidaCapturada.toString();

        //-----> La consola debe decir 2 métodos (externo + run), no 1
        assertTrue(textoConsola.contains("ConAnidado.java tiene: 2 métodos"),
                "El resumen de consola debería contar también el método anidado");
    }
}