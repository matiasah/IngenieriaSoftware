package bridge;

public interface Dispositivo {

	void encender();

	void apagar();

	/**
	 * 
	 * @param volumen
	 */
	void setVolumen(int volumen);

	int getVolumen();

	/**
	 * 
	 * @param canal
	 */
	void setCanal(int canal);

	int getCanal();

	void imprimirEstado();

}