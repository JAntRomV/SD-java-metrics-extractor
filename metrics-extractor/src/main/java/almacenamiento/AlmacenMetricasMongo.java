package almacenamiento;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//-----> Clase principal para gestionar datos en MongoDB
public class AlmacenMetricasMongo implements AutoCloseable {

    private final MongoClient cliente;
    private final MongoDatabase baseDatos;
    private final MongoCollection<Document> coleccion;
    private final MongoCollection<Document> coleccionClases;
    private final MongoCollection<Document> coleccionDinamicas;

    public AlmacenMetricasMongo(ConfiguracionMongo config) {
        this.cliente = MongoClients.create(config.construirUri());
        this.baseDatos = cliente.getDatabase(config.baseDatos);
        this.coleccion = baseDatos.getCollection(config.coleccion);
        this.coleccionClases = baseDatos.getCollection(config.coleccionClases);
        this.coleccionDinamicas = baseDatos.getCollection(config.coleccionDinamicas);
    }

    public long contarDocumentos() {
        return coleccion.countDocuments();
    }

    public List<Document> obtenerRepositoriosPendientes() {
        List<Document> resultado = new ArrayList<>();
        Bson filtro = Filters.or(
                Filters.exists("status", false),
                Filters.in("status", "pending", "metrics_in_progress")
        );
        Bson orden = Sorts.orderBy(Sorts.ascending("mining.score.rank"), Sorts.ascending("_id"));

        FindIterable<Document> cursor = coleccion.find(filtro).sort(orden);
        for (Document doc : cursor) {
            resultado.add(doc);
        }
        return resultado;
    }

    public List<Document> obtenerTodosLosRepositorios() {
        List<Document> resultado = new ArrayList<>();
        Bson orden = Sorts.orderBy(Sorts.ascending("mining.score.rank"), Sorts.ascending("_id"));

        FindIterable<Document> cursor = coleccion.find().sort(orden);
        for (Document doc : cursor) {
            resultado.add(doc);
        }
        return resultado;
    }

    public Document obtenerRepositorioPorId(String idRepo) {
        return coleccion.find(Filters.eq("_id", idRepo)).first();
    }

    public void borrarSoloDinamicas(String idRepo) {
        coleccionDinamicas.deleteMany(Filters.eq("repoId", idRepo));
    }

    public void inicializarMetricasVacias(String idRepo) {
        coleccionClases.deleteMany(Filters.eq("repoId", idRepo));
        coleccionDinamicas.deleteMany(Filters.eq("repoId", idRepo));

        Document metricsVacio = new Document("estaticas", new Document("totalClases", 0)
                        .append("coleccionClases", coleccionClases.getNamespace().getCollectionName()))
                .append("dinamicas", new Document("coleccionDinamicas", coleccionDinamicas.getNamespace().getCollectionName()));

        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics", metricsVacio),
                Updates.set("metricsStatus", new Document("static", "pending").append("dynamic", "pending")),
                Updates.set("status", "metrics_in_progress")
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    public void agregarClaseAMetricas(String idRepo, Document claseDoc) {
        String clase = claseDoc.getString("clase");
        String id = idRepo + "_" + clase;

        Document documentoClase = new Document("_id", id)
                .append("repoId", idRepo)
                .append("clase", clase);
        documentoClase.putAll(claseDoc);
        documentoClase.put("_id", id);

        Bson filtro = Filters.eq("_id", id);
        coleccionClases.replaceOne(filtro, documentoClase, new ReplaceOptions().upsert(true));
    }

    public void agregarDinamicoAMetricas(String idRepo, Document dinamicoDoc) {
        String clase = dinamicoDoc.getString("clase");
        int parte = dinamicoDoc.getInteger("parte", 1);
        String id = idRepo + "_" + clase + "_" + parte;

        Document documento = new Document("_id", id)
                .append("repoId", idRepo)
                .append("clase", clase)
                .append("parte", parte);
        documento.putAll(dinamicoDoc);
        documento.put("_id", id);

        Bson filtro = Filters.eq("_id", id);
        coleccionDinamicas.replaceOne(filtro, documento, new ReplaceOptions().upsert(true));
    }

    public void agregarCaminosAMetricas(String idRepo, Document caminoParteDoc) {
        String clase = caminoParteDoc.getString("clase");
        int parte = caminoParteDoc.getInteger("parte", 1);
        String id = idRepo + "_" + clase + "_caminos_" + parte;

        Document documento = new Document("_id", id)
                .append("repoId", idRepo)
                .append("clase", clase)
                .append("parte", parte);
        documento.putAll(caminoParteDoc);
        documento.put("_id", id);

        Bson filtro = Filters.eq("_id", id);
        coleccionClases.replaceOne(filtro, documento, new ReplaceOptions().upsert(true));
    }

    public void actualizarEstadoParcial(String idRepo, String tipo, String valor) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.set("metricsStatus." + tipo, valor);
        coleccion.updateOne(filtro, actualizacion);
    }

    public void actualizarStatusGeneral(String idRepo, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.set("status", nuevoStatus);
        coleccion.updateOne(filtro, actualizacion);
    }

    public void marcarSoloEstaticoCompleto(String idRepo, String razonSinDatosDinamicos) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("status", "metrics_static_only"),
                Updates.set("metrics.dinamicas.razonSinDatos", razonSinDatosDinamicos)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    public void finalizarMetricas(String idRepo, int totalClases, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics.estaticas.totalClases", totalClases),
                Updates.set("status", nuevoStatus)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    public void guardarMetricas(String idRepo, Document metrics, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics", metrics),
                Updates.set("status", nuevoStatus)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    public Map<Object, Long> contarPorValorDeStatus() {
        Map<Object, Long> conteo = new LinkedHashMap<>();
        for (Document doc : coleccion.find()) {
            Object valor = doc.get("status");
            conteo.merge(valor, 1L, Long::sum);
        }
        return conteo;
    }

    public double obtenerTamanoColeccionEnMB() {
        Document stats = baseDatos.runCommand(new Document("collStats", coleccion.getNamespace().getCollectionName()));
        Number tamanoBytes = stats.get("size", Number.class);
        return tamanoBytes == null ? 0.0 : tamanoBytes.doubleValue() / (1024.0 * 1024.0);
    }

    //-----> AGREGADO: cursor (no lista) de documentos de clase que tienen
    //-----> metricas estaticas calculadas -Halstead + CFG-. Se usa "exists"
    //-----> sobre "metricasJson" porque esta misma coleccion tambien guarda
    //-----> los documentos de caminos, que no tienen ese campo.
    public FindIterable<Document> obtenerCursorClasesConMetricas() {
        return coleccionClases.find(Filters.exists("metricasJson", true));
    }

    //-----> AGREGADO: cursor de documentos de clase que tienen caminos
    //-----> estaticos -distinguidos por tener el campo "caminos"-.
    public FindIterable<Document> obtenerCursorClasesConCaminos() {
        return coleccionClases.find(Filters.exists("caminos", true));
    }

    //-----> AGREGADO: cursor de todos los documentos dinamicos
    //-----> (benchmarks + cronometro de caminos) de todos los repos.
    public FindIterable<Document> obtenerCursorDinamicas() {
        return coleccionDinamicas.find();
    }

    //-----> AGREGADO: repos con alguna incidencia -fallidos por completo,
    //-----> o que solo llegaron a la parte estatica-.
    public List<Document> obtenerRepositoriosConIncidencias() {
        List<Document> resultado = new ArrayList<>();
        Bson filtro = Filters.in("status", "metrics_failed", "metrics_static_only");
        for (Document doc : coleccion.find(filtro)) {
            resultado.add(doc);
        }
        return resultado;
    }

    @Override
    public void close() {
        cliente.close();
    }
}