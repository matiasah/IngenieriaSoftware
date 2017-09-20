package codigo;

import java.util.ArrayList;

public class VeletaObserver extends InstrumentoObserver {

    private Veleta veleta;

    /**
     *
     * @param veleta
     */
    public VeletaObserver(Veleta veleta) {
        
        this.veleta = veleta;
        
    }

    @Override
    public void actualizar() {
        
        ArrayList<Double> arregloMedicion = new ArrayList<>();
        
        arregloMedicion.add(this.veleta.getX());
        arregloMedicion.add(this.veleta.getY());
        
        this.estacion.enviarMedicion( new Medicion(TipoMedicion.VELETA, arregloMedicion));
        
    }

}
