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

//-----> Analizador de codigo basado en Visitor Pattern
public class MetricsAnalyzer extends VoidVisitorAdapter<Void> {
    
    private ProjectMetrics projectMetrics; //-----> Repositorio de metricas

    //-----> Clave para descontar lineas podadas
    public static final DataKey<Integer> LINEAS_PODADAS = new DataKey<Integer>() {};

    private String currentClassName = "Unknown";
    private String currentFileName  = "Unknown";

    public MetricsAnalyzer(ProjectMetrics projectMetrics) {
        this.projectMetrics = projectMetrics;
    }

    //-----> Reinicia reporte para no saturar memoria
    public void reiniciarReporte(ProjectMetrics nuevoReporte) {
        this.projectMetrics = nuevoReporte;
    }

    public void setCurrentFileName(String fileName) {
        this.currentFileName = fileName;
    }

    //-----> Procesa metodos anidados extraidos
    public void analizarMetodoSuelto(MethodDeclaration md, String nombreClase) {
        String claseAnterior = this.currentClassName;
        this.currentClassName = nombreClase;
        this.visit(md, null);
        this.currentClassName = claseAnterior;
    }

    //-----> Visita clases del AST
    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        String previousClass = this.currentClassName;
        this.currentClassName = cid.getNameAsString();
        super.visit(cid, arg);
        this.currentClassName = previousClass;
    }

    //-----> Visita metodos y extrae datos
    @Override
    public void visit(MethodDeclaration md, Void arg) {
        if (!md.getBody().isPresent()) {
            super.visit(md, arg);
            return;
        }

        //-----> Calculo de LOC
        int loc = (md.getBegin().isPresent() && md.getEnd().isPresent())
                ? md.getEnd().get().line - md.getBegin().get().line + 1
                : 0;

        //-----> Descontar sub-metodos
        if (md.containsData(LINEAS_PODADAS)) {
            loc -= md.getData(LINEAS_PODADAS);
        }

        //-----> Conteo de estructuras de decision
        int ifs       = md.findAll(IfStmt.class).size();
        int fors      = md.findAll(ForStmt.class).size();
        int foreachs  = md.findAll(ForEachStmt.class).size();
        int whiles    = md.findAll(WhileStmt.class).size();
        int dos       = md.findAll(DoStmt.class).size();
        int switches  = md.findAll(SwitchEntry.class).size();
        int catches   = md.findAll(CatchClause.class).size();
        int ternaries = md.findAll(ConditionalExpr.class).size();
        int decisions = ifs + fors + foreachs + whiles + dos + switches + catches + ternaries;

        int[] halstead = collectHalstead(md);

        CfgCalculator.CfgResult cfg = CfgCalculator.estimateCfg(md, decisions);

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

        projectMetrics.addMethod(currentClassName, currentFileName, method);

        super.visit(md, arg);
    }

    //-----> Recolecta operadores y operandos
    private static int[] collectHalstead(MethodDeclaration md) {
        Set<String> distinctOps  = new HashSet<>();
        Set<String> distinctOpds = new HashSet<>();

        int totalOps  = 0;
        int totalOpds = 0;

        //-----> Expresiones de asignacion
        for (AssignExpr e : md.findAll(AssignExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }

        //-----> Expresiones binarias
        for (BinaryExpr e : md.findAll(BinaryExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }

        //-----> Expresiones unarias
        for (UnaryExpr e : md.findAll(UnaryExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }

        //-----> Palabras clave como operadores
        addKeyword(md, IfStmt.class,       "if",      distinctOps); totalOps += md.findAll(IfStmt.class).size();
        addKeyword(md, ForStmt.class,      "for",     distinctOps); totalOps += md.findAll(ForStmt.class).size();
        addKeyword(md, ForEachStmt.class,  "foreach", distinctOps); totalOps += md.findAll(ForEachStmt.class).size();
        addKeyword(md, WhileStmt.class,    "while",   distinctOps); totalOps += md.findAll(WhileStmt.class).size();
        addKeyword(md, DoStmt.class,       "do",      distinctOps); totalOps += md.findAll(DoStmt.class).size();
        addKeyword(md, ReturnStmt.class,   "return",  distinctOps); totalOps += md.findAll(ReturnStmt.class).size();
        addKeyword(md, ThrowStmt.class,    "throw",   distinctOps); totalOps += md.findAll(ThrowStmt.class).size();

        //-----> Identificadores y literales como operandos
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

        return new int[]{ distinctOps.size(), distinctOpds.size(), totalOps, totalOpds };
    }

    //-----> Registra palabra clave si existe
    private static <T extends Node> void addKeyword(
            MethodDeclaration md, Class<T> type, String keyword, Set<String> ops) {
        if (!md.findAll(type).isEmpty()) ops.add(keyword);
    }
}