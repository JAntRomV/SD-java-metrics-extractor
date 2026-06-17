package estatica;
//-----------------------Recorre el arbol calcula el grafo de flujo cfg= Extrae las metricas clase x clase------------------------------
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.HashSet;
import java.util.Set;
//---------------------------LLaman al visitir----------------------------
public class MetricsAnalyzer extends VoidVisitorAdapter<Void> {

    private final ProjectMetrics projectMetrics;
//----------------------- revisa en que clase y archivo se encuentra--------------------------
    private String currentClassName = "Unknown";
    private String currentFileName  = "Unknown";

    public MetricsAnalyzer(ProjectMetrics projectMetrics) {
        this.projectMetrics = projectMetrics;
    }

//--------------------App llama a este setter antes de parsear cada archivo .java-----------------------
    public void setCurrentFileName(String fileName) {
        this.currentFileName = fileName;
    }


//-----------guarda el nombre de la clase anterior acrualiza el current y llama alsuper.visit-------
    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {
        String previousClass = this.currentClassName;
        this.currentClassName = cid.getNameAsString();

        super.visit(cid, arg);  

        this.currentClassName = previousClass; 
    }

    @Override
    public void visit(MethodDeclaration md, Void arg) {

//----------------------------- Salta si estan vacios------------------------------------------
        if (!md.getBody().isPresent()) {
            super.visit(md, arg);
            return;
        }

//------------------------------registra en que linea empieza y termina cada metodo------------------------------
        int loc = (md.getBegin().isPresent() && md.getEnd().isPresent())
                ? md.getEnd().get().line - md.getBegin().get().line + 1
                : 0;

//-busca recursivamente en todo el AST del método todos los nodos de ese tipo. 
// Cada uno representa un punto donde el flujo de ejecución puede tomar caminos distintos
        int ifs       = md.findAll(IfStmt.class).size();
        int fors      = md.findAll(ForStmt.class).size();
        int foreachs  = md.findAll(ForEachStmt.class).size();
        int whiles    = md.findAll(WhileStmt.class).size();
        int dos       = md.findAll(DoStmt.class).size();
        int switches  = md.findAll(SwitchEntry.class).size();
        int catches   = md.findAll(CatchClause.class).size();
        int ternaries = md.findAll(ConditionalExpr.class).size();
        int decisions = ifs + fors + foreachs + whiles + dos + switches + catches + ternaries;

//--------------------------Halstead --------------------
        int[] halstead = collectHalstead(md);

//-------------------------CFG--------------------------------------------
        int[] cfg = buildCfg(md, decisions);
        // cfg[0]=nodes, cfg[1]=edges, cfg[2]=CC, cfg[3]=unconnectedNodes

//-------reune todos los valores en un objeto y lo registra en el proyecto------
        MethodMetrics method = new MethodMetrics(
                currentFileName, currentClassName, md.getNameAsString(),
                loc,
                halstead[0], halstead[1], halstead[2], halstead[3],
                cfg[2],         // CC
                cfg[0],         // nodes
                cfg[1],         // edges
                cfg[3]          // unconnectedNodes
        );

        projectMetrics.addMethod(currentClassName, currentFileName, method);

        super.visit(md, arg);
    }

//--------------------No se guarda duplicados solo se guarda una vez----------------------
    private static int[] collectHalstead(MethodDeclaration md) {
        Set<String> distinctOps  = new HashSet<>();
        Set<String> distinctOpds = new HashSet<>();
        int totalOps  = 0;
        int totalOpds = 0;

//--------------------------Captura asignaciones (=, +=, -=, ...)
        for (AssignExpr e : md.findAll(AssignExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }
        
        for (BinaryExpr e : md.findAll(BinaryExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }
        
        for (UnaryExpr e : md.findAll(UnaryExpr.class)) {
            distinctOps.add(e.getOperator().asString());
            totalOps++;
        }
//------------------------------palabras clave de control---------------------------------------
        addKeyword(md, IfStmt.class,       "if",      distinctOps); totalOps += md.findAll(IfStmt.class).size();
        addKeyword(md, ForStmt.class,      "for",     distinctOps); totalOps += md.findAll(ForStmt.class).size();
        addKeyword(md, ForEachStmt.class,  "foreach", distinctOps); totalOps += md.findAll(ForEachStmt.class).size();
        addKeyword(md, WhileStmt.class,    "while",   distinctOps); totalOps += md.findAll(WhileStmt.class).size();
        addKeyword(md, DoStmt.class,       "do",      distinctOps); totalOps += md.findAll(DoStmt.class).size();
        addKeyword(md, ReturnStmt.class,   "return",  distinctOps); totalOps += md.findAll(ReturnStmt.class).size();
        addKeyword(md, ThrowStmt.class,    "throw",   distinctOps); totalOps += md.findAll(ThrowStmt.class).size();

//-----------------------------nombres de variables / referencias----------------------------
        for (NameExpr e : md.findAll(NameExpr.class)) {
            distinctOpds.add(e.getNameAsString());
            totalOpds++;
        }
//----------------------------Operandos: literales
        for (IntegerLiteralExpr e : md.findAll(IntegerLiteralExpr.class)) {
            distinctOpds.add(e.getValue()); totalOpds++;
        }
        for (DoubleLiteralExpr e : md.findAll(DoubleLiteralExpr.class)) {
            distinctOpds.add(e.getValue()); totalOpds++;
        }
        for (StringLiteralExpr e : md.findAll(StringLiteralExpr.class)) {
            distinctOpds.add(e.getValue()); totalOpds++;
        }
        for (BooleanLiteralExpr e : md.findAll(BooleanLiteralExpr.class)) {
            distinctOpds.add(String.valueOf(e.isValue())); totalOpds++;
        }
        for (NullLiteralExpr e : md.findAll(NullLiteralExpr.class)) {
            distinctOpds.add("null"); totalOpds++;
        }

        return new int[]{ distinctOps.size(), distinctOpds.size(), totalOps, totalOpds };
    }

//------------------Agrega la palabra clave al set de operadores distintos si hay al menos 1 ocurrencia-----------
    private static <T extends com.github.javaparser.ast.Node> void addKeyword(
            MethodDeclaration md, Class<T> type, String keyword, Set<String> ops) {
        if (!md.findAll(type).isEmpty()) ops.add(keyword);
    }

    private static int[] buildCfg(MethodDeclaration md, int decisions) {
        int nodes            = 2 + decisions;
        int edges            = nodes + decisions;
        int cc               = edges - nodes + 2;
        int earlyExits       = md.findAll(ReturnStmt.class).size()
                             + md.findAll(ThrowStmt.class).size();
        int unconnectedNodes = Math.max(0, earlyExits - 1);

        return new int[]{ nodes, edges, cc, unconnectedNodes };
    }
}