package codigo;

public class BarometroObserver extends InstrumentoObserver {

    private Barometro barometro;

    /**
     *
     * @param barometro
     */
    public BarometroObserver(Barometro barometro) {
        
        this.barometro = barometro;
        
    }

    @Override
    public void actualizar() {
        
        this.estacion.enviarMedicion( new Medicion(TipoMedicion.BAROMETRO, this.barometro.getPresion()));
        
    }

}
