package integracion;

import almacenamiento.EstadoAnalisis;
import dinamica.EjecutorCompleto;
import estatica.ProcesadorMetricas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//-----> Ejecuta el análisis estático y dinámico completo
public class AnalizadorUnificado {

    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);

        String rutaProyecto = params.get("proyecto");
        String carpetaSalida = params.getOrDefault("salida", "resultados");

        if (rutaProyecto == null) {
            System.err.println("Uso: java -cp ... integracion.AnalizadorUnificado --proyecto:/ruta [--salida:resultados]");
            System.err.println("     (tambien acepta los parametros de dinamica: --batchSize --batchIndex --I --WI --F --classpath ...)");
            return;
        }

        System.out.println("==========================================================");
        System.out.println(" EXTRACCION DE METRICAS ESTATICAS Y DINAMICAS)");
        System.out.println(" Proyecto: " + rutaProyecto);
        System.out.println("==========================================================");

        String salidaEstatica = carpetaSalida + "/resultados_estaticos";
        String salidaDinamica = carpetaSalida + "/resultados_dinamicos";

        //-----> Corre el análisis estático
        System.out.println("\n----------------------------------------------------------");
        System.out.println("-----> METRICAS ESTATICAS");
        System.out.println("----------------------------------------------------------");

        //-----> AGREGADO: marca aqui mismo, justo antes/despues de correr el
        //-----> analisis estatico real -no en OrquestadorRepos, que solo se
        //-----> enteraba de "completada" hasta que TODO el pipeline terminaba-
        EstadoAnalisis.marcarFase("estatica", EstadoAnalisis.EstadoFase.EN_PROGRESO);
        new ProcesadorMetricas().analizarUnProyecto(rutaProyecto, salidaEstatica);
        EstadoAnalisis.marcarFase("estatica", EstadoAnalisis.EstadoFase.COMPLETADA);

        //-----> Corre el análisis dinámico
        System.out.println("\n----------------------------------------------------------");
        System.out.println("-----> METRICAS DINAMICAS");
        System.out.println("----------------------------------------------------------");
        String[] argsDinamicos = conSalidaOverride(args, salidaDinamica);
        EjecutorCompleto.main(argsDinamicos);

        System.out.println("\n==========================================================");
        System.out.println(" EXTRACCION DE METRICAS FINALIZADO");
        System.out.println("==========================================================");
        System.out.println(" Resultados estaticos en : " + salidaEstatica);
        System.out.println(" Resultados dinamicos en : " + salidaDinamica);
        System.out.println("==========================================================");
    }

    private static String[] conSalidaOverride(String[] argsOriginales, String nuevaSalida) {
        List<String> resultado = new ArrayList<>();
        boolean reemplazado = false;
        for (String arg : argsOriginales) {
            if (arg.startsWith("--salida:")) {
                resultado.add("--salida:" + nuevaSalida);
                reemplazado = true;
            } else {
                resultado.add(arg);
            }
        }
        if (!reemplazado) {
            resultado.add("--salida:" + nuevaSalida);
        }
        return resultado.toArray(new String[0]);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split(":", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
    }
}