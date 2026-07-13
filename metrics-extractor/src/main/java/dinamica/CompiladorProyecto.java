package dinamica;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class CompiladorProyecto {

    //-----> Guarda el resultado de la compilacion: si funcionó o no, y dónde quedaron los archivos listos
    public static class ResultadoCompilacion {
        public final boolean exitoso;
        public final String carpetaClases;
        public final String mensaje;

        public ResultadoCompilacion(boolean exitoso, String carpetaClases, String mensaje) {
            this.exitoso = exitoso;
            this.carpetaClases = carpetaClases;
            this.mensaje = mensaje;
        }
    }

    //-----> Recibe la carpeta principal del proyecto externo e inicia el proceso de compilacion
    public static ResultadoCompilacion compilar(String rutaProyecto) throws Exception {
        File raiz = new File(rutaProyecto);

        //-----> Filtro de seguridad: si la carpeta no existe, avisa y se detiene
        if (!raiz.exists() || !raiz.isDirectory()) {
            return new ResultadoCompilacion(false, null, "La carpeta del proyecto no existe: " + rutaProyecto);
        }

        //-----> Busca los archivos clave para saber si es un proyecto Maven o Gradle
        File pomFile = new File(raiz, "pom.xml");
        File gradleFile = new File(raiz, "build.gradle");
        File gradleKtsFile = new File(raiz, "build.gradle.kts");

        String[] comando;
        String carpetaClases;

        //-----> Si encuentra pom.xml, usa comandos de Maven y busca la carpeta target
        if (pomFile.exists()) {
            comando = new String[]{"mvn", "compile", "-q"};
            carpetaClases = new File(raiz, "target/classes").getAbsolutePath();

        //-----> Si encuentra archivos de Gradle, usa los comandos de Gradle correspondientes
        } else if (gradleFile.exists() || gradleKtsFile.exists()) {
            File gradlew = new File(raiz, "gradlew");
            String ejecutable = gradlew.exists() ? "./gradlew" : "gradle";
            comando = new String[]{ejecutable, "compileJava"};
            carpetaClases = new File(raiz, "build/classes/java/main").getAbsolutePath();

        //-----> Si no es ninguno de los dos, avisa que no sabe cómo compilarlo
        } else {
            return new ResultadoCompilacion(false, null,
                    "No se encontro pom.xml ni build.gradle en: " + rutaProyecto);
        }

        System.out.println("-----> Compilando con: " + String.join(" ", comando) + " (en " + raiz.getAbsolutePath() + ")");

        //-----> Prepara y arranca el comando en la terminal de Linux
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.directory(raiz);
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        //-----> Lee y muestra en la pantalla lo que va haciendo la compilacion en vivo
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
            String linea;
            while ((linea = lector.readLine()) != null) {
                System.out.println("   [compilacion] " + linea);
            }
        }

        //-----> Escudo de proteccion: si tarda mas de 15 minutos, cancela todo para que no se congele tu Ubuntu
        boolean termino = proceso.waitFor(15, TimeUnit.MINUTES);
        if (!termino) {
            proceso.destroyForcibly();
            return new ResultadoCompilacion(false, null, "La compilacion tardo mas de 15 minutos, se cancelo.");
        }

        //-----> Si la terminal devuelve un codigo diferente a cero, significa que la compilacion fallo
        int codigoSalida = proceso.exitValue();
        if (codigoSalida != 0) {
            return new ResultadoCompilacion(false, null, "La compilacion fallo, codigo de salida: " + codigoSalida);
        }

        //-----> Revisa si realmente se crearon los archivos compilados en la carpeta destino
        File carpeta = new File(carpetaClases);
        if (!carpeta.exists()) {
            return new ResultadoCompilacion(false, null,
                    "La compilacion dijo que salio bien, pero no se encontro: " + carpetaClases);
        }

        //-----> Todo salio perfecto
        return new ResultadoCompilacion(true, carpetaClases, "Compilacion exitosa");
    }
}