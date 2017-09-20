package bridge;

public class ControlRemotoAvanzado extends ControlRemoto {

    /**
     *
     * @param dispositivo
     */
    public ControlRemotoAvanzado(Dispositivo dispositivo) {
        
        super(dispositivo);
        
    }
    
    public void mute() {
        
        this.dispositivo.setVolumen(0);
        
    }

}
