package dinamica;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

//-----> Inserta instrucciones de monitoreo (marcar) antes de cada sentencia JavaParser
public class InstrumentadorCaminos {

    static {
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
    }

    //-----> Lee un archivo Java, localiza el método objetivo y le injecta llamadas de rastreo
    public String instrumentar(String rutaArchivoOriginal, String nombreMetodoObjetivo, String carpetaSalida) throws Exception {
        File archivoOriginal = new File(rutaArchivoOriginal);
        CompilationUnit cu = StaticJavaParser.parse(archivoOriginal);

        String nombreClase = cu.getPrimaryTypeName().orElseThrow(() ->
                new IllegalArgumentException("No se pudo determinar el nombre de la clase en: " + rutaArchivoOriginal));

        String paquete = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String claseCompleta = paquete.isEmpty() ? nombreClase : paquete + "." + nombreClase;

        boolean metodoObjetivoEncontrado = false;

        //-----> Busca el metodo exacto sin parametros
        List<MethodDeclaration> metodos = cu.findAll(MethodDeclaration.class);
        for (MethodDeclaration metodo : metodos) {
            if (metodo.getNameAsString().equals(nombreMetodoObjetivo)
                    && metodo.getParameters().isEmpty()
                    && metodo.getBody().isPresent()) {
                instrumentarMetodo(metodo, claseCompleta);
                metodoObjetivoEncontrado = true;
                break;
            }
        }

        if (!metodoObjetivoEncontrado) {
            throw new IllegalStateException(
                    "El metodo '" + nombreMetodoObjetivo + "' no tiene codigo fuente explicito en " +
                    rutaArchivoOriginal + " (probablemente generado por Lombok u otro procesador de " +
                    "anotaciones, ej. @Data/@Getter/@ToString) -- no se puede cronometrar por camino.");
        }

        //-----> Guarda el archivo modificado con la instrumentacion
        File carpeta = new File(carpetaSalida);
        carpeta.mkdirs();
        File archivoInstrumentado = new File(carpeta, nombreClase + ".java");
        Files.writeString(archivoInstrumentado.toPath(), cu.toString(), StandardCharsets.UTF_8);

        return archivoInstrumentado.getAbsolutePath();
    }

    //-----> Recorre las instrucciones del método e intercala la funcion de registro de tiempo
    private void instrumentarMetodo(MethodDeclaration metodo, String claseCompleta) {
        BlockStmt cuerpo = metodo.getBody().get();
        List<Statement> instrucciones = cuerpo.findAll(Statement.class).stream()
                .filter(s -> !estaDentroDeLambda(s))
                .collect(Collectors.toList());

        int numeroInstruccion = 1;
        for (Statement instruccion : instrucciones) {
            if (instruccion instanceof BlockStmt) continue;

            boolean esPrimeraInstruccion = (numeroInstruccion == 1);
            String etiqueta = "INSTR-" + numeroInstruccion;

            //-----> Encapsula la sentencia actual en un bloque agregando 'RegistradorTiempos.marcar()' antes
            BlockStmt bloque = new BlockStmt();
            bloque.addStatement(crearLlamadaMarcar(etiqueta, esPrimeraInstruccion));

            instruccion.replace(bloque);
            bloque.addStatement(instruccion);

            numeroInstruccion++;
        }
    }

    //-----> Verifica si un nodo del AST esta contenido dentro de una expresion Lambda
    private boolean estaDentroDeLambda(Node nodo) {
        Optional<Node> padre = nodo.getParentNode();
        while (padre.isPresent()) {
            if (padre.get() instanceof LambdaExpr) {
                return true;
            }
            padre = padre.get().getParentNode();
        }
        return false;
    }

    //-----> Genera sintacticamente la sentencia 'RegistradorTiempos.marcar("INSTR-X", boolean)'
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