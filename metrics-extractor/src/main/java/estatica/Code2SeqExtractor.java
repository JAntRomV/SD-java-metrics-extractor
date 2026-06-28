package estatica;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import java.util.ArrayList;
import java.util.List;

public class Code2SeqExtractor {

//----->Guarda el nodo real y el texto de lo que vale 
    private static class TerminalNode {
        Node node;
        String valor;

        TerminalNode(Node node, String valor) {
            this.node = node;
            this.valor = valor;
        }
    }

//-----/Guarda todas las variables y valores fijos y los guarda en una lista\---------
    public static List<String> extraerCaminos(MethodDeclaration md) {
        List<TerminalNode> terminales = new ArrayList<>();
        List<String> caminos = new ArrayList<>();

//----> examina cada una de las partes del metodo
        md.findAll(Node.class).forEach(nodo -> {
          
        //---->Siencuentra un nombre de variable u objetivo extrae su texto en plano  
            if (nodo instanceof NameExpr) {
                terminales.add(new TerminalNode(nodo, ((NameExpr) nodo).getNameAsString()));

        //----> Si escuentra valores fijos hace lo mismo       
            } else if (nodo instanceof LiteralExpr) {

            //--->.replace coloca diagonales a las comillas    
                String textoSafe = nodo.toString().replace("\"", "\\\"");
                terminales.add(new TerminalNode(nodo, textoSafe));
            }
        });
//-----/Agarra la lista anterior y empieza a buscar los pares el inicio y el final\----
        for (int i = 0; i < terminales.size(); i++) {
            for (int j = i + 1; j < terminales.size(); j++) {
                TerminalNode inicio = terminales.get(i);
                TerminalNode fin = terminales.get(j);

                String camino = encontrarCaminoEntreNodos(inicio.node, fin.node);
                String trillizo = inicio.valor + "|" + camino + "|" + fin.valor;
                caminos.add(trillizo);
            }
        }
        return caminos;
    }
//----/Agarra esos pares y calcula el camino\------ 
    private static String encontrarCaminoEntreNodos(Node inicio, Node fin) {
//---->Se crea una lista vacia llamada ruta para ir anotando el camino
    List<String> ruta = new ArrayList<>();
    
//-----> Subimos desde el nodo de inicio hacia sus ancestros
    Node actual = inicio.getParentNode().orElse(null);
    
//----->  seguira escalando de padre en padre hacia arriba hasta que se termine el arcbol 
    while (actual != null) {
    
//----> NUEVO: Se quitaron los  nombres técnicos
        if (actual instanceof BinaryExpr) {
            ruta.add("Operacion(" + ((BinaryExpr) actual).getOperator().asString() + ")");

        } else if (actual instanceof AssignExpr) {
            ruta.add("Asignacion(" + ((AssignExpr) actual).getOperator().asString() + ")");

        } else if (actual instanceof IfStmt) {
            ruta.add("Condicion(if)");

        } else if (actual instanceof WhileStmt) {
            ruta.add("Bucle(while)");

        } else if (actual instanceof ForStmt) {
            ruta.add("Bucle(for)");
        } 
//----->Estructura del Código y Bloques
        else if (actual instanceof BlockStmt) {
            ruta.add("Llaves{}");
        } else if (actual instanceof ExpressionStmt) {
            ruta.add(";");
        } else if (actual instanceof com.github.javaparser.ast.CompilationUnit) {
            ruta.add("Archivo.Java");
        } 
//----->Declaración de Variables, Atributos y Parámetros
        else if (actual instanceof VariableDeclarator) {
            ruta.add("DeclaradorVariable");

        } else if (actual instanceof VariableDeclarationExpr) {
            ruta.add("Defines una variable");

        } else if (actual instanceof com.github.javaparser.ast.body.FieldDeclaration) {
            ruta.add("Declaracion de atributo");

        } else if (actual instanceof com.github.javaparser.ast.body.Parameter) {
            ruta.add("Parametro/Argumento");
        } 
//----->Llamadas, Retornos y Creación de Objetos
        else if (actual instanceof com.github.javaparser.ast.expr.MethodCallExpr) {
            ruta.add("Llamada a Metodo");

        } else if (actual instanceof com.github.javaparser.ast.stmt.ReturnStmt) {
            ruta.add("Retorno");

        } else if (actual instanceof com.github.javaparser.ast.expr.ObjectCreationExpr) {
            ruta.add("CreacionObjeto");
        } 
//----->Operaciones Matemáticas Avanzadas o Paréntesis
        else if (actual instanceof com.github.javaparser.ast.expr.UnaryExpr) {
            ruta.add("OperacionUnaria"); 

        } else if (actual instanceof com.github.javaparser.ast.expr.ConditionalExpr) {
            ruta.add("CondicionTernaria"); 

        } else if (actual instanceof com.github.javaparser.ast.expr.EnclosedExpr) {
            ruta.add("Parentesis"); 
        } 
//----->Otras Estructuras de Control (Manejo de Errores y Casos)
        else if (actual instanceof com.github.javaparser.ast.stmt.TryStmt) {
            ruta.add("ZonaTry");

        } else if (actual instanceof com.github.javaparser.ast.stmt.CatchClause) {
            ruta.add("ZonaCatch");

        } else if (actual instanceof com.github.javaparser.ast.stmt.ThrowStmt) {
            ruta.add("LanzarError");

        } else if (actual instanceof com.github.javaparser.ast.stmt.SwitchStmt) {
            ruta.add("SelectorSwitch");

        } else if (actual instanceof com.github.javaparser.ast.stmt.SwitchEntry) {
            ruta.add("CasoSwitch");

        } else if (actual instanceof com.github.javaparser.ast.stmt.DoStmt) {
            ruta.add("BucleDoWhile");

        } else if (actual instanceof com.github.javaparser.ast.stmt.ForEachStmt) {
            ruta.add("BucleForEach");
        }
//----->Arreglos
        else if (actual instanceof com.github.javaparser.ast.expr.ArrayCreationExpr) {
            ruta.add("CrearArreglo");

        } else if (actual instanceof com.github.javaparser.ast.expr.ArrayAccessExpr) {
            ruta.add("AccesoArreglo");
        } 
//-----> Si no se encuentra se escribe el nombre tecnico 
        else {
          ruta.add(actual.getClass().getSimpleName());        }
        
//----->cada vez que sube revisa si en donde nos encontramos es el nodo final

        if (actual.isAncestorOf(fin)) {
            break; // Detenemos el camino porque ya encontramos el punto de unión
        }
        actual = actual.getParentNode().orElse(null);
    }

    // Si la ruta quedó vacía, ponemos un conector genérico
    if (ruta.isEmpty()) {
        ruta.add("ChildOf");
    }

    return String.join("->", ruta);
}
}