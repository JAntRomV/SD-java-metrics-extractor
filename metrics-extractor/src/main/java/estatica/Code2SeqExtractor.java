package estatica;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import java.util.ArrayList;
import java.util.List;

//-----> Clase que sirve para extraer caminos del codigo y volverlos a armar como texto
public class Code2SeqExtractor {

    //-----> Estructura para guardar el nodo real y su texto plano
    private static class TerminalNode {
        Node node;
        String valor;

        TerminalNode(Node node, String valor) {
            this.node = node;
            this.valor = valor;
        }
    }

    //-----> Extrae todos los pares de terminales (variables/valores) y su camino en el AST
    public static List<String> extraerCaminosDesdeMetodo(MethodDeclaration md) {
        List<TerminalNode> terminales = new ArrayList<>();
        List<String> caminos = new ArrayList<>();

        //-----> Buscamos absolutamente todas las piezas (nodos) dentro del metodo
        md.findAll(Node.class).forEach(nodo -> {
            if (nodo instanceof NameExpr) {

                //-----> Uso de una variable (ej. dentro de una expresion)
                terminales.add(new TerminalNode(nodo, ((NameExpr) nodo).getNameAsString()));
            } else if (nodo instanceof LiteralExpr) {
                
                //-----> Valor fijo (numero, cadena, booleano, etc.)
                String textoSafe = nodo.toString().replace("\"", "\\\"");
                terminales.add(new TerminalNode(nodo, textoSafe));
            } else if (nodo instanceof SimpleName) {

                //-----> Captura el nombre real al crear variables.
                Node padre = nodo.getParentNode().orElse(null);
                
                //-----> Si el padre es una variable creada o un parametro, guardamos su nombre
                if (padre instanceof VariableDeclarator || padre instanceof Parameter) {
                    terminales.add(new TerminalNode(nodo, ((SimpleName) nodo).asString()));
                }
            }
        });

        //-----> Comparamos cada palabra o valor contra todos los demas para hacer parejas
        for (int i = 0; i < terminales.size(); i++) {
            for (int j = i + 1; j < terminales.size(); j++) {
                TerminalNode a = terminales.get(i);
                TerminalNode b = terminales.get(j);

                //-----> Ordena por aparicion visual en el codigo (de izquierda a derecha).
                TerminalNode inicio = a;
                TerminalNode fin = b;
                Position posA = a.node.getBegin().orElse(null);
                Position posB = b.node.getBegin().orElse(null);
                
                //-----> Si 'a' esta mas adelante en el archivo que 'b', los volteamos
                if (posA != null && posB != null && posA.compareTo(posB) > 0) {
                    inicio = b;
                    fin = a;
                }

                //-----> Buscamos la ruta entre ellos en el arbol
                String camino = encontrarCaminoEntreNodos(inicio.node, fin.node);
                
                //-----> Juntamos todo en un "trillizo": Origen | Ruta | Destino
                String trillizo = inicio.valor + "|" + camino + "|" + fin.valor;
                caminos.add(trillizo);
            }
        }
        return caminos;
    }

    //-----> Calcula el camino completo entre dos nodos: sube desde "inicio" hasta el
    //-----> ancestro comun LCA y luego baja hasta "fin".
    private static String encontrarCaminoEntreNodos(Node inicio, Node fin) {
        List<String> subida = new ArrayList<>();
        List<String> bajada = new ArrayList<>();

        //-----> Empezamos a subir desde el padre del primer nodo
        Node actual = inicio.getParentNode().orElse(null);
        Node lca = null;

        //-----> Subimos por el arbol buscando la rama comun que tambien conecte a 'fin'
        while (actual != null) {
            if (actual.isAncestorOf(fin)) {
                lca = actual;
                //-----> Encontramos el punto de union y lo marcamos con un asterisco (*)
                subida.add("*" + describirNodo(actual));
                break;
            }
            //-----> Si no es el punto comun, lo anotamos en la ruta de subida
            subida.add(describirNodo(actual));
            actual = actual.getParentNode().orElse(null);
        }

        //-----> Si encontramos el punto comun, ahora rastreamos el camino de bajada hacia 'fin'
        if (lca != null) {
            Node actualBajada = fin.getParentNode().orElse(null);
            while (actualBajada != null && actualBajada != lca) {

                //-----> Lo agregamos al inicio de la lista de bajada para mantener el orden correcto
                bajada.add(0, describirNodo(actualBajada));
                actualBajada = actualBajada.getParentNode().orElse(null);
            }
        }

        //-----> Proteccion por si la subida quedo vacia
        if (subida.isEmpty()) {
            subida.add("*ChildOf");
        }

        //-----> Juntamos la ruta de subida con la de bajada usando flechas "->"
        List<String> rutaCompleta = new ArrayList<>(subida);
        rutaCompleta.addAll(bajada);
        return String.join("->", rutaCompleta);
    }

    //-----> Guarda el tipo de dato o signo (+, =, ==) para no perder informacion.
    private static String describirNodo(Node nodo) {
        String nombreClase = nodo.getClass().getSimpleName();
        
        //-----> Si es una creacion de variable, le pegamos el tipo
        if (nodo instanceof VariableDeclarator) {
            String tipo = ((VariableDeclarator) nodo).getType().asString();
            return nombreClase + "(" + tipo + ")";
        }
        
        //-----> Si es una operacion matematica/logica, le pegamos el simbolo 
        if (nodo instanceof BinaryExpr) {
            String operador = ((BinaryExpr) nodo).getOperator().asString();
            return nombreClase + "(" + operador + ")";
        }
        
        //-----> Si es una asignacion de valor, le pegamos el signo
        if (nodo instanceof AssignExpr) {
            String operador = ((AssignExpr) nodo).getOperator().asString();
            return nombreClase + "(" + operador + ")";
        }
        return nombreClase;
    }

    //-----> Toma los trillizos generados y reconstruye fragmentos de codigo verificables
    public static List<String> reconstruirCodigoDesdeCaminos(List<String> caminos) {
        List<String> codigoReconstruido = new ArrayList<>();

        //-----> Revisamos cada trillizo uno por uno
        for (String camino : caminos) {
            String[] partes = camino.split("\\|");
            if (partes.length < 3) continue; //-----> Si esta roto, lo saltamos

            String inicio = partes[0].trim();
            String ruta = partes[1].trim();
            String fin = partes[2].trim();

            //----->Ignora rutas largas de ecuaciones complejas para evitar armar lineas inventadas.
            String[] nodos = ruta.split("->");
            if (nodos.length != 1) continue; //-----> Si la ruta es larga o compleja, la ignoramos

            //-----> Revisamos si el nodo tiene la marca del asterisco (*) de union
            String nodoLCA = nodos[0].startsWith("*") ? nodos[0].substring(1) : null;
            if (nodoLCA == null) continue;

            //-----> Si se unieron en una creacion de variable, armamos: "tipo variable = valor"
            if (nodoLCA.startsWith("VariableDeclarator")) {
                String tipo = extraerParametro(nodoLCA, "VariableDeclarator");
                if (tipo != null) {
                    agregarSiNuevo(codigoReconstruido, tipo + " " + inicio + "=" + fin);
                }
            //-----> Si se unieron en un signo de igual, armamos: "variable = valor"
            } else if (nodoLCA.startsWith("AssignExpr")) {
                String operador = extraerParametro(nodoLCA, "AssignExpr");
                if (operador != null) {
                    agregarSiNuevo(codigoReconstruido, inicio + operador + fin);
                }
            //-----> Si se unieron en un operador matematico, armamos: "variable + valor"
            } else if (nodoLCA.startsWith("BinaryExpr")) {
                String operador = extraerParametro(nodoLCA, "BinaryExpr");
                if (operador != null) {
                    agregarSiNuevo(codigoReconstruido, inicio + operador + fin);
                }
            }
        }

        return codigoReconstruido;
    }

    //-----> Agrega la linea de codigo a la lista solo si no la habiamos guardado antes
    private static void agregarSiNuevo(List<String> lista, String linea) {
        if (!lista.contains(linea)) {
            lista.add(linea);
        }
    }

    //-----> Extrae el valor entre parentesis de un token tipo "NombreNodo(valor)"
    private static String extraerParametro(String nodoConParametro, String nombreNodo) {
        int idx = nodoConParametro.indexOf(nombreNodo + "(");

        if (idx == -1) return null; //-----> Si no tiene parentesis, salimos
        int inicioParam = idx + nombreNodo.length() + 1;
        int finParam = nodoConParametro.indexOf(")", inicioParam);
        
        if (finParam == -1) return null; //-----> Si esta mal cerrado, salimos
        
        //-----> Corta y devuelve lo que este adentro, por ejemplo el "int" o el "+"
        return nodoConParametro.substring(inicioParam, finParam);
    }
}