package dinamica;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

//-----> Compilador automatico para proyectos Java
public class CompiladorProyecto {

    //-----> Lenguajes soportados para clases compiladas
    private static final Set<String> LENGUAJES_CONOCIDOS = Set.of("java", "kotlin", "groovy", "scala");

    //-----> Guarda los datos del resultado de compilacion
    public static class ResultadoCompilacion {
        public final boolean exitoso;
        public final String carpetaClases;
        public final String mensaje;
        public final String classpathCompleto;

        //-----> Asigna los valores del resultado
        public ResultadoCompilacion(boolean exitoso, String carpetaClases, String mensaje, String classpathCompleto) {
            this.exitoso = exitoso;
            this.carpetaClases = carpetaClases;
            this.mensaje = mensaje;
            this.classpathCompleto = classpathCompleto;
        }
    }

    //-----> Sobrecarga del metodo compilar
    public static ResultadoCompilacion compilar(String rutaProyecto) throws Exception {
        return compilar(rutaProyecto, false);
    }

    //-----> Detecta la herramienta y compila el proyecto
    public static ResultadoCompilacion compilar(String rutaProyecto, boolean calcularClasspathCompleto) {
        try {
            File raiz = new File(rutaProyecto);

            //-----> Valida que la carpeta exista
            if (!raiz.exists() || !raiz.isDirectory()) {
                return new ResultadoCompilacion(false, null, "La carpeta del proyecto no existe: " + rutaProyecto, null);
            }

            //-----> Busca archivos de configuracion
            File pomFile = new File(raiz, "pom.xml");
            File gradleFile = new File(raiz, "build.gradle");
            File gradleKtsFile = new File(raiz, "build.gradle.kts");

            String[] comando;

            //-----> Arma comando para Maven o Gradle
            if (pomFile.exists()) {
                comando = new String[]{"mvn", "compile", "-q", "-DskipTests"};
            } else if (gradleFile.exists() || gradleKtsFile.exists()) {
                File gradlew = new File(raiz, "gradlew");
                String ejecutable = gradlew.exists() ? "./gradlew" : "gradle";

                //-----> 🔌 MODIFICADO: se agrega "--max-workers=1". Aunque ya se
                //-----> usaba "--no-daemon" (evita el Build Daemon persistente),
                //-----> Gradle sigue pudiendo forkear procesos "worker" separados
                //-----> para compilar en paralelo (workers de compilacion, que
                //-----> usan la MISMA infraestructura interna de sockets que el
                //-----> Build Daemon -de ahi los mensajes "DefaultDaemonConnection"
                //-----> en el log aunque tecnicamente no sea el Build Daemon-). En
                //-----> un contenedor de 512MB, cada worker forkeado es una JVM
                //-----> adicional compitiendo por memoria junto con la app Spring
                //-----> Boot y el propio proceso "gradlew --no-daemon". Forzar a 1
                //-----> solo worker evita esa multiplicacion y es la causa mas
                //-----> probable del OOM que tumbaba la compilacion a medias.
                comando = new String[]{
                        ejecutable, "compileJava",
                        "--dependency-verification=off",
                        "--no-daemon",
                        "--max-workers=1",
                        "-x", "test"
                };
            } else {
                return new ResultadoCompilacion(false, null, "No se encontro pom.xml ni build.gradle en: " + rutaProyecto, null);
            }

            System.out.println("-----> Compilando con: " + String.join(" ", comando) + " (en " + raiz.getAbsolutePath() + ")");

            //-----> Configura el proceso de compilacion
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.directory(raiz);
            pb.redirectErrorStream(true);

            //-----> 🔌 NUEVO: acota la memoria del proceso hijo (y de cualquier
            //-----> JVM que el propio Gradle llegue a forkear adentro, ej. si
            //-----> algo ignora --no-daemon). Sin esto, cada JVM hija puede
            //-----> reclamar hasta 1/4 de la RAM del contenedor por defecto,
            //-----> lo cual en un contenedor de 512MB es demasiado si hay mas
            //-----> de una JVM viva a la vez (la app Spring Boot + el proceso
            //-----> de compilacion). Ver limitarMemoriaProcesoHijo() mas abajo.
            if (gradleFile.exists() || gradleKtsFile.exists()) {
                limitarMemoriaProcesoHijo(pb);
            }

            //-----> Ejecuta el proceso con limite de tiempo
            ResultadoProceso resultadoProceso = ejecutarProceso(pb, "   [compilacion] ", 15, TimeUnit.MINUTES);

            if (!resultadoProceso.termino) {
                return new ResultadoCompilacion(false, null, "La compilacion tardo mas de 15 minutos, se cancelo.", null);
            }

            //-----> Revisa si hubo error en el proceso
            int codigoSalida = resultadoProceso.codigoSalida;
            if (codigoSalida != 0) {
                return new ResultadoCompilacion(false, null, "La compilacion fallo, codigo de salida: " + codigoSalida, null);
            }

            //-----> Localiza la carpeta con archivos .class
            String carpetaClases = resolverCarpetaClases(raiz, pomFile.exists());
            if (carpetaClases == null || carpetaClases.isBlank()) {
                return new ResultadoCompilacion(false, null, "La compilacion salio bien, pero no se encontraron archivos .class compilados en " + rutaProyecto, null);
            }

            //-----> Calcula el classpath con librerias si se pide
            String classpathCompleto = carpetaClases;
            if (calcularClasspathCompleto && pomFile.exists()) {
                System.out.println("-----> Calculando el classpath completo de dependencias (Maven)...");
                classpathCompleto = obtenerClasspathMaven(raiz, carpetaClases);
            } else if (calcularClasspathCompleto && (gradleFile.exists() || gradleKtsFile.exists())) {
                System.out.println("-----> Calculando el classpath completo de dependencias (Gradle)...");
                classpathCompleto = obtenerClasspathGradle(raiz, carpetaClases);
            }

            return new ResultadoCompilacion(true, carpetaClases, "Compilacion exitosa", classpathCompleto);

        } catch (Exception e) {
            return new ResultadoCompilacion(false, null, "Excepcion durante la compilacion: " + e.getMessage(), null);
        }
    }

    //-----> Estado final de la ejecucion del proceso
    private static class ResultadoProceso {
        final boolean termino;
        final int codigoSalida;

        ResultadoProceso(boolean termino, int codigoSalida) {
            this.termino = termino;
            this.codigoSalida = codigoSalida;
        }
    }

    //-----> 🔌 NUEVO: acota la memoria de cualquier JVM que el proceso Gradle
    //-----> hijo llegue a levantar (el propio cliente "gradlew --no-daemon", y
    //-----> por seguridad tambien GRADLE_OPTS/JAVA_OPTS por si algun sub-paso
    //-----> del build ignora --no-daemon y termina levantando una JVM extra).
    //-----> Los valores son conservadores pensando en un contenedor de 512MB
    //-----> total (compartido con la app Spring Boot) -- si el plan del
    //-----> contenedor cambia, hay que ajustar estos numeros.
    private static void limitarMemoriaProcesoHijo(ProcessBuilder pb) {
        String opcionesMemoria = "-Xmx256m -XX:MaxMetaspaceSize=128m";
        Map<String, String> entorno = pb.environment();
        entorno.put("GRADLE_OPTS", opcionesMemoria);
        entorno.put("JAVA_OPTS", opcionesMemoria);
    }

    //-----> Corre procesos externos con hilo lector y timeout
    private static ResultadoProceso ejecutarProceso(ProcessBuilder pb, String prefijoLog, long tiempoLimite, TimeUnit unidad) throws Exception {
        Process proceso = pb.start();
        proceso.getOutputStream().close();

        Thread hiloLector = new Thread(() -> {
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println(prefijoLog + linea);
                }
            } catch (Exception ignorado) {
                //-----> Proceso terminado a la fuerza
            }
        });
        hiloLector.setDaemon(true);
        hiloLector.start();

        boolean termino = proceso.waitFor(tiempoLimite, unidad);
        if (!termino) {
            proceso.destroyForcibly();
        }
        hiloLector.join(3000);

        return new ResultadoProceso(termino, termino ? proceso.exitValue() : -1);
    }
//_________________________________________________________________________________________
    //-----> Busca rutas de archivos .class compilados
    private static String resolverCarpetaClases(File raiz, boolean esMaven) {
        //-----> Ruta por defecto de Maven
        if (esMaven) {
            File targetClasses = new File(raiz, "target/classes");
            if (targetClasses.exists()) return targetClasses.getAbsolutePath();
        }

        //-----> Busca carpetas en submodulos o Gradle
        List<String> raices = buscarRaicesDeModulos(raiz);
        if (!raices.isEmpty()) {
            return String.join(File.pathSeparator, raices);
        }

        //-----> Busqueda alternativa de clases
        return resolverCarpetaClasesFallback(raiz);
    }

    //-----> Lista carpetas llamadas classes
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
//________________________________________________________________________________________
    //-----> Procesa estructura interna de carpetas classes
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
//__________________________________________________________________________________
    //-----> Revisa si la carpeta contiene archivos .class
    private static boolean contieneClases(Path carpeta) throws Exception {
        try (var stream = Files.walk(carpeta)) {
            return stream.anyMatch(p -> p.toString().endsWith(".class"));
        }
    }

    //-----> Recorre el proyecto buscando archivos .class
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

    //-----> Obtiene el classpath de dependencias con Maven
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

            ResultadoProceso resultadoProceso = ejecutarProceso(pb, "   [classpath] ", 5, TimeUnit.MINUTES);

            if (!resultadoProceso.termino || resultadoProceso.codigoSalida != 0 || !archivoTemporal.exists()) {
                return carpetaClases;
            }

            String dependencias = Files.readString(archivoTemporal.toPath()).trim();
            return dependencias.isEmpty() ? carpetaClases : carpetaClases + File.pathSeparator + dependencias;
        } catch (Exception e) {
            return carpetaClases;
        }
    }

    //-----> Obtiene el classpath de dependencias con Gradle
    private static String obtenerClasspathGradle(File raiz, String carpetaClases) {
        File initScript = null;
        File archivoSalida = null;
        try {
            initScript = File.createTempFile("init-classpath-", ".gradle");
            initScript.deleteOnExit();
            archivoSalida = File.createTempFile("classpath-gradle-", ".txt");
            archivoSalida.deleteOnExit();

            String rutaSalida = archivoSalida.getAbsolutePath().replace("\\", "\\\\");

            String script =
                    "allprojects { proyecto ->\n" +
                    "    proyecto.afterEvaluate {\n" +
                    "        def archivoDeSalida = new File(\"" + rutaSalida + "\")\n" +
                    "        def nombresConfig = ['runtimeClasspath', 'compileClasspath', 'testRuntimeClasspath']\n" +
                    "        nombresConfig.each { nombreConfig ->\n" +
                    "            def config = proyecto.configurations.findByName(nombreConfig)\n" +
                    "            if (config != null && config.canBeResolved) {\n" +
                    "                try {\n" +
                    "                    config.files.each { archivoJar ->\n" +
                    "                        archivoDeSalida.append(archivoJar.absolutePath + System.lineSeparator())\n" +
                    "                    }\n" +
                    "                } catch (Exception ignorado) { }\n" +
                    "            }\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n";

            Files.writeString(initScript.toPath(), script, StandardCharsets.UTF_8);

            File gradlew = new File(raiz, "gradlew");
            String ejecutable = gradlew.exists() ? "./gradlew" : "gradle";

            //-----> 🔌 MODIFICADO: antes este comando NO tenia "--no-daemon", a
            //-----> diferencia del comando principal de compilacion. Eso podia
            //-----> levantar un Build Daemon REAL y persistente -Gradle reutiliza
            //-----> daemons compatibles entre invocaciones por diseno-, que se
            //-----> queda vivo de fondo y puede acumular memoria repo tras repo
            //-----> a lo largo del lote completo que corre OrquestadorRepos, sin
            //-----> que nada lo libere entre un repo y el siguiente. Se agrega
            //-----> tambien "--max-workers=1" por la misma razon que en el
            //-----> comando de compilacion (ver comentario en compilar()).
            String[] comando = {
                    ejecutable, "--init-script", initScript.getAbsolutePath(),
                    "-q", "--no-daemon", "--max-workers=1", "help"
            };

            System.out.println("-----> Ejecutando: " + String.join(" ", comando) + " (en " + raiz.getAbsolutePath() + ")");

            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.directory(raiz);
            pb.redirectErrorStream(true);
            limitarMemoriaProcesoHijo(pb); //-----> 🔌 NUEVO: mismo limite de memoria que el comando principal

            ResultadoProceso resultadoProceso = ejecutarProceso(pb, "   [classpath-gradle] ", 5, TimeUnit.MINUTES);

            if (!resultadoProceso.termino || !archivoSalida.exists()) {
                return carpetaClases;
            }

            List<String> lineas = Files.readAllLines(archivoSalida.toPath(), StandardCharsets.UTF_8);
            Set<String> rutasUnicas = new LinkedHashSet<>();
            for (String linea : lineas) {
                if (!linea.isBlank()) rutasUnicas.add(linea.trim());
            }

            if (rutasUnicas.isEmpty()) {
                return carpetaClases;
            }

            return carpetaClases + File.pathSeparator + String.join(File.pathSeparator, rutasUnicas);
        } catch (Exception e) {
            return carpetaClases;
        } finally {
            if (initScript != null) initScript.delete();
            if (archivoSalida != null) archivoSalida.delete();
        }
    }
}