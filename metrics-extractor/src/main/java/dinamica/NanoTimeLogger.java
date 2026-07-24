package dinamica;

import java.time.LocalDateTime;

//-----> Contenedor que guarda el momento anterior registrado para calcular duraciones de tiempo
public class NanoTimeLogger {

    private long _prevNanos;
    private LocalDateTime _prevFechaHora;
    private int _idLog;

    // Crea el objeto con el tiempo inicial y el ID de registro
    public NanoTimeLogger(long prevNanos, LocalDateTime prevFechaHora, int idLog) {
        this._prevNanos = prevNanos;
        this._prevFechaHora = prevFechaHora;
        this._idLog = idLog;
    }

    public long getPrevNanos() { return _prevNanos; }
    public void setPrevNanos(long prevNanos) { this._prevNanos = prevNanos; }

    public LocalDateTime getPrevFechaHora() { return _prevFechaHora; }
    public void setPrevFechaHora(LocalDateTime prevFechaHora) { this._prevFechaHora = prevFechaHora; }

    public int getIDLog() { return _idLog; }
    public void setIDLog(int idLog) { this._idLog = idLog; }
}