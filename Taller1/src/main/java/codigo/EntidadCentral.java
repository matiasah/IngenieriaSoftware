package codigo;

import java.util.ArrayList;

public class EntidadCentral {

    private ArrayList<Estacion> estaciones;
    private ArrayList<Medicion> mediciones;

    public EntidadCentral() {

        this.estaciones = new ArrayList<>();
        this.mediciones = new ArrayList<>();

    }

    public void actualizarMediciones() {

        this.mediciones.clear();

        for (Estacion estacion : this.estaciones) {

            estacion.capturarMediciones();

        }

    }
    
    public void mostrarMediciones() {
        
        System.out.println("------ MEDICIONES -------");
        
        for ( Medicion medicion : this.mediciones ) {
            
            System.out.println(medicion);
            
        }
        
    }

    /**
     *
     * @param estacion
     */
    public void agregarEstacion(Estacion estacion) {

        this.estaciones.add(estacion);

    }

    /**
     *
     * @param estacion
     */
    public void eliminarEstacion(Estacion estacion) {

        this.estaciones.remove(estacion);

    }

    public void enviarMedicion(Medicion medicion) {

        this.mediciones.add(medicion);

    }
    
}
