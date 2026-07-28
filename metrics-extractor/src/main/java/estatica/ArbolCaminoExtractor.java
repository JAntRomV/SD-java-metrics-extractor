package estatica;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// -----> Extrae los caminos del árbol de código método por método.
public class ArbolCaminoExtractor {

    // -----> Guarda los caminos (en texto y números) de un solo método.
    public static class ResultadoMetodo {
        public String nombreMetodo;
        public List<String> vectorTexto = new ArrayList<>();
        public List<List<Integer>> vectorNumerico = new ArrayList<>();
    }

    // -----> Guarda el nombre de la clase y la lista de todos sus métodos.
    public static class ResultadoClase {
        public String nombreClase;
        public List<ResultadoMetodo> metodos = new ArrayList<>();
    }

    // -----> Numero que se le asigna a cada nodo del código.
    private int contadorGlobal = 1;

    // -----> Mapa para darle un ID único a cada objeto nodo.
    private Map<Node, Integer> numeracionNodos = new IdentityHashMap<>();

    // -----> Recorre el archivo de Java y saca los caminos de cada método.
    public ResultadoClase procesarClase(CompilationUnit cu, String nombreArchivo) {
        ResultadoClase resultado = new ResultadoClase();
        resultado.nombreClase = nombreArchivo;

        // -----> Busca todos los métodos del archivo.
        List<MethodDeclaration> metodos = cu.findAll(MethodDeclaration.class);

        for (MethodDeclaration metodo : metodos) {
            if (metodo.getBody().isPresent()) {
                // -----> Reinicia el contador para que cada método empiece desde 1.
                contadorGlobal = 1;
                numeracionNodos.clear();

                ResultadoMetodo resultadoMetodo = new ResultadoMetodo();
                resultadoMetodo.nombreMetodo = metodo.getNameAsString();

                // -----> Le pone números a las piezas y genera sus rutas.
                asignarNumerosPreorden(metodo.getBody().get());
                generarCaminos(metodo.getBody().get(), new ArrayList<>(), new ArrayList<>(), resultadoMetodo);

                resultado.metodos.add(resultadoMetodo);
            }
        }
        return resultado;
    }

    // -----> Asigna un número a cada instrucción o parte del código.
    private void asignarNumerosPreorden(Node nodo) {

        // -----> Salta el bloque principal para no gastar el número 1.
        if (!(nodo instanceof BlockStmt && nodo.getParentNode().isPresent() && nodo.getParentNode().get() instanceof MethodDeclaration)) {
          
            // -----> Guarda el nodo con su número y suma 1 al contador.
            numeracionNodos.put(nodo, contadorGlobal++);
        }
        // -----> Sigue numerando las piezas de más abajo.
        for (Node hijo : nodo.getChildNodes()) {
            asignarNumerosPreorden(hijo);
        }
    }

    // -----> Camina por el código uniendo el texto y sus números.
    private void generarCaminos(Node nodo, List<String> txtCamino, List<Integer> numCamino, ResultadoMetodo res) {

        // -----> Obtiene el número asignado a este nodo.
        int numeroAsignado = numeracionNodos.getOrDefault(nodo, 0);

        // -----> Maneja las bifurcaciones cuando encuentra un "if".
        if (nodo instanceof IfStmt) {
            IfStmt condicional = (IfStmt) nodo;
            // -----> Limpia los espacios de la condición.
            String condicion = "if(" + condicional.getCondition().toString().replaceAll("\\s+", "") + ")";

            // -----> Ruta para el camino del SÍ.
            List<String> txtSi = new ArrayList<>(txtCamino);
            List<Integer> numSi = new ArrayList<>(numCamino);
            
            txtSi.add(condicion);
            if (numeroAsignado != 0) numSi.add(numeroAsignado);
            txtSi.add("condicion_sisi");
            
            // -----> Explora la rama del SÍ.
            generarCaminos(condicional.getThenStmt(), txtSi, numSi, res);

            // -----> Ruta para el camino del NO (si hay un else).
            if (condicional.getElseStmt().isPresent()) {

                List<String> txtNo = new ArrayList<>(txtCamino);
                List<Integer> numNo = new ArrayList<>(numCamino);
                
                txtNo.add(condicion);
                if (numeroAsignado != 0) numNo.add(numeroAsignado);
                txtNo.add("else{}");
                txtNo.add("condicion_sino");
                
                // -----> Explora la rama del NO.
                generarCaminos(condicional.getElseStmt().get(), txtNo, numNo, res);
            }
            return; 
        }

        // -----> Procesa cualquier otra línea de código.
        String textoNodo = nodo.toString().trim().replaceAll("\\s+", " ").replace("\n", "");
        
        // -----> Guarda el texto si es corto y no son llaves sueltas.
        if (!(nodo instanceof BlockStmt) && !(nodo instanceof MethodDeclaration) && textoNodo.length() < 60) {
            txtCamino.add(textoNodo);
            
            // -----> Agrega el número de nodo si es válido.
            if (numeroAsignado != 0 && !numCamino.contains(numeroAsignado)) {
                numCamino.add(numeroAsignado);
            }
        }

        // -----> Revisa las piezas hijas.
        List<Node> hijos = nodo.getChildNodes();
        
        // -----> Si llegó al final del camino, arma y guarda la línea.
        if (hijos.isEmpty()) {

            // -----> Une las palabras usando el separador "|".
            String lineaFinal = String.join("|", txtCamino);

            // -----> Guarda el camino si no está duplicado dentro del mismo método.
            if (!res.vectorTexto.contains(lineaFinal) && !lineaFinal.isEmpty()) {
                res.vectorTexto.add(lineaFinal);
                res.vectorNumerico.add(new ArrayList<>(numCamino));
            }
        } else {
            // -----> Si tiene más piezas abajo, sigue bajando.
            for (Node hijo : hijos) {
                generarCaminos(hijo, txtCamino, numCamino, res);
            }
        }
    }
}