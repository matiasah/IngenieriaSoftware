package codigo;

import java.util.ArrayList;

public abstract class InstrumentoSubject {

    private ArrayList<InstrumentoObserver> observadores;

    public InstrumentoSubject() {

        this.observadores = new ArrayList<>();

    }

    /**
     *
     * @param instrumentoObserver
     */
    public void agregarObservador(InstrumentoObserver instrumentoObserver) {

        this.observadores.add(instrumentoObserver);

    }

    /**
     *
     * @param instrumentoObserver
     */
    public void eliminarObservador(InstrumentoObserver instrumentoObserver) {

        this.observadores.add(instrumentoObserver);

    }
    
    public void notificar() {
        
        for ( InstrumentoObserver observador : this.observadores ) {
            
            observador.actualizar();
            
        }
        
    }
    
    public abstract void capturar();

}
