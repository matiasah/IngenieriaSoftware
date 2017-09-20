
public class TestPunto {

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        
        Punto primerPunto = new Punto();
        Punto segundoPunto = new Punto(5, 3);
        Punto tercerPunto = new Punto(2, -1);
        Punto cuartoPunto = segundoPunto.getMedio(tercerPunto);
        
        Punto puntoPrueba = new Punto(4, 3);
        
        System.out.println(puntoPrueba.getDistancia(primerPunto));
        
    }

}
