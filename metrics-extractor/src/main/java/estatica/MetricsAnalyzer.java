package estatica;
//______________/Recorre el arbol y Extrae las metricas de cada metodod\_________________________
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MetricsAnalyzer extends VoidVisitorAdapter<Void> {
//------>Guarda el reporte global  donde se ira juntando toda la informacion 
    private final ProjectMetrics projectMetrics;
//------>Nombre del archivo y clase que se analiza
    private String currentClassName = "Unknown";
    private String currentFileName  = "Unknown";

//------>Es el constructor recibe los reportes para poder guardar los datos 
    public MetricsAnalyzer(ProjectMetrics projectMetrics) {
        this.projectMetrics = projectMetrics;
    }

//------> le dice cual es el nombre del archivo que va a empezar a leer
    public void setCurrentFileName(String fileName) {
        this.currentFileName = fileName;
    }


/**      Cada vez que se encuentra una clase guarda el nombre anterior a la clase
*------> actualiza el nombre de la clase que se esta leyendo  recorre el codigo
*        y cuando termina regresa al nombre anterior 
*/
    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {

        String previousClass = this.currentClassName;

        this.currentClassName = cid.getNameAsString();

        super.visit(cid, arg);  

        this.currentClassName = previousClass; 
    }
//_______________________________________________________________
    @Override
    public void visit(MethodDeclaration md, Void arg) {

//----->Salta el metodo si estavacio
        if (!md.getBody().isPresent()) {
            super.visit(md, arg);
            return;
        }

//------>CAlcula cuantas lineas de codigo tiene el metodo
        int loc = (md.getBegin().isPresent() && md.getEnd().isPresent())
                ? md.getEnd().get().line - md.getBegin().get().line + 1
                : 0;

//------>BUsca y cuenta todos los puntos de decision
        int ifs       = md.findAll(IfStmt.class).size();
        int fors      = md.findAll(ForStmt.class).size();
        int foreachs  = md.findAll(ForEachStmt.class).size();
        int whiles    = md.findAll(WhileStmt.class).size();
        int dos       = md.findAll(DoStmt.class).size();
        int switches  = md.findAll(SwitchEntry.class).size();
        int catches   = md.findAll(CatchClause.class).size();
        int ternaries = md.findAll(ConditionalExpr.class).size();
        int decisions = ifs + fors + foreachs + whiles + dos + switches + catches + ternaries;

//----->Extrae las variables base deeeeeee halstead
        int[] halstead = collectHalstead(md);

//----->Se llama a la clase CfgCalculator
CfgCalculator.CfgResult cfg = CfgCalculator.estimateCfg(md, decisions);


//------>Reune todos los valores en un objeto y lo registra en el proyecto------
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

//----->Nuevo
        //----->Extraer los caminos de Code2Seq para este método
        List<String> caminosC2S = Code2SeqExtractor.extraerCaminos(md);
        method.setCaminosCode2Seq(caminosC2S);    
            
//----->Registra el metodo en el reporte global
        projectMetrics.addMethod(currentClassName, currentFileName, method);

        super.visit(md, arg);
    }

//____________________________________________________________________
    private static int[] collectHalstead(MethodDeclaration md) {

//------>No permiten que se guarden  nombres repetidos
        Set<String> distinctOps  = new HashSet<>();
        Set<String> distinctOpds = new HashSet<>();

//----->Cuenta el total absoluto de veces que aparecen
        int totalOps  = 0;
        int totalOpds = 0;

//------>Busca asignaciones incremento odecremento-operaciones 
//       matematicas o comparaciones 
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
//------->Cuenta palabras clave comooperadores 
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

//------>Agrega la palabra clave al set de operadores distintos si hay al menos 1 ocurrencia
    private static <T extends com.github.javaparser.ast.Node> void addKeyword(
            MethodDeclaration md, Class<T> type, String keyword, Set<String> ops) {
        if (!md.findAll(type).isEmpty()) ops.add(keyword);
    }

}