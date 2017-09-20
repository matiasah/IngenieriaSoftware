package bridge;

public class TV implements Dispositivo {

    private boolean on;
    private int canal;
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
        
        this.canal = canal;
        
    }

    @Override
    public int getCanal() {
        
        return this.canal;
        
    }

    @Override
    public void imprimirEstado() {
        
        System.out.println("Encendido: " + this.on);
        System.out.println("Canal: " + this.canal);
        System.out.println("Volumen: " + this.volumen);
        
    }

}
