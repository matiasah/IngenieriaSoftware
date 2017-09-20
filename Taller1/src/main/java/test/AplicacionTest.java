package test;

import codigo.*;
import java.util.ArrayList;

public class AplicacionTest {

    public static void main(String[] args) {
        
        // Instanciar objeto entidad central
        EntidadCentral ent = new EntidadCentral();

        // Crear las listas con los instrumentos
        ArrayList<InstrumentoSubject> instEst1 = new ArrayList<>();
        ArrayList<InstrumentoSubject> instEst2 = new ArrayList<>();

        // Crear los instrumentos
        Barometro barometro = new Barometro();
        Higrometro higrometro = new Higrometro();
        Anemometro anemometro = new Anemometro();
        
        // Instanciar observadores
        BarometroObserver barometroObserver = new BarometroObserver(barometro);
        HigrometroObserver higrometroObserver = new HigrometroObserver(higrometro);
        AnemometroObserver anemometroObserver = new AnemometroObserver(anemometro);
        
        // Agregar un observador a cada instrumento
        barometro.agregarObservador( barometroObserver );
        higrometro.agregarObservador( higrometroObserver );
        anemometro.agregarObservador( anemometroObserver );

        // Agregar los instrumentos creados a la primera lista
        instEst1.add(barometro);
        instEst1.add(higrometro);
        instEst1.add(anemometro);

        // Crear nuevos instrumentos
        Pluviometro pluviometro = new Pluviometro();
        Termometro termometro = new Termometro();
        Veleta veleta = new Veleta();
        Higrometro higrometro2 = new Higrometro();
        
        // Crear observadores para los nuevos instrumentos
        PluviometroObserver pluviometroObserver = new PluviometroObserver(pluviometro);
        TermometroObserver termometroObserver = new TermometroObserver(termometro);
        VeletaObserver veletaObserver = new VeletaObserver(veleta);
        HigrometroObserver higrometroObserver2 = new HigrometroObserver(higrometro2);
        
        // Agregar observadores a los nuevos instrumentos
        pluviometro.agregarObservador( pluviometroObserver );
        termometro.agregarObservador( termometroObserver );
        veleta.agregarObservador( veletaObserver );
        higrometro2.agregarObservador( higrometroObserver2 );
        
        // Agregar los nuevos instrumentos a la segunda lista
        instEst2.add(pluviometro);
        instEst2.add(termometro);
        instEst2.add(veleta);
        instEst2.add(higrometro2);

        // Crear dos estaciones, cada uno con sus instrumentos y una entidad central
        Estacion est1 = new Estacion(ent, instEst1);
        Estacion est2 = new Estacion(ent, instEst2);
        
        // Fijar estaciones de los observadores
        barometroObserver.setEstacion(est1);
        higrometroObserver.setEstacion(est1);
        anemometroObserver.setEstacion(est1);
        
        pluviometroObserver.setEstacion(est2);
        termometroObserver.setEstacion(est2);
        veletaObserver.setEstacion(est2);
        higrometroObserver2.setEstacion(est2);
        
        // Agregar las estaciones a la entidad central
        ent.agregarEstacion(est1);
        ent.agregarEstacion(est2);
        
        // Actualizar y mostrar mediciones
        ent.actualizarMediciones();
        ent.mostrarMediciones();
        

    }

}
