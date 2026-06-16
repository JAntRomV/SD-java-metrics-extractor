package estatica;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;


//---------------------------Visitante AST que extrae métricas estáticas clase por clase, método por método.------------------------------

public class MetricsAnalyzer extends VoidVisitorAdapter<Void> {

    private final ProjectMetrics projectMetrics;

    // Clase que se está analizando en este momento se reemplaza cada vez que el visitor entra a una nueva clase)
    private ClassMetrics currentClass = null;

    // Ruta relativa del archivo actual (se actualiza desde App antes de cada archivo)
    private String currentFileName = "Unknown";

    public MetricsAnalyzer(ProjectMetrics projectMetrics) {
        this.projectMetrics = projectMetrics;
    }

//----------- App llama a este setter antes de parsear cada archivo .java---------------------------------------
    public void setCurrentFileName(String fileName) {
        this.currentFileName = fileName;
    }

//--------------Al entrar a una clase: crear su contenedor-----------------------------
    @Override
    public void visit(ClassOrInterfaceDeclaration cid, Void arg) {

//---------------------------Crear un ClassMetrics limpio para esta clase
        ClassMetrics classMetrics = new ClassMetrics(currentFileName, cid.getNameAsString());

//--------------------- Guardar referencia para que visit(MethodDeclaration) sepa a qué clase agregar-----------------------------
        ClassMetrics previousClass = this.currentClass;
        this.currentClass = classMetrics;

//--------------------- Continuar el recorrido — esto dispara los visit de los métodos---------------------------------
        super.visit(cid, arg);

//----------------------- Al salir de la clase: solo agregarla al proyecto si tiene al menos un método-----------------------
        if (!classMetrics.getMethods().isEmpty()) {
            projectMetrics.addClass(classMetrics);
        }

//---------------------------- Restaurar la clase anterior (maneja clases anidadas correctamente)----------------------------
        this.currentClass = previousClass;
    }

//------------------------------Al entrar a un método: extraer sus métricas-----------------------------------
    @Override
    public void visit(MethodDeclaration md, Void arg) {

//-------------------------------- Saltar métodos abstractos o de interfaz (no tienen cuerpo que analizar)----------------------------
        if (!md.getBody().isPresent()) {
            super.visit(md, arg);
            return;
        }

//---------------------------- Saltar si por alguna razón no hay una clase activa---------------------------------------
        if (currentClass == null) {
            super.visit(md, arg);
            return;
        }

//--------------------------------------LOC-------------------------------------------------------
        int loc = (md.getBegin().isPresent() && md.getEnd().isPresent())
                ? md.getEnd().get().line - md.getBegin().get().line + 1
                : 0;

        // 2. Puntos de decisión — cada uno representa una bifurcación en el flujo
        int ifs       = md.findAll(IfStmt.class).size();
        int fors      = md.findAll(ForStmt.class).size();
        int foreachs  = md.findAll(ForEachStmt.class).size();
        int whiles    = md.findAll(WhileStmt.class).size();
        int dos       = md.findAll(DoStmt.class).size();
        int switches  = md.findAll(SwitchEntry.class).size();
        int catches   = md.findAll(CatchClause.class).size();
        int ternaries = md.findAll(ConditionalExpr.class).size();

        int decisionPoints = ifs + fors + foreachs + whiles + dos
                           + switches + catches + ternaries;

//--------------------------------------- Halstead----------------------------------------
        HalsteadTokenCollector halstead = new HalsteadTokenCollector();
        halstead.collect(md);

//--------------------------------------CFG------------------------------------------------
        CfgBuilder cfgBuilder = new CfgBuilder();
        CfgBuilder.CfgResult cfg = cfgBuilder.analyze(md, decisionPoints);

//-----------------Crear el objeto de métricas para este método y agregarlo a su clase----------------------
        MethodMetrics methodMetrics = new MethodMetrics(
                currentFileName,
                currentClass.getClassName(),
                md.getNameAsString(),
                loc,
                halstead.getDistinctOperators(),
                halstead.getDistinctOperands(),
                halstead.getTotalOperators(),
                halstead.getTotalOperands(),
                cfg.CC,
                cfg.nodes,
                cfg.edges,
                cfg.unconnectedNodes
        );

        currentClass.addMethod(methodMetrics);

        super.visit(md, arg);
    }
}