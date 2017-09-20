package codigo;

public class TermometroObserver extends InstrumentoObserver {

    private Termometro termometro;

    /**
     *
     * @param termometro
     */
    public TermometroObserver(Termometro termometro) {

        this.termometro = termometro;

    }

    @Override
    public void actualizar() {

        this.estacion.enviarMedicion(new Medicion(TipoMedicion.TERMOMETRO, this.termometro.getTemperatura()));

    }

}
