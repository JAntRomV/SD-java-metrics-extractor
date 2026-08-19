package almacenamiento;

import org.bson.Document;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

//-----> Lee e interpreta archivos JSON/CSV generados en la salida de análisis.
public class LectorResultados {

    private static final Pattern SUFIJO_METRICAS_JSON = Pattern.compile("Metricas\\.json$");
    private static final Pattern SUFIJO_CAMINOS = Pattern.compile("_caminos\\.json$");

    //-----> Límite máximo de filas guardadas por subdocumento de métricas dinámicas.
    private static final int MAX_FILAS_POR_PARTE = 15;

    //-----> 🔌 NUEVO: límite máximo de caminos (aplanados across todos los métodos
    //-----> de la clase) guardados por subdocumento estático. Antes el array
    //-----> completo de caminos de una clase se guardaba embebido en un solo
    //-----> Document -para clases con muchos métodos/ramas eso podía pesar
    //-----> cientos de KB por documento en repo_metrics_static-.
    private static final int MAX_CAMINOS_POR_PARTE = 300;

    //-----> Interfaz funcional para transmitir documentos estáticos procesados.
    @FunctionalInterface
    public interface ConsumidorClase {
        void aceptar(Document claseDoc) throws Exception;
    }

    //-----> 🔌 NUEVO: interfaz funcional para transmitir fragmentos de caminos
    //-----> de una clase, ya separados del documento base de metricasJson.
    @FunctionalInterface
    public interface ConsumidorCaminoParte {
        void aceptar(Document caminoParteDoc) throws Exception;
    }

    //-----> Interfaz funcional para transmitir documentos dinámicos fragmentados.
    @FunctionalInterface
    public interface ConsumidorMetodoDinamico {
        void aceptar(Document dinamicoDoc) throws Exception;
    }

    //-----> Procesa secuencialmente los JSON estáticos generados por cada clase.
    //-----> 🔌 MODIFICADO: ahora recibe un segundo consumidor para los caminos,
    //-----> que ya NO van embebidos dentro del Document de la clase -se fragmentan
    //-----> y se entregan por separado, ver aplanarCaminos()-.
    public int procesarClasesUnaAUna(File carpetaEstaticos, ConsumidorClase consumidor, ConsumidorCaminoParte consumidorCaminos) throws Exception {
        if (carpetaEstaticos == null || !carpetaEstaticos.exists()) return 0;

        File[] archivos = carpetaEstaticos.listFiles();
        if (archivos == null) return 0;

        Map<String, Map<String, File>> archivosPorClase = new LinkedHashMap<>();

        for (File archivo : archivos) {
            if (archivo.isDirectory()) continue;
            String nombre = archivo.getName();

            if (SUFIJO_METRICAS_JSON.matcher(nombre).find() && !nombre.endsWith("_caminos.json")) {
                registrar(archivosPorClase, SUFIJO_METRICAS_JSON.matcher(nombre).replaceFirst(""), "metricasJson", archivo);
            } else if (SUFIJO_CAMINOS.matcher(nombre).find()) {
                registrar(archivosPorClase, SUFIJO_CAMINOS.matcher(nombre).replaceFirst(""), "caminos", archivo);
            }
        }

        int total = 0;
        for (Map.Entry<String, Map<String, File>> entrada : archivosPorClase.entrySet()) {
            String claseBase = entrada.getKey();
            Map<String, File> archivosDeClase = entrada.getValue();

            Document claseDoc = new Document();
            claseDoc.put("clase", claseBase);
            if (archivosDeClase.containsKey("metricasJson")) {
                claseDoc.put("metricasJson", leerJson(archivosDeClase.get("metricasJson")));
            }

            consumidor.aceptar(claseDoc);
            total++;

            //-----> 🔌 NUEVO: los caminos se leen, se aplanan (metodo + camino) y
            //-----> se fragmentan en partes de MAX_CAMINOS_POR_PARTE, en vez de
            //-----> ir embebidos completos dentro de claseDoc.
            if (archivosDeClase.containsKey("caminos")) {
                Object caminosParseados = leerJson(archivosDeClase.get("caminos"));
                List<Document> aplanado = aplanarCaminos(caminosParseados);

                if (!aplanado.isEmpty()) {
                    int totalPartes = Math.max(1, (int) Math.ceil(aplanado.size() / (double) MAX_CAMINOS_POR_PARTE));
                    for (int i = 0; i < totalPartes; i++) {
                        int inicio = i * MAX_CAMINOS_POR_PARTE;
                        int fin = Math.min(inicio + MAX_CAMINOS_POR_PARTE, aplanado.size());

                        Document parteDoc = new Document("clase", claseBase)
                                .append("parte", i + 1)
                                .append("totalPartes", totalPartes)
                                .append("caminos", new ArrayList<>(aplanado.subList(inicio, fin)));

                        consumidorCaminos.aceptar(parteDoc);
                    }
                }
            }
        }

        return total;
    }

    //-----> 🔌 NUEVO: aplana el JSON de caminos (agrupado por metodo) en una sola
    //-----> lista donde cada entrada trae su propio "metodo", para poder repartirla
    //-----> en partes de tamaño parejo sin importar cuantos metodos tenia la clase.
    private List<Document> aplanarCaminos(Object caminosJson) {
        List<Document> aplanado = new ArrayList<>();
        if (!(caminosJson instanceof Document)) return aplanado; //-----> JSON crudo sin parsear, no se puede fragmentar

        Document doc = (Document) caminosJson;
        List<Document> metodos = doc.getList("metodos", Document.class, new ArrayList<>());
        for (Document metodoDoc : metodos) {
            String nombreMetodo = metodoDoc.getString("metodo");
            List<Document> caminos = metodoDoc.getList("caminos", Document.class, new ArrayList<>());
            for (Document camino : caminos) {
                Document entradaAplanada = new Document("metodo", nombreMetodo);
                entradaAplanada.putAll(camino); //-----> camino_id, texto, serie_numerica
                aplanado.add(entradaAplanada);
            }
        }
        return aplanado;
    }

    //-----> Agrupa archivos escaneados por nombre de clase asociada.
    private void registrar(Map<String, Map<String, File>> mapa, String claseBase, String tipo, File archivo) {
        mapa.computeIfAbsent(claseBase, k -> new LinkedHashMap<>()).put(tipo, archivo);
    }

    //-----> Fragmenta y procesa filas de CSVs dinámicos por clase en partes pequeñas.
    public int procesarDinamicosPorClaseUnaAUna(File carpetaDinamicos, ConsumidorMetodoDinamico consumidor) throws Exception {
        if (carpetaDinamicos == null || !carpetaDinamicos.exists()) return 0;

        Map<String, List<Document>> benchmarksPorClase = new LinkedHashMap<>();
        File benchmarks = new File(carpetaDinamicos, "Benchmarks.csv");
        if (benchmarks.exists()) {
            benchmarksPorClase = agruparPorClase(leerCsvComoFilas(benchmarks), "Param: metodoObjetivo");
        }

        Map<String, List<Document>> caminosPorClase = new LinkedHashMap<>();
        File caminos = new File(carpetaDinamicos, "cronometro_caminos.csv");
        if (caminos.exists()) {
            caminosPorClase = agruparPorClase(leerCsvComoFilas(caminos), "Clase");
        }

        Set<String> todasLasClases = new LinkedHashSet<>();
        todasLasClases.addAll(benchmarksPorClase.keySet());
        todasLasClases.addAll(caminosPorClase.keySet());

        int totalDocumentosSubidos = 0;

        for (String clase : todasLasClases) {
            List<Document> filasBenchmark = benchmarksPorClase.getOrDefault(clase, new ArrayList<>());
            List<Document> filasCaminos = caminosPorClase.getOrDefault(clase, new ArrayList<>());

            int totalFilas = filasBenchmark.size() + filasCaminos.size();
            int totalPartes = Math.max(1, (int) Math.ceil(totalFilas / (double) MAX_FILAS_POR_PARTE));

            List<List<Document>> partesBenchmark = partir(filasBenchmark, totalPartes);
            List<List<Document>> partesCaminos = partir(filasCaminos, totalPartes);

            for (int i = 0; i < totalPartes; i++) {
                Document doc = new Document();
                doc.put("clase", clase);
                doc.put("parte", i + 1);
                doc.put("totalPartes", totalPartes);
                doc.put("benchmarks", partesBenchmark.get(i));
                doc.put("cronometroCaminos", partesCaminos.get(i));

                consumidor.aceptar(doc);
                totalDocumentosSubidos++;
            }
        }

        return totalDocumentosSubidos;
    }

    //-----> Agrupa filas en un mapa cuya clave es la clase extraída de un campo.
    private Map<String, List<Document>> agruparPorClase(List<Document> filas, String nombreColumnaLlave) {
        Map<String, List<Document>> agrupado = new LinkedHashMap<>();
        String llaveSanitizada = sanitizarLlave(nombreColumnaLlave);

        for (Document fila : filas) {
            String valor = fila.getString(llaveSanitizada);
            if (valor == null || valor.isBlank()) continue;

            String clase = valor.contains("#") ? valor.substring(0, valor.indexOf('#')) : valor;
            agrupado.computeIfAbsent(clase, k -> new ArrayList<>()).add(fila);
        }
        return agrupado;
    }

    //-----> Divide de forma equitativa una lista de documentos en N sublistas.
    private List<List<Document>> partir(List<Document> lista, int enPartes) {
        List<List<Document>> resultado = new ArrayList<>();
        int total = lista.size();
        int base = total / enPartes;
        int resto = total % enPartes;
        int inicio = 0;

        for (int i = 0; i < enPartes; i++) {
            int tamanoParte = base + (i < resto ? 1 : 0);
            int fin = Math.min(inicio + tamanoParte, total);
            resultado.add(new ArrayList<>(lista.subList(inicio, fin)));
            inicio = fin;
        }
        return resultado;
    }

    //-----> Lee archivos descriptivos de texto de resúmenes de escaneo y rutas.
    public Document leerResumenesDeProyecto(File carpetaDinamicos) throws Exception {
        Document resultado = new Document();
        if (carpetaDinamicos == null || !carpetaDinamicos.exists()) {
            return resultado;
        }

        File resumenEscaneo = new File(carpetaDinamicos, "_escaneo_resumen.txt");
        if (resumenEscaneo.exists()) {
            resultado.put("escaneoResumen", leerResumenComoDocumento(resumenEscaneo));
        }

        File resumenCaminos = new File(carpetaDinamicos, "_caminos_resumen.txt");
        if (resumenCaminos.exists()) {
            resultado.put("caminosResumen", leerResumenComoDocumento(resumenCaminos));
        }

        return resultado;
    }

    //-----> Lee y convierte un archivo JSON a BSON Document.
    private Object leerJson(File archivo) throws Exception {
        String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
        try {
            return Document.parse(contenido);
        } catch (Exception e) {
            return contenido;
        }
    }

    //-----> Convierte las filas de un CSV en una lista de documentos BSON.
    private List<Document> leerCsvComoFilas(File archivo) throws Exception {
        List<String> lineas = Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8);
        List<Document> filas = new ArrayList<>();
        if (lineas.isEmpty()) return filas;

        String[] encabezados = parsearLineaCsv(lineas.get(0));

        for (int i = 1; i < lineas.size(); i++) {
            if (lineas.get(i).isBlank()) continue;
            String[] valores = parsearLineaCsv(lineas.get(i));
            Document fila = new Document();
            for (int j = 0; j < encabezados.length && j < valores.length; j++) {
                fila.put(sanitizarLlave(encabezados[j].trim()), valores[j].trim());
            }
            filas.add(fila);
        }
        return filas;
    }

    //-----> Parsea líneas en formato 'clave=valor' a un Document BSON.
    private Document leerResumenComoDocumento(File archivo) throws Exception {
        Document resultado = new Document();
        for (String linea : Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8)) {
            String[] partes = linea.split("=", 2);
            if (partes.length == 2) {
                resultado.put(sanitizarLlave(partes[0].trim()), partes[1].trim());
            }
        }
        return resultado;
    }

    //-----> Parsea una línea de texto CSV respetando comillas y delimitadores.
    private String[] parsearLineaCsv(String linea) {
        List<String> campos = new ArrayList<>();
        for (String parte : linea.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String limpio = parte.trim();
            if (limpio.length() >= 2 && limpio.startsWith("\"") && limpio.endsWith("\"")) {
                limpio = limpio.substring(1, limpio.length() - 1);
            }
            campos.add(limpio);
        }
        return campos.toArray(new String[0]);
    }

    //-----> Reemplaza caracteres no permitidos en nombres de claves de MongoDB.
    private String sanitizarLlave(String texto) {
        return texto.replace('.', '_');
    }
}