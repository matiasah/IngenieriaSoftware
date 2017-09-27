package poligonos;


public class TestPoligono {

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        
        Punto [] puntos = new Punto[]{
            new Punto(0, 0),
            new Punto(2, 0),
            new Punto(2, 2),
            new Punto(0, 2)
        };

        Poligono primerPoligono = new Poligono(puntos);
        
        System.out.println("Vertices: " + primerPoligono.getNumVertices());
        System.out.println("Perimetro: " + primerPoligono.getPerimetro());
        System.out.println(primerPoligono);
        
        primerPoligono.trasladar(4, -3);
        
        System.out.println("Vertices: " + primerPoligono.getNumVertices());
        System.out.println("Perimetro: " + primerPoligono.getPerimetro());
        System.out.println(primerPoligono);
        
        primerPoligono.escalar(3, 0.5);
        
        System.out.println("Vertices: " + primerPoligono.getNumVertices());
        System.out.println("Perimetro: " + primerPoligono.getPerimetro());
        System.out.println(primerPoligono);
        
    }

}
