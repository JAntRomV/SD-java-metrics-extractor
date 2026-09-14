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

    //-----> Conecta a Mongo y asigna las colecciones
    public AlmacenMetricasMongo(ConfiguracionMongo config) {
        this.cliente = MongoClients.create(config.construirUri());
        this.baseDatos = cliente.getDatabase(config.baseDatos);
        this.coleccion = baseDatos.getCollection(config.coleccion);
        this.coleccionClases = baseDatos.getCollection(config.coleccionClases);
        this.coleccionDinamicas = baseDatos.getCollection(config.coleccionDinamicas);
    }

    //-----> Cuenta el total de repos
    public long contarDocumentos() {
        return coleccion.countDocuments();
    }

    //-----> Trae los repos con estado pendiente
    //-----> MODIFICADO: ya NO incluye "metrics_in_progress". Antes, un repo que
    //-----> se quedaba atorado por un crash del contenedor (tipico de falta de
    //-----> memoria) volvia a la cola de pendientes y OrquestadorRepos lo
    //-----> reintentaba solo en la siguiente corrida -si la causa era memoria,
    //-----> normalmente volvia a fallar igual, desperdiciando tiempo-. Ahora,
    //-----> en cuanto un repo llega aqui con "metrics_in_progress" significa
    //-----> que su intento anterior murio a medias (nunca llego a marcarse
    //-----> como completo/fallido/solo-estatico), y se deja fuera de esta lista
    //-----> a proposito: solo queda accesible via obtenerRepositoriosAtorados()
    //-----> -el CSV "repos por memoria"- para correrlo manual, hasta que alguien
    //-----> decida resetearlo con AlmacenMetricasMongo/MetricsController.
    //-----> (Es seguro asumir que "metrics_in_progress" aqui es un repo muerto,
    //-----> no uno realmente activo: MetricsController nunca deja arrancar un
    //-----> lote nuevo -409- mientras otro ya esta corriendo, asi que si este
    //-----> metodo se esta ejecutando, ningun otro repo puede estar en
    //-----> progreso de verdad al mismo tiempo.)
    public List<Document> obtenerRepositoriosPendientes() {
        List<Document> resultado = new ArrayList<>();

        Bson filtro = Filters.or(
                Filters.exists("status", false),
                Filters.eq("status", "pending")
        );
        Bson orden = Sorts.orderBy(Sorts.ascending("mining.score.rank"), Sorts.ascending("_id"));

        FindIterable<Document> cursor = coleccion.find(filtro).sort(orden);
        for (Document doc : cursor) {
            resultado.add(doc);
        }
        return resultado;
    }

    //-----> Trae todos los repos sin filtrar
    public List<Document> obtenerTodosLosRepositorios() {
        List<Document> resultado = new ArrayList<>();
        Bson orden = Sorts.orderBy(Sorts.ascending("mining.score.rank"), Sorts.ascending("_id"));

        FindIterable<Document> cursor = coleccion.find().sort(orden);
        for (Document doc : cursor) {
            resultado.add(doc);
        }
        return resultado;
    }

    //-----> Busca repo por su ID
    public Document obtenerRepositorioPorId(String idRepo) {
        return coleccion.find(Filters.eq("_id", idRepo)).first();
    }

    //-----> Borra metricas dinamicas de un repo
    public void borrarSoloDinamicas(String idRepo) {
        coleccionDinamicas.deleteMany(Filters.eq("repoId", idRepo));
    }

    //-----> Limpia y crea la estructura inicial
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

    //-----> Guarda o actualiza metricas dinamicas
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

    //-----> Guarda los caminos de ejecucion en partes
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

    //-----> Cambia el estado estatico o dinamico
    public void actualizarEstadoParcial(String idRepo, String tipo, String valor) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.set("metricsStatus." + tipo, valor);
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Actualiza SOLO el status general del repo, sin tocar "metrics" ni
    //-----> "metricsStatus". Se usa desde ReprocesarDinamico: como ese flujo
    //-----> reprocesa unicamente la parte dinamica y no vuelve a calcular el
    //-----> total de clases estaticas, no puede usar finalizarMetricas() ni
    //-----> guardarMetricas() -ambos pisarian "metrics" con datos incompletos-.
    //-----> Sin este metodo, un reproceso dinamico exitoso dejaba el repo
    //-----> marcado como "metrics_static_only" en el resumen general, aunque
    //-----> ya tuviera estatica Y dinamica completas.
    public void actualizarStatusGeneral(String idRepo, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.set("status", nuevoStatus);
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Marca repo solo con fase estatica
    public void marcarSoloEstaticoCompleto(String idRepo, String razonSinDatosDinamicos) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("status", "metrics_static_only"),
                Updates.set("metrics.dinamicas.razonSinDatos", razonSinDatosDinamicos)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Actualiza el total de clases y el estado
    public void finalizarMetricas(String idRepo, int totalClases, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics.estaticas.totalClases", totalClases),
                Updates.set("status", nuevoStatus)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Guarda todo el documento de metricas
    public void guardarMetricas(String idRepo, Document metrics, String nuevoStatus) {
        Bson filtro = Filters.eq("_id", idRepo);
        Bson actualizacion = Updates.combine(
                Updates.set("metrics", metrics),
                Updates.set("status", nuevoStatus)
        );
        coleccion.updateOne(filtro, actualizacion);
    }

    //-----> Cuenta repos agrupados por estado
    public Map<Object, Long> contarPorValorDeStatus() {
        Map<Object, Long> conteo = new LinkedHashMap<>();
        for (Document doc : coleccion.find()) {
            Object valor = doc.get("status");
            conteo.merge(valor, 1L, Long::sum);
        }
        return conteo;
    }

    //-----> Calcula el tamano en MB de la coleccion
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

    //-----> AGREGADO: repos que quedaron atorados en "metrics_in_progress"
    //-----> -senal de que el contenedor murio a la mitad, probablemente por
    //-----> falta de memoria (ver CompiladorProyecto / proyectos Gradle)-.
    //-----> Es solo un reporte de lectura para el CSV exportable; no hace
    //-----> ningun cambio automatico ni marca nada como fallido por si solo.
    public List<Document> obtenerRepositoriosAtorados() {
        List<Document> resultado = new ArrayList<>();
        for (Document doc : coleccion.find(Filters.eq("status", "metrics_in_progress"))) {
            resultado.add(doc);
        }
        return resultado;
    }

    //-----> Cierra la conexion a la base de datos
    @Override
    public void close() {
        cliente.close();
    }
}