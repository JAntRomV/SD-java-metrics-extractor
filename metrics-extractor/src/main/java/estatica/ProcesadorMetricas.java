package estatica;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

//-----> Orquestador del análisis de código
public class ProcesadorMetricas {

    //-----> Analiza directorio con multiples proyectos
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

    //-----> Analiza un unico proyecto especifico
    public void analizarUnProyecto(String rutaProyecto, String carpetaResultados) {
        File proyectoActual = new File(rutaProyecto);

        if (!proyectoActual.exists() || !proyectoActual.isDirectory()) {
            System.err.println("Error: la carpeta del proyecto no existe: " + proyectoActual.getAbsolutePath());
            return;
        }

        analizarProyecto(proyectoActual, carpetaResultados, crearParserModerno());
    }

    //-----> Configura JavaParser
    private JavaParser crearParserModerno() {
        ParserConfiguration configLocal = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        return new JavaParser(configLocal);
    }

    //-----> Recorre y analiza archivos .java
    private void analizarProyecto(File proyectoActual, String carpetaResultados, JavaParser parserConfigurado) {
        String nombreDelProyectoFound = proyectoActual.getName();

        ContadorProyecto contadorActual = new ContadorProyecto();

        System.out.println("\n-> Analizando proyecto: " + nombreDelProyectoFound.toUpperCase());

        MetricsAnalyzer analizador = new MetricsAnalyzer(new ProjectMetrics(nombreDelProyectoFound));

        List<String> archivosExitosos = new ArrayList<>();
        List<String> archivosSaltados = new ArrayList<>();
        int totalArchivosJsonGenerados = 0;

        try {
            List<File> archivosJava = new ArrayList<>();
            buscarArchivosJava(proyectoActual, archivosJava);

            if (archivosJava.isEmpty()) {
                System.out.println("   [Aviso] No hay archivos .java en: " + nombreDelProyectoFound);
                return;
            }

            for (File archivo : archivosJava) {
                try {
                    analizador.setCurrentFileName(archivo.getName());

                    ProjectMetrics reporteArchivo = new ProjectMetrics(nombreDelProyectoFound);
                    analizador.reiniciarReporte(reporteArchivo);

                    ParseResult<CompilationUnit> resultado = parserConfigurado.parse(archivo);

                    if (!resultado.isSuccessful()) {
                        throw new ParseProblemException(resultado.getProblems());
                    }

                    CompilationUnit cu = resultado.getResult().get();

                    int metodosAnidadosPodados = extraerYPodarSubmetodos(cu, analizador);

                    analizador.visit(cu, null);

                    totalArchivosJsonGenerados += MetricsExporter.exportarProyecto(reporteArchivo, carpetaResultados);

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

    //-----> Busqueda recursiva de archivos .java
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

    //-----> Valida si es carpeta de pruebas
    private boolean esCarpetaDeTest(File directorio) {
        String nombre = directorio.getName().toLowerCase();
        return nombre.equals("test")
                || nombre.equals("tests")
                || nombre.equals("testfixtures")
                || nombre.equals("androidtest");
    }

    //-----> Valida si es archivo de pruebas
    private boolean esArchivoDeTest(String nombreArchivo) {
        return nombreArchivo.endsWith("Test.java")
                || nombreArchivo.endsWith("Tests.java")
                || nombreArchivo.endsWith("IT.java");
    }

    //-----> Separa metodos lambda o internos
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

                    analizador.analizarMetodoSuelto(submetodo, nombreClase);

                    if (submetodo.getBegin().isPresent() && submetodo.getEnd().isPresent()) {
                        int lineasSubmetodo = submetodo.getEnd().get().line - submetodo.getBegin().get().line + 1;

                        int lineasPrevias = metodoPadre.containsData(MetricsAnalyzer.LINEAS_PODADAS)
                                ? metodoPadre.getData(MetricsAnalyzer.LINEAS_PODADAS)
                                : 0;

                        metodoPadre.setData(MetricsAnalyzer.LINEAS_PODADAS, lineasPrevias + lineasSubmetodo);
                    }

                    submetodo.remove();
                }
            }
        }
        return totalSubmetodosPodados;
    }
}