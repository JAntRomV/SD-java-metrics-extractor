package almacenamiento;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//-----> Diagnostico de almacenamiento Mongo
public class DiagnosticoAlmacenamiento implements AutoCloseable {

    //-----> Limite de advertencia de tamano
    private static final long ADVERTENCIA_TAMANO_BYTES = 12L * 1024 * 1024;

    private final MongoClient cliente;
    private final MongoDatabase baseDatos;
    private final MongoCollection<Document> coleccion;
    private final MongoCollection<Document> coleccionClases;
    private final MongoCollection<Document> coleccionDinamicas;

    //-----> Constructor con configuracion
    public DiagnosticoAlmacenamiento(ConfiguracionMongo config) {
        this.cliente = MongoClients.create(config.construirUri());
        this.baseDatos = cliente.getDatabase(config.baseDatos);
        this.coleccion = baseDatos.getCollection(config.coleccion);
        this.coleccionClases = baseDatos.getCollection(config.coleccionClases);
        this.coleccionDinamicas = baseDatos.getCollection(config.coleccionDinamicas);
    }

    //-----> Revisa estado de un repositorio
    public Document diagnosticarRepo(String idRepo) {
        Document repo = coleccion.find(Filters.eq("_id", idRepo)).first();
        Document reporte = new Document("repoId", idRepo);

        if (repo == null) {
            reporte.append("encontrado", false).append("mensaje", "No existe ningun repo con ese _id en el catalogo.");
            return reporte;
        }

        String status = repo.getString("status");
        reporte.append("encontrado", true).append("status", status);

        //-----> Extrae subdocumento de metricas
        Document metrics = repo.get("metrics", Document.class);

        if ("metrics_failed".equals(status) && metrics != null) {
            reporte.append("errorRegistrado", metrics.getString("error"));
        }

        //-----> 🔌 NUEVO: si el repo quedo en "solo estatico completo", muestra la
        //-----> razon guardada por AlmacenMetricasMongo.marcarSoloEstaticoCompleto()
        //-----> de por que la fase dinamica no genero datos.
        if ("metrics_static_only".equals(status) && metrics != null) {
            Document dinamicasMeta = metrics.get("dinamicas", Document.class);
            if (dinamicasMeta != null) {
                reporte.append("razonSinDatosDinamicos", dinamicasMeta.getString("razonSinDatos"));
            }
        }

        Document metricsEstaticas = metrics != null ? metrics.get("estaticas", Document.class) : null;
        int clasesEsperadas = metricsEstaticas != null ? metricsEstaticas.getInteger("totalClases", 0) : 0;

        //-----> Conteo de clases estaticas
        //-----> 🔌 MODIFICADO: repo_metrics_static ahora tiene dos tipos de
        //-----> documentos por clase -el doc base (metricasJson) y N fragmentos de
        //-----> caminos, distinguibles porque los fragmentos traen "parte"-. Sin
        //-----> este filtro, este conteo mezclaba ambos e inflaba
        //-----> "clasesEncontradasEnMongo" con los fragmentos.
        long clasesReales = coleccionClases.countDocuments(
                Filters.and(Filters.eq("repoId", idRepo), Filters.exists("parte", false)));
        double espacioClasesMB = estimarEspacioRepoMB(coleccionClases, idRepo);

        Document diagnosticoEstatico = new Document()
                .append("clasesEsperadas", clasesEsperadas)
                .append("clasesEncontradasEnMongo", clasesReales)
                .append("espacioEstimadoMB", redondear(espacioClasesMB))
                .append("faltanTablas", clasesReales < clasesEsperadas);
        reporte.append("estatica", diagnosticoEstatico);

        reporte.append("dinamica", diagnosticarDinamica(idRepo));

        reporte.append("documentosCercaDelLimite16MB", buscarDocumentosCercaDelLimite(idRepo));

        return reporte;
    }

    //-----> Revisa metricas dinamicas por clase
    private Document diagnosticarDinamica(String idRepo) {
        Map<String, Integer> partesEncontradasPorClase = new LinkedHashMap<>();
        Map<String, Integer> partesDeclaradasPorClase = new LinkedHashMap<>();
        long filasBenchmarksTotales = 0;
        long filasCaminosTotales = 0;
        long partesEncontradas = 0;

        for (Document doc : coleccionDinamicas.find(Filters.eq("repoId", idRepo))) {
            partesEncontradas++;
            String clase = doc.getString("clase");
            int totalPartesDeclarado = doc.getInteger("totalPartes", 1);

            partesEncontradasPorClase.merge(clase, 1, Integer::sum);
            partesDeclaradasPorClase.put(clase, totalPartesDeclarado);

            List<?> benchmarks = doc.get("benchmarks", List.class);
            List<?> caminos = doc.get("cronometroCaminos", List.class);
            filasBenchmarksTotales += benchmarks != null ? benchmarks.size() : 0;
            filasCaminosTotales += caminos != null ? caminos.size() : 0;
        }

        List<String> clasesConPartesFaltantes = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : partesDeclaradasPorClase.entrySet()) {
            String clase = entrada.getKey();
            int declaradas = entrada.getValue();
            int encontradas = partesEncontradasPorClase.getOrDefault(clase, 0);
            if (encontradas < declaradas) {
                clasesConPartesFaltantes.add(clase + " (encontradas " + encontradas + "/" + declaradas + ")");
            }
        }

        double espacioDinamicasMB = estimarEspacioRepoMB(coleccionDinamicas, idRepo);

        return new Document()
                .append("partesEncontradas", partesEncontradas)
                .append("filasBenchmarksTotales", filasBenchmarksTotales)
                .append("filasCronometroCaminosTotales", filasCaminosTotales)
                .append("espacioEstimadoMB", redondear(espacioDinamicasMB))
                .append("clasesConPartesFaltantes", clasesConPartesFaltantes)
                .append("faltanTablas", !clasesConPartesFaltantes.isEmpty());
    }

    //-----> Obtiene estado general del catalogo
    public Document resumenGeneral() {
        Document reporte = new Document();

        reporte.append("espacioRepoCatalogMB", redondear(tamanoColeccionMB(coleccion)));
        reporte.append("espacioRepoClassMetricsMB", redondear(tamanoColeccionMB(coleccionClases)));
        reporte.append("espacioRepoDynamicMetricsMB", redondear(tamanoColeccionMB(coleccionDinamicas)));

        Document conteoStatus = new Document();

        //-----> Suma repos pendientes
        long pendientesSinStatus = coleccion.countDocuments(Filters.exists("status", false));
        long pendientesConStatusExplicito = coleccion.countDocuments(Filters.eq("status", "pending"));
        conteoStatus.append("pending", pendientesSinStatus + pendientesConStatusExplicito);

        //-----> 🔌 MODIFICADO: se agrega "metrics_static_only" (estatica completa,
        //-----> dinamica sin datos) como categoria propia en el resumen general.
        for (String status : new String[]{"metrics_in_progress", "metrics_static_only", "metrics_complete", "metrics_failed"}) {
            conteoStatus.append(status, coleccion.countDocuments(Filters.eq("status", status)));
        }
        reporte.append("reposPorStatus", conteoStatus);

        return reporte;
    }

    //-----> Calcula tamano en MB
    private double tamanoColeccionMB(MongoCollection<Document> coleccionAConsultar) {
        Document stats = baseDatos.runCommand(new Document("collStats", coleccionAConsultar.getNamespace().getCollectionName()));
        Number tamanoBytes = stats.get("size", Number.class);
        return tamanoBytes == null ? 0.0 : tamanoBytes.doubleValue() / (1024.0 * 1024.0);
    }

    //-----> Estima el peso en MB de un repo
    private double estimarEspacioRepoMB(MongoCollection<Document> coleccionAConsultar, String idRepo) {
        long totalBytes = 0;
        for (Document doc : coleccionAConsultar.find(Filters.eq("repoId", idRepo))) {
            totalBytes += doc.toJson().getBytes(StandardCharsets.UTF_8).length;
        }
        return totalBytes / (1024.0 * 1024.0);
    }

    //-----> Detecta documentos muy pesados
    //-----> 🔌 MODIFICADO: los mensajes usaban los nombres viejos de las colecciones
    //-----> ("repo_class_metrics" / "repo_dynamic_metrics") aunque el codigo real
    //-----> (ConfiguracionMongo) usa por defecto "repo_metrics_static" y
    //-----> "repo_metrics_dynamic". Eran solo texto de log -- no afectaban a donde
    //-----> se escribia -- pero causaban confusion, asi que se corrigen los strings
    //-----> para que coincidan con los nombres reales de las colecciones.
    private List<String> buscarDocumentosCercaDelLimite(String idRepo) {
        List<String> resultado = new ArrayList<>();

        for (Document doc : coleccionClases.find(Filters.eq("repoId", idRepo))) {
            long tamano = doc.toJson().getBytes(StandardCharsets.UTF_8).length;
            if (tamano >= ADVERTENCIA_TAMANO_BYTES) {
                //-----> Documento estatico pesado
                resultado.add("repo_metrics_static: clase=" + doc.getString("clase")
                        + " (~" + redondear(tamano / (1024.0 * 1024.0)) + "MB)");
            }
        }
        for (Document doc : coleccionDinamicas.find(Filters.eq("repoId", idRepo))) {
            long tamano = doc.toJson().getBytes(StandardCharsets.UTF_8).length;
            if (tamano >= ADVERTENCIA_TAMANO_BYTES) {
                //-----> Documento dinamico pesado
                resultado.add("repo_metrics_dynamic: clase=" + doc.getString("clase")
                        + " parte=" + doc.get("parte") + "/" + doc.get("totalPartes")
                        + " (~" + redondear(tamano / (1024.0 * 1024.0)) + "MB)");
            }
        }
        return resultado;
    }

    //-----> Redondea a 2 decimales
    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    //-----> Imprime resultado de un repo
    public void imprimirDiagnostico(String idRepo) {
        Document reporte = diagnosticarRepo(idRepo);

        System.out.println("==========================================================");
        System.out.println(" DIAGNOSTICO: " + idRepo);
        System.out.println("==========================================================");

        if (!reporte.getBoolean("encontrado", false)) {
            System.out.println(" " + reporte.getString("mensaje"));
            System.out.println("==========================================================");
            return;
        }

        System.out.println(" Status: " + reporte.getString("status"));
        if (reporte.containsKey("errorRegistrado")) {
            System.out.println(" Error registrado: " + reporte.get("errorRegistrado"));
        }
        if (reporte.containsKey("razonSinDatosDinamicos")) {
            System.out.println(" [SOLO ESTATICO] Razon sin datos dinamicos: " + reporte.get("razonSinDatosDinamicos"));
        }

        Document estatica = reporte.get("estatica", Document.class);
        System.out.println("\n --- ESTATICA ---");
        System.out.println("   Clases esperadas           : " + estatica.getInteger("clasesEsperadas"));
        System.out.println("   Clases encontradas en Mongo: " + estatica.get("clasesEncontradasEnMongo"));
        System.out.println("   Espacio estimado            : " + estatica.get("espacioEstimadoMB") + " MB");
        if (estatica.getBoolean("faltanTablas", false)) {
            System.out.println("   [ALERTA] Faltan tablas de clases -no todas se subieron con exito-.");
        }

        Document dinamica = reporte.get("dinamica", Document.class);
        System.out.println("\n --- DINAMICA ---");
        System.out.println("   Partes (documentos) encontradas    : " + dinamica.get("partesEncontradas"));
        System.out.println("   Filas de benchmarks totales        : " + dinamica.get("filasBenchmarksTotales"));
        System.out.println("   Filas de cronometro caminos totales: " + dinamica.get("filasCronometroCaminosTotales"));
        System.out.println("   Espacio estimado                   : " + dinamica.get("espacioEstimadoMB") + " MB");
        if (dinamica.getBoolean("faltanTablas", false)) {
            System.out.println("   [ALERTA] Faltan partes dinamicas -alguna clase no subio todas las partes que declaro-:");
            @SuppressWarnings("unchecked")
            List<String> clasesConPartesFaltantes = (List<String>) dinamica.get("clasesConPartesFaltantes");
            for (String linea : clasesConPartesFaltantes) {
                System.out.println("      - " + linea);
            }
        }

        @SuppressWarnings("unchecked")
        List<String> documentosGrandes = (List<String>) reporte.get("documentosCercaDelLimite16MB");
        if (documentosGrandes != null && !documentosGrandes.isEmpty()) {
            System.out.println("\n --- DOCUMENTOS CERCA DEL LIMITE DE 16MB ---");
            for (String linea : documentosGrandes) {
                System.out.println("   [AVISO] " + linea);
            }
        }

        System.out.println("==========================================================");
    }

    //-----> Imprime resumen del catalogo
    public void imprimirResumenGeneral() {
        Document reporte = resumenGeneral();

        System.out.println("==========================================================");
        System.out.println(" RESUMEN GENERAL DEL CATALOGO");
        System.out.println("==========================================================");
        System.out.println(" Espacio repo_catalog        : " + reporte.get("espacioRepoCatalogMB") + " MB");
        System.out.println(" Espacio repo_class_metrics  : " + reporte.get("espacioRepoClassMetricsMB") + " MB");
        System.out.println(" Espacio repo_dynamic_metrics: " + reporte.get("espacioRepoDynamicMetricsMB") + " MB");

        Document conteoStatus = reporte.get("reposPorStatus", Document.class);
        System.out.println("\n Repos por status:");
        for (String clave : conteoStatus.keySet()) {
            System.out.println("   " + clave + ": " + conteoStatus.get(clave));
        }
        System.out.println("==========================================================");
    }

    //-----> Punto de entrada principal
    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);
        ConfiguracionMongo config = ConfiguracionMongo.desdeVariablesDeEntorno();

        try (DiagnosticoAlmacenamiento diagnostico = new DiagnosticoAlmacenamiento(config)) {
            if (params.containsKey("repo")) {
                diagnostico.imprimirDiagnostico(params.get("repo"));
            } else {
                diagnostico.imprimirResumenGeneral();
            }
        }
    }

    //-----> Parsea argumentos de consola
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

    //-----> Cierra cliente de Mongo
    @Override
    public void close() {
        cliente.close();
    }
}