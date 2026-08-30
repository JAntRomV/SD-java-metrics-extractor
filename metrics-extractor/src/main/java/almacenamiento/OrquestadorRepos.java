package almacenamiento;

import integracion.AnalizadorUnificado;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

//-----> Procesa y analiza lotes de repositorios
public class OrquestadorRepos {

    //-----> Lee argumentos y arranca lote
    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);
        ejecutarLote(params);
    }

    //-----> Corre el flujo de mineria completo
    public static void ejecutarLote(Map<String, String> params) throws Exception {

        String carpetaClones = params.getOrDefault("clones", "repos_clonados");
        String carpetaResultadosBase = params.getOrDefault("salida", "resultados");
        Integer limite = params.containsKey("limite") ? Integer.parseInt(params.get("limite")) : null;
        String repoUnico = params.get("repo");

        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();
        LectorResultados lector = new LectorResultados();

        try (AlmacenMetricasMongo almacen = new AlmacenMetricasMongo(config)) {

            List<Document> pendientes;

            if (repoUnico != null && !repoUnico.isBlank()) {
                Document repoEncontrado = almacen.obtenerRepositorioPorId(repoUnico);
                if (repoEncontrado == null) {
                    throw new IllegalArgumentException(
                            "No se encontro el repo '" + repoUnico + "' en el catalogo "
                            + "(revisa que el _id sea EXACTO, incluyendo mayusculas/minusculas "
                            + "y sin espacios de mas -copialo tal cual de la lista de repos-).");
                }
                pendientes = new ArrayList<>();
                pendientes.add(repoEncontrado);
            } else {
                pendientes = almacen.obtenerRepositoriosPendientes();

                if (limite != null && limite < pendientes.size()) {
                    pendientes = pendientes.subList(0, limite);
                }
            }

            System.out.println("==========================================================");
            System.out.println(" REPOS A PROCESAR: " + pendientes.size()
                    + (repoUnico != null ? " (modo: un solo repo -> " + repoUnico + ")"
                       : (limite != null ? " (limitado con --limite:" + limite + ")" : "")));
            System.out.println("==========================================================");

            int exitosos = 0;
            int fallidos = 0;
            int soloEstaticos = 0;

            for (Document repo : pendientes) {
                String idRepo = repo.getString("_id");
                String htmlUrl = repo.getString("htmlUrl");
                String rama = repo.getString("defaultBranch");
                File carpetaRepo = null;

                //-----> Reinicia el progreso visible para este repo
                EstadoAnalisis.iniciarRepo(idRepo);

                System.out.println("\n----------------------------------------------------------");
                System.out.println("-----> Procesando: " + idRepo);
                System.out.println("----------------------------------------------------------");

                try {
                    carpetaRepo = clonarSiNoExiste(htmlUrl, rama, carpetaClones, idRepo);

                    String carpetaSalida = carpetaResultadosBase + "/" + sanitizar(idRepo);
                    String carpetaEstaticos = carpetaSalida + "/resultados_estaticos/" + sanitizar(carpetaRepo.getName());
                    String carpetaDinamicos = carpetaSalida + "/resultados_dinamicos";

                    //-----> Marca el inicio de la fase estatica
                    EstadoAnalisis.marcarFase("estatica", EstadoAnalisis.EstadoFase.EN_PROGRESO);

                    AnalizadorUnificado.main(new String[]{
                            "--proyecto:" + carpetaRepo.getAbsolutePath(),
                            "--salida:" + carpetaSalida
                    });

                    almacen.inicializarMetricasVacias(idRepo);

                    int clasesSubidas = lector.procesarClasesUnaAUna(
                            new File(carpetaEstaticos),
                            claseDoc -> almacen.agregarClaseAMetricas(idRepo, claseDoc),
                            caminoParteDoc -> almacen.agregarCaminosAMetricas(idRepo, caminoParteDoc)
                    );

                    if (clasesSubidas == 0) {
                        String detalle = "Se leyeron 0 clases desde: " + carpetaEstaticos
                                + " (revisar si la ruta coincide con la carpeta real generada)";
                        System.err.println("-----> " + idRepo + ": " + detalle);
                        //-----> Estatica fallo: benchmarks y caminos quedan como omitidas
                        EstadoAnalisis.marcarFase("estatica", EstadoAnalisis.EstadoFase.FALLIDA);
                        EstadoAnalisis.omitirRestantesDesdeDe("estatica");
                        almacen.guardarMetricas(idRepo, new Document("error", detalle), "metrics_failed");
                        fallidos++;
                        continue;
                    }

                    System.out.println("-----> " + idRepo + ": " + clasesSubidas + " clase(s) subidas.");
                    almacen.actualizarEstadoParcial(idRepo, "static", "complete");
                    //-----> Estatica completada (benchmarks/caminos ya se marcaron
                    //-----> dentro de EjecutorCompleto, llamado arriba via AnalizadorUnificado)
                    EstadoAnalisis.marcarFase("estatica", EstadoAnalisis.EstadoFase.COMPLETADA);

                    int documentosDinamicosSubidos = lector.procesarDinamicosPorClaseUnaAUna(
                            new File(carpetaDinamicos),
                            dinamicoDoc -> almacen.agregarDinamicoAMetricas(idRepo, dinamicoDoc)
                    );

                    if (documentosDinamicosSubidos == 0) {
                        String razon = "El analisis dinamico no genero Benchmarks.csv ni cronometro_caminos.csv en: "
                                + carpetaDinamicos + " (posible catalogo de metodos vacio, sin metodos aprobados, "
                                + "o fallo de compilacion del proyecto; revisar _escaneo_resumen.txt, "
                                + "_clases_descartadas.log y _caminos_resumen.txt dentro de esa carpeta)";
                        System.err.println("-----> " + idRepo + ": " + razon);
                        almacen.marcarSoloEstaticoCompleto(idRepo, razon);
                        soloEstaticos++;
                        continue;
                    }

                    System.out.println("-----> " + idRepo + ": " + documentosDinamicosSubidos + " documento(s) dinamico(s) subidos (por clase/parte).");
                    almacen.actualizarEstadoParcial(idRepo, "dynamic", "complete");

                    almacen.finalizarMetricas(idRepo, clasesSubidas, "metrics_complete");
                    exitosos++;

                    System.out.println("-----> " + idRepo + ": metricas completas guardadas en Mongo.");

                    double mbUsados = almacen.obtenerTamanoColeccionEnMB();
                    System.out.printf("-----> Espacio usado en repo_catalog hasta ahora: %.2f MB%n", mbUsados);

                } catch (Throwable e) {
                    System.err.println("-----> " + idRepo + ": fallo al procesar -> " + e.getMessage());
                    //-----> Un fallo inesperado marca la fase en progreso como fallida
                    //-----> y omite las que faltaban
                    EstadoAnalisis.marcarFallaGeneral();
                    try {
                        almacen.guardarMetricas(idRepo, new Document("error", String.valueOf(e.getMessage())), "metrics_failed");
                    } catch (Throwable errorSecundario) {
                        System.err.println("-----> " + idRepo + ": ademas fallo al registrar el error en Mongo -> " + errorSecundario.getMessage());
                    }
                    fallidos++;
                } finally {
                    if (carpetaRepo != null) {
                        System.out.println("-----> Liberando espacio: borrando clon de " + idRepo);
                        borrarRecursivo(carpetaRepo);
                    }
                }
            }

            System.out.println("\n==========================================================");
            System.out.println(" PROCESO FINALIZADO");
            System.out.println(" Exitosos: " + exitosos + "  |  Solo estaticos: " + soloEstaticos + "  |  Fallidos: " + fallidos);
            System.out.printf(" Espacio total usado en repo_catalog: %.2f MB%n", almacen.obtenerTamanoColeccionEnMB());
            System.out.println("==========================================================");
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

        proceso.getOutputStream().close();

        Thread hiloLector = new Thread(() -> {
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    System.out.println("   [git] " + linea);
                }
            } catch (Exception ignorado) {
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

    //-----> Reemplaza caracteres invalidos por guiones
    private static String sanitizar(String s) {
        return (s == null || s.isEmpty()) ? "sin_nombre" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    //-----> Convierte arreglo de argumentos en Map
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