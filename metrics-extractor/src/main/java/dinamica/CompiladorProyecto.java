package dinamica;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

//-----> Automatiza la compilación de proyectos Java externos (Maven o Gradle) sin intervención manual
public class CompiladorProyecto {

    private static final Set<String> LENGUAJES_CONOCIDOS = Set.of("java", "kotlin", "groovy", "scala");

    // Guardador simple para retornar el resultado de la compilación
    public static class ResultadoCompilacion {
        public final boolean exitoso;
        public final String carpetaClases;
        public final String mensaje;
        public final String classpathCompleto;

        public ResultadoCompilacion(boolean exitoso, String carpetaClases, String mensaje, String classpathCompleto) {
            this.exitoso = exitoso;
            this.carpetaClases = carpetaClases;
            this.mensaje = mensaje;
            this.classpathCompleto = classpathCompleto;
        }
    }

    public static ResultadoCompilacion compilar(String rutaProyecto) throws Exception {
        return compilar(rutaProyecto, false);
    }

    // Detecta si el proyecto es Maven o Gradle y ejecuta la compilación en la terminal
    public static ResultadoCompilacion compilar(String rutaProyecto, boolean calcularClasspathCompleto) {
        try {
            File raiz = new File(rutaProyecto);

            if (!raiz.exists() || !raiz.isDirectory()) {
                return new ResultadoCompilacion(false, null, "La carpeta del proyecto no existe: " + rutaProyecto, null);
            }

            File pomFile = new File(raiz, "pom.xml");
            File gradleFile = new File(raiz, "build.gradle");
            File gradleKtsFile = new File(raiz, "build.gradle.kts");

            String[] comando;

            // Detecta la herramienta de construcción
            if (pomFile.exists()) {
                comando = new String[]{"mvn", "compile", "-q", "-DskipTests"};
            } else if (gradleFile.exists() || gradleKtsFile.exists()) {
                File gradlew = new File(raiz, "gradlew");
                String ejecutable = gradlew.exists() ? "./gradlew" : "gradle";

                comando = new String[]{
                        ejecutable, "compileJava",
                        "--dependency-verification=off",
                        "--no-daemon",
                        "-x", "test"
                };
            } else {
                return new ResultadoCompilacion(false, null, "No se encontro pom.xml ni build.gradle en: " + rutaProyecto, null);
            }

            System.out.println("-----> Compilando con: " + String.join(" ", comando) + " (en " + raiz.getAbsolutePath() + ")");

            // Ejecuta el comando en el sistema operativo
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.directory(raiz);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println("   [compilacion] " + linea);
                }
            }

            // Espera máximo 15 minutos a que termine la compilación
            boolean termino = proceso.waitFor(15, TimeUnit.MINUTES);
            if (!termino) {
                proceso.destroyForcibly();
                return new ResultadoCompilacion(false, null, "La compilacion tardo mas de 15 minutos, se cancelo.", null);
            }

            int codigoSalida = proceso.exitValue();
            if (codigoSalida != 0) {
                return new ResultadoCompilacion(false, null, "La compilacion fallo, codigo de salida: " + codigoSalida, null);
            }

            // Encuentra dónde quedaron los archivos .class compilados
            String carpetaClases = resolverCarpetaClases(raiz, pomFile.exists());
            if (carpetaClases == null || carpetaClases.isBlank()) {
                return new ResultadoCompilacion(false, null, "La compilacion salio bien, pero no se encontraron archivos .class compilados en " + rutaProyecto, null);
            }

            // Si se requiere, resuelve también las librerías externas (JARs)
            String classpathCompleto = carpetaClases;
            if (calcularClasspathCompleto && pomFile.exists()) {
                System.out.println("-----> Calculando el classpath completo de dependencias...");
                classpathCompleto = obtenerClasspathMaven(raiz, carpetaClases);
            }

            return new ResultadoCompilacion(true, carpetaClases, "Compilacion exitosa", classpathCompleto);

        } catch (Exception e) {
            return new ResultadoCompilacion(false, null, "Excepcion durante la compilacion: " + e.getMessage(), null);
        }
    }

    // Encuentra la ubicación de los archivos .class compilados dentro del proyecto
    private static String resolverCarpetaClases(File raiz, boolean esMaven) {
        if (esMaven) {
            File targetClasses = new File(raiz, "target/classes");
            if (targetClasses.exists()) return targetClasses.getAbsolutePath();
        }

        List<String> raices = buscarRaicesDeModulos(raiz);
        if (!raices.isEmpty()) {
            return String.join(File.pathSeparator, raices);
        }

        return resolverCarpetaClasesFallback(raiz);
    }

    private static List<String> buscarRaicesDeModulos(File raiz) {
        List<String> resultado = new ArrayList<>();
        try (var stream = Files.walk(raiz.toPath())) {
            List<Path> carpetasClasses = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null && p.getFileName().toString().equals("classes"))
                    .collect(Collectors.toList());

            for (Path carpetaClasses : carpetasClasses) {
                resultado.addAll(resolverRaicesDentroDeClasses(carpetaClasses));
            }
        } catch (Exception e) {
            System.err.println("-----> Error buscando raices de modulos: " + e.getMessage());
        }
        return resultado.stream().distinct().collect(Collectors.toList());
    }

    private static List<String> resolverRaicesDentroDeClasses(Path carpetaClasses) {
        List<String> resultado = new ArrayList<>();
        try {
            boolean pareceLayoutGradle = false;

            try (var lenguajes = Files.list(carpetaClasses)) {
                for (Path posibleLenguaje : lenguajes.filter(Files::isDirectory).collect(Collectors.toList())) {
                    if (LENGUAJES_CONOCIDOS.contains(posibleLenguaje.getFileName().toString().toLowerCase())) {
                        pareceLayoutGradle = true;
                        try (var sourceSets = Files.list(posibleLenguaje)) {
                            for (Path posibleSourceSet : sourceSets.filter(Files::isDirectory).collect(Collectors.toList())) {
                                if (contieneClases(posibleSourceSet)) {
                                    resultado.add(posibleSourceSet.toAbsolutePath().toString());
                                }
                            }
                        }
                    }
                }
            }

            if (!pareceLayoutGradle && contieneClases(carpetaClasses)) {
                resultado.add(carpetaClasses.toAbsolutePath().toString());
            }
        } catch (Exception e) {
            System.err.println("-----> Error resolviendo raices dentro de " + carpetaClasses + ": " + e.getMessage());
        }
        return resultado;
    }

    private static boolean contieneClases(Path carpeta) throws Exception {
        try (var stream = Files.walk(carpeta)) {
            return stream.anyMatch(p -> p.toString().endsWith(".class"));
        }
    }

    private static String resolverCarpetaClasesFallback(File raiz) {
        List<String> carpetasConClases = new ArrayList<>();
        try (var stream = Files.walk(raiz.toPath())) {
            carpetasConClases = stream
                    .filter(p -> p.toString().endsWith(".class") && !p.toString().contains("$"))
                    .map(Path::getParent)
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("-----> Error escaneando carpetas de clases: " + e.getMessage());
        }

        if (carpetasConClases.isEmpty()) {
            return null;
        }

        return String.join(File.pathSeparator, carpetasConClases);
    }

    // Le pide a Maven la lista de todas las librerías dependientes del proyecto
    private static String obtenerClasspathMaven(File raiz, String carpetaClases) {
        try {
            File archivoTemporal = File.createTempFile("classpath-", ".txt");
            archivoTemporal.deleteOnExit();

            String[] comandoClasspath = {
                    "mvn", "dependency:build-classpath",
                    "-Dmdep.outputFile=" + archivoTemporal.getAbsolutePath(), "-q"
            };

            ProcessBuilder pb = new ProcessBuilder(comandoClasspath);
            pb.directory(raiz);
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println("   [classpath] " + linea);
                }
            }

            boolean termino = proceso.waitFor(5, TimeUnit.MINUTES);
            if (!termino || proceso.exitValue() != 0 || !archivoTemporal.exists()) {
                return carpetaClases;
            }

            String dependencias = Files.readString(archivoTemporal.toPath()).trim();
            return dependencias.isEmpty() ? carpetaClases : carpetaClases + File.pathSeparator + dependencias;
        } catch (Exception e) {
            return carpetaClases;
        }
    }
}