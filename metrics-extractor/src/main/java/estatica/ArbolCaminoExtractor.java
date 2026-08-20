package estatica;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

//-----> Extractor de caminos AST
public class ArbolCaminoExtractor {

    //-----> Datos de metodos analizados
    public static class ResultadoMetodo {
        public String nombreMetodo; //-----> Nombre del metodo
        public List<String> vectorTexto = new ArrayList<>(); //-----> Caminos en texto
        public List<List<Integer>> vectorNumerico = new ArrayList<>(); //-----> Caminos en numeros
        public boolean truncado = false; //-----> Indica si supero el limite
    }

    //-----> Datos de clases analizadas
    public static class ResultadoClase {
        public String nombreClase; //-----> Nombre de clase
        public List<ResultadoMetodo> metodos = new ArrayList<>(); //-----> Lista de metodos
    }

    private int contadorGlobal = 1; //-----> Contador de nodos
    private Map<Node, Integer> numeracionNodos = new IdentityHashMap<>(); //-----> Mapa de IDs

    private static final int MAX_CAMINOS_POR_METODO = 500; //-----> Maximo de caminos

    //-----> Recorre metodos de la clase
    public ResultadoClase procesarClase(CompilationUnit cu, String nombreArchivo) {
        ResultadoClase resultado = new ResultadoClase();
        resultado.nombreClase = nombreArchivo;

        List<MethodDeclaration> metodos = cu.findAll(MethodDeclaration.class);

        for (MethodDeclaration metodo : metodos) {
            if (metodo.getBody().isPresent()) {
                contadorGlobal = 1;
                numeracionNodos.clear();

                ResultadoMetodo resultadoMetodo = new ResultadoMetodo();
                resultadoMetodo.nombreMetodo = metodo.getNameAsString();

                asignarNumerosPreorden(metodo.getBody().get());
                generarCaminos(metodo.getBody().get(), new ArrayList<>(), new ArrayList<>(), resultadoMetodo);

                resultado.metodos.add(resultadoMetodo);
            }
        }
        return resultado;
    }

    //-----> Asigna ID preorden a nodos
    private void asignarNumerosPreorden(Node nodo) {
        if (!(nodo instanceof BlockStmt && nodo.getParentNode().isPresent() && nodo.getParentNode().get() instanceof MethodDeclaration)) {
            numeracionNodos.put(nodo, contadorGlobal++);
        }
        for (Node hijo : nodo.getChildNodes()) {
            asignarNumerosPreorden(hijo);
        }
    }

    //-----> Genera caminos recursivamente
    private void generarCaminos(Node nodo, List<String> txtCamino, List<Integer> numCamino, ResultadoMetodo res) {
        //-----> Corta si supera el limite
        if (res.vectorTexto.size() >= MAX_CAMINOS_POR_METODO) {
            res.truncado = true;
            return;
        }

        int numeroAsignado = numeracionNodos.getOrDefault(nodo, 0);

        if (nodo instanceof IfStmt) { //-----> Control de condicional IF
            IfStmt condicional = (IfStmt) nodo;
            String condicion = "if(" + condicional.getCondition().toString().replaceAll("\\s+", "") + ")";

            List<String> txtSi = new ArrayList<>(txtCamino);
            List<Integer> numSi = new ArrayList<>(numCamino);

            txtSi.add(condicion);
            if (numeroAsignado != 0) numSi.add(numeroAsignado);
            txtSi.add("condicion_sisi");

            generarCaminos(condicional.getThenStmt(), txtSi, numSi, res);

            //-----> Validacion de limite en rama THEN
            if (res.vectorTexto.size() >= MAX_CAMINOS_POR_METODO) {
                res.truncado = true;
                return;
            }

            if (condicional.getElseStmt().isPresent()) { //-----> Rama ELSE
                List<String> txtNo = new ArrayList<>(txtCamino);
                List<Integer> numNo = new ArrayList<>(numCamino);

                txtNo.add(condicion);
                if (numeroAsignado != 0) numNo.add(numeroAsignado);
                txtNo.add("else{}");
                txtNo.add("condicion_sino");

                generarCaminos(condicional.getElseStmt().get(), txtNo, numNo, res);
            }
            return;
        }

        String textoNodo = nodo.toString().trim().replaceAll("\\s+", " ").replace("\n", "");

        List<String> txtActual = new ArrayList<>(txtCamino);
        List<Integer> numActual = new ArrayList<>(numCamino);

        if (!(nodo instanceof BlockStmt) && !(nodo instanceof MethodDeclaration) && textoNodo.length() < 60) {
            txtActual.add(textoNodo);

            if (numeroAsignado != 0 && !numActual.contains(numeroAsignado)) {
                numActual.add(numeroAsignado);
            }
        }

        List<Node> hijos = nodo.getChildNodes();
        if (hijos.isEmpty()) { //-----> Al llegar a una hoja guarda el camino
            String lineaFinal = String.join("|", txtActual);

            if (!res.vectorTexto.contains(lineaFinal) && !lineaFinal.isEmpty()) {
                res.vectorTexto.add(lineaFinal);
                res.vectorNumerico.add(new ArrayList<>(numActual));
            }
        } else {
            for (Node hijo : hijos) {
                generarCaminos(hijo, txtActual, numActual, res);
            }
        }
    }
}