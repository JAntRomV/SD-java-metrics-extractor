package dinamica;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

//-----> Modifica el código fuente (.java) insertando llamadas a RegistradorTiempos.marcar(...) en cada línea
public class InstrumentadorCaminos {

    // Permite analizar construcciones modernas de código Java
    static {
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    // Lee el archivo .java original e inserta cronómetros en las instrucciones del método objetivo
    public String instrumentar(String rutaArchivoOriginal, String nombreMetodoObjetivo, String carpetaSalida) throws Exception {
        File archivoOriginal = new File(rutaArchivoOriginal);
        CompilationUnit cu = StaticJavaParser.parse(archivoOriginal);

        String nombreClase = cu.getPrimaryTypeName().orElseThrow(() ->
                new IllegalArgumentException("No se pudo determinar el nombre de la clase en: " + rutaArchivoOriginal));

        String paquete = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String claseCompleta = paquete.isEmpty() ? nombreClase : paquete + "." + nombreClase;

        boolean metodoObjetivoEncontrado = false;

        List<MethodDeclaration> metodos = cu.findAll(MethodDeclaration.class);
        for (MethodDeclaration metodo : metodos) {
            if (metodo.getParameters().isEmpty() && metodo.getBody().isPresent()) {
                instrumentarMetodo(metodo, claseCompleta);
                if (metodo.getNameAsString().equals(nombreMetodoObjetivo)) {
                    metodoObjetivoEncontrado = true;
                }
            }
        }

        // Valida que el método no sea autogenerado (ej. Lombok)
        if (!metodoObjetivoEncontrado) {
            throw new IllegalStateException(
                    "El metodo '" + nombreMetodoObjetivo + "' no tiene codigo fuente explicito en " +
                    rutaArchivoOriginal + " (probablemente generado por Lombok u otro procesador de " +
                    "anotaciones, ej. @Data/@Getter/@ToString) -- no se puede cronometrar por camino.");
        }

        File carpeta = new File(carpetaSalida);
        carpeta.mkdirs();
        File archivoInstrumentado = new File(carpeta, nombreClase + ".java");
        Files.writeString(archivoInstrumentado.toPath(), cu.toString(), StandardCharsets.UTF_8);

        return archivoInstrumentado.getAbsolutePath();
    }

    // Modifica cada sentencia e inyecta la instrucción de marcado antes de ejecutarla
    private void instrumentarMetodo(MethodDeclaration metodo, String claseCompleta) {
        BlockStmt cuerpo = metodo.getBody().get();
        List<Statement> instrucciones = cuerpo.findAll(Statement.class);

        int numeroInstruccion = 1;
        for (Statement instruccion : instrucciones) {
            if (instruccion instanceof BlockStmt) continue;

            boolean esPrimeraInstruccion = (numeroInstruccion == 1);
            String etiqueta = "INSTR-" + numeroInstruccion;

            BlockStmt bloque = new BlockStmt();
            bloque.addStatement(crearLlamadaMarcar(etiqueta, esPrimeraInstruccion));

            instruccion.replace(bloque);
            bloque.addStatement(instruccion);

            numeroInstruccion++;
        }
    }

    // Crea sintácticamente el código dinamica.RegistradorTiempos.marcar("INSTR-X", boolean)
    private ExpressionStmt crearLlamadaMarcar(String etiqueta, boolean esNuevaIteracion) {
        MethodCallExpr llamada = new MethodCallExpr();
        llamada.setScope(StaticJavaParser.parseExpression("dinamica.RegistradorTiempos"));
        llamada.setName("marcar");

        NodeList<Expression> args = new NodeList<>();
        args.add(StaticJavaParser.parseExpression("\"" + etiqueta + "\""));
        args.add(StaticJavaParser.parseExpression(String.valueOf(esNuevaIteracion)));
        llamada.setArguments(args);

        return new ExpressionStmt(llamada);
    }
}