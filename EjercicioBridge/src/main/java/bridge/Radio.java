package bridge;

public class Radio implements Dispositivo {

    private boolean on;
    private int dial;
    private int volumen;

    @Override
    public void encender() {
        
        this.on = true;
        
    }

    @Override
    public void apagar() {
        
        this.on = false;
    
    }

    @Override
    public void setVolumen(int volumen) {
        
        this.volumen = volumen;
        
    }

    @Override
    public int getVolumen() {
        
        return this.volumen;
        
    }

    @Override
    public void setCanal(int canal) {
        
        this.dial = canal;
        
    }

    @Override
    public int getCanal() {
        
        return this.dial;
        
    }

    @Override
    public void imprimirEstado() {
        
        System.out.println("Encendida: " + this.on);
        System.out.println("Dial: " + this.dial);
        System.out.println("Volumen: " + this.volumen);
        
    }

}
