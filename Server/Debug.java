package Server;
public class Debug {

    private static boolean DEBUG = true;
    public static void DEBUG(String s) {
	if (DEBUG)
	    System.out.println("\t" + s);
    }
}
