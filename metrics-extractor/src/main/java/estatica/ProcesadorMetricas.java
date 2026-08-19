package estatica;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// ----> Esta es la clase motor o principal del proceso: busca las carpetas, analiza archivo por archivo .java y coordina el análisis estático
public class ProcesadorMetricas {

    // ----> Inicia el análisis recorriendo múltiples proyectos dentro de una carpeta contenedora
    public void iniciarAnalisis(String carpetaCodigoFuente, String carpetaResultados) {
        File directorioProyecto = new File(carpetaCodigoFuente);

        if (!directorioProyecto.exists() || !directorioProyecto.isDirectory()) {
            System.err.println("Error: La carpeta contenedora no existe: " + directorioProyecto.getAbsolutePath());
            return;
        }

        File[] proyectos = directorioProyecto.listFiles(File::isDirectory);
        if (proyectos == null || proyectos.length == 0) {
            System.out.println("No se encontraron subcarpetas de proyectos en: " + carpetaCodigoFuente);
            return;
        }

        JavaParser parserConfigurado = crearParserModerno();

        for (File proyectoActual : proyectos) {
            analizarProyecto(proyectoActual, carpetaResultados, parserConfigurado);
        }
    }

    // ----> Permite analizar un único proyecto directamente pasando su ruta
    public void analizarUnProyecto(String rutaProyecto, String carpetaResultados) {
        File proyectoActual = new File(rutaProyecto);

        if (!proyectoActual.exists() || !proyectoActual.isDirectory()) {
            System.err.println("Error: la carpeta del proyecto no existe: " + proyectoActual.getAbsolutePath());
            return;
        }

        analizarProyecto(proyectoActual, carpetaResultados, crearParserModerno());
    }

    // ----> Configura JavaParser con la versión más reciente de Java para poder leer código moderno
    private JavaParser crearParserModerno() {
        ParserConfiguration configLocal = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        return new JavaParser(configLocal);
    }

    // ----> Procesa todos los archivos .java de un proyecto en particular
    private void analizarProyecto(File proyectoActual, String carpetaResultados, JavaParser parserConfigurado) {
        String nombreDelProyectoFound = proyectoActual.getName();

        ContadorProyecto contadorActual = new ContadorProyecto();

        System.out.println("\n-> Analizando proyecto: " + nombreDelProyectoFound.toUpperCase());

        // ----> 🔌 MODIFICADO: antes aqui se creaba UN SOLO ProjectMetrics
        // ----> ("reporteGlobal") que acumulaba las metricas de TODAS las clases
        // ----> del proyecto hasta el final (ver MetricsExporter.export() mas
        // ----> abajo, que ya no se llama asi). En repos grandes (~4000 archivos)
        // ----> eso hacia crecer la memoria sin limite y tumbaba el proceso por
        // ----> OOM del contenedor antes de alcanzar a exportar nada. Ahora el
        // ----> analizador se reinicia con un ProjectMetrics chiquito (una sola
        // ----> clase) DENTRO del loop, y cada clase se exporta y se descarta de
        // ----> inmediato -ver reporteArchivo mas abajo-.
        MetricsAnalyzer analizador = new MetricsAnalyzer(new ProjectMetrics(nombreDelProyectoFound));

        List<String> archivosExitosos = new ArrayList<>();
        List<String> archivosSaltados = new ArrayList<>();
        int totalArchivosJsonGenerados = 0; // ----> 🔌 NUEVO: cuenta cuantos JSON de metricas se generaron en total

        try {
            // ----> Busca todos los archivos que terminen en .java dentro de las subcarpetas
            List<File> archivosJava = new ArrayList<>();
            buscarArchivosJava(proyectoActual, archivosJava);

            if (archivosJava.isEmpty()) {
                System.out.println("   [Aviso] No hay archivos .java en: " + nombreDelProyectoFound);
                return;
            }

            // ----> Recorre cada archivo .java encontrado
            for (File archivo : archivosJava) {
                try {
                    analizador.setCurrentFileName(archivo.getName());

                    // ----> 🔌 NUEVO: reporte "chiquito", propio de este archivo. Aqui es
                    // ----> donde MetricsAnalyzer va a ir guardando las metricas de esta
                    // ----> clase (y de sus sub-metodos podados) EN VEZ de guardarlas en
                    // ----> un reporte compartido de todo el proyecto.
                    ProjectMetrics reporteArchivo = new ProjectMetrics(nombreDelProyectoFound);
                    analizador.reiniciarReporte(reporteArchivo);

                    // ----> Lee el archivo y genera el árbol sintáctico (AST)
                    ParseResult<CompilationUnit> resultado = parserConfigurado.parse(archivo);

                    if (!resultado.isSuccessful()) {
                        throw new ParseProblemException(resultado.getProblems());
                    }

                    CompilationUnit cu = resultado.getResult().get();

                    // ----> Remueve sub-métodos anidados para analizarlos por separado
                    int metodosAnidadosPodados = extraerYPodarSubmetodos(cu, analizador);

                    // ----> Ejecuta el analizador de métricas
                    analizador.visit(cu, null);

                    // ----> 🔌 NUEVO: exporta el JSON de metricas de ESTA clase de
                    // ----> inmediato (igual que ya se hacia con el JSON de caminos un
                    // ----> poco mas abajo), en vez de esperar a tener todo el proyecto
                    // ----> acumulado. Asi "reporteArchivo" puede liberarse de memoria
                    // ----> tan pronto termina esta vuelta del for -no se queda vivo
                    // ----> hasta el final del proyecto completo-.
                    totalArchivosJsonGenerados += MetricsExporter.exportarProyecto(reporteArchivo, carpetaResultados);

                    // ----> Extrae los caminos del AST y genera su archivo JSON
                    ArbolCaminoExtractor extractorArbol = new ArbolCaminoExtractor();
                    ArbolCaminoExtractor.ResultadoClase resultadoArbol = extractorArbol.procesarClase(cu, archivo.getName());
                    MetricsExporter.writeArbolCaminosJson(archivo.getName(), nombreDelProyectoFound, carpetaResultados, resultadoArbol);

                    List<com.github.javaparser.ast.body.MethodDeclaration> metodosDeLaClase = cu
                            .findAll(com.github.javaparser.ast.body.MethodDeclaration.class);

                    contadorActual.registrarPesoArchivo(archivo);

                    contadorActual.clasesTotales++;
                    int totalMetodosDeLaClase = metodosDeLaClase.size() + metodosAnidadosPodados;

                    contadorActual.metodosTotalesProyecto += totalMetodosDeLaClase;
                    contadorActual.reporteMetodosPorClase.add("   --> Clase: " + archivo.getName() + " tiene: " + totalMetodosDeLaClase + " métodos.");

                    archivosExitosos.add(archivo.getName());

                } catch (ParseProblemException e) {
                    archivosSaltados.add(archivo.getName() + " (Estructuras sintácticas de Java moderno no soportadas)");
                } catch (Exception e) {
                    archivosSaltados.add(archivo.getName() + " (Error general de lectura/procesamiento)");
                }
            }

            // ----> 🔌 MODIFICADO: ya NO se exporta nada aqui al final -cada clase ya
            // ----> se exporto de inmediato dentro del for, ver "totalArchivosJsonGenerados"
            // ----> mas arriba-. Solo se imprime el mismo mensaje resumen que antes
            // ----> generaba MetricsExporter.export(), pero usando el contador que se
            // ----> fue acumulando archivo por archivo.
            System.out.println("Se genero: " + totalArchivosJsonGenerados
                    + " archivo(s) con exito en: " + new File(carpetaResultados).getAbsolutePath());

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
            System.err.println("Ocurrió un error crítico durante el análisis del proyecto " + nombreDelProyectoFound + ": "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----> Función recursiva para buscar archivos .java entrando a todas las subcarpetas del proyecto
    // ----> 🔌 MODIFICADO: ademas del filtro por nombre de archivo (*Test.java,
    // ----> *Tests.java, *IT.java), ahora NO ENTRA a carpetas llamadas "test"
    // ----> (el layout estandar de Maven/Gradle: src/test/java/...). Esto es
    // ----> necesario porque el filtro por nombre solo cubre la convencion de
    // ----> SUFIJO (ClaseTest.java) -en repos que usan la convencion de
    // ----> PREFIJO (TestClase.java, comun en estilo JUnit3) o que tienen
    // ----> clases de apoyo para test (MockAlgo.java) esos archivos NO
    // ----> terminaban filtrandose y se seguian analizando por completo. Podar
    // ----> la carpeta entera es ademas mas eficiente: ni siquiera se listan
    // ----> sus archivos, en vez de listarlos y descartarlos uno por uno.
    private void buscarArchivosJava(File directorio, List<File> listaArchivos) {
        if (esCarpetaDeTest(directorio)) {
            return;
        }

        File[] archivosYCarpetas = directorio.listFiles();
        if (archivosYCarpetas != null) {
            for (File elemento : archivosYCarpetas) {
                if (elemento.isDirectory()) {
                    buscarArchivosJava(elemento, listaArchivos);
                } else if (elemento.getName().endsWith(".java") && !esArchivoDeTest(elemento.getName())) {
                    listaArchivos.add(elemento);
                }
            }
        }
    }

    // ----> 🔌 NUEVO: detecta carpetas raiz de codigo de test segun la
    // ----> convencion estandar de Maven/Gradle (src/test/java, src/test/kotlin,
    // ----> src/testFixtures/..., etc.) para podar toda la subrama de una vez.
    private boolean esCarpetaDeTest(File directorio) {
        String nombre = directorio.getName().toLowerCase();
        return nombre.equals("test")
                || nombre.equals("tests")
                || nombre.equals("testfixtures")
                || nombre.equals("androidtest");
    }

    // ----> 🔌 NUEVO: detecta si un nombre de archivo corresponde a una clase de test
    private boolean esArchivoDeTest(String nombreArchivo) {
        return nombreArchivo.endsWith("Test.java")
                || nombreArchivo.endsWith("Tests.java")
                || nombreArchivo.endsWith("IT.java");
    }

    // ----> Busca métodos definidos dentro de otros métodos (sub-métodos), los extrae y ajusta el conteo de líneas
    private int extraerYPodarSubmetodos(CompilationUnit cu, MetricsAnalyzer analizador) {
        int totalSubmetodosPodados = 0;

        List<com.github.javaparser.ast.body.MethodDeclaration> todosLosMetodos = cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class);

        for (com.github.javaparser.ast.body.MethodDeclaration metodoPadre : todosLosMetodos) {
            if (metodoPadre.getBody().isPresent()) {
                List<com.github.javaparser.ast.body.MethodDeclaration> internos = metodoPadre.getBody().get().findAll(com.github.javaparser.ast.body.MethodDeclaration.class);

                for (com.github.javaparser.ast.body.MethodDeclaration submetodo : internos) {
                    totalSubmetodosPodados++;

                    String nombreClase = submetodo.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                            .map(c -> c.getNameAsString())
                            .orElse("Unknown");

                    // ----> Analiza el sub-método cortado como un método independiente
                    analizador.analizarMetodoSuelto(submetodo, nombreClase);

                    // ----> Guarda las líneas restadas para no sumar doble en el método padre
                    if (submetodo.getBegin().isPresent() && submetodo.getEnd().isPresent()) {
                        int lineasSubmetodo = submetodo.getEnd().get().line - submetodo.getBegin().get().line + 1;

                        int lineasPrevias = metodoPadre.containsData(MetricsAnalyzer.LINEAS_PODADAS)
                                ? metodoPadre.getData(MetricsAnalyzer.LINEAS_PODADAS)
                                : 0;

                        metodoPadre.setData(MetricsAnalyzer.LINEAS_PODADAS, lineasPrevias + lineasSubmetodo);
                    }

                    // ----> Elimina el sub-método del cuerpo del padre
                    submetodo.remove();
                }
            }
        }
        return totalSubmetodosPodados;
    }
}