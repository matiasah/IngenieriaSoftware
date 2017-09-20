package codigo;

public class Medicion {

    private TipoMedicion tipoMedicion;
    private Object valor;

    /**
     *
     * @param tipo
     * @param valor
     */
    public Medicion(TipoMedicion tipo, Object valor) {
        
        this.tipoMedicion = tipo;
        this.valor = valor;
        
    }

    public TipoMedicion getTipo() {
        
        return this.tipoMedicion;
        
    }

    public Object getValor() {
        
        return this.valor;
        
    }

    @Override
    public String toString() {
        
        return this.tipoMedicion.toString() + ": " + this.valor;
        
    }

}
