package codigo;

import java.util.ArrayList;

public class Estacion {

    private EntidadCentral entidadCentral;
    private boolean avanzada;
    private ArrayList<InstrumentoSubject> instrumentos;

    /**
     *
     * @param entidadCentral
     * @param instrumentos
     */
    public Estacion(EntidadCentral entidadCentral, ArrayList<InstrumentoSubject> instrumentos) {

        this.entidadCentral = entidadCentral;
        this.instrumentos = instrumentos;
        this.avanzada = instrumentos.size() > 3;

    }

    public void setAvanzada(boolean avanzada) {

        this.avanzada = avanzada;

    }

    public boolean getAvanzada() {

        return this.avanzada;

    }

    public void capturarMediciones() {

        for (InstrumentoSubject instrumento : this.instrumentos) {

            instrumento.capturar();

        }

    }

    public void enviarMedicion(Medicion medicion) {

        this.entidadCentral.enviarMedicion(medicion);

    }

}
