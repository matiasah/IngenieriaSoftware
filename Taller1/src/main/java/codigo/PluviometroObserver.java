package codigo;

public class PluviometroObserver extends InstrumentoObserver {

    private Pluviometro pluviometro;

    /**
     *
     * @param pluviometro
     */
    public PluviometroObserver(Pluviometro pluviometro) {

        this.pluviometro = pluviometro;

    }

    @Override
    public void actualizar() {

        this.estacion.enviarMedicion(new Medicion(TipoMedicion.PLUVIOMETRO, this.pluviometro.getAguaCaida()));

    }

}
