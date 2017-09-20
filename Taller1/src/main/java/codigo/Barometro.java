package codigo;

public class Barometro extends InstrumentoSubject {

    private int presion;

    @Override
    public void capturar() {

        this.presion = (int) (Math.random() * 100);
        this.notificar();

    }

    public int getPresion() {

        return this.presion;

    }

}
