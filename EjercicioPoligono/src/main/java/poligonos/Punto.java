package poligonos;


public class Punto {

    private double x;
    private double y;

    /**
     *
     * @param x
     * @param y
     */
    public Punto(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Punto() {
        this.x = 0;
        this.y = 0;
    }

    public Punto getMedio(Punto punto) {

        if (punto == null) {
            throw new NullPointerException("se recibió punto nulo");
        }

        Punto medio = new Punto();

        medio.setX((this.x + punto.getX()) * 0.5);
        medio.setY((this.y + punto.getY()) * 0.5);

        return medio;

    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    /**
     *
     * @param punto
     */
    public double getDistancia(Punto punto) {
        if (punto == null) {
            throw new NullPointerException("se recibió punto nulo");
        }

        double dx = this.x - punto.getX();
        double dy = this.y - punto.getY();

        return Math.sqrt(dx * dx + dy * dy);
    }

    public String toString() {
        return "(" + this.x + "," + this.y + ")";
    }

}
