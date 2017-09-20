package codigo;

public class Termometro extends InstrumentoSubject {

    private int temperatura;

    @Override
    public void capturar() {

        this.temperatura = (int) (Math.random() * 100 - 30);
        this.notificar();

    }

    public int getTemperatura() {

        return this.temperatura;

    }

}
