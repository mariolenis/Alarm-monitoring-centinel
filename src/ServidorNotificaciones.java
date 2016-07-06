
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Kailu Mario
 */
public class ServidorNotificaciones implements Runnable {
   
    private boolean detener = false;
    private String imei, mensaje = "";
    private ServerSocket serverSocket;
    private DataOutputStream outToClient;
    private Socket connectionSocket;
    private final ArrayList<ServidorNotificaciones> servidor = new ArrayList<>();

    public ServidorNotificaciones(ServerSocket serverSocket, String imei) {
        this.imei = imei;
        this.serverSocket = serverSocket;
    }
    
    // Metodo interno para establecer el socket por donde se responde
    private ServidorNotificaciones(Socket auxSocket, DataOutputStream outToClient){
        this.connectionSocket = auxSocket;
        this.outToClient = outToClient;
    }
    
    public void iniciar() // debe ser lanzado como un Thread
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket connectionSocket = null;
                    while (true) // Se lanza indefinidamente.
                    {
                        System.out.println("iniciando...");
                        connectionSocket = serverSocket.accept();
                        // Ha entrado una nueva conexión, Eliminar el anterior que coincida con el imei
                        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        Scanner sc = new Scanner(inFromClient).useDelimiter("!");
                        String recivedImei = sc.next();
                        
                        if (imei.equals(recivedImei)) // Cumple 
                        {
                            ServidorNotificaciones srv = new ServidorNotificaciones(connectionSocket, outToClient);
                            Thread auxThread = new Thread(srv);
                            auxThread.start();
                            System.out.println("Servicio para: " + recivedImei + " Con Hilo: " + auxThread.getId());
                            
                            if (!servidor.isEmpty())
                            {
                                for (ServidorNotificaciones server: servidor) // Detenerlos a todos
                                {
                                    server.detener();
                                }
                                servidor.clear(); // Solo debe esuchar uno
                            }
                            servidor.add(srv);
                        }
                    }
                } catch (IOException ex) {
                    try {
                        if (connectionSocket != null && connectionSocket.isConnected())
                            connectionSocket.close();
                        Logger.getLogger(ServidorNotificaciones.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex1) {
                        Logger.getLogger(ServidorNotificaciones.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            }
        }).start();        
    }
    
    @Override
    public void run() {
        try {
            while (!detener)
            {
                if (!mensaje.equals("") && mensaje.length() > 2 )
                {
                    outToClient.writeBytes(mensaje);
                    mensaje = "";
                }
                Thread.sleep(1000);
            }
        } catch (IOException ex) {
            Logger.getLogger(ServidorNotificaciones.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(ServidorNotificaciones.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void notificar(String mensaje) // Este proceso es invocado externamente
    {
        if (!servidor.isEmpty())
        {
            ServidorNotificaciones srv = servidor.get(servidor.size()-1);
            srv.mensaje = mensaje;
        }
    }
    
    public boolean detener() // Este proceso es invocado internamente por el hilo primario
    {
        try {
            this.detener = true;
            connectionSocket.close();
            return true;
        } catch (IOException ex) {
            Logger.getLogger(ServidorNotificaciones.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }    
}
