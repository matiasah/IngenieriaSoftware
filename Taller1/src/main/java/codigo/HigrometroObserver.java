package codigo;

public class HigrometroObserver extends InstrumentoObserver {

    private Higrometro higrometro;

    /**
     *
     * @param higrometro
     */
    public HigrometroObserver(Higrometro higrometro) {
        
        this.higrometro = higrometro;
        
    }

    @Override
    public void actualizar() {
        
        this.estacion.enviarMedicion( new Medicion(TipoMedicion.HIGROMETRO, higrometro.getHumedad()));
        
    }

}
