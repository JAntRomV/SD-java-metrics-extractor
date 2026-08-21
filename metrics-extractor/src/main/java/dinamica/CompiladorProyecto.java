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

//-----> Compila proyectos automáticos en Java
public class CompiladorProyecto {

    //-----> Lenguajes permitidos para buscar clases
    private static final Set<String> LENGUAJES_CONOCIDOS = Set.of("java", "kotlin", "groovy", "scala");

    //-----> Almacena la salida del proceso de compilación
    public static class ResultadoCompilacion {
        public final boolean exitoso;
        public final String carpetaClases;
        public final String mensaje;
        public final String classpathCompleto;

        //-----> Guarda las propiedades de la compilación
        public ResultadoCompilacion(boolean exitoso, String carpetaClases, String mensaje, String classpathCompleto) {
            this.exitoso = exitoso;
            this.carpetaClases = carpetaClases;
            this.mensaje = mensaje;
            this.classpathCompleto = classpathCompleto;
        }
    }

    //-----> Ejecuta compilación con classpath simple
    public static ResultadoCompilacion compilar(String rutaProyecto) throws Exception {
        return compilar(rutaProyecto, false);
    }

    //-----> Procesa la compilación según la herramienta del proyecto
    public static ResultadoCompilacion compilar(String rutaProyecto, boolean calcularClasspathCompleto) {
        try {
            File raiz = new File(rutaProyecto);

            //-----> Confirma la existencia de la carpeta
            if (!raiz.exists() || !raiz.isDirectory()) {
                return new ResultadoCompilacion(false, null, "La carpeta del proyecto no existe: " + rutaProyecto, null);
            }

            //-----> Archivos de construcción
            File pomFile = new File(raiz, "pom.xml");
            File gradleFile = new File(raiz, "build.gradle");
            File gradleKtsFile = new File(raiz, "build.gradle.kts");

            String[] comando;

            //-----> Selecciona comandos para Maven o Gradle
            if (pomFile.exists()) {
                comando = new String[]{"mvn", "compile", "-q", "-DskipTests"};
            } else if (gradleFile.exists() || gradleKtsFile.exists()) {
                File gradlew = new File(raiz, "gradlew");
                String ejecutable = gradlew.exists() ? "./gradlew" : "gradle";

                //-----> Configura ejecución liviana de Gradle con 1 worker
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

            //-----> Inicia el proceso externo
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.directory(raiz);
            pb.redirectErrorStream(true);

            //-----> 🔌 MODIFICADO: antes esto solo se aplicaba a Gradle. La rama
            //-----> de Maven ("mvn compile") no tenia NINGUN limite de memoria
            //-----> -ni MAVEN_OPTS ni nada-, asi que quedaba expuesta al mismo
            //-----> riesgo de OOM del contenedor que ya se habia resuelto para
            //-----> Gradle (el proceso "mvn" hijo tambien es una JVM completa
            //-----> compitiendo por los mismos 512MB junto con la app Spring
            //-----> Boot). Ahora se aplica siempre, sin importar la herramienta.
            limitarMemoriaProcesoHijo(pb);

            //-----> Corre la compilación con tiempo máximo
            ResultadoProceso resultadoProceso = ejecutarProceso(pb, "   [compilacion] ", 60, TimeUnit.MINUTES);

            if (!resultadoProceso.termino) {
                return new ResultadoCompilacion(false, null, "La compilacion tardo mas de 60 minutos, se cancelo.", null);
            }

            //-----> 🔌 NUEVO: si fallo especificamente por falta de memoria (no por
            //-----> cualquier otra razon, ej. error de sintaxis o dependencia
            //-----> faltante -eso reintentar no lo arregla-), se reintenta UNA sola
            //-----> vez con mas memoria en vez de rendirse de inmediato. Asi la
            //-----> gran mayoria de los repos (chicos) siguen usando el limite base
            //-----> y seguro, y solo los pocos que de verdad necesitan mas RAM para
            //-----> compilar pagan el costo del segundo intento -y solo ellos-.
            if (resultadoProceso.codigoSalida != 0 && resultadoProceso.pareceFalloDeMemoria) {
                System.out.println("-----> La compilacion fallo por falta de memoria, reintentando una vez con mas memoria (" + MEMORIA_AMPLIADA + ")...");
                limitarMemoriaProcesoHijo(pb, MEMORIA_AMPLIADA);
                resultadoProceso = ejecutarProceso(pb, "   [compilacion-reintento] ", 60, TimeUnit.MINUTES);

                if (!resultadoProceso.termino) {
                    return new ResultadoCompilacion(false, null, "La compilacion (reintento con mas memoria) tardo mas de 60 minutos, se cancelo.", null);
                }
            }

            //-----> Valida el estado final de la ejecución
            int codigoSalida = resultadoProceso.codigoSalida;
            if (codigoSalida != 0) {
                return new ResultadoCompilacion(false, null, "La compilacion fallo, codigo de salida: " + codigoSalida, null);
            }

            //-----> Ubica los compilados generados
            String carpetaClases = resolverCarpetaClases(raiz, pomFile.exists());
            if (carpetaClases == null || carpetaClases.isBlank()) {
                return new ResultadoCompilacion(false, null, "La compilacion salio bien, pero no se encontraron archivos .class compilados en " + rutaProyecto, null);
            }

            //-----> Resuelve las dependencias adicionales
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

    //-----> Estado de salida del comando
    private static class ResultadoProceso {
        final boolean termino;
        final int codigoSalida;
        //-----> 🔌 NUEVO: true si en la salida del proceso aparecio algun rastro
        //-----> de que fallo por falta de memoria (y no por otra razon, ej. error
        //-----> de sintaxis o dependencia faltante). Sirve para decidir si vale la
        //-----> pena reintentar con mas memoria, en vez de reintentar a ciegas
        //-----> cada vez que algo falla.
        final boolean pareceFalloDeMemoria;

        ResultadoProceso(boolean termino, int codigoSalida, boolean pareceFalloDeMemoria) {
            this.termino = termino;
            this.codigoSalida = codigoSalida;
            this.pareceFalloDeMemoria = pareceFalloDeMemoria;
        }
    }

    //-----> 🔌 MODIFICADO: valores base (primer intento, seguro para el
    //-----> contenedor) y ampliado (solo para el reintento cuando el primer
    //-----> intento fallo especificamente por falta de memoria). La mayoria de
    //-----> los repos del catalogo nunca tocan el valor ampliado -solo los que
    //-----> de verdad lo necesitan pagan ese costo extra, y solo una vez-.
    private static final String MEMORIA_BASE = "-Xmx160m -XX:MaxMetaspaceSize=96m";
    private static final String MEMORIA_AMPLIADA = "-Xmx320m -XX:MaxMetaspaceSize=160m";

    //-----> Ajusta variables de entorno de memoria para JVM
    private static void limitarMemoriaProcesoHijo(ProcessBuilder pb) {
        limitarMemoriaProcesoHijo(pb, MEMORIA_BASE);
    }

    private static void limitarMemoriaProcesoHijo(ProcessBuilder pb, String opcionesMemoria) {
        Map<String, String> entorno = pb.environment();
        entorno.put("GRADLE_OPTS", opcionesMemoria);
        entorno.put("MAVEN_OPTS", opcionesMemoria);
        entorno.put("JAVA_OPTS", opcionesMemoria);
    }

    //-----> 🔌 NUEVO: fragmentos de texto que delatan un fallo por falta de
    //-----> memoria en la salida de Maven/Gradle/la JVM en general.
    private static final String[] SENALES_FALLO_MEMORIA = {
            "OutOfMemoryError", "Java heap space", "GC overhead limit exceeded"
    };

    //-----> Maneja subprocesos y captura su salida
    private static ResultadoProceso ejecutarProceso(ProcessBuilder pb, String prefijoLog, long tiempoLimite, TimeUnit unidad) throws Exception {
        Process proceso = pb.start();
        proceso.getOutputStream().close();

        //-----> 🔌 NUEVO: bandera compartida con el hilo lector para detectar,
        //-----> linea por linea segun se va imprimiendo, si el proceso hijo dejo
        //-----> rastro de haberse quedado sin memoria.
        java.util.concurrent.atomic.AtomicBoolean falloDeMemoria = new java.util.concurrent.atomic.AtomicBoolean(false);

        Thread hiloLector = new Thread(() -> {
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println(prefijoLog + linea);
                    for (String senal : SENALES_FALLO_MEMORIA) {
                        if (linea.contains(senal)) {
                            falloDeMemoria.set(true);
                            break;
                        }
                    }
                }
            } catch (Exception ignorado) {
                //-----> Finalización del lector
            }
        });
        hiloLector.setDaemon(true);
        hiloLector.start();

        boolean termino = proceso.waitFor(tiempoLimite, unidad);
        if (!termino) {
            proceso.destroyForcibly();
        }
        hiloLector.join(3000);

        return new ResultadoProceso(termino, termino ? proceso.exitValue() : -1, falloDeMemoria.get());
    }
//_________________________________________________________________________________________
    //-----> Encuentra directorios de binarios compilados
    private static String resolverCarpetaClases(File raiz, boolean esMaven) {
        //-----> Ruta estándar en Maven
        if (esMaven) {
            File targetClasses = new File(raiz, "target/classes");
            if (targetClasses.exists()) return targetClasses.getAbsolutePath();
        }

        //-----> Búsqueda modular o Gradle
        List<String> raices = buscarRaicesDeModulos(raiz);
        if (!raices.isEmpty()) {
            return String.join(File.pathSeparator, raices);
        }

        //-----> Estrategia alternativa de búsqueda
        return resolverCarpetaClasesFallback(raiz);
    }

    //-----> Rastrea carpetas de clases
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
    //-----> Extrae las rutas de paquetes válidas
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
    //-----> Verifica presencia de binarios
    private static boolean contieneClases(Path carpeta) throws Exception {
        try (var stream = Files.walk(carpeta)) {
            return stream.anyMatch(p -> p.toString().endsWith(".class"));
        }
    }

    //-----> Escanea el proyecto entero buscando clases
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

    //-----> Genera la lista de dependencias con Maven
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
            limitarMemoriaProcesoHijo(pb); //-----> 🔌 NUEVO: esta invocacion de mvn no tenia limite de memoria

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

    //-----> Genera la lista de dependencias con Gradle
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

            //-----> Ejecuta tarea temporal para exportar dependencias
            String[] comando = {
                    ejecutable, "--init-script", initScript.getAbsolutePath(),
                    "-q", "--no-daemon", "--max-workers=1", "help"
            };

            System.out.println("-----> Ejecutando: " + String.join(" ", comando) + " (en " + raiz.getAbsolutePath() + ")");

            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.directory(raiz);
            pb.redirectErrorStream(true);
            limitarMemoriaProcesoHijo(pb);

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