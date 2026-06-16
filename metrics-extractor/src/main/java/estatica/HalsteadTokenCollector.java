package estatica;
//----------------------------Recorre el arbol AST de un metodo contando operadores reales--------------------------------------
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.HashSet;
import java.util.Set;

public class HalsteadTokenCollector extends VoidVisitorAdapter<Void> {
//-----------------------Se encarga de no agregar un operador que ya existe--------------------------
    private final Set<String> distinctOperators = new HashSet<>();
    private final Set<String> distinctOperands = new HashSet<>();
//-----------------------Cuentan las operaciones repetidas
    private int totalOperators = 0;
    private int totalOperands = 0;

    public void collect(MethodDeclaration md) {
        md.accept(this, null);
    }
//
    @Override
    public void visit(AssignExpr n, Void arg) {
        String op = n.getOperator().asString();
        distinctOperators.add(op);
        totalOperators++;
        super.visit(n, arg);
    }
//-----------------------CAptura las expresiones binarias
    @Override
    public void visit(BinaryExpr n, Void arg) {
        String op = n.getOperator().asString();
        distinctOperators.add(op);
        totalOperators++;
        super.visit(n, arg);
    }
//-------------------------capturan operadores que trabajan con un solo valor
    @Override
    public void visit(UnaryExpr n, Void arg) {
        String op = n.getOperator().asString();
        distinctOperators.add(op);
        totalOperators++;
        super.visit(n, arg);
    }
//palabras clave if, for, while
    @Override
    public void visit(IfStmt n, Void arg) {
        distinctOperators.add("if");
        totalOperators++;
        super.visit(n, arg);
    }

    @Override
    public void visit(ForStmt n, Void arg) {
        distinctOperators.add("for");
        totalOperators++;
        super.visit(n, arg);
    }

    @Override
    public void visit(WhileStmt n, Void arg) {
        distinctOperators.add("while");
        totalOperators++;
        super.visit(n, arg);
    }

    @Override
    public void visit(NameExpr n, Void arg) {
        distinctOperands.add(n.getNameAsString());
        totalOperands++;
        super.visit(n, arg);
    }
//-----------------Lineales
    @Override
    public void visit(IntegerLiteralExpr n, Void arg) {
        distinctOperands.add(n.getValue());
        totalOperands++;
        super.visit(n, arg);
    }

    @Override
    public void visit(StringLiteralExpr n, Void arg) {
        distinctOperands.add(n.getValue());
        totalOperands++;
        super.visit(n, arg);
    }

    @Override
    public void visit(BooleanLiteralExpr n, Void arg) {
        distinctOperands.add(String.valueOf(n.isValue()));
        totalOperands++;
        super.visit(n, arg);
    }

    //---------------------------------------------- Getters
    public int getDistinctOperators() { return distinctOperators.size(); }
    public int getDistinctOperands() { return distinctOperands.size(); }
    public int getTotalOperators() { return totalOperators; }
    public int getTotalOperands() { return totalOperands; }
}