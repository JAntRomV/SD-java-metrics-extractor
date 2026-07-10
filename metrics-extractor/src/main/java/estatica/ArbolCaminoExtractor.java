package estatica;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

//-----> Clase principal que desarma el código y extrae los caminos del árbol
public class ArbolCaminoExtractor {

    //-----> Estructura contenedora de los resultados finales por clase
    public static class ResultadoClase {
        public String nombreClase;
        public List<String> vectorTexto = new ArrayList<>();
        public List<List<Integer>> vectorNumerico = new ArrayList<>();
    }

    //-----> Cuenta los números del 1 en adelante para ponérselos a los nodos
    private int contadorGlobal = 1;

    //-----> Usamos IdentityHashMap para asegurar que cada objeto Nodo físico tenga su propio número único
    private Map<Node, Integer> numeracionNodos = new IdentityHashMap<>();

    //-----> Función principal que saca los caminos de cada método de la clase
    public ResultadoClase procesarClase(CompilationUnit cu, String nombreArchivo) {
        ResultadoClase resultado = new ResultadoClase();
        resultado.nombreClase = nombreArchivo;

        //-----> Busca todos los métodos que existan en el archivo de Java
        List<MethodDeclaration> metodos = cu.findAll(MethodDeclaration.class);

        for (MethodDeclaration metodo : metodos) {
            if (metodo.getBody().isPresent()) {
                contadorGlobal = 1;
                numeracionNodos.clear();
                asignarNumerosPreorden(metodo.getBody().get());
                generarCaminos(metodo.getBody().get(), new ArrayList<>(), new ArrayList<>(), resultado);
            }
        }
        return resultado;
    }
//----------------------------
    //-----> Visita y numera cronológicamente cada elemento del código sin repetir
    private void asignarNumerosPreorden(Node nodo) {

        //-----> Si es el bloque de llaves principal del método, no le ponemos número para no gastarlo
        if (!(nodo instanceof BlockStmt && nodo.getParentNode().isPresent() && nodo.getParentNode().get() instanceof MethodDeclaration)) {
          
            //-----> Guarda el nodo en la lista junto con su número asignado y suma 1 al contador
            numeracionNodos.put(nodo, contadorGlobal++);
        }
        //-----> Revisa los nodos hijos (las ramas de abajo del árbol) para numerarlos también
        for (Node hijo : nodo.getChildNodes()) {
            asignarNumerosPreorden(hijo);
        }
    }
//----------------------
    //-----> Camina por el flujo y recolecta los textos y sus respectivos números ya asignados
    private void generarCaminos(Node nodo, List<String> txtCamino, List<Integer> numCamino, ResultadoClase res) {

        //-----> Busca el número que le tocó a esta pieza  si no tiene le pone 0
        int numeroAsignado = numeracionNodos.getOrDefault(nodo, 0);

        //----/CASO BIFURCACIÓN (IF)\----
        //-----> Si es un  "if", abrimos las ramas de verdadero y falso
        if (nodo instanceof IfStmt) {
            IfStmt condicional = (IfStmt) nodo;
            //-----> Limpia los espacios en blanco de la condición para que quede compacta
            String condicion = "if(" + condicional.getCondition().toString().replaceAll("\\s+", "") + ")";

            // --->Ruta del SÍ 
            //-----> Copia el camino que llevaba guardado hasta este momento
            List<String> txtSi = new ArrayList<>(txtCamino);
            List<Integer> numSi = new ArrayList<>(numCamino);
            
            //-----> Agrega el texto del if y su número a la ruta del SÍ
            txtSi.add(condicion);
            if (numeroAsignado != 0) numSi.add(numeroAsignado);
            txtSi.add("condicion_sisi");
            
            //-----> Se mete a explorar todo lo que está adentro de las llaves del SÍ
            generarCaminos(condicional.getThenStmt(), txtSi, numSi, res);

            // --->Ruta del NO 
            //-----> Si el if tiene un "else", creamos su propia rama
            if (condicional.getElseStmt().isPresent()) {

                //-----> Copia el camino que llevaba antes del if
                List<String> txtNo = new ArrayList<>(txtCamino);
                List<Integer> numNo = new ArrayList<>(numCamino);
                
                //-----> Agrega el texto del if, su número y las marcas del else a la ruta del NO
                txtNo.add(condicion);
                if (numeroAsignado != 0) numNo.add(numeroAsignado);
                txtNo.add("else{}");
                txtNo.add("condicion_sino");
                
                //-----> Se mete a explorar todo lo que está adentro de las llaves del else
                generarCaminos(condicional.getElseStmt().get(), txtNo, numNo, res);
            }
            return; 
        }
//--------------------------
        //--------------/(Cualquier tipo de código)\----
        //-----> Convierte la pieza de código a texto limpio en una sola línea
        String textoNodo = nodo.toString().trim().replaceAll("\\s+", " ").replace("\n", "");
        
        //-----> Si no son llaves sueltas ni el método entero, y el texto es cortito, lo guarda
        if (!(nodo instanceof BlockStmt) && !(nodo instanceof MethodDeclaration) && textoNodo.length() < 60) {
            txtCamino.add(textoNodo);
            
            //-----> Guarda el número en la serie numérica si es válido y no estaba repetido
            if (numeroAsignado != 0 && !numCamino.contains(numeroAsignado)) {
                numCamino.add(numeroAsignado);
            }
        }

        //-----> Obtiene las piezas más chicas que componen a este nodo
        List<Node> hijos = nodo.getChildNodes();
        //-----> Si ya no hay más hijos abajo (llegamos a una punta del árbol)
        if (hijos.isEmpty()) {

            //-----> Junta todas las palabras usando el separador "|" para armar el renglón final
            String lineaFinal = String.join("|", txtCamino);

            //-----> Si el camino no es repetido y no está vacío, lo guarda en los resultados del JSON
            if (!res.vectorTexto.contains(lineaFinal) && !lineaFinal.isEmpty()) {
                res.vectorTexto.add(lineaFinal);
                res.vectorNumerico.add(new ArrayList<>(numCamino));
            }
        } else {
            //-----> Si todavía quedan hijos abajo, sigue bajando por cada uno de ellos
            for (Node hijo : hijos) {
                generarCaminos(hijo, txtCamino, numCamino, res);
            }
        }
    }
}