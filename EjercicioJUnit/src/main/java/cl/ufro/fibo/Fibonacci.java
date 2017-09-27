package cl.ufro.fibo;

/**
 *
 * @author Matías
 */
public class Fibonacci {
    
    public static int fib(int n) {
        
        return n == 0 ? 0 : ( n <= 2 ? 1 : fib( n - 1 ) + fib( n - 2 ) );
        
    }
    
}
