package codigo;

public class AnemometroObserver extends InstrumentoObserver {

    private Anemometro anemometro;

    /**
     *
     * @param anemometro
     */
    public AnemometroObserver(Anemometro anemometro) {

        this.anemometro = anemometro;

    }

    @Override
    public void actualizar() {

        this.estacion.enviarMedicion(new Medicion(TipoMedicion.ANEMOMETRO, this.anemometro.getVelocidadViento()));

    }

}
