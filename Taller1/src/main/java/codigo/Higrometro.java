package codigo;

public class Higrometro extends InstrumentoSubject {

    private float humedad;

    @Override
    public void capturar() {

        this.humedad = (float) Math.random();
        this.notificar();

    }

    public float getHumedad() {
        
        return this.humedad;
        
    }

}
