package reproductormusical;

import java.util.*;

public class ListaMultimedia {

    private final ArrayList<Multimedia> lista;

    /**
     * Constructor de clase ListaMultimedia
     */
    public ListaMultimedia() {
        this.lista = new ArrayList<>();
    }

    /**
     *
     * @return El tamaño de la lista
     */
    public int getTam() {
        return this.lista.size();
    }

    /**
     * Agrega un objeto multimedia a la lista
     * @param multimedia El objeto multimedia a agregar
     */
    public void agregarMultimedia(Multimedia multimedia) {
        this.lista.add(multimedia);
    }

    /**
     * 
     * @param indice
     * @return El objeto multimedia en la ubicación indicada
     */
    public Multimedia getMultimedia(int indice) {
        return this.lista.get(indice);
    }

    /**
     * 
     * @param multimedia
     * @return La ubicación del objeto multimedia en la lista
     */
    public int getIndice(Multimedia multimedia) {
        for (int i = 0, size = this.lista.size(); i < size; i++) {
            Multimedia mult = this.lista.get(i);
            if (mult.equals(multimedia)) {
                return i;
            }
        }
        return -1;
    }

    /**
     *
     * @return La lista de objetos multimedia agregados
     */
    @Override
    public String toString() {
        
        String out = "";
        
        for ( Multimedia mult : this.lista ) {
            
            out += "---------------------\n" + mult.toString() + "\n";
            
        }
        
        return out.length() > 0 ? out.substring(1, out.length()) : "";
        
    }

}
