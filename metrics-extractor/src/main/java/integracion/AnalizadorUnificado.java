package integracion;

import dinamica.EjecutorCompleto;
import estatica.ProcesadorMetricas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// -----> Clase principal para correr los dos analisis (estatico y dinamico) a un mismo proyecto.
public class AnalizadorUnificado {

    // -----> Metodo main que controla y ejecuta todo el proceso.
    public static void main(String[] args) throws Exception {
        // -----> Lee los datos que mandas en la terminal y los guarda en un mapa.
        Map<String, String> params = parseArgs(args);

        // -----> Obtiene la ruta del proyecto y la carpeta de guardado.
        String rutaProyecto = params.get("proyecto");
        String carpetaSalida = params.getOrDefault("salida", "resultados");

        // -----> Si falta el proyecto, muestra un mensaje de ayuda y se detiene.
        if (rutaProyecto == null) {
            System.err.println("Uso: java -cp ... integracion.AnalizadorUnificado --proyecto:/ruta [--salida:resultados]");
            System.err.println("     (tambien acepta los parametros de dinamica: --batchSize --batchIndex --I --WI --F --classpath ...)");
            return;
        }

        // -----> Muestra el titulo del programa en la consola.
        System.out.println("==========================================================");
        System.out.println(" METRICS EXTRACTOR");
        System.out.println(" Proyecto: " + rutaProyecto);
        System.out.println("==========================================================");

        // -----> Crea rutas distintas para los resultados de cada modulo.
        String salidaEstatica = carpetaSalida + "/resultados_estaticos";
        String salidaDinamica = carpetaSalida + "/resultados_dinamicos";

        // -----> FASE ESTATICA: Lee solo el texto del codigo sin compilar.
        System.out.println("\n----------------------------------------------------------");
        System.out.println("-----> METRICA ESTATICA");
        System.out.println("----------------------------------------------------------");
        new ProcesadorMetricas().analizarUnProyecto(rutaProyecto, salidaEstatica);

        // -----> FASE DINAMICA: Compila y mide los tiempos del programa con JMH.
        System.out.println("\n----------------------------------------------------------");
        System.out.println("-----> METRICA DINAMICA");
        System.out.println("----------------------------------------------------------");
        String[] argsDinamicos = conSalidaOverride(args, salidaDinamica);
        EjecutorCompleto.main(argsDinamicos);

        // -----> Imprime el mensaje final indicando que todo termino bien.
        System.out.println("\n==========================================================");
        System.out.println(" ANALISIS  FINALIZADO");
        System.out.println("==========================================================");
        System.out.println(" Resultados estaticos en : " + salidaEstatica);
        System.out.println(" Resultados dinamicos en : " + salidaDinamica);
        System.out.println("==========================================================");
    }

    // -----> Cambia la carpeta de salida para la parte dinamica sin alterar lo demas.
    private static String[] conSalidaOverride(String[] argsOriginales, String nuevaSalida) {
        List<String> resultado = new ArrayList<>();
        boolean reemplazado = false;
        
        // -----> Revisa cada dato; si halla "--salida:", pone la nueva carpeta.
        for (String arg : argsOriginales) {
            if (arg.startsWith("--salida:")) {
                resultado.add("--salida:" + nuevaSalida);
                reemplazado = true;
            } else {
                resultado.add(arg);
            }
        }
        
        // -----> Si no existia "--salida:", la agrega al final.
        if (!reemplazado) {
            resultado.add("--salida:" + nuevaSalida);
        }
        return resultado.toArray(new String[0]);
    }

    // -----> Separa las opciones recibidas en la terminal (ej. --proyecto:ruta).
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            // -----> Solo revisa palabras que inicien con "--".
            if (arg.startsWith("--")) {
                // -----> Corta la palabra usando los ":" para sacar la clave y su valor.
                String[] parts = arg.substring(2).split(":", 2);
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
    }
}