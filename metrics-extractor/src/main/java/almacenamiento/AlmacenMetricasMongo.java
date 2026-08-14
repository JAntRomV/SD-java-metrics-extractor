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

//-----> Gestor de colecciones y metricas en MongoDB
public class AlmacenMetricasMongo implements AutoCloseable {

    private final MongoClient cliente;
    private final MongoDatabase baseDatos;
    private final MongoCollection<Document> coleccion;
    private final MongoCollection<Document> coleccionClases;
    private final MongoCollection<Document> coleccionDinamicas;

    //-----> Constructor con configuracion
    public AlmacenMetricasMongo(ConfiguracionMongo config) {
        this.cliente = MongoClients.create(config.construirUri());
        this.baseDatos = cliente.getDatabase(config.baseDatos);
        this.coleccion = baseDatos.getCollection(config.coleccion);
        this.coleccionClases = baseDatos.getCollection(config.coleccionClases);
        this.coleccionDinamicas = baseDatos.getCollection(config.coleccionDinamicas);
    }

    //-----> Cuenta total de repos en catalogo
    public long contarDocumentos() {
        return coleccion.countDocuments();
    }

    //-----> Obtiene repos pendientes de procesar
    public List<Document> obtenerRepositoriosPendientes() {
        List<Document> resultado = new ArrayList<>();

        //-----> Filtro para repos sin status o pendientes
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

    //-----> Busca un repo por su ID
    public Document obtenerRepositorioPorId(String idRepo) {
        return coleccion.find(Filters.eq("_id", idRepo)).first();
    }

    //-----> Elimina solo metricas dinamicas
    public void borrarSoloDinamicas(String idRepo) {
        coleccionDinamicas.deleteMany(Filters.eq("repoId", idRepo));
    }

    //-----> Prepara la estructura vacia del repo
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

    //-----> Guarda o actualiza una clase estatica
    public void agregarClaseAMetricas(String idRepo, Document claseDoc) {
        Document documentoClase = new Document("repoId", idRepo).append("clase", claseDoc.getString("clase"));
        documentoClase.putAll(claseDoc);

        Bson filtro = Filters.and(Filters.eq("repoId", idRepo), Filters.eq("clase", claseDoc.getString("clase")));
        coleccionClases.replaceOne(filtro, documentoClase, new ReplaceOptions().upsert(true));
    }

    //-----> Guarda o actualiza una parte dinamica
    public void agregarDinamicoAMetricas(String idRepo, Document dinamicoDoc) {
        Document documento = new Document("repoId", idRepo)
                .append("clase", dinamicoDoc.getString("clase"))
                .append("parte", dinamicoDoc.getInteger("parte", 1));
        documento.putAll(dinamicoDoc);

        Bson filtro = Filters.and(
                Filters.eq("repoId", idRepo),
                Filters.eq("clase", dinamicoDoc.getString("clase")),
                Filters.eq("parte", dinamicoDoc.getInteger("parte", 1))
        );
        coleccionDinamicas.replaceOne(filtro, documento, new ReplaceOptions().upsert(true));
    }

    //-----> Actualiza el estado estatico o dinamico
    public void actualizarEstadoParcial(String idRepo, String tipo, String valor) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.set("metricsStatus." + tipo, valor);
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Finaliza el proceso y guarda totales
    public void finalizarMetricas(String idRepo, int totalClases, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics.estaticas.totalClases", totalClases),
                Updates.set("status", nuevoStatus)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Guarda metricas y actualiza status
    public void guardarMetricas(String idRepo, Document metrics, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics", metrics),
                Updates.set("status", nuevoStatus)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Agrupa y cuenta repos por tipo de status
    public Map<Object, Long> contarPorValorDeStatus() {
        Map<Object, Long> conteo = new LinkedHashMap<>();
        for (Document doc : coleccion.find()) {
            Object valor = doc.get("status");
            conteo.merge(valor, 1L, Long::sum);
        }
        return conteo;
    }

    //-----> Mide tamano del catalogo en MB
    public double obtenerTamanoColeccionEnMB() {
        Document stats = baseDatos.runCommand(new Document("collStats", coleccion.getNamespace().getCollectionName()));
        Number tamanoBytes = stats.get("size", Number.class);
        return tamanoBytes == null ? 0.0 : tamanoBytes.doubleValue() / (1024.0 * 1024.0);
    }

    //-----> Cierra conexion a Mongo
    @Override
    public void close() {
        cliente.close();
    }
}