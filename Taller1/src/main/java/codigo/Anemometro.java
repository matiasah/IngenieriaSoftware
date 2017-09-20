package codigo;

public class Anemometro extends InstrumentoSubject {

    private int velocidadViento;

    @Override
    public void capturar() {
        
        this.velocidadViento = (int) (Math.random() * 1000);
        this.notificar();

    }

    public int getVelocidadViento() {
        
        return this.velocidadViento;
        
    }

}
