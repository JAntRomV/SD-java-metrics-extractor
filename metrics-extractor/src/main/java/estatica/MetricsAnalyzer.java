package estatica;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.HashSet;
import java.util.Set;

// ----> Esta clase recorre todo el código fuente en Java para contar variables, palabras clave, decisiones y métodos
public class MetricsAnalyzer extends VoidVisitorAdapter<Void> {
    private final ProjectMetrics projectMetrics;

    // ----> Clave especial para restar líneas de código cuando un método tiene sub-métodos adentro
    public static final DataKey<Integer> LINEAS_PODADAS = new DataKey<Integer>() {};

    private String currentClassName = "Unknown";
    private String currentFileName  = "Unknown";

    // ----> Constructor: recibe el reporte del proyecto para ir guardando los hallazgos
    public MetricsAnalyzer(ProjectMetrics projectMetrics) {
        this.projectMetrics = projectMetrics;
    }

    // ----> Asigna el archivo que se está leyendo en el momento
    public void setCurrentFileName(String fileName) {
        this.currentFileName = fileName;
    }

    // ----> Analiza un método que fue extraído o cortado de forma independiente
    public void analizarMetodoSuelto(MethodDeclaration md, String nombreClase) {
        String claseAnterior = this.currentClassName;
        this.currentClassName = nombreClase;
        this.visit(md, null);
        this.currentClassName = claseAnterior;
    }

    // ----> Se ejecuta automáticamente al entrar a una Clase de Java
    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        String previousClass = this.currentClassName;
        this.currentClassName = cid.getNameAsString();
        super.visit(cid, arg);
        this.currentClassName = previousClass;
    }

    // ----> Se ejecuta automáticamente cada vez que encuentra un Método dentro de una clase
    @Override
    public void visit(MethodDeclaration md, Void arg) {
        // ----> Si el método está vacío (sin código), lo ignoramos
        if (!md.getBody().isPresent()) {
            super.visit(md, arg);
            return;
        }

        // ----> Calculamos cuántas líneas de código (LOC) tiene el método
        int loc = (md.getBegin().isPresent() && md.getEnd().isPresent())
                ? md.getEnd().get().line - md.getBegin().get().line + 1
                : 0;

        // ----> Si le podamos un sub-método, le restamos esas líneas para no contar doble
        if (md.containsData(LINEAS_PODADAS)) {
            loc -= md.getData(LINEAS_PODADAS);
        }

        // ----> Contamos todas las decisiones del código (ifs, fors, whiles, switches, etc.)
        int ifs       = md.findAll(IfStmt.class).size();
        int fors      = md.findAll(ForStmt.class).size();
        int foreachs  = md.findAll(ForEachStmt.class).size();
        int whiles    = md.findAll(WhileStmt.class).size();
        int dos       = md.findAll(DoStmt.class).size();
        int switches  = md.findAll(SwitchEntry.class).size();
        int catches   = md.findAll(CatchClause.class).size();
        int ternaries = md.findAll(ConditionalExpr.class).size();
        int decisions = ifs + fors + foreachs + whiles + dos + switches + catches + ternaries;

        // ----> Obtenemos los contadores de Halstead (operadores y operandos)
        int[] halstead = collectHalstead(md);

        // ----> Calculamos el grafo de flujo de control (CFG) del método
        CfgCalculator.CfgResult cfg = CfgCalculator.estimateCfg(md, decisions);

        // ----> Creamos el objeto con todas las métricas completas de este método
        MethodMetrics method = new MethodMetrics(
                currentFileName,
                currentClassName,
                md.getNameAsString(),
                loc,
                halstead[0], halstead[1], halstead[2], halstead[3],
                cfg.cyclomaticComplexity,
                cfg.nodes,
                cfg.edges,
                cfg.unconnectedNodes
        );

        // ----> Guardamos el método procesado en el reporte global del proyecto
        projectMetrics.addMethod(currentClassName, currentFileName, method);

        super.visit(md, arg);
    }

    // ----> Función auxiliar para buscar y contar operadores (if, +, =, return) y operandos (variables, números, textos)
    private static int[] collectHalstead(MethodDeclaration md) {
        Set<String> distinctOps  = new HashSet<>();
        Set<String> distinctOpds = new HashSet<>();

        int totalOps  = 0;
        int totalOpds = 0;

        // ----> Contamos asignaciones (=, +=, etc.)
        for (AssignExpr e : md.findAll(AssignExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }

        // ----> Contamos operaciones binarias (+, -, *, &&, etc.)
        for (BinaryExpr e : md.findAll(BinaryExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }

        // ----> Contamos operaciones unarias (!, ++, --)
        for (UnaryExpr e : md.findAll(UnaryExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }

        // ----> Registramos palabras clave del lenguaje como operadores
        addKeyword(md, IfStmt.class,       "if",      distinctOps); totalOps += md.findAll(IfStmt.class).size();
        addKeyword(md, ForStmt.class,      "for",     distinctOps); totalOps += md.findAll(ForStmt.class).size();
        addKeyword(md, ForEachStmt.class,  "foreach", distinctOps); totalOps += md.findAll(ForEachStmt.class).size();
        addKeyword(md, WhileStmt.class,    "while",   distinctOps); totalOps += md.findAll(WhileStmt.class).size();
        addKeyword(md, DoStmt.class,       "do",      distinctOps); totalOps += md.findAll(DoStmt.class).size();
        addKeyword(md, ReturnStmt.class,   "return",  distinctOps); totalOps += md.findAll(ReturnStmt.class).size();
        addKeyword(md, ThrowStmt.class,    "throw",   distinctOps); totalOps += md.findAll(ThrowStmt.class).size();

        // ----> Contamos operandos (variables, números, cadenas de texto, booleanos, nulos)
        for (NameExpr e : md.findAll(NameExpr.class)) {
            distinctOpds.add(e.getNameAsString());
            totalOpds++;
        }
        for (IntegerLiteralExpr e : md.findAll(IntegerLiteralExpr.class)) {
            distinctOpds.add(e.getValue());
            totalOpds++;
        }
        for (DoubleLiteralExpr e : md.findAll(DoubleLiteralExpr.class)) {
            distinctOpds.add(e.getValue());
            totalOpds++;
        }
        for (StringLiteralExpr e : md.findAll(StringLiteralExpr.class)) {
            distinctOpds.add(e.getValue());
            totalOpds++;
        }
        for (BooleanLiteralExpr e : md.findAll(BooleanLiteralExpr.class)) {
            distinctOpds.add(String.valueOf(e.isValue()));
            totalOpds++;
        }
        for (NullLiteralExpr e : md.findAll(NullLiteralExpr.class)) {
            distinctOpds.add("null");
            totalOpds++;
        }

        // ----> Devolvemos el conteo final: [operadores únicos, operandos únicos, total operadores, total operandos]
        return new int[]{ distinctOps.size(), distinctOpds.size(), totalOps, totalOpds };
    }

    // ----> Método de apoyo para agregar palabras clave sin repetir
    private static <T extends Node> void addKeyword(
            MethodDeclaration md, Class<T> type, String keyword, Set<String> ops) {
        if (!md.findAll(type).isEmpty()) ops.add(keyword);
    }
}