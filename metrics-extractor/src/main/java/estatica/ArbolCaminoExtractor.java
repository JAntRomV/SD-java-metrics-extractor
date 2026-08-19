package estatica;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

//-----> Extrae caminos del AST de una clase Java
public class ArbolCaminoExtractor {

    //-----> Guarda resultados de un metodo
    public static class ResultadoMetodo {
        public String nombreMetodo;
        public List<String> vectorTexto = new ArrayList<>();
        public List<List<Integer>> vectorNumerico = new ArrayList<>();
        //-----> 🔌 NUEVO: marca si se alcanzo el limite de caminos y se dejaron
        //-----> de generar mas (ver MAX_CAMINOS_POR_METODO abajo).
        public boolean truncado = false;
    }

    //-----> Guarda resultados de una clase
    public static class ResultadoClase {
        public String nombreClase;
        public List<ResultadoMetodo> metodos = new ArrayList<>();
    }

    private int contadorGlobal = 1;
    private Map<Node, Integer> numeracionNodos = new IdentityHashMap<>();

    //-----> 🔌 NUEVO: tope de caminos raiz-a-hoja que se generan por metodo.
    //-----> Sin este limite, un metodo con varios if/else anidados puede generar
    //-----> una explosion combinatoria de caminos (crece potencialmente 2^n con
    //-----> el numero de decisiones), llenando la memoria del proceso antes de
    //-----> que nada se llegue a guardar en disco o en Mongo -esto era la causa
    //-----> raiz de los OOM que tumbaban el contenedor a media generacion de
    //-----> [CAMINO JSON]-.
    private static final int MAX_CAMINOS_POR_METODO = 500;

    //-----> Procesa metodos de la clase y extrae caminos
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

    //-----> Asigna un id numerico preorden a cada nodo
    private void asignarNumerosPreorden(Node nodo) {
        if (!(nodo instanceof BlockStmt && nodo.getParentNode().isPresent() && nodo.getParentNode().get() instanceof MethodDeclaration)) {
            numeracionNodos.put(nodo, contadorGlobal++);
        }
        for (Node hijo : nodo.getChildNodes()) {
            asignarNumerosPreorden(hijo);
        }
    }

    //-----> Genera los caminos recursivamente por nodos
    private void generarCaminos(Node nodo, List<String> txtCamino, List<Integer> numCamino, ResultadoMetodo res) {
        //-----> 🔌 NUEVO: corta la recursion en cuanto se alcanza el tope, en vez
        //-----> de seguir explorando ramas y acumulando mas caminos en memoria.
        if (res.vectorTexto.size() >= MAX_CAMINOS_POR_METODO) {
            res.truncado = true;
            return;
        }

        int numeroAsignado = numeracionNodos.getOrDefault(nodo, 0);

        if (nodo instanceof IfStmt) {
            IfStmt condicional = (IfStmt) nodo;
            String condicion = "if(" + condicional.getCondition().toString().replaceAll("\\s+", "") + ")";

            List<String> txtSi = new ArrayList<>(txtCamino);
            List<Integer> numSi = new ArrayList<>(numCamino);

            txtSi.add(condicion);
            if (numeroAsignado != 0) numSi.add(numeroAsignado);
            txtSi.add("condicion_sisi");

            generarCaminos(condicional.getThenStmt(), txtSi, numSi, res);

            //-----> 🔌 NUEVO: si ya se llego al tope explorando la rama "si", no
            //-----> vale la pena explorar tambien la rama "no".
            if (res.vectorTexto.size() >= MAX_CAMINOS_POR_METODO) {
                res.truncado = true;
                return;
            }

            if (condicional.getElseStmt().isPresent()) {
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

        //-----> Copia listas para no contaminar hermanos
        List<String> txtActual = new ArrayList<>(txtCamino);
        List<Integer> numActual = new ArrayList<>(numCamino);

        if (!(nodo instanceof BlockStmt) && !(nodo instanceof MethodDeclaration) && textoNodo.length() < 60) {
            txtActual.add(textoNodo);

            if (numeroAsignado != 0 && !numActual.contains(numeroAsignado)) {
                numActual.add(numeroAsignado);
            }
        }

        List<Node> hijos = nodo.getChildNodes();
        if (hijos.isEmpty()) {
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