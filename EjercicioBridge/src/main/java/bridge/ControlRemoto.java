package bridge;

public class ControlRemoto {

    protected Dispositivo dispositivo;

    /**
     *
     * @param dispositivo
     */
    public ControlRemoto(Dispositivo dispositivo) {
        
        this.dispositivo = dispositivo;
        
    }

    public void subirCanal() {
        
        this.dispositivo.setCanal(this.dispositivo.getCanal() + 1);
        
        System.out.println("Canal = " + this.dispositivo.getCanal());
        
    }

    public void bajarCanal() {
        
        this.dispositivo.setCanal(this.dispositivo.getCanal() - 1);
        
        System.out.println("Canal = " + this.dispositivo.getCanal());
        
    }

    public void subirVolumen() {
        
        this.dispositivo.setVolumen(this.dispositivo.getVolumen() + 10);
        
        System.out.println("Volumen = " + this.dispositivo.getVolumen());
        
    }

    public void bajarVolumen() {
        
        this.dispositivo.setVolumen(this.dispositivo.getVolumen() - 10);
        
        System.out.println("Volumen = " + this.dispositivo.getVolumen());
        
    }

    public void power() {
        
        System.out.println("Encendiendo dispositivo");
        
        this.dispositivo.encender();
        
    }

}
