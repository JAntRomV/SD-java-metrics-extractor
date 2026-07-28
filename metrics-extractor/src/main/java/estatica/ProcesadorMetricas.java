package estatica;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// -----> Clase principal que busca todos los proyectos, lee sus archivos .java y manda a calcular sus metricas.
public class ProcesadorMetricas {

    // -----> Metodo para procesar una carpeta que contiene VARIAS subcarpetas (proyectos en lote).
    public void iniciarAnalisis(String carpetaCodigoFuente, String carpetaResultados) {
        File directorioProyecto = new File(carpetaCodigoFuente);

        // -----> Valida que la carpeta recibida por parametro exista y sea un directorio.
        if (!directorioProyecto.exists() || !directorioProyecto.isDirectory()) {
            System.err.println("Error: La carpeta contenedora no existe: " + directorioProyecto.getAbsolutePath());
            return;
        }

        // -----> Listamos todas las subcarpetas (cada una representa un proyecto individual).
        File[] proyectos = directorioProyecto.listFiles(File::isDirectory);
        if (proyectos == null || proyectos.length == 0) {
            System.out.println("No se encontraron subcarpetas de proyectos en: " + carpetaCodigoFuente);
            return;
        }

        // -----> Crea la instancia del parser de Java con la configuracion actualizada.
        JavaParser parserConfigurado = crearParserModerno();

        // -----> Se procesa proyecto por proyecto.
        for (File proyectoActual : proyectos) {
            analizarProyecto(proyectoActual, carpetaResultados, parserConfigurado);
        }
    }

    // -----> NUEVO (Issue 3 - integracion de modulos): analiza UN SOLO proyecto
    // -----> externo directamente, sin asumir que "carpetaCodigoFuente" contiene
    // -----> VARIAS subcarpetas de proyectos distintos (que es lo que hace
    // -----> iniciarAnalisis). Esto es lo que usa el punto de entrada unificado
    // -----> (integracion.AnalizadorUnificado), que recibe la MISMA carpeta raiz
    // -----> de proyecto que ya usa dinamica.EjecutorCompleto con --proyecto -si
    // -----> se le pasara esa ruta a iniciarAnalisis(), trataria por error cada
    // -----> subcarpeta interna del proyecto (src, target, etc.) como si fuera un
    // -----> proyecto distinto.
    public void analizarUnProyecto(String rutaProyecto, String carpetaResultados) {
        File proyectoActual = new File(rutaProyecto);

        // -----> Verifica que la carpeta raiz del proyecto exista en el sistema.
        if (!proyectoActual.exists() || !proyectoActual.isDirectory()) {
            System.err.println("Error: la carpeta del proyecto no existe: " + proyectoActual.getAbsolutePath());
            return;
        }

        // -----> Llama al analisis directo sobre el proyecto recibido.
        analizarProyecto(proyectoActual, carpetaResultados, crearParserModerno());
    }

    // -----> Configuracion del parser para que entienda codigo moderno de Java sin romperse.
    private JavaParser crearParserModerno() {
        ParserConfiguration configLocal = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        return new JavaParser(configLocal);
    }

    // -----> Contiene TODA la logica que antes vivia directamente dentro del "for"
    // -----> de iniciarAnalisis(). Se extrajo a su propio metodo para poder
    // -----> reutilizarla tanto en modo lote (varios proyectos) como en modo
    // -----> individual (analizarUnProyecto) sin duplicar codigo.
    private void analizarProyecto(File proyectoActual, String carpetaResultados, JavaParser parserConfigurado) {
        String nombreDelProyectoFound = proyectoActual.getName();

        // -----> Este bloque le pertenece a: Contador Proyecto (lleva métricas acumuladas del proyecto).
        ContadorProyecto contadorActual = new ContadorProyecto();

        System.out.println("\n-> Analizando proyecto: " + nombreDelProyectoFound.toUpperCase());

        // -----> Aquí se guardará todo el reporte (Limpio para cada proyecto).
        ProjectMetrics reporteGlobal = new ProjectMetrics(nombreDelProyectoFound);

        // -----> Se crea nuestro analizador encargado de visitar las estructuras del codigo.
        MetricsAnalyzer analizador = new MetricsAnalyzer(reporteGlobal);

        // -----> Listas creadas para llevar el conteo de que archivos pasaron bien y cuales dieron error.
        List<String> archivosExitosos = new ArrayList<>();
        List<String> archivosSaltados = new ArrayList<>();

        try {
            // -----> Buscamos archivos .java en la carpeta y en todas sus subcarpetas.
            List<File> archivosJava = new ArrayList<>();
            buscarArchivosJava(proyectoActual, archivosJava);

            // -----> Si no encuentra codigo .java en el proyecto, muestra un aviso y termina este analisis.
            if (archivosJava.isEmpty()) {
                System.out.println("   [Aviso] No hay archivos .java en: " + nombreDelProyectoFound);
                return;
            }

            // -----> Se recorre la lista de los archivos encontrados para analizarlos uno por uno.
            for (File archivo : archivosJava) {

                // -----> Proteccion por archivo para que si uno falla, el programa continue analizando los demas.
                try {
                    // -----> Le pasamos el nombre del archivo al analizador.
                    analizador.setCurrentFileName(archivo.getName());

                    // -----> Convierte el archivo en un arbol AST usando la configuracion moderna.
                    ParseResult<CompilationUnit> resultado = parserConfigurado.parse(archivo);

                    // -----> Si ocurrió un problema durante el parseo, lanza la excepción para atraparla en la proteccion.
                    if (!resultado.isSuccessful()) {
                        throw new ParseProblemException(resultado.getProblems());
                    }

                    CompilationUnit cu = resultado.getResult().get();

                    // -----> Poda los métodos que están declarados dentro de otro método.
                    int metodosAnidadosPodados = extraerYPodarSubmetodos(cu, analizador);

                    // -----> El analizador recorre el árbol extrayendo Halstead y el Grafo (CFG).
                    analizador.visit(cu, null);

                    // -----> Este bloque le pertenece a: Arbol (extrae caminos e información de control).
                    ArbolCaminoExtractor extractorArbol = new ArbolCaminoExtractor();
                    ArbolCaminoExtractor.ResultadoClase resultadoArbol = extractorArbol.procesarClase(cu, archivo.getName());
                    MetricsExporter.writeArbolCaminosJson(archivo.getName(), nombreDelProyectoFound, carpetaResultados, resultadoArbol);

                    // -----> Este bloque le pertenece a: CODE2SEQ.
                    // -----> Buscamos todos los métodos dentro del archivo y extraemos sus caminos.
                    List<com.github.javaparser.ast.body.MethodDeclaration> metodosDeLaClase = cu
                            .findAll(com.github.javaparser.ast.body.MethodDeclaration.class);
                    List<String> trillizosDeLaClase = new ArrayList<>();

                    // -----> Juntamos el codigo real de los metodos aplastado y sin espacios para validar al final.
                    StringBuilder originalMetodosDestilados = new StringBuilder();

                    for (com.github.javaparser.ast.body.MethodDeclaration metodo : metodosDeLaClase) {
                        trillizosDeLaClase.addAll(Code2SeqExtractor.extraerCaminosDesdeMetodo(metodo));

                        // -----> Si el metodo tiene lineas de codigo, borramos sus espacios y lo unimos al total.
                        if (metodo.getBody().isPresent()) {
                            String cuerpoLimpio = metodo.getBody().get().toString().replaceAll("[\\s{};()]+", "");
                            originalMetodosDestilados.append(cuerpoLimpio);
                        }
                    }

                    // -----> Convertimos esos trillizos en codigo reconstruido.
                    List<String> miCodigoReconstruido = Code2SeqExtractor.reconstruirCodigoDesdeCaminos(trillizosDeLaClase);

                    // -----> VALIDACIÓN EXACTA DEL CAMINO DEL TRILLIZO
                    if (!miCodigoReconstruido.isEmpty()) {
                        String originalDestilado = originalMetodosDestilados.toString();
                        boolean claseValida = true;

                        for (String lineaReconstruida : miCodigoReconstruido) {
                            String lineaDestilada = lineaReconstruida.replaceAll("[\\s{};()]+", "");

                            if (!lineaDestilada.isEmpty() && !originalDestilado.contains(lineaDestilada)) {
                                claseValida = false;
                                break;
                            }
                        }

                        if (claseValida) {
                            System.out.println(" [VALIDACIÓN] La clase " + archivo.getName()
                                    + " coincide con el código original.");
                        } else {
                            System.out.println(" [ALERTA] La clase " + archivo.getName()
                                    + " NO coincide o no es válida con el código original.");
                        }
                    } else {
                        System.out.println(" [AVISO] La clase " + archivo.getName()
                                + " no contiene trillizos suficientes para ser validada.");
                    }

                    // -----> Registramos el peso en disco del archivo para armar el TOP 10 de clases pesadas.
                    contadorActual.registrarPesoArchivo(archivo);

                    // -----> Incrementamos los contadores del reporte por consola y añadimos el desglose.
                    contadorActual.clasesTotales++;
                    // -----> El total real de métodos de esta clase incluye los que estaban anidados.
                    int totalMetodosDeLaClase = metodosDeLaClase.size() + metodosAnidadosPodados;

                    contadorActual.metodosTotalesProyecto += totalMetodosDeLaClase;
                    contadorActual.reporteMetodosPorClase.add("   --> Clase: " + archivo.getName() + " tiene: " + totalMetodosDeLaClase + " métodos.");

                    archivosExitosos.add(archivo.getName());

                } catch (ParseProblemException e) {
                    // -----> Captura errores donde el archivo .java tiene sintaxis no soportada por la versión del parser.
                    archivosSaltados.add(archivo.getName() + " (Estructuras sintácticas de Java moderno no soportadas)");
                } catch (Exception e) {
                    // -----> Captura cualquier otro error durante la lectura o procesamiento del archivo individual.
                    archivosSaltados.add(archivo.getName() + " (Error general de lectura/procesamiento)");
                }
            }

            // -----> Agrupa y exporta las métricas generadas a la carpeta de salida correspondiente.
            List<ProjectMetrics> listaReportes = new ArrayList<>();
            listaReportes.add(reporteGlobal);

            MetricsExporter.export(listaReportes, carpetaResultados);

            // -----> Imprime el resumen visual del procesamiento en la terminal.
            System.out.println("\n------------------------------------------------");
            System.out.println(" RESUMEN DE PROCESAMIENTO (" + nombreDelProyectoFound.toUpperCase() + "):");
            System.out.println("   Archivos analizados con éxito: " + archivosExitosos.size());
            System.out.println("   Archivos saltados del lote:   " + archivosSaltados.size());

            if (!archivosSaltados.isEmpty()) {
                System.out.println("\n ARCHIVOS DETECTADOS CON ERROR DE SINTAXIS / OMITIDOS:");
                for (String archivoOmitido : archivosSaltados) {
                    System.out.println("   - " + archivoOmitido);
                }
            }
            System.out.println("------------------------------------------------");

            System.out.println("===  Proceso del proyecto " + nombreDelProyectoFound + " terminado con éxito ===");
            contadorActual.mostrarReporteTerminal(nombreDelProyectoFound);

        } catch (Exception e) {
            // -----> Atrapa fallos graves que puedan detener la lectura del proyecto completo.
            System.err.println("Ocurrió un error crítico durante el análisis del proyecto " + nombreDelProyectoFound + ": "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    // -----> Explora de manera recursiva todas las carpetas buscando archivos con la extension .java.
    private void buscarArchivosJava(File directorio, List<File> listaArchivos) {
        File[] archivosYCarpetas = directorio.listFiles();
        if (archivosYCarpetas != null) {
            for (File elemento : archivosYCarpetas) {
                if (elemento.isDirectory()) {
                    buscarArchivosJava(elemento, listaArchivos);
                } else if (elemento.getName().endsWith(".java")) {
                    listaArchivos.add(elemento);
                }
            }
        }
    }

    // NUEVA: METODO DENTRO DE METODO
    // -----> Detecta métodos anidados, les saca sus propias métricas, los poda del padre y avisa cuántos encontró.
    private int extraerYPodarSubmetodos(CompilationUnit cu, MetricsAnalyzer analizador) {
        int totalSubmetodosPodados = 0;

        List<com.github.javaparser.ast.body.MethodDeclaration> todosLosMetodos = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class);

        for (com.github.javaparser.ast.body.MethodDeclaration metodoPadre : todosLosMetodos) {
            if (metodoPadre.getBody().isPresent()) {
                List<com.github.javaparser.ast.body.MethodDeclaration> internos = metodoPadre.getBody().get().findAll(com.github.javaparser.ast.body.MethodDeclaration.class);

                for (com.github.javaparser.ast.body.MethodDeclaration submetodo : internos) {
                    totalSubmetodosPodados++;

                    // -----> Identifica el nombre de la clase contenedora del submétodo.
                    String nombreClase = submetodo.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                            .map(c -> c.getNameAsString())
                            .orElse("Unknown");

                    // -----> Procesa las métricas del submétodo de manera independiente.
                    analizador.analizarMetodoSuelto(submetodo, nombreClase);

                    // -----> Resta las líneas del submétodo de las líneas contadas para el método padre.
                    if (submetodo.getBegin().isPresent() && submetodo.getEnd().isPresent()) {
                        int lineasSubmetodo = submetodo.getEnd().get().line - submetodo.getBegin().get().line + 1;

                        // -----> Guarda las líneas del anidado en el padre, para que las reste de su propio LOC.
                        int lineasPrevias = metodoPadre.containsData(MetricsAnalyzer.LINEAS_PODADAS)
                                ? metodoPadre.getData(MetricsAnalyzer.LINEAS_PODADAS)
                                : 0;

                        metodoPadre.setData(MetricsAnalyzer.LINEAS_PODADAS, lineasPrevias + lineasSubmetodo);
                    }

                    // -----> Elimina el submétodo del árbol AST para no duplicar su análisis más adelante.
                    submetodo.remove();
                }
            }
        }
        return totalSubmetodosPodados;
    }
}