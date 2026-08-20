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

//-----> Convierte archivos JSON/CSV a Documentos
public class LectorResultados {

    private static final Pattern SUFIJO_METRICAS_JSON = Pattern.compile("Metricas\\.json$");
    private static final Pattern SUFIJO_CAMINOS = Pattern.compile("_caminos\\.json$");

    private static final int MAX_FILAS_POR_PARTE = 15;
    private static final int MAX_CAMINOS_POR_PARTE = 300;

    //-----> Interfaz para procesar clases
    @FunctionalInterface
    public interface ConsumidorClase {
        void aceptar(Document claseDoc) throws Exception;
    }

    //-----> Interfaz para procesar caminos
    @FunctionalInterface
    public interface ConsumidorCaminoParte {
        void aceptar(Document caminoParteDoc) throws Exception;
    }

    //-----> Interfaz para procesar dinamicos
    @FunctionalInterface
    public interface ConsumidorMetodoDinamico {
        void aceptar(Document dinamicoDoc) throws Exception;
    }

    //-----> Lee los archivos JSON estaticos
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

    //-----> Ordena los caminos en lista plana
    private List<Document> aplanarCaminos(Object caminosJson) {
        List<Document> aplanado = new ArrayList<>();
        if (!(caminosJson instanceof Document)) return aplanado;

        Document doc = (Document) caminosJson;
        List<Document> metodos = doc.getList("metodos", Document.class, new ArrayList<>());
        for (Document metodoDoc : metodos) {
            String nombreMetodo = metodoDoc.getString("metodo");
            List<Document> caminos = metodoDoc.getList("caminos", Document.class, new ArrayList<>());
            for (Document camino : caminos) {
                Document entradaAplanada = new Document("metodo", nombreMetodo);
                entradaAplanada.putAll(camino);
                aplanado.add(entradaAplanada);
            }
        }
        return aplanado;
    }

    //-----> Agrupa archivos escaneados por clase
    private void registrar(Map<String, Map<String, File>> mapa, String claseBase, String tipo, File archivo) {
        mapa.computeIfAbsent(claseBase, k -> new LinkedHashMap<>()).put(tipo, archivo);
    }

    //-----> Lee los CSVs dinamicos por partes
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

    //-----> Clasifica filas segun la columna clase
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

    //-----> Divide una lista en partes iguales
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

    //-----> Lee archivos de texto del resumen
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

    //-----> Parsea un JSON a BSON Document
    private Object leerJson(File archivo) throws Exception {
        String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8);
        try {
            return Document.parse(contenido);
        } catch (Exception e) {
            return contenido;
        }
    }

    //-----> Parsea un CSV a lista de Documentos
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

    //-----> Lee un TXT con formato clave=valor
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

    //-----> Corta la linea CSV por comas
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

    //-----> Cambia puntos por guiones bajos
    private String sanitizarLlave(String texto) {
        return texto.replace('.', '_');
    }
}