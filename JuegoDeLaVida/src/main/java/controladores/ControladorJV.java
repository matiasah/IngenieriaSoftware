package controladores;

import java.util.HashMap;
import java.util.Timer;
import java.util.Observable;
import java.util.Observer;

/**
 *
 * @author Matías
 */
public class ControladorJV extends Observable {

    private Timer timer;
    private ControladorTimerJV timerTask;

    private boolean[][] matriz;
    private int ancho;
    private int largo;

    /**
     * Constructor de la clase
     */
    public ControladorJV() {

        this.timer = new Timer();
        this.timerTask = null;

        this.matriz = null;
        this.ancho = 0;
        this.largo = 0;

    }

    /**
     * Cambia el tamaño del tablero
     *
     * @param n 'n' filas
     * @param m 'm' columnas
     */
    public void setTamaño(int n, int m) {

        this.ancho = m;
        this.largo = n;
        this.matriz = new boolean[this.ancho][this.largo];

        // Notificar a observadores
        HashMap tabla = new HashMap();

        tabla.put("ancho", this.ancho);
        tabla.put("largo", this.largo);

        this.setChanged();
        this.notifyObservers(new EventoJV(TipoEventoJV.CAMBIO_TAMAÑO, tabla));

    }

    /**
     * Reanima una celula
     *
     * @param x Posicion X de la celula
     * @param y Posicion Y de la celula
     */
    public void setViva(int x, int y) {

        this.matriz[x][y] = true;

        // Notificar a observadores
        HashMap tabla = new HashMap();

        tabla.put("x", x);
        tabla.put("y", y);
        tabla.put("viva", true);

        this.setChanged();
        this.notifyObservers(new EventoJV(TipoEventoJV.CAMBIO_CELDA, tabla));

    }

    /**
     * Mata a una celula
     *
     * @param x Posicion X de la celula
     * @param y Posicion Y de la celula
     */
    public void setMuerta(int x, int y) {

        this.matriz[x][y] = false;

        // Notificar a observadores
        HashMap tabla = new HashMap();

        tabla.put("x", x);
        tabla.put("y", y);
        tabla.put("viva", false);

        this.setChanged();
        this.notifyObservers(new EventoJV(TipoEventoJV.CAMBIO_CELDA, tabla));

    }

    /**
     * Indica si una celula se encuentra viva
     *
     * @param x Posicion X de la celula
     * @param y Posicion Y de la celula
     * @return True si cierta celula se encuentra viva
     */
    public boolean getViva(int x, int y) {

        if (y < 0 || y >= this.largo) {

            return false;

        }

        if (x < 0 || x >= this.ancho) {

            return false;

        }

        return this.matriz[x][y];

    }

    /**
     * Cuenta la cantidad de celulas vivas a su alrededor
     *
     * @param x
     * @param y
     * @return
     */
    private int getVivas(int x, int y) {

        int numVivas = 0;

        if (this.getViva(x - 1, y - 1)) {
            numVivas++;
        }
        if (this.getViva(x - 1, y)) {
            numVivas++;
        }
        if (this.getViva(x - 1, y + 1)) {
            numVivas++;
        }
        if (this.getViva(x, y - 1)) {
            numVivas++;
        }
        if (this.getViva(x, y + 1)) {
            numVivas++;
        }
        if (this.getViva(x + 1, y - 1)) {
            numVivas++;
        }
        if (this.getViva(x + 1, y)) {
            numVivas++;
        }
        if (this.getViva(x + 1, y + 1)) {
            numVivas++;
        }

        return numVivas;

    }

    /**
     *
     */
    public void generar() {

        for (int x = 0; x < this.ancho; x++) {
            
            for (int y = 0; y < this.largo; y++) {
                
                if ( (int) ( Math.random() * 2) == 1 ) {
                    
                    this.setViva(x, y);
                    
                } else {
                    
                    this.setMuerta(x, y);
                    
                }
                
            }
            
        }
        
    }

    /**
     *
     */
    public void avanzar() {

        if (this.timerTask == null) {

            this.timerTask = new ControladorTimerJV(this);
            this.timer.scheduleAtFixedRate(this.timerTask, 0, 300);

        }

    }

    /**
     *
     */
    public void detener() {

        if (this.timerTask != null) {

            this.timerTask.cancel();
            this.timerTask = null;

        }

    }

    public void run() {

        boolean[][] nuevaMatriz = new boolean[this.ancho][this.largo];

        for (int x = 0; x < this.ancho; x++) {

            for (int y = 0; y < this.largo; y++) {

                int vivas = this.getVivas(x, y);

                nuevaMatriz[x][y] = this.getViva(x, y);

                if (!this.getViva(x, y) && vivas == 3) {

                    nuevaMatriz[x][y] = true;

                }

                if (this.getViva(x, y) && vivas > 3) {

                    nuevaMatriz[x][y] = false;

                }

                if (this.getViva(x, y) && vivas < 2) {

                    nuevaMatriz[x][y] = false;

                }

            }

        }

        for (int x = 0; x < this.ancho; x++) {

            for (int y = 0; y < this.largo; y++) {

                if (nuevaMatriz[x][y]) {

                    this.setViva(x, y);

                } else {

                    this.setMuerta(x, y);

                }

            }

        }

    }

    /**
     * Agregar un nuevo observador a este controlador
     *
     * @param observer
     */
    @Override
    public void addObserver(Observer observer) {

        if (observer != null) {

            HashMap tabla = new HashMap();

            tabla.put("ancho", this.ancho);
            tabla.put("largo", this.largo);

            observer.update(this, new EventoJV(TipoEventoJV.CAMBIO_TAMAÑO, tabla));

        }

        super.addObserver(observer);

    }

}
