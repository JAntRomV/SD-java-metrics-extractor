package integracion;

import dinamica.EjecutorCompleto;
import estatica.ProcesadorMetricas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//-----> Ejecuta el análisis estático y dinámico completo
public class AnalizadorUnificado {

    public static void main(String[] args) throws Exception {
        //-----> Obtiene parámetros de entrada
        Map<String, String> params = parseArgs(args);

        String rutaProyecto = params.get("proyecto");
        String carpetaSalida = params.getOrDefault("salida", "resultados");

        //-----> Revisa la ruta obligatoria del proyecto
        if (rutaProyecto == null) {
            System.err.println("Uso: java -cp ... integracion.AnalizadorUnificado --proyecto:/ruta [--salida:resultados]");
            System.err.println("     (tambien acepta los parametros de dinamica: --batchSize --batchIndex --I --WI --F --classpath ...)");
            return;
        }

        System.out.println("==========================================================");
        System.out.println(" EXTRACCION DE METRICAS ESTATICAS Y DINAMICAS)");
        System.out.println(" Proyecto: " + rutaProyecto);
        System.out.println("==========================================================");

        //-----> Configura rutas de salida para cada análisis
        String salidaEstatica = carpetaSalida + "/resultados_estaticos";
        String salidaDinamica = carpetaSalida + "/resultados_dinamicos";

        //-----> Corre el análisis estático
        System.out.println("\n----------------------------------------------------------");
        System.out.println("-----> METRICAS ESTATICAS");
        System.out.println("----------------------------------------------------------");
        new ProcesadorMetricas().analizarUnProyecto(rutaProyecto, salidaEstatica);

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

    //-----> Modifica la carpeta de salida en los argumentos
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

    //-----> Convierte arreglos de argumentos a mapa clave-valor
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