
import java.util.*;

public class Poligono {

    private Punto[] puntos;

    /**
     *
     * @param puntos
     */
    public Poligono(Punto[] puntos) {
        // TODO - implement Poligono.Punto
        if (puntos == null) {
            throw new NullPointerException("No se recibieron puntos");
        } else if (puntos.length < 3) {
            throw new IllegalArgumentException("Hay menos de 3 puntos");
        }
        this.puntos = puntos;
    }

    /**
     *
     * @param x
     * @param y
     */
    public void trasladar(double x, double y) {
        // TODO - implement Poligono.trasladar
        for (int i = 0; i < this.puntos.length; i++) {
            Punto punto = this.puntos[i];
            punto.setX(punto.getX() + x);
            punto.setY(punto.getY() + y);
        }
    }

    /**
     *
     * @param xFac
     * @param yFac
     */
    public void escalar(double xFac, double yFac) {
        // TODO - implement Poligono.escalar
        for (int i = 0; i < this.puntos.length; i++) {
            Punto punto = this.puntos[i];
            punto.setX(punto.getX() * xFac);
            punto.setY(punto.getY() * yFac);
        }
    }

    public int getNumVertices() {
        // TODO - implement Poligono.numVertices
        return this.puntos.length;
    }

    public String toString() {
        // TODO - implement Poligono.toString
        String linea = "";
        for (int i = 0; i < this.puntos.length; i++) {
            linea += this.puntos[i] + (i == this.puntos.length - 1 ? "" : "\n");
        }
        return linea;
    }

    public double getPerimetro() {
        // TODO - implement Poligono.perimetro
        double perimetro = 0;

        for (int i = 1; i < this.puntos.length; i++) {
            perimetro += this.puntos[i].getDistancia(this.puntos[i-1]);
        }

        perimetro += this.puntos[0].getDistancia(this.puntos[this.puntos.length - 1]);

        return perimetro;

    }

}
