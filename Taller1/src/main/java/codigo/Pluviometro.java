package codigo;

public class Pluviometro extends InstrumentoSubject {

    private int aguaCaida;

    @Override
    public void capturar() {

        this.aguaCaida = (int) (Math.random() * 100);
        this.notificar();

    }

    public int getAguaCaida() {

        return this.aguaCaida;

    }

}
