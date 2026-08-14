package almacenamiento;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LectorResultadosTest {

    private final LectorResultados lector = new LectorResultados();

    // ======================= procesarClasesUnaAUna =======================

    @Test
    void agrupaMetricasJsonYCaminosBajoLaMismaClase(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("FooMetricas.json"), "{\"total\":5}");
        Files.writeString(tempDir.resolve("Foo_caminos.json"), "{\"caminos\":1}");

        List<Document> recibidos = new ArrayList<>();
        int total = lector.procesarClasesUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals(1, total);
        assertEquals(1, recibidos.size());

        Document doc = recibidos.get(0);
        assertEquals("Foo", doc.getString("clase"));
        assertInstanceOf(Document.class, doc.get("metricasJson"));
        assertEquals(5, ((Document) doc.get("metricasJson")).getInteger("total"));
        assertNotNull(doc.get("caminos"));
    }

    @Test
    void siSoloHayMetricasJsonNoAgregaLaClaveCaminos(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("BarMetricas.json"), "{\"total\":1}");

        List<Document> recibidos = new ArrayList<>();
        lector.procesarClasesUnaAUna(tempDir.toFile(), recibidos::add);

        assertFalse(recibidos.get(0).containsKey("caminos"));
    }

    @Test
    void siElContenidoNoEsJsonValidoLoGuardaComoTextoPlano(@TempDir Path tempDir) throws Exception {
        String contenidoInvalido = "esto no es json{{{";
        Files.writeString(tempDir.resolve("BazMetricas.json"), contenidoInvalido);

        List<Document> recibidos = new ArrayList<>();
        lector.procesarClasesUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals(contenidoInvalido, recibidos.get(0).get("metricasJson"));
    }

    @Test
    void archivosQueNoCoincidenConLosSufijosSeIgnoran(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("readme.txt"), "hola");
        Files.writeString(tempDir.resolve("notas.json"), "{}");

        List<Document> recibidos = new ArrayList<>();
        int total = lector.procesarClasesUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals(0, total);
        assertTrue(recibidos.isEmpty());
    }

    @Test
    void carpetaInexistenteORetornaCeroSinLlamarAlConsumidor(@TempDir Path tempDir) throws Exception {
        File carpetaFalsa = tempDir.resolve("no_existe").toFile();
        assertEquals(0, lector.procesarClasesUnaAUna(carpetaFalsa, doc -> fail("no deberia llamarse")));
    }

    @Test
    void carpetaNulaRetornaCeroSinLlamarAlConsumidor() throws Exception {
        assertEquals(0, lector.procesarClasesUnaAUna(null, doc -> fail("no deberia llamarse")));
    }

    // ================== procesarDinamicosPorClaseUnaAUna ==================

    @Test
    void agrupaFilasDeBenchmarksPorClaseExtraidaAntesDelHash(@TempDir Path tempDir) throws Exception {
        String csv = "Param: metodoObjetivo,Score\n"
                + "com.foo.Bar#metodoX,1.23\n"
                + "com.foo.Bar#metodoY,4.56\n"
                + "com.foo.Baz#metodoZ,7.89\n";
        Files.writeString(tempDir.resolve("Benchmarks.csv"), csv);

        List<Document> recibidos = new ArrayList<>();
        int total = lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals(2, total); // una parte por cada clase (2 y 1 filas, ambas <15)

        Document docBar = recibidos.stream().filter(d -> d.getString("clase").equals("com.foo.Bar")).findFirst().orElseThrow();
        assertEquals(1, docBar.getInteger("parte"));
        assertEquals(1, docBar.getInteger("totalPartes"));
        assertEquals(2, docBar.getList("benchmarks", Document.class).size());
        assertTrue(docBar.getList("cronometroCaminos", Document.class).isEmpty());

        Document docBaz = recibidos.stream().filter(d -> d.getString("clase").equals("com.foo.Baz")).findFirst().orElseThrow();
        assertEquals(1, docBaz.getList("benchmarks", Document.class).size());
    }

    @Test
    void combinaBenchmarksYCaminosDeLaMismaClaseEnUnSoloDocumento(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Benchmarks.csv"),
                "Param: metodoObjetivo,Score\ncom.foo.Bar#m1,1.0\n");
        Files.writeString(tempDir.resolve("cronometro_caminos.csv"),
                "Clase,Tiempo\ncom.foo.Bar#m1,99\n");

        List<Document> recibidos = new ArrayList<>();
        int total = lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals(1, total);
        Document doc = recibidos.get(0);
        assertEquals("com.foo.Bar", doc.getString("clase"));
        assertEquals(1, doc.getList("benchmarks", Document.class).size());
        assertEquals(1, doc.getList("cronometroCaminos", Document.class).size());
    }

    @Test
    void divideEnVariasPartesCuandoSuperaElLimiteDeFilasPorParte(@TempDir Path tempDir) throws Exception {
        StringBuilder csv = new StringBuilder("Param: metodoObjetivo,Score\n");
        for (int i = 0; i < 17; i++) {
            csv.append("com.foo.Grande#m").append(i).append(",1.0\n");
        }
        Files.writeString(tempDir.resolve("Benchmarks.csv"), csv.toString());

        List<Document> recibidos = new ArrayList<>();
        int total = lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), recibidos::add);

        // 17 filas / limite de 15 por parte -> ceil(17/15) = 2 partes
        assertEquals(2, total);
        recibidos.sort((a, b) -> a.getInteger("parte") - b.getInteger("parte"));

        assertEquals(1, recibidos.get(0).getInteger("parte"));
        assertEquals(2, recibidos.get(0).getInteger("totalPartes"));
        assertEquals(2, recibidos.get(1).getInteger("totalPartes"));

        int sumaFilas = recibidos.get(0).getList("benchmarks", Document.class).size()
                + recibidos.get(1).getList("benchmarks", Document.class).size();
        assertEquals(17, sumaFilas);
    }

    @Test
    void valorSinAlmohadillaUsaElValorCompletoComoClase(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Benchmarks.csv"),
                "Param: metodoObjetivo,Score\nSoloUnaClaseSinMetodo,1.0\n");

        List<Document> recibidos = new ArrayList<>();
        lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals("SoloUnaClaseSinMetodo", recibidos.get(0).getString("clase"));
    }

    @Test
    void filasConValorEnBlancoEnLaColumnaClaveSeOmiten(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Benchmarks.csv"),
                "Param: metodoObjetivo,Score\n,1.0\ncom.foo.Bar#m1,2.0\n");

        List<Document> recibidos = new ArrayList<>();
        int total = lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), recibidos::add);

        assertEquals(1, total);
        assertEquals(1, recibidos.get(0).getList("benchmarks", Document.class).size());
    }

    @Test
    void respetaComillasYComasInternasAlParsearElCsv(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("Benchmarks.csv"),
                "Param: metodoObjetivo,Detalle\n\"com.foo.Bar#m1\",\"valor, con coma\"\n");

        List<Document> recibidos = new ArrayList<>();
        lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), recibidos::add);

        Document fila = recibidos.get(0).getList("benchmarks", Document.class).get(0);
        assertEquals("valor, con coma", fila.getString("Detalle"));
        assertEquals("com.foo.Bar", recibidos.get(0).getString("clase"));
    }

    @Test
    void sinNingunCsvPresenteRetornaCero(@TempDir Path tempDir) throws Exception {
        assertEquals(0, lector.procesarDinamicosPorClaseUnaAUna(tempDir.toFile(), doc -> fail("no deberia llamarse")));
    }

    @Test
    void carpetaDinamicaInexistenteORetornaCero(@TempDir Path tempDir) throws Exception {
        File carpetaFalsa = tempDir.resolve("no_existe").toFile();
        assertEquals(0, lector.procesarDinamicosPorClaseUnaAUna(carpetaFalsa, doc -> fail("no deberia llamarse")));
    }

    @Test
    void carpetaDinamicaNulaRetornaCero() throws Exception {
        assertEquals(0, lector.procesarDinamicosPorClaseUnaAUna(null, doc -> fail("no deberia llamarse")));
    }

    // ========================= leerResumenesDeProyecto =========================

    @Test
    void leeAmbosArchivosDeResumenSiExisten(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("_escaneo_resumen.txt"), "clasesEncontradas=10\nclasesDescartadas=2\n");
        Files.writeString(tempDir.resolve("_caminos_resumen.txt"), "metodosIntentados=5\nmetodosMedidos=4\n");

        Document resultado = lector.leerResumenesDeProyecto(tempDir.toFile());

        Document escaneo = resultado.get("escaneoResumen", Document.class);
        assertEquals("10", escaneo.getString("clasesEncontradas"));

        Document caminos = resultado.get("caminosResumen", Document.class);
        assertEquals("5", caminos.getString("metodosIntentados"));
    }

    @Test
    void siSoloExisteUnoDeLosDosArchivosSoloIncluyeEse(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("_escaneo_resumen.txt"), "clasesEncontradas=3\n");

        Document resultado = lector.leerResumenesDeProyecto(tempDir.toFile());

        assertTrue(resultado.containsKey("escaneoResumen"));
        assertFalse(resultado.containsKey("caminosResumen"));
    }

    @Test
    void ignoraLineasSinIgualQueLasSepare(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("_escaneo_resumen.txt"), "lineaInvalidaSinIgual\nclasesEncontradas=3\n");

        Document resultado = lector.leerResumenesDeProyecto(tempDir.toFile());
        Document escaneo = resultado.get("escaneoResumen", Document.class);

        assertEquals(1, escaneo.size());
        assertEquals("3", escaneo.getString("clasesEncontradas"));
    }

    @Test
    void carpetaNulaORetornaDocumentoVacio() throws Exception {
        assertTrue(lector.leerResumenesDeProyecto(null).isEmpty());
    }

    @Test
    void carpetaInexistenteRetornaDocumentoVacio(@TempDir Path tempDir) throws Exception {
        File carpetaFalsa = tempDir.resolve("no_existe").toFile();
        assertTrue(lector.leerResumenesDeProyecto(carpetaFalsa).isEmpty());
    }
}