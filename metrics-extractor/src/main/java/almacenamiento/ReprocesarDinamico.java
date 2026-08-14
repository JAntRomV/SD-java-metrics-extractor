package almacenamiento;

import org.bson.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

//-----> Herramienta puntual para dinamica
public class ReprocesarDinamico {

    //-----> Ejecuta reproceso dinamico
    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);

        String idRepo = params.get("repo");
        if (idRepo == null) {
            System.err.println("Uso: java -cp ... almacenamiento.ReprocesarDinamico --repo:owner/nombre [--clones:repos_clonados] [--salida:resultados]");
            return;
        }

        String carpetaClones = params.getOrDefault("clones", "repos_clonados");
        String carpetaResultadosBase = params.getOrDefault("salida", "resultados");

        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        LectorResultados lector = new LectorResultados();

        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {

            Document repo = almacen.obtenerRepositorioPorId(idRepo);
            if (repo == null) {
                System.err.println("-----> No se encontro el repo '" + idRepo + "' en repo_catalog. Revisa que el _id sea exacto (ej. \"owner/nombre\").");
                return;
            }

            String htmlUrl = repo.getString("htmlUrl");
            String rama = repo.getString("defaultBranch");
            File carpetaRepo = null;

            System.out.println("==========================================================");
            System.out.println(" REPROCESANDO ANALISIS DINAMICO");
            System.out.println(" Repo: " + idRepo);
            System.out.println("==========================================================");

            try {
                carpetaRepo = clonarSiNoExiste(htmlUrl, rama, carpetaClones, idRepo);

                String carpetaSalida = carpetaResultadosBase + "/" + sanitizar(idRepo);
                String carpetaDinamicos = carpetaSalida + "/resultados_dinamicos";

                //-----> Ejecuta solo analisis dinamico
                System.out.println("\n----------------------------------------------------------");
                System.out.println("-----> Corriendo de nuevo el analisis dinamico (Fase 1 + Fase 2)...");
                System.out.println("----------------------------------------------------------");
                dinamica.EjecutorCompleto.main(new String[]{
                        "--proyecto:" + carpetaRepo.getAbsolutePath(),
                        "--salida:" + carpetaDinamicos
                });

                System.out.println("\n-----> Borrando documentos dinamicos ANTERIORES de Mongo para: " + idRepo
                        + " (para no mezclar la numeracion de 'parte' vieja con la nueva)");
                almacen.borrarSoloDinamicas(idRepo);

                //-----> Resube metricas dinamicas
                int documentosDinamicosSubidos = lector.procesarDinamicosPorClaseUnaAUna(
                        new File(carpetaDinamicos),
                        dinamicoDoc -> almacen.agregarDinamicoAMetricas(idRepo, dinamicoDoc)
                );

                if (documentosDinamicosSubidos == 0) {
                    System.err.println("-----> " + idRepo + ": el analisis dinamico no genero Benchmarks.csv ni cronometro_caminos.csv en: " + carpetaDinamicos);
                    return;
                }

                almacen.actualizarEstadoParcial(idRepo, "dynamic", "complete");

                System.out.println("\n==========================================================");
                System.out.println(" REPROCESO DINAMICO FINALIZADO CON EXITO");
                System.out.println(" Documentos dinamicos subidos (por clase/parte): " + documentosDinamicosSubidos);
                System.out.println("==========================================================");

            } finally {
                if (carpetaRepo != null) {
                    System.out.println("-----> Liberando espacio: borrando clon de " + idRepo);
                    borrarRecursivo(carpetaRepo);
                }
            }
        }
    }

    //-----> Clona repositorio si falta
    private static File clonarSiNoExiste(String htmlUrl, String rama, String carpetaClones, String idRepo) throws Exception {
        File destino = new File(carpetaClones, sanitizar(idRepo));
        File marcadorGit = new File(destino, ".git");

        if (destino.exists() && marcadorGit.exists()) {
            System.out.println("-----> Ya estaba clonado, se reusa: " + destino.getAbsolutePath());
            return destino;
        }

        if (destino.exists() && !marcadorGit.exists()) {
            System.out.println("-----> Se encontro un clon incompleto/roto, se borra y se vuelve a clonar: " + destino.getAbsolutePath());
            borrarRecursivo(destino);
        }

        new File(carpetaClones).mkdirs();

        String[] comando = (rama != null && !rama.isBlank())
                ? new String[]{"git", "clone", "--depth", "1", "--branch", rama, htmlUrl, destino.getAbsolutePath()}
                : new String[]{"git", "clone", "--depth", "1", htmlUrl, destino.getAbsolutePath()};

        System.out.println("-----> Clonando: " + String.join(" ", comando));

        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);
        Process proceso = pb.start();

        //-----> Cierra entrada estandar git
        proceso.getOutputStream().close();

        //-----> Hilo para leer log git
        Thread hiloLector = new Thread(() -> {
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println("   [git] " + linea);
                }
            } catch (Exception ignorado) {
                //-----> Proceso interrumpido por timeout
            }
        });
        hiloLector.setDaemon(true);
        hiloLector.start();

        boolean termino = proceso.waitFor(5, TimeUnit.MINUTES);
        if (!termino) {
            proceso.destroyForcibly();
            hiloLector.join(3000);
            borrarRecursivo(destino);
            throw new IllegalStateException("git clone tardo mas de 5 minutos, se cancelo: " + htmlUrl);
        }
        hiloLector.join(3000);

        if (proceso.exitValue() != 0) {
            borrarRecursivo(destino);
            throw new IllegalStateException("git clone fallo (codigo " + proceso.exitValue() + "): " + htmlUrl);
        }

        return destino;
    }

    //-----> Elimina carpetas recursivamente
    private static void borrarRecursivo(File carpeta) {
        if (!carpeta.exists()) return;
        try (Stream<java.nio.file.Path> flujo = Files.walk(carpeta.toPath())) {
            flujo.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception e) {
                    System.err.println("-----> No se pudo borrar: " + p + " (" + e.getMessage() + ")");
                }
            });
        } catch (Exception e) {
            System.err.println("-----> Error limpiando " + carpeta + ": " + e.getMessage());
        }
    }

    //-----> Sanitiza nombres de carpetas
    private static String sanitizar(String s) {
        return (s == null || s.isEmpty()) ? "sin_nombre" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    //-----> Parsea parametros de entrada
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split(":", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
    }
}