package codigo;

public abstract class InstrumentoObserver {
    
    protected Estacion estacion;
    
    public InstrumentoObserver() {
        
        this.estacion = null;
        
    }
    
    public void setEstacion(Estacion estacion) {
        
        this.estacion = estacion;
        
    }

    public abstract void actualizar();
    
}
