package codigo;

public class Veleta extends InstrumentoSubject {

    private double x;
    private double y;

    @Override
    public void capturar() {

        this.x = Math.random() * 10 - 5.0;
        this.y = Math.random() * 10 - 5.0;
        this.notificar();

    }

    public double getX() {

        return this.x;

    }

    public double getY() {

        return this.y;

    }

}
