import java.util.logging.Level;
import java.util.logging.Logger;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Kailu Mario
 * @version 0.10
 * @since 2013-06-02 15:20
 */
public class Centinela implements Runnable{
    
    private final String GTALKBACKUP = "seg.montgomery@gmail.com";
    private final String GTALKOFICIAL = "seguridad.montgomery@montgomery.com.co";
    
    private Connection conn;
    private int timeoutNotificacion = 30000;
    private String idOperacion, gTalkCuenta, pushMessage;
    private XMPPConnection connection, xmpp, xmppNotificaciones;
    private Calendar ultimaHora, horaSuper;
    private ArrayList<Operador> operador = new ArrayList<>(); //, supervisor = new Vector();
    private ArrayList<Supervisor> supervisor = new ArrayList<>();
            
    private ServerSocket gpsSocket;
    //private ServerSocket gpsSocket          = new ServerSocket(9999);
    private ServerSocket supervisorSocket   = new ServerSocket(9998);
    private ServerSocket solutekSocket      = new ServerSocket(9997);
    private ServerSocket paradoxSocket      = new ServerSocket(9996);
    private ServerSocket pushSocket         = new ServerSocket(9995);
    private Thread clasificadorEvento, generardorTardeXCerrar, pushNotification,
            asignadorAlarma, tareaProgramada, notificaciones, alarmaIp, trackers, posicionSupervisor, turnos, solutek, paradox, tPeriodica;
    
    public Centinela() throws IOException
    //<editor-fold defaultstate="collapsed" desc="Constructor de servicios">
    {
        try {
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver);
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
            
            ultimaHora  = Calendar.getInstance();
            horaSuper   = Calendar.getInstance();
            
            gTalkCuenta = GTALKOFICIAL;
            notificaciones = new Thread(this);
            notificaciones.start();
            
            alarmaIp = new Thread(this);
            alarmaIp.start();
            
            // SERVICIOS DE ALARMA
            // Thread para identificar que tipo de evento.
            clasificadorEvento = new Thread(this);
            clasificadorEvento.start();
            
            //Thread de vrificación de clientes con o sin operador asignado.
            asignadorAlarma = new Thread(this);
            asignadorAlarma.start();
            
            generardorTardeXCerrar = new Thread(this);
            generardorTardeXCerrar.start();
            
            tareaProgramada = new Thread(this);
            tareaProgramada.start();
            
            // Thread TCP Server para GPS
            //tcpServer = new Thread(this);
            //tcpServer.start();
            trackers = new Thread(this);
            trackers.start();
            
            pushNotification = new Thread(this);
            pushNotification.start();
            
            turnos = new Thread(this);
            turnos.start();
            
            //posicionSupervisor = new Thread(this);
            //posicionSupervisor.start();
            
            tPeriodica = new Thread(this);
            tPeriodica.start();
            
            solutek = new Thread(this);
            solutek.start();
            
            paradox = new Thread(this);
            paradox.start();
            
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    public void run()
    {
        Thread hilo = Thread.currentThread();
        while (clasificadorEvento == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo de clasificacion">
        {
            try {
                
                Statement s = (Statement) conn.createStatement();
                Statement st = (Statement) conn.createStatement();
                String sSQL = "SELECT * FROM evento WHERE estado = 'PENDIENTE' order by idEvento";
                ResultSet res = s.executeQuery(sSQL);
                
                while( res.next() )
                {
                    
                    String categoria = clasificarEvento(res.getString("evento"), res.getString("protocolo"));
                    // Actualizar el estado de la alarma por cierres
                    if ( res.getString("tipoEvento").contains("CIERRE") || res.getString("tipoEvento").contains("APERTURA") || categoria.contains("ARMADO"))
                    {
                        String sts;
                        if ( categoria.contains("ARMADO") && res.getString("tipoEvento").equals("RESTAURE") )
                            sts = "CIERRE";
                        else if ( categoria.contains("ARMADO") && res.getString("tipoEvento").equals("EVENTO") )
                            sts = "APERTURA";
                        else
                            sts = res.getString("tipoEvento");
                        
                        String sEstado = "UPDATE alarma SET estado = '"+ sts +"', fechaUltimoEvento = now() "
                                + "WHERE idAlarma = '"+res.getString("idAlarma")+"'";
                        st.executeUpdate(sEstado);
                    }
                    
                    String strEstado = "TRAMITAR";
                    if ( res.getString("tipoEvento").contains("CIERRE") || (categoria.contains("ARMADO") && res.getString("tipoEvento").equals("RESTAURE")) ) // operador[1].agregarEvento(res.getString("idEvento"));  
                    {
                        strEstado = "ATENDIDO";
                        idOperacion = UUID.randomUUID().toString();
                        st.executeUpdate("UPDATE evento SET estado = 'ATENDIDO' WHERE evento = '800' OR (evento LIKE '40%' AND tipoEvento = 'APERTURA')"
                                + " AND fecha < '"+ res.getString("fecha") +"'"
                                + " AND idAlarma = '"+ res.getString("idAlarma") +"'");
                        
                        // Componer mensaje
                        String mensaje;
                        ResultSet ar = st.executeQuery("SELECT cliente.nombreEscrito FROM cliente, alarma WHERE alarma.idCliente = cliente.idCliente AND alarma.estadoActual != 'I' AND alarma.idAlarma = '"+ res.getString("idAlarma") +"'");
                        if (ar.first() && !ar.getString("nombreEscrito").equals("") && !ar.getString("nombreEscrito").equals("null"))
                        {
                            mensaje = "Establecimiento: " + ar.getString("nombreEscrito").toUpperCase() + ". ";
                            mensaje += "Su sistema ha sido ARMADO correctamente, cualquier novedad le informaremos oportunamente.";

                            // Restaurar los horario de cierre
                            st.executeUpdate("UPDATE horario SET AM = defaultAM, PM = defaultPM, last = NOW() WHERE idAlarma = '"+ res.getString("idAlarma") +"'");

                            // Informar del cierre
                            informarCierre(res.getString("idAlarma"), mensaje, "REPORTE DE ARMADO");
                        }
                    }
                    
                    /*else if (res.getString("tipoEvento").contains("RESTAURE") && categoria.equals("PARQUEO"))  
                    {
                        // Han activado el modo parqueo
                        strEstado = "ATENDIDO";
                        // Componer mensaje
                        StringBuilder mensaje = new StringBuilder() ;
                        ResultSet ar = st.executeQuery("SELECT * FROM alarma WHERE idAlarma = '"+ res.getString("idAlarma") +"'");
                        
                        if (ar.first())
                        {
                            mensaje.append("Vehículo: ").append(ar.getString("descripcion"));
                            mensaje.append("\nSu sistema de parqueo ha sido ACTIVADO correctamente, cualquier novedad le informaremos oportunamente.");
                            st.executeUpdate("UPDATE alarma SET estado = 'MOD.PARQUEO' WHERE idAlarma = '"+ res.getString("idAlarma") +"'");
                            
                            // INFORMAR MOD. PARQUEO
                            idOperacion = UUID.randomUUID().toString();
                            informarCierre(res.getString("idAlarma"), mensaje.toString(), "MODO PARQUEO ACTIVADO");
                        }
                    }*/
                    
                    else if (categoria.equals("PRUEBAS"))
                        strEstado = "ATENDIDO";
                    
                    // Dar la orden de tramitar.
                    st.executeUpdate("UPDATE evento SET "
                            + "categoriaEvento = '"+ categoria +"', "
                            + "tipoZona = '"+ determinarZona(res.getString("idAlarma"), res.getString("zona")) +"', "
                            + "estado = '"+ strEstado +"', idOperacion = '"+ idOperacion +"' "
                            + "WHERE idEvento = '"+ res.getString("idEvento") +"'");
                    idOperacion = "";
                }
                
                res.close();
                st.close();
                s.close();
                Thread.sleep(600);
                
            } catch ( InterruptedException | SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>
        
        while (asignadorAlarma == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo para asignar un cliente a un operador">
        {
            try {
                //Seleccionar clientes que tengan eventos TRAMITAR
                String sSQL = "SELECT idAlarma FROM evento WHERE estado = 'TRAMITAR' OR estado LIKE 'EN TRAMITE' GROUP BY idAlarma";
                Statement st = (Statement)conn.createStatement();
                ResultSet res = st.executeQuery(sSQL);
                
                if (res.first())
                {
                    do 
                    {
                        int i;
                        String idAlarma = res.getString("idAlarma");
                        for (i=0; i < operador.size(); i++) {
                            //Operador op = (Operador)operador.elementAt(i);
                            //if (op.getAlarmaAsignada().equals(idAlarma))
                            if (operador.get(i).getAlarmaAsignada().equals(idAlarma))
                                break;
                        }

                        if (i == operador.size()) // No hubo ninguno. Crear
                            operador.add(new Operador(1000l, idAlarma, connection));
                    } while (res.next());
                }
                
                // No de haber reultados 
                if (operador.size() > 0)
                {
                    for (int i=0; i < operador.size(); i++) {
                        Operador op = (Operador)operador.get(i);
                        if (op.getEstado()) {
                            System.out.println("Eliminando Operador para idAlarma " + op.getAlarmaAsignada());
                            op.cerrarConexion();
                            op = null;
                            operador.remove(i);
                            i--;
                        }
                    }
                }
                res.close();
                st.close();
                Thread.sleep(500);
                
            } catch (    SQLException | InterruptedException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }   
        }//</editor-fold>
        
        while (generardorTardeXCerrar == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo de tardes para cerrar">
        {
            try {
                
                // Verificar que clientes se encuentran abiertos y que no se les haya generado evento 800 el mismo día.  
                // System.out.println("Verificando tardes para cerrar...");
                Calendar fecha = Calendar.getInstance();
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");                
                String sSQL = "SELECT alarmaNew.idAlarma, IFNULL(evento, 'VERIFICAR') AS evento, fechaUltimoEvento, fecha, alarmaNew.tipoAlarma FROM ( "
                        + "SELECT idAlarma, tipoAlarma, fechaUltimoEvento FROM alarma WHERE (estado = 'APERTURA' OR estado = 'ENCENDIDO') AND estadoActual != 'I' "
                        + ") AS alarmaNew LEFT JOIN ( "
                        + "SELECT idAlarma, evento, fecha FROM evento WHERE evento = '800' "
                        + "AND estado != 'ATENDIDO' "
                        + "AND evento.fecha between '"+ f.format(fecha.getTime()) +" 00:00' AND now() "
                        + ") AS tmpTable on alarmaNew.idAlarma = tmpTable.idAlarma "
                        + "ORDER BY evento DESC, alarmaNew.idAlarma";
                
                Statement s = (Statement) conn.createStatement();
                ResultSet res = s.executeQuery(sSQL);
                
                while (res.next())
                {
                    // Solo aquellos clientes que no tengan
                    if(!res.getString("evento").equals("VERIFICAR"))
                        break; // Si ya hay eventos tipo 800 no hay nada más que verificar.
                    
                    // VERFICAR LOS HORARIOS DE CIERRE DE LOS CLIENTES
                    f = new SimpleDateFormat("HH:mm");
                    sSQL = "SELECT * FROM horario WHERE dia LIKE '%"+ fecha.get(Calendar.DAY_OF_WEEK) +"%' "
                            + "AND PM < '"+ f.format(fecha.getTime()) +"' "
                            + "AND idAlarma = '"+ res.getString("idAlarma") +"'";
                    
                    //System.out.println(sSQL);
                    Statement ts = (Statement)conn.createStatement();
                    ResultSet clientes = ts.executeQuery(sSQL);
                    StringBuilder tardeParaCerrar = new StringBuilder();
                    
                    if (clientes.first())
                    {
                        // Aqui estan los clientes que no han cerrado y ya es tarde para cerrar.
                        switch (res.getString("tipoAlarma")) {
                            case "ANTIROBO":
                                {
                                    String trama = res.getString("idAlarma") + "18180001000";
                                    tardeParaCerrar.append("INSERT INTO evento (trama, protocolo, evento, idAlarma, particion, tipoEvento, zona) VALUES ");
                                    tardeParaCerrar.append("('").append(trama).append("', 'CID', '800', '").append(res.getString("idAlarma")).append("', '01', 'EVENTO', '000'); ");
                                    break;
                                }
                            case "GPS":
                                {
                                    String trama = "GPS" + res.getString("idAlarma") + "0000";
                                    tardeParaCerrar.append("INSERT INTO evento (trama, protocolo, evento, idAlarma, particion, tipoEvento, zona, clid) VALUES ");
                                    tardeParaCerrar.append("('").append(trama).append("', 'GPS', '800', '").append(res.getString("idAlarma")).append("', '0.0', 'EVENTO', '000', '0.0/0.0/0.0'); ");
                                    break;
                                }
                        }
                    }
                    
                    // Ejecutar conunto de consultas.
                    if (!tardeParaCerrar.toString().equals(""))
                        ts.executeUpdate(tardeParaCerrar.toString());                    
                    // Tardes para cerrar generados.
                    
                    clientes.close();
                    ts.close();
                }
                res.close();
                s.close();
                Thread.sleep(120000); // cada 2 minutos.
                
            } catch (    InterruptedException | SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }//</editor-fold>
        
        while (tareaProgramada == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo de verificación de horarios por defecto">
        {
            try {
                Calendar c = Calendar.getInstance();
                Statement st = (Statement) conn.createStatement();
                ResultSet res = st.executeQuery("SELECT MAX(last) AS last FROM horario;");
                
                if (res.first())
                {                  
                    Calendar lastUpdate = Calendar.getInstance();
                    lastUpdate.setTimeInMillis(c.getTimeInMillis());
                    String[] aTime = res.getString("last").split(" ");
                    String[] nTime = aTime[0].split("-");
                    if (nTime.length == 3)
                    {
                        lastUpdate.set(Calendar.YEAR, Integer.parseInt(nTime[0]));
                        lastUpdate.set(Calendar.MONTH, Integer.parseInt(nTime[1])-1);
                        lastUpdate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(nTime[2]));

                        if (lastUpdate.getTimeInMillis() < c.getTimeInMillis()) // Si es un nuevo día
                        {
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                            ResultSet r = st.executeQuery("SELECT * FROM festivos WHERE fecha = '"+ format.format(c.getTime()) +"'");
                            
                            if (r.first()) // El nuevo día es Festivo
                            {
                                ResultSet resFestivo = st.executeQuery("SELECT * FROM horario WHERE dia LIKE '1'");
                                while(resFestivo.next())
                                {
                                    StringBuilder sqlExec = new StringBuilder();
                                    sqlExec.append("UPDATE horario SET AM = '");
                                    sqlExec.append(resFestivo.getString("defaultAM"));
                                    sqlExec.append("', PM = '");
                                    sqlExec.append(resFestivo.getString("defaultPM"));
                                    sqlExec.append("' WHERE idAlarma = '");
                                    sqlExec.append(resFestivo.getString("idAlarma"));
                                    sqlExec.append("' AND dia LIKE '%");
                                    sqlExec.append(c.get(Calendar.DAY_OF_WEEK));
                                    sqlExec.append("%'");
                                    st.addBatch(sqlExec.toString());
                                }
                                st.executeBatch();
                                resFestivo.close();
                            } else                            
                                st.executeUpdate("UPDATE horario SET AM = defaultAM, PM = defaultPM, last = NOW()");
                            r.close();
                        }
                    }
                }
                res.close();
                st.close();
                
                Thread.sleep(2*3600000); //Cada 2 Horas
            } catch (    InterruptedException | SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }//</editor-fold>
        
        // PUERTO DE CONEXIÓN TCP        
        while(trackers == hilo)
        //<editor-fold defaultstate="collapsed" desc="Socket de Patrulleros. 9998">
        {
            try 
            {
                final Socket connectionSocket = supervisorSocket.accept();
                new Thread(new Runnable() {                    
                    @Override
                    public void run() {
                        String sSQL = "";
                        DataOutputStream outToClient;
                        try {
                            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                            outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                            Scanner sc = new Scanner(inFromClient).useDelimiter("!");
                            String track = sc.next();
                            
                            System.out.println(track);
                            String[] datos;
                            if (track.indexOf("*") > 0)
                                //<editor-fold defaultstate="collapsed" desc="Con Trackers">
                            {
                                datos = track.substring(0, track.indexOf('*')).split(",");
                                if (datos.length > 15 && track.contains("GSr"))
                                {
                                    String imei = datos[1];
                                    String lon = datos[9].replace("W", "-").replace("E", "");
                                    String lat = datos[10].replace("N", "").replace("S", "-");
                                    String hea = datos[13];
                                    String bat = datos[16];
                                    int evt = Integer.parseInt(datos[4]);
                                    
                                    lat = String.valueOf( coordenar(Double.parseDouble(lat)/100) );
                                    lon = String.valueOf( coordenar(Double.parseDouble(lon)/100) );
                                    Calendar fecha = Calendar.getInstance();
                                    SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                                    System.out.println(lat  + "," + lon + " " + f.format(fecha.getTime()));
                                    
                                    String tramaAlarm = String.format("%7s", Integer.toBinaryString(evt)).replace(' ', '0');
                                    char[] evento   = tramaAlarm.toCharArray();  // Eventos recibidos array de 8 elementos.
                                    if (evento[6] == '1')
                                    {
                                        switch (imei) {
                                            case "011412000071450":
                                                outToClient.writeBytes("GSC,"+ imei +",Na*48!\n\r"); // Dismiss SOS
                                                outToClient.writeBytes("GSC,"+ imei +",Ni*40!\n\r"); // Dismiss ALL
                                                break;
                                            case "011412000068001":
                                                outToClient.writeBytes("GSC,"+ imei +",Na*40!\n\r"); // Dismiss SOS
                                                outToClient.writeBytes("GSC,"+ imei +",Ni*48!\n\r"); // Dismiss ALL
                                                break;
                                        }
                                        sSQL = "REPORTE DE REACCIÓN";
                                        System.out.println(tramaAlarm + " DISMISS " + imei);
                                        // TODOD: INSERTAR EN LA VITACORA QUE YA HA LLEGADO A UNA REACCIÓN
                                    }
                                    else
                                    {
                                        sSQL = "REPORTE DE POSICIÓN";
                                    }
                                    Statement st = (Statement) conn.createStatement();
                                    st.executeUpdate("INSERT INTO eventoGPS (idGPS, lon, lat, orientacion, tipo, bateria) VALUES "
                                            + "('"+ imei +"', '"+ lon +"', '"+ lat +"', '"+ hea +"', '"+ sSQL +"', '"+ bat +"')");
                                    st.close();
                                }
                            }
                            //</editor-fold>
                            
                            else if (track.contains(":") && (track.startsWith("N") || track.startsWith("R")))
                            {
                                sSQL = "REPORTE DE POSICIÓN";
                                String[] dato = track.split(":");
                                Statement st = (Statement) conn.createStatement();
                                if (dato[0].startsWith("R"))
                                {
                                    // Insertar llegada al sitio
                                    sSQL = "REPORTE DE REACCIÓN";
                                    ResultSet rs = st.executeQuery("SELECT * FROM reaccion WHERE idReaccion = '"+ dato[0].replace("R", "") +"'");
                                    if (rs.first())
                                    {
                                        switch (rs.getString("estado")) {
                                            case "PENDIENTE":
                                            case "EN TRAMITE":
                                            case "NO ASIGNADA":
                                                // Agregar posición al reporte
                                                SimpleDateFormat f = new SimpleDateFormat("EEE, dd 'de' MMMMM yyyy 'a la(s)' HH:mm", new Locale("ES"));
                                                idOperacion = rs.getString("idOperacion");
                                                String latLon = dato[3] + "," + dato[4];
                                                // Actualización de reporte
                                                informarCierre(rs.getString("idAlarma"), "El supervisor ya se encuentra en el domicilio de su sistema de alarma, puede ver su posición ingresando al siguiente link\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=" + latLon, "REPORTE DE REACCIÓN " + f.format(Calendar.getInstance().getTime()));
                                                break;
                                        }
                                    }
                                }
                                
                                st.executeUpdate("INSERT INTO eventoGPS (idGPS, lat, lon, orientacion, tipo, bateria) VALUES "
                                        + "('"+ dato[1] +"', '"+ dato[3] +"', '"+ dato[4] +"', '"+ dato[5] +"', '"+ sSQL +"', '"+ dato[6] +"')");
                                st.close();
                            }
                            else if (track.equals("RP"))
                            {
                                Statement st = (Statement) conn.createStatement();
                                ResultSet rs = st.executeQuery("SELECT idReaccion, nombreEscrito FROM reaccion, alarma, cliente WHERE "
                                        + "reaccion.idAlarma = alarma.idAlarma AND "
                                        + "alarma.idCliente = cliente.idCliente AND "
                                        + "(reaccion.estado = 'EN TRAMITE' OR reaccion.estado = 'NO ASIGNADA') ORDER BY fecha DESC");
                                StringBuilder sb = new StringBuilder();
                                while (rs.next())
                                {
                                    sb.append(rs.getString("idReaccion")).append(" ").append(rs.getString("nombreEscrito")).append("-");
                                }
                                if (sb.length() > 0)
                                    outToClient.writeBytes(sb.toString().substring(0, sb.length()-1) + "\n\r");
                                else
                                    outToClient.writeBytes("0\n\r");
                            }
                        } catch (IOException | NoSuchElementException ex) {
                            try {
                                if (connectionSocket != null && !connectionSocket.isClosed())
                                    connectionSocket.close();
                            } catch (IOException ex1) {
                                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex1);
                            }
                        } catch (SQLException ex) {
                            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }).start();
                // TODO: si la trama tiene 2 puntos.
                //outToClient.close();
                //connectionSocket.close();            
            }
            catch (IOException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }//</editor-fold>
        
        while (solutek == hilo)
        //<editor-fold defaultstate="collapsed" desc="SOLUTEK 9997">
        {
            Socket connectionSocket = null;
            DataOutputStream outToClient;
            try {
                if (solutekSocket.isClosed())
                    solutekSocket = new ServerSocket(9997);
                connectionSocket = solutekSocket.accept();
                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                Scanner sc = new Scanner(inFromClient).useDelimiter(String.valueOf((char)20));
                String clientSentence = sc.next();
                //System.out.println(clientSentence + " " + connectionSocket.getRemoteSocketAddress());
                if (clientSentence.startsWith("5011"))
                {
                    Calendar c = Calendar.getInstance();
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    System.out.println(clientSentence + " " + clientSentence.length());
                    String[] tramaRecibida = clientSentence.split(" ");
                    
                    Statement st = (Statement) conn.createStatement();
                    String idAlarma = tramaRecibida[1].substring(2, 6);
                    String evt      = tramaRecibida[1].substring(6, 7);
                    String par      = tramaRecibida[1].substring(10, 12);
                    String zona     = tramaRecibida[1].substring(12);
                    int    evento   = Integer.parseInt(tramaRecibida[1].substring(7, 10));
                    String trama    = "";
                    switch (evt) {
                        case "E":
                            if (evento >= 400 && evento < 500)
                                evt = "APERTURA";
                            else
                                evt = "EVENTO";
                            trama = idAlarma + "181" + evento + par + zona;
                            break;
                        case "R":
                            if (evento >= 400 && evento < 500)
                                 evt = "CIERRE";
                            else
                                evt = "RESTAURE";
                            trama = idAlarma + "183" + evento + par + zona;
                            break;
                    }
                    
                    String sSQL = "INSERT INTO evento (fecha, trama, idAlarma, particion, evento, zona, tipoEvento, clid, inbound) VALUES ("
                            + "'"+ f.format(c.getTime()) +"', "
                            + "'"+ trama +"', "
                            + "'"+ idAlarma +"', "
                            + "'"+ par +"', "
                            + "'"+ evento +"', "
                            + "'"+ zona +"', "
                            + "'"+ evt +"', "
                            + "'000-0000', "
                            + "'"+ connectionSocket.getInetAddress() +"')";
                    st.executeUpdate(sSQL);
                    st.close();
                }
                outToClient.writeBytes( String.valueOf((char)6) );
                connectionSocket.close();
                
            } catch (IOException | NoSuchElementException | NumberFormatException ex) {
                try {                    
                    if (connectionSocket!= null)
                        connectionSocket.close();
                    solutekSocket.close();
                } catch (IOException ex1) {
                    Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex1);
                }
            } catch (SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>
        
        while (paradox == hilo)
        //<editor-fold defaultstate="collapsed" desc="PARADOX - PIMA 9996">
        {
            try
            {
                if (paradoxSocket.isClosed())
                    paradoxSocket = new ServerSocket(9996);
                final Socket connectionSocket = paradoxSocket.accept();
                new Thread(new Runnable() {
                    @Override
                    public void run() 
                    //<editor-fold defaultstate="collapsed" desc="....">
                    {
                        try {
                            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
                            DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                            Scanner sc = new Scanner(inFromClient).useDelimiter(String.valueOf((char)20));
                            String clientSentence = sc.next();
                            
                            //System.out.println(clientSentence + " " + connectionSocket.getRemoteSocketAddress());
                            if (clientSentence.startsWith("599"))
                            {
                                Calendar c = Calendar.getInstance();
                                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                System.out.println(clientSentence + " " + clientSentence.length());
                                String[] tramaRecibida = clientSentence.split(" ");
                                
                                Statement st = (Statement) conn.createStatement();
                                String idAlarma = tramaRecibida[1].substring(2, 6);
                                String evt      = tramaRecibida[1].substring(6, 7);
                                String par      = tramaRecibida[1].substring(10, 12);
                                String zona     = tramaRecibida[1].substring(12);
                                int    evento   = Integer.parseInt(tramaRecibida[1].substring(7, 10));
                                String trama    = "";
                                switch (evt) {
                                    case "E":
                                        if (evento >= 400 && evento < 500)
                                            evt = "APERTURA";
                                        else
                                            evt = "EVENTO";
                                        trama = idAlarma + "181" + evento + par + zona;
                                        break;
                                    case "R":
                                        if (evento >= 400 && evento < 500)
                                            evt = "CIERRE";
                                        else
                                            evt = "RESTAURE";
                                        trama = idAlarma + "183" + evento + par + zona;
                                        break;
                                }
                                
                                String sSQL = "INSERT INTO evento (fecha, trama, idAlarma, particion, evento, zona, tipoEvento, clid, inbound) VALUES ("
                                        + "'"+ f.format(c.getTime()) +"', "
                                        + "'"+ trama +"', "
                                        + "'"+ idAlarma +"', "
                                        + "'"+ par +"', "
                                        + "'"+ evento +"', "
                                        + "'"+ zona +"', "
                                        + "'"+ evt +"', "
                                        + "'000-0000', "
                                        + "'"+ connectionSocket.getInetAddress() +"')";
                                st.executeUpdate(sSQL);
                                st.close();
                            }
                            
                            outToClient.writeBytes( String.valueOf((char)6) );
                            connectionSocket.close();
                        } catch (IOException | NoSuchElementException ex) {
                            try {
                                if (connectionSocket != null)
                                    connectionSocket.close();
                                paradoxSocket.close();
                            } catch (IOException ex1) {
                                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex1);
                            }
                        } catch (SQLException ex) {
                            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                //</editor-fold>
                }).start();
            }
            catch (IOException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }//</editor-fold>
        
        // Notificaciones y verificaciones        
        while(pushNotification == hilo)
        //<editor-fold defaultstate="collapsed" desc="Notificaciones. XMPP OpenFire">
        {
            while (true)
            {
                try {
                    if (xmppNotificaciones == null || !xmppNotificaciones.isConnected()) 
                    {
                        
                        ConnectionConfiguration c = new ConnectionConfiguration("localhost", 5222, "Axar");
                        xmppNotificaciones = new XMPPConnection(c);
                        xmppNotificaciones.connect();
                        xmppNotificaciones.login("seguridad.online", "online__2013");
                        Presence presence = new Presence(Presence.Type.available);
                        xmppNotificaciones.sendPacket(presence);

                        PacketFilter filter = new MessageTypeFilter(Message.Type.chat);            
                        xmppNotificaciones.addPacketListener(new PacketListener() {
                            @Override
                            //<editor-fold defaultstate="collapsed" desc="Procesamiento de mensajes entrantes, SUPERS">
                            public void processPacket(Packet packet) {
                                Message message = (Message)packet;

                                if(message.getBody() != null)
                                {
                                    String remitente = message.getFrom().substring(0, message.getFrom().indexOf("@"));
                                    //System.out.println( message.getFrom() + " " + xmppNotificaciones.getRoster().getEntry(remitente) +  " " + message.getBody());
                                }

                                if(message.getBody() != null && message.getBody().contains(":") && (message.getBody().startsWith("N") || message.getBody().startsWith("R") || message.getBody().startsWith("X")))                                    
                                {
                                    try { //<editor-fold defaultstate="collapsed" desc="Reporte normal de posicion">
                                        String sSQL = "REPORTE DE POSICIÓN";
                                        String[] dato = message.getBody().split(":");
                                        Statement st = (Statement) conn.createStatement();
                                        if (dato[0].startsWith("R"))
                                        {
                                            // Insertar llegada al sitio
                                            sSQL = "REPORTE DE REACCIÓN";
                                            ResultSet rs = st.executeQuery("SELECT * FROM reaccion WHERE idReaccion = '"+ dato[0].replace("R", "") +"'");
                                            if (rs.first())
                                            {
                                                switch (rs.getString("estado")) {
                                                    case "PENDIENTE":
                                                    case "EN TRAMITE":
                                                    case "NO ASIGNADA":
                                                        // Agregar posición al reporte
                                                        SimpleDateFormat f = new SimpleDateFormat("EEE, dd 'de' MMMMM yyyy 'a la(s)' HH:mm", new Locale("ES"));
                                                        idOperacion = rs.getString("idOperacion");
                                                        String latLon = dato[3] + "," + dato[4];
                                                        // Actualización de reporte
                                                        informarCierre(rs.getString("idAlarma"), "El supervisor ya se encuentra en el domicilio de su sistema de alarma, puede ver su posición ingresando al siguiente link\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=" + latLon, "REPORTE DE REACCIÓN " + f.format(Calendar.getInstance().getTime()));
                                                        break;
                                                }
                                            }
                                        }
                                        else if (dato[0].startsWith("X"))
                                            sSQL = "REPORTE SIN SEÑAL GPS";

                                        st.executeUpdate("INSERT INTO eventoGPS (idGPS, lat, lon, orientacion, tipo, bateria) VALUES "
                                                + "('"+ dato[1] +"', '"+ dato[3] +"', '"+ dato[4] +"', '"+ dato[5] +"', '"+ sSQL +"', '"+ dato[6] +"')");
                                        st.close();
                                    }
                                    //</editor-fold>
                                    catch (SQLException ex) {
                                        Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }                                    
                                else if (message.getBody() != null && message.getBody().equals("RP"))
                                {
                                    try { //<editor-fold defaultstate="collapsed" desc="Reacciones Pendientes">
                                        Statement st = (Statement) conn.createStatement();
                                        ResultSet rs = st.executeQuery("SELECT idReaccion, reaccion.fecha AS fecha, reaccion.estado AS estado, nombreEscrito, alarma.direccion, alarma.barrio FROM reaccion, alarma, cliente WHERE "
                                                + "reaccion.idAlarma = alarma.idAlarma AND "
                                                + "alarma.idCliente = cliente.idCliente AND "
                                                + "(reaccion.estado = 'EN TRAMITE' OR reaccion.estado = 'NO ASIGNADA') ORDER BY fecha DESC");
                                        StringBuilder sb = new StringBuilder();
                                        while (rs.next())
                                        {
                                            sb.append(rs.getString("idReaccion")).append("::").
                                                    append(rs.getString("nombreEscrito")).append("::").
                                                    append(rs.getString("fecha")).append("::").
                                                    append(rs.getString("direccion")).append(" B/ ").
                                                    append(rs.getString("barrio")).append("::").
                                                    append(rs.getString("estado")).append("--");
                                        }

                                        Message msg = new Message(message.getFrom(), Message.Type.chat);
                                        if (sb.length() > 0)
                                            msg.setBody(Notificacion.APP_DATA + sb.toString().substring(0, sb.length()-2));
                                        else
                                            msg.setBody(Notificacion.APP_DATA + "0");
                                        xmppNotificaciones.sendPacket(msg);
                                    }
                                    //</editor-fold>
                                    catch (SQLException ex) {
                                        Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                                else if (message.getBody() != null && message.getBody().startsWith("L500"))                                        
                                {
                                    String reaccion[] = message.getBody().split("::");
                                    // Indetificar de quien proviene el mensaje.
                                    for (Supervisor sp : supervisor)
                                    {
                                        if (message.getFrom().contains(sp.idGPS))
                                        {
                                            sp.l500(reaccion[1]);
                                            break;
                                        }
                                    }
                                }
                            }
                            //</editor-fold>
                        }, filter);

                        final FileTransferManager fileManager = new FileTransferManager(xmppNotificaciones);
                        FileTransferNegotiator.setServiceEnabled(xmppNotificaciones, true);

                        fileManager.addFileTransferListener(new FileTransferListener() {
                            @Override
                            //<editor-fold defaultstate="collapsed" desc="Recepción de archivos de audio">
                            public void fileTransferRequest(FileTransferRequest ftr) {
                                final IncomingFileTransfer tranfer = ftr.accept();
                                final String[] descripcion = ftr.getDescription().split("::");
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            final File dir = new File("/var/spool/axar/audioReportes/");
                                            if (!dir.exists()) dir.mkdir();
                                            File destino = new File(dir, tranfer.getFileName());
                                            tranfer.recieveFile(destino);
                                            while (!tranfer.isDone())
                                            {   Thread.sleep(1000); }
                                            System.out.println(tranfer.getPeer() + " " + tranfer.getFileName() + " " + tranfer.getStatus());
                                            String nArchivo = tranfer.getFileName().replace(".3gp", "");
                                            if (tranfer.getStatus() == IncomingFileTransfer.Status.complete)
                                            {
                                                Process p = Runtime.getRuntime().exec(
                                                    "/root/ffmpeg/ffmpeg -i /var/spool/axar/audioReportes/"+ nArchivo +".3gp -c:v null /var/spool/axar/audioReportes/"+ nArchivo+".wav"
                                                );
                                                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                                String line;
                                                while ((line = in.readLine()) != null) { System.out.println(line); }
                                                in.close();
                                                p.destroy();
                                                
                                                // Traer la información del cliente.
                                                Statement st = (Statement) conn.createStatement();
                                                ResultSet rs = st.executeQuery("SELECT usuario.idAlarma AS idAlarma, usuario.nombre AS nombre, usuario.email AS email, reaccion.idOperacion AS idOperacion "
                                                        + "FROM reaccion, usuario "
                                                        + "WHERE idReaccion = '"+ descripcion[1].replace("R", "") +"' AND reaccion.idAlarma = usuario.idAlarma");                                                
                                                // Insersión en vitacora, envío de audio al cliente-
                                                switch (descripcion[0])
                                                {
                                                    case "AUDIO":
                                                        StringBuilder sb = new StringBuilder();
                                                        sb.append("El supervisor ha generado un reporte de audio con respecto a la reacción de su alarma. Ajunto encontrará el audio.");
                                                        while (rs.next())
                                                        {
                                                            if (rs.getString("email").contains("@") && rs.getString("email").contains("."))
                                                            {
                                                                idOperacion = rs.getString("idOperacion");
                                                                enviarEmail(rs.getString("idAlarma"), sb.toString(), "REPORTE DE AUDIO POR REACCIÓN", rs.getString("nombre"), rs.getString("email"), 
                                                                        "/var/spool/axar/audioReportes/"+ nArchivo+".wav");
                                                            }
                                                        }
                                                        break;
                                                    case "FOTO":
                                                        break;
                                                }
                                                
                                            }
                                            destino.delete();
                                                                                        
                                        } catch (XMPPException ex) {
                                            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                                        } catch (InterruptedException ex) {
                                            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                                        } catch (IOException ex) {
                                            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                                        } catch (SQLException ex) {
                                            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }
                                }).start();                                    
                            }
                            //</editor-fold>
                        });

                        if (xmppNotificaciones.isConnected())
                            System.out.println("\n\rSistema conectado... " + xmppNotificaciones.getUser());
                        
                    }
                    else if (xmppNotificaciones != null && xmppNotificaciones.isConnected())
                    {
                        Presence presence = new Presence(Presence.Type.available);
                        xmppNotificaciones.sendPacket(presence);
                    }
                    Thread.sleep(15000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                } catch (XMPPException ex) {
                    System.out.println("No es posible realizar la conexióon " + ex.getMessage());
                    Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }//</editor-fold>
        
        while (notificaciones == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo para verificar la conexión con gTalk | actualiza a los supervisores">
        {
            try {
                if (connection == null)
                {
                    ConnectionConfiguration connConfig = new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
                    connection = new XMPPConnection(connConfig);
                } 
                else if (connection != null && connection.isConnected() && connection.getUser().contains(GTALKBACKUP))
                {
                    connection.disconnect();
                    gTalkCuenta = GTALKOFICIAL;
                }
                
                if(!connection.isConnected())
                {
                    connection.connect();
                    System.out.println("Connected to " + connection.getHost());
                    connection.login(gTalkCuenta, "online__2013");
                    
                    if (!connection.isConnected())
                        throw new XMPPException();

                    else if (connection.isConnected() && gTalkCuenta.equals(GTALKOFICIAL))    // SI TIENE EXITO CONECTADOSE
                    {
                        // Arranca Cuenta BackUP
                        ConnectionConfiguration c = new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
                        xmpp = new XMPPConnection(c);
                        xmpp.connect();
                        xmpp.login(GTALKBACKUP, "online__2013");
                        Presence presence = new Presence(Presence.Type.available);
                        xmpp.sendPacket(presence);
                        
                        // Tiempo oficial
                        timeoutNotificacion = 30000;
                        /*Roster roster = xmpp.getRoster();
                        roster.addRosterListener(new RosterListener() {
                            public void entriesAdded(Collection<String> addresses) {}
                            public void entriesDeleted(Collection<String> addresses) {}
                            public void entriesUpdated(Collection<String> addresses) {}
                            public void presenceChanged(Presence presence) {
                                System.out.println("Presence changed: " + presence.getFrom() + " " + presence);
                            }
                        });*/
                    }
                    
                    System.out.println("Logged in as " + gTalkCuenta + " " + connection.getUser() );
                    Presence presence = new Presence(Presence.Type.available);
                    presence.setStatus("24 Horas... 365 Días al cuidado de su seguridad.");
                    connection.sendPacket(presence);
                    
                    PacketFilter filter = new MessageTypeFilter(Message.Type.chat);
                    connection.addPacketListener(new MessageParrot(connection), filter);
                } 
                else if (connection.isConnected())
                {
                    // Re actualizar la presencia de la gTalkCuenta
                    Presence presence = connection.getRoster().getPresence(gTalkCuenta);
                    presence.setStatus("24 Horas... 365 Días al cuidado de su seguridad.");
                    presence.setType(Presence.Type.available);
                    connection.sendPacket(presence);
                    
                    if (xmpp != null && xmpp.isConnected()) // Verificar que este en línea
                    {
                        presence = xmpp.getRoster().getPresence(gTalkCuenta);
                        if (presence.getType() != Presence.Type.available) // NO ESTA EN LÍNEA
                        {
                            // Iniciar desconexión para que se vuelva a conectar
                            System.out.println("No esta en línea ...." + gTalkCuenta + ": " + presence.getType().toString().toUpperCase());
                            Message msj = new Message("mario.lenis.libreros@gmail.com");
                            msj.setBody("Cuenta " + gTalkCuenta + " Por fuera... Iniciando backup");
                            xmpp.sendPacket(msj);                            
                            xmpp.disconnect();
                            
                            // Lanza la excepción
                            throw new XMPPException();
                        }
                    }
                }
                
                Thread.sleep(timeoutNotificacion); // Cada 30 segundos o 60 si esta con Backup
            }
            catch ( InterruptedException ex) {
                System.out.println("M " + ex.getMessage());
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } 
            catch (XMPPException | IllegalStateException ex) {
                gTalkCuenta = GTALKBACKUP;
                timeoutNotificacion = 60000;
                if (connection != null && connection.isConnected())
                    connection.disconnect();
                System.out.println("" + ex.getMessage());
            }
        }
        //</editor-fold>
        
        while (alarmaIp == hilo)
        //<editor-fold defaultstate="collapsed" desc="Verificación de Alarmas IP">
        {
            try {
                
                String line;
                ResultSet rs;
                Calendar c = Calendar.getInstance();
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
                Statement sts = (Statement) conn.createStatement();
                Process p = Runtime.getRuntime().exec(new String[]{"asterisk", "-rx", "sip show peers"});
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));               
                
                while ((line = in.readLine()) != null) 
                {                    
                    if ( line.contains("UNREACHABLE") || line.contains("UNKNOWN") )
                    {
                        // Esta línea esta caída
                        String extension = "", estado = "";
                        if (line.indexOf("/") > 0)
                        {
                            extension = line.substring(0, line.indexOf("/")).replaceAll(" ", "");
                            estado = " AND estadoActual = 'A'";
                        }
                        else if (line.contains("Emcali"))
                        {
                            extension = "Emcali";
                            estado = "";
                        }
                        
                        // Verificar que el ultimo evento sea un resataure; si es restaure, se interta
                        String sSQL = "SELECT max(idEvento) as idEvento, tipoEvento, evento.idAlarma FROM evento, alarma WHERE "
                                + "lineaTransmision = 'IP" + extension + "' AND evento.idAlarma = alarma.idAlarma AND evento = '700' "
                                + "GROUP BY tipoEvento ORDER BY idEvento DESC";
                        
                        rs = sts.executeQuery(sSQL);
                        sSQL = "";
                        if (rs.first() && rs.getString("tipoEvento").equals("RESTAURE"))
                        {
                            idOperacion = UUID.randomUUID().toString();                            
                            String idAlarma = rs.getString("idAlarma");
                            sSQL = "INSERT INTO evento (trama, protocolo, idAlarma, particion, evento, tipoEvento, zona) VALUES "
                                    + "('"+rs.getString("idAlarma")+"18170101000', 'CID', '"+ idAlarma +"', '01', '700', 'EVENTO', '000')";
                            if (rs.getString("idAlarma").equals("0001"))
                                informarCierre(rs.getString("idAlarma"), "Troncal " + extension.toUpperCase() + " Fuera de servicio", "REPORTE DE MONITOREO");
                            else
                                informarCierre(rs.getString("idAlarma"), "Nuestro sistema de monitoreo nos muestra que su sistema de alarma se ha desconectado de la comunicación IP.", "REPORTE DE MONITOREO");
                        }
                        
                        if (!sSQL.equals("")) sts.executeUpdate(sSQL);
                        sSQL = "SELECT idAlarma FROM alarma WHERE lineaTransmision = 'IP" + extension + "'";
                        rs = sts.executeQuery(sSQL);
                        if (rs.first()) sts.executeUpdate("UPDATE alarma SET modo = 'OFF' WHERE lineaTransmision = 'IP"+ extension +"'");
                        rs.close();
                    }
                    else if (line.contains("OK"))
                    {
                        String extension = "";
                        if (line.indexOf("/") > 0)
                            extension = line.substring(0, line.indexOf("/")).replaceAll(" ", "");
                        else if (line.contains("Emcali"))
                            extension = "Emcali";
                        
                        String sSQL = "SELECT max(idEvento) as idEvento, tipoEvento, evento.idAlarma FROM evento, alarma WHERE "
                                + "lineaTransmision = 'IP" + extension + "' AND evento.idAlarma = alarma.idAlarma AND evento = '700' "
                                + "GROUP BY tipoEvento ORDER BY idEvento DESC";
                        
                        rs = sts.executeQuery(sSQL);
                        sSQL = "";
                        if (rs.first() && rs.getString("tipoEvento").equals("EVENTO"))
                        {
                            idOperacion = UUID.randomUUID().toString();
                            String idAlarma = rs.getString("idAlarma");
                            sSQL = "INSERT INTO evento (trama, protocolo, idAlarma, particion, evento, tipoEvento, zona) VALUES "
                                    + "('"+rs.getString("idAlarma")+"18170101000', 'CID', '"+ idAlarma +"', '01', '700', 'RESTAURE', '000')";
                            if (rs.getString("idAlarma").equals("0001"))
                                informarCierre(rs.getString("idAlarma"), "Troncal " + extension.toUpperCase() + " ha restaurado!", "REPORTE DE MONITOREO");
                            else
                                informarCierre(rs.getString("idAlarma"), "Nuestro sistema de monitoreo nos muestra que la comunicación IP ha restaurado correctamente.", "REPORTE DE MONITOREO");
                        }
                                                
                        if (!sSQL.equals("")) sts.executeUpdate(sSQL);
                        sSQL = "SELECT idAlarma FROM alarma WHERE lineaTransmision = 'IP" + extension + "'";
                        rs = sts.executeQuery(sSQL);
                        if (rs.first()) sts.executeUpdate("UPDATE alarma SET modo = 'ON' WHERE lineaTransmision = 'IP"+ extension +"'");
                        rs.close();
                    }
                }
                sts.close();
                in.close();
                p.destroy();
                
                Thread.sleep(2000);
                
            } catch ( IOException | InterruptedException | SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>
        
        while (tPeriodica == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo de verificacion de cierres o Pruebas // y cierre de puertos cada hora">
        {
            try 
            {
                Calendar hActual = Calendar.getInstance();
                Calendar hTemp = (Calendar) ultimaHora.clone(); 
                hTemp.add(Calendar.HOUR, 1);
                
                // Si hay cambio de día
                if ( hActual.getTimeInMillis() > hTemp.getTimeInMillis() && hActual.get(Calendar.HOUR_OF_DAY) < ultimaHora.get(Calendar.HOUR_OF_DAY) )
                //<editor-fold defaultstate="collapsed" desc="Supervision de cierres y señales">
                {
                    // Buscar los eventos de todos los clientes que esten activos o en revisión de: ultimaHora
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
                    String sSQL = "SELECT * FROM " +
                            "(SELECT idAlarma, estado FROM alarma WHERE (estadoActual = 'A' OR estadoActual = 'R') AND tipoAlarma = 'ANTIROBO' AND idAlarma > '0099' ORDER BY idAlarma) AS t1 LEFT JOIN " +
                            "(SELECT idAlarma AS reportec FROM evento WHERE tipoEvento LIKE 'CIERRE' AND fecha BETWEEN '" +
                            f.format(ultimaHora.getTime()) + " 00:00:00' AND '" +
                            f.format(ultimaHora.getTime()) + " 23:59:59' GROUP BY idAlarma ) AS t2 " +
                            "ON t1.idAlarma = t2.reportec LEFT JOIN " +
                            "(SELECT idAlarma AS reportep FROM evento WHERE categoriaEvento LIKE 'PRUEBAS' AND fecha BETWEEN '" +
                            f.format(ultimaHora.getTime()) + " 00:00' AND '" +
                            f.format(ultimaHora.getTime()) + " 23:59:59' GROUP BY idAlarma ) AS t3 " +
                            "ON t1.idAlarma = t3.reportep ORDER BY reportec, idAlarma";
                    
                    Statement st = (Statement) conn.createStatement();
                    Statement ns = (Statement) conn.createStatement();
                    ResultSet rs = st.executeQuery(sSQL);
                    StringBuilder sb = new StringBuilder();
                    StringBuilder sqlBuilder = new StringBuilder();
                    StringBuilder inBuilder = new StringBuilder();
                    
                    SimpleDateFormat nf = new SimpleDateFormat("EEE, dd 'de' MMM yyyy", new Locale("ES"));
                    String fechaEvento = nf.format(ultimaHora.getTime());
                    
                    while (rs.next())
                    {
                        // Si esta abierto y no ha llegado el cierre
                        if (rs.getString("estado").equals("APERTURA") && (rs.getString("reportec") == null || rs.getString("reportec").equals("")))
                        {
                            // Verificar si el cliente ya tiene orden de servicio en trámite o pendiente.
                            ResultSet nrs = ns.executeQuery("SELECT idservicio FROM servicio WHERE idAlarma = '"+ rs.getString("idAlarma") +"' AND (estado = 'ENTRAMITE' OR estado = 'PENDIENTE') ORDER BY fecha DESC LIMIT 1");
                            if (nrs.first())
                                inBuilder.append("UPDATE servicio SET motivo = CONCAT('No se registró cierre el día ").append(fechaEvento).append("<br>', motivo) WHERE idservicio = '").append(nrs.getString("idservicio")).append("';");
                            else
                                sqlBuilder.append("('").append(rs.getString("idAlarma")).append("', 'No se registró cierre el día ").append(fechaEvento).append("<br>', 'AREA DE SOPORTE'), ");
                            
                            sb.append(rs.getString("idAlarma")).append("<br>");
                        }
                        // Si esta cerrado pero no llegó test
                        else if (rs.getString("estado").equals("CIERRE") && (rs.getString("reportep") == null || rs.getString("reportep").equals("")))
                        {
                            ResultSet nrs = ns.executeQuery("SELECT idservicio FROM servicio WHERE idAlarma = '"+ rs.getString("idAlarma") +"' AND (estado = 'ENTRAMITE' OR estado = 'PENDIENTE') ORDER BY fecha DESC LIMIT 1");
                            if (nrs.first())
                                inBuilder.append("UPDATE servicio SET motivo = CONCAT('Alarma en CIERRE sin TEST el día ").append(fechaEvento).append("<br>', motivo) WHERE idservicio = '").append(nrs.getString("idservicio")).append("';");
                            else
                                sqlBuilder.append("('").append(rs.getString("idAlarma")).append("', 'Alarma en CIERRE sin TEST el día ").append(fechaEvento).append("<br>', 'AREA DE SOPORTE'), ");
                        }
                        
                        if (rs.getString("reportec") != null && !rs.getString("reportec").equals(""))
                            break;
                    }
                    
                    //
                    if (!sb.toString().equals(""))
                    {
                        if (sqlBuilder.length() > 0)
                        {
                            int t = sqlBuilder.length()-2;
                            st.executeUpdate("INSERT INTO servicio (idAlarma, motivo, solicitante) VALUES " + sqlBuilder.toString().substring(0, t));
                        }
                        if (inBuilder.length() > 0)
                        {
                            int t = inBuilder.length()-1;
                            String[] multipleIn = inBuilder.toString().substring(0, t).split(";");
                            for (String query: multipleIn)
                            {
                                st.executeUpdate(query);
                            }
                        }
                        String msj = "Lista de CASID sin cierre para el día " + fechaEvento + "<br><br>" + sb.toString() + "<br>";
                        enviarEmail("0000", msj, "CASIDs sin cierre", "SOPORTE TÉCNICO", "hector@montgomery.com.co", null);
                        enviarEmail("0000", msj, "CASIDs sin cierre", "SOPORTE TÉCNICO", "mario.lenis@kerberus.com.co", null);
                    }
                    rs.close();
                    st.close();
                    ns.close();
                }
//</editor-fold>
                
                // Si la hora Actual es mayor a la última hora, ha pasado una hora.
                if ( hActual.getTimeInMillis() > hTemp.getTimeInMillis() )
                {
                    //gpsSocket.close();
                    //supervisorSocket.close();
                    solutekSocket.close();
                    //paradoxSocket.close();
                    
                    //Actualizar la ultima hora a la actual;
                    ultimaHora = Calendar.getInstance();
                }
                
                Thread.sleep(60000*2);
            } catch (InterruptedException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>
        
        while (turnos == hilo)
        //<editor-fold defaultstate="collapsed" desc="Hilo de Turnos">
        {
            try { 
                // AGREGAR SUPERVISORES QUE ESTEN DISPONIBLES O LOS ELIMINA
                verificarSupervisor();
                Thread.sleep(50000);
                
            } catch ( InterruptedException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>
         
        //<editor-fold defaultstate="collapsed" desc="Actualización de posiciones SUPERVISORES">
        while (posicionSupervisor == hilo)
        {
            try {
                StringBuilder eventos = new StringBuilder();
                Statement st = (Statement) conn.createStatement();  
                Statement st1 = (Statement) conn.createStatement();  
                ResultSet res = st.executeQuery("SELECT * FROM eventoGPS WHERE estado = 'PENDIENTE' ORDER BY fecha");
                String dirActual = "", barrio = "";
                String url = "http://maps.googleapis.com/maps/api/geocode/xml?latlng=";
                HttpClient cliente = new HttpClient();
                HttpMethod metodo;
                
                while (res.next())
                {
                    eventos.append("idEvento='").append(res.getString("idEvento")).append("' OR ");
                    String latlng = res.getString("lat") + "," + res.getString("lon");
                    metodo = new GetMethod(url + latlng + "&sensor=true");
                    cliente.executeMethod(metodo);
                    InputStream resXML = metodo.getResponseBodyAsStream();

                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setValidating(false);
                    dbf.setIgnoringComments(false);
                    dbf.setIgnoringElementContentWhitespace(true);
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();

                    Document doc = (Document) db.parse(resXML);
                    doc.getDocumentElement().normalize();
                  
                    NodeList nList = doc.getElementsByTagName("result");

                    for (int temp = 0; temp < nList.getLength(); temp++) {
                        Node nNode = nList.item(temp);
                        if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                            Element eElement = (Element) nNode;                    
                            String tipo = eElement.getElementsByTagName("type").item(0).getTextContent();
                            if (tipo.equals("street_address"))
                            {
                                dirActual = eElement.getElementsByTagName("formatted_address").item(0).getTextContent();
                                dirActual = dirActual.substring(0, dirActual.indexOf(",")).replaceAll("#", "Número").replaceAll("-", " ");
                                //System.out.println("Dirección : " + dirActual);

                                NodeList subList = doc.getElementsByTagName("address_component");
                                OUTER:
                                for (int i = 0; i < subList.getLength(); i++) {
                                    Node subNodo = subList.item(i);
                                    Element n = (Element) subNodo;
                                    switch (n.getElementsByTagName("type").item(0).getTextContent()) {
                                        case "route":
                                            break;
                                        case "sublocality":
                                            //System.out.println("Barrio : " + n.getElementsByTagName("long_name").item(0).getTextContent());
                                            barrio = n.getElementsByTagName("long_name").item(0).getTextContent();
                                            break OUTER;
                                    }
                                }
                                break;
                            }
                        }
                    }
                    dirActual = dirActual.replace("'", "\\'");
                    System.out.println("Dirección Supervisor: " + dirActual);
                    st1.executeUpdate("UPDATE supervisor SET GPS = '"+ dirActual +", Barrio: "+ barrio +"' WHERE idGPS = '"+ res.getString("idGPS") +"'");
                }
                if (eventos.length() > 0)
                    st.executeUpdate("UPDATE eventoGPS SET estado = 'PROCESADO' WHERE "+ eventos.toString().substring(0, eventos.length()-4));
                st1.close();
                st.close();
                
            } catch (SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ParserConfigurationException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } catch (SAXException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        //</editor-fold>
    }
    
    private String clasificarEvento(String evento, String protocolo)
    //<editor-fold defaultstate="collapsed" desc="Califica evento de acuerdo a la categoría">
    {
        try {
            ResultSet res;
            Statement s = (Statement) conn.createStatement();
            switch (protocolo) {
                case "CID":
                    res = s.executeQuery("SELECT categoria FROM tablaCID WHERE idCID = '"+ evento +"'");
                    break;
                case "GPS":
                    res = s.executeQuery("SELECT categoria FROM tablaGPS WHERE idGPS = '"+ evento +"'");
                    break;
                default:
                    s.close();
                    return "";
            }
            
            String strCat = "";
            while(res.next())
            {
                strCat = res.getString("categoria").toUpperCase();
            }
            res.close();
            s.close();
            return strCat;
            
        } catch (SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }
    //</editor-fold>
        
    private String determinarZona(String idAlarma, String zona)
    //<editor-fold defaultstate="collapsed" desc="Determina la zona del cliente">
    {
        try {
            ResultSet res;
            Statement s = (Statement) conn.createStatement();
            res = s.executeQuery("SELECT tipoZona FROM zona WHERE idAlarma = '"+ idAlarma +"' AND zona = '"+ zona +"'");
            while(res.next())
            {
                return res.getString("tipoZona");
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "INTERNA";
    }
    //</editor-fold>
    
    private double coordenar(double var)
    //<editor-fold defaultstate="collapsed" desc="Pasar coodenadas a sistema google.maps">
    {
        String n = String.valueOf(var);
        String[] dec = new String[] {n.substring(0, n.indexOf('.')), n.substring(n.indexOf('.')+1)};
        double mmm  = (Double.parseDouble("0." + dec[1])*100)/60;
        if (var < 0)
            mmm = mmm * -1;
        return (Double.parseDouble(dec[0]) + mmm);
    }
    //</editor-fold>
    
    private boolean informarCierre(String idAlarma, String mensaje, String asunto)
    //<editor-fold defaultstate="collapsed" desc="GTALK">
    {
        try {
            Statement st = (Statement) conn.createStatement();
            ResultSet res = st.executeQuery("SELECT * FROM usuario WHERE idAlarma = '"+ idAlarma +"' AND nombre NOT LIKE 'COACCION' ORDER BY usuario");
            StringBuilder msjGTalk = new StringBuilder();
            msjGTalk.append(mensaje);
            while (res.next())
            {
                String nombre = res.getString("nombre");
                String gTalk  = res.getString("gtalk");
                String email  = res.getString("email");
                if (!gTalk.equals("") && gTalk.contains("@")){
                        
                    Presence presence = connection.getRoster().getPresence(gTalk);
                    if (presence.getType() == Presence.Type.available)
                    {
                        try {
                            Message msg = new Message(gTalk, Message.Type.chat);                            
                            msg.setBody("Apreciad@ " + nombre + ". " + msjGTalk.toString());
                            connection.sendPacket(msg);
                            st = (Statement) conn.createStatement();
                            st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, idOperacion) Values "
                                    + "('"+ idAlarma +"', 'gTalk', '"+ msjGTalk.toString() +"', '"+ gTalk +"', '"+ idOperacion +"')");
                            System.out.println("Enviado " + gTalk);

                        } catch (SQLException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    else
                    {
                        System.out.println("Usuario desconectado " + nombre);
                    }
                }
                // Notificacion via email
                enviarEmail(idAlarma, mensaje, asunto, nombre, email, null);
            }
            st.close();
            
        } catch (SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println(ex.getMessage());
        }
        return false;
    }//</editor-fold>
    
    public void enviarEmail(String idAlarma, String mensaje, String asunto, String nombre, String destino, String attach)
    //<editor-fold defaultstate="collapsed" desc="Envio de notificación via eMail">
    {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try 
        {
            Statement st = (Statement) conn.createStatement();
            if (destino.contains("@") && destino.indexOf("@") < destino.lastIndexOf(".") )
            {
                Properties props = System.getProperties();
                Authenticator auth = new PopupAuthenticator("seguridad.montgomery@montgomery.com.co", "online__2013");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", 25);
                Session session = Session.getInstance(props, auth);
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress("seguridad.montgomery@montgomery.com.co", "Seguridad Montgomery"));
                message.setSubject(asunto);
                message.setSentDate(Calendar.getInstance().getTime());
                message.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(destino, nombre));

                StringBuilder body = new StringBuilder();

                body.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\" /></head>");
                body.append("<body>");
                body.append("<div style=\"font-size:12px; text-align:left; border:1px solid white; font-family:Verdana, Arial, Helvetica, sans-serif; \">");
                body.append("<strong>Apreciad@ ");
                body.append(nombre).append("</strong><br><br>");
                body.append(mensaje);

                body.append(" Este es un servicio del operador inteligente. <br><br>");
                body.append("<b>&copy;2014 Seguridad Montgomery Ltda.</b><br>Website: <a href=\"http://www.montgomery.com.co\">www.montgomery.com.co</a><br>");
                body.append("PBX: +57(2) 311 0610<br>Santiago de Cali.<br>Colombia.");
                body.append("</div></body></html>");

                if (attach != null && attach.length() > 0)
                {
                    File audio = new File(attach);
                    if (audio.exists())
                    {                        
                        Multipart multiParte = new MimeMultipart();
                        
                        MimeBodyPart mime = new MimeBodyPart();
                        FileDataSource attachment = new FileDataSource(audio);
                        mime.setDataHandler(new DataHandler(attachment));
                        mime.setFileName(audio.getName());                        
                        multiParte.addBodyPart(mime);
                        
                        MimeBodyPart nBody = new MimeBodyPart();
                        nBody.setContent(body.toString(), "text/html");
                        multiParte.addBodyPart(nBody);
                        
                        message.setContent(multiParte);
                        Transport.send(message);
                    }
                } else
                {
                    message.setContent(body.toString(), "text/html");
                    Transport.send(message);
                }
                
                // Actualiar Vitacora SUCESS                
                st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, estado, idOperacion, fechaActualizacion) Values "
                        + "('"+ idAlarma +"', 'Email', '"+ mensaje +"', '"+ destino +"', 'INFORMADO', '"+ idOperacion +"', '"+ f.format(Calendar.getInstance().getTime()) +"')");
                st.close();
            }
            else
            {
                st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, estado, idOperacion, fechaActualizacion) Values "
                        + "('"+ idAlarma +"', 'Email', 'Usuario sin Email', '"+ nombre +"', 'INFORMADO', '"+ idOperacion +"', '"+ f.format(Calendar.getInstance().getTime()) +"')");
                st.close();
            }
            
        } catch (MessagingException ex) {
            try {
                // Actualizar vitacora FALLIDO
                Statement st = (Statement) conn.createStatement();
                st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, estado, idOperacion, fechaActualizacion) Values "
                        + "('"+ idAlarma +"', 'Email', 'Falló la entrega de la notificación via email', '"+ destino +"', 'INFORMADO', '"+ idOperacion +"', '"+ f.format(Calendar.getInstance().getTime()) +"')");
                st.close();
            } catch (SQLException ex1) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex1);
            }
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    private void verificarSupervisor()
    //<editor-fold defaultstate="collapsed" desc="Agrega a los supervisores">
    {
        try {
            String sSQL = "SELECT * FROM supervisor ORDER BY idSupervisor";
            Statement st = (Statement) conn.createStatement();
            ResultSet rs = st.executeQuery(sSQL);
            while(rs.next())
            {
                switch (rs.getString("status")) {
                    case "I":
                        // TODO: Remover al patrullero siempre y cuando no tenga reacciones pendiente
                        for (int i=0; i < supervisor.size(); i++)
                        {
                            Supervisor spr = supervisor.get(i);
                            if (spr.status.equals("I"))
                            {
                                // Verificar que no tenga reacciones pendientes.
                                Statement nst = (Statement) conn.createStatement();
                                ResultSet nrs = nst.executeQuery("SELECT COUNT(idReaccion) AS cant FROM reaccion WHERE idSupervisor = '"+ spr.idSupervisor +"' "
                                        + "AND (estado = 'EN TRAMITE' OR estado = 'PENDIENTE')");
                                if (nrs.first() && nrs.getString("cant").equals("0") && spr.cerrarConexiones())
                                {   // Eliminarlo
                                    spr = null;
                                    supervisor.remove(i);
                                    i--;
                                }
                                nrs.close();
                                nst.close();
                            }
                        }
                        break;
                    case "A":
                        // Verificar que no exista
                        boolean existe = false;
                        for (int i=0; i < supervisor.size(); i++)
                        {
                            Supervisor spr = supervisor.get(i); //.elementAt(i);
                            if (spr.idSupervisor.equals(rs.getString("idSupervisor")))
                            {
                                existe = true;
                                break;
                            }
                        }
                        if (!existe) // No existe
                            supervisor.add(new Supervisor(rs.getString("idSupervisor"), connection, xmppNotificaciones)); // Agregarlo
                        break; // Break del Switch
                }
            }
            
            rs.close();
            st.close();
            
        } catch (SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    //</editor-fold>
    
    private String diaSpanish(Object nFecha)
    //<editor-fold defaultstate="collapsed" desc="Dias es Español">
    {
        String fecha = (String)nFecha;
        fecha = fecha.replaceFirst("Mon", "Lun");
        fecha = fecha.replaceFirst("Tue", "Mar");
        fecha = fecha.replaceFirst("Wed", "Mie");
        fecha = fecha.replaceFirst("Thu", "Jue");
        fecha = fecha.replaceFirst("Fri", "Vie");
        fecha = fecha.replaceFirst("Sat", "Sab");
        fecha = fecha.replaceFirst("Sun", "Dom");
        
        fecha = fecha.replaceFirst("Jan", "Ene");
        fecha = fecha.replaceFirst("Apr", "Abr");
        fecha = fecha.replaceFirst("Aug", "Ago");
        fecha = fecha.replaceFirst("Dec", "Dic");
        
        return fecha;
    }
    //</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="/ROOT/AXAR/AXAR_LOG.DAT">
    private void generarLog(Exception logger, String linea)
    {
        try
        {
            String filename= "/root/Axar/axar_log.dat";
            FileWriter fw = new FileWriter(filename,true); //the true will append the new data
            fw.write(linea + "\n" + Calendar.getInstance().getTime().toString() + "\n");//appends the string to the file
            Writer s = new StringWriter();
            PrintWriter pw = new PrintWriter(s);
            logger.printStackTrace(pw);
            fw.write(s.toString() + "\n");
            fw.close();
        }
        catch(IOException ioe)
        {
            System.out.println("IOException: " + ioe.getMessage());
        }
    }
    //</editor-fold>
    
    public static void main(String[] args) throws Exception
    {
        Centinela centinela;
        centinela = new Centinela();           
    }
    
    public class MessageParrot implements PacketListener {
        private XMPPConnection xmppConnection;
        
        public MessageParrot(XMPPConnection conn) {
            xmppConnection = conn;
        }
        
        public boolean esNumero(String strNum)
        //<editor-fold defaultstate="collapsed" desc="comment">
        {
            boolean ret = true;
            try {
                Double.parseDouble(strNum);
            }catch (NumberFormatException e) {
                ret = false;
            }
            return ret;
        }
//</editor-fold>
        
        @Override
        public void processPacket(Packet packet) {
            Message message = (Message)packet;
            if(message.getBody() != null) {
                try {
                    Statement st = (Statement) conn.createStatement();
                    String fromName = StringUtils.parseBareAddress(message.getFrom());
                    String mensaje  = message.getBody().trim().toUpperCase();
                    
                    System.out.println("Message from " + fromName + "\n" + mensaje + "\n");
                    
                    // Buscar en supervisores si hay alguno con el email
                    ResultSet rs = st.executeQuery("SELECT * FROM supervisor WHERE gTalk = '"+ fromName +"' AND status = 'A'");
                    if (rs.first()){
                        Message reply = new Message(fromName, Message.Type.chat);
                        //reply.setTo(fromName);
                        String idSupervisor = rs.getString("idSupervisor");
                        
                        if (mensaje.equals("R"))
                        //<editor-fold defaultstate="collapsed" desc="Consulta de reacciones PENDIENTES">
                        {
                            String sSQL = "SELECT nAlarma.nombreEscrito, nAlarma.idAlarma, nAlarma.direccion, nAlarma.barrio, reaccion.* FROM reaccion, "
                                + "(SELECT alarma.*, cliente.nombre, cliente.nombreEscrito FROM alarma, cliente WHERE alarma.idCliente = cliente.idCliente) as nAlarma "
                                + "WHERE reaccion.idAlarma = nAlarma.idAlarma AND (reaccion.estado = 'EN TRAMITE' OR reaccion.estado = 'PENDIENTE' OR reaccion.estado = 'NO ASIGNADA') "
                                + "AND idSupervisor = '"+ idSupervisor +"'";
                                
                            rs = st.executeQuery(sSQL);
                            if (rs.next())
                            {
                                StringBuilder stR = new StringBuilder();
                                do
                                {
                                    String dir = rs.getString("direccion").replaceAll("Número", "#").replaceAll("Carrera", "Cra.").replaceAll("Diagonal", "Dig.");
                                    stR.append("[ R").append(rs.getString("idReaccion")).append(" ]\n"); 
                                    stR.append(rs.getString("nombreEscrito")).append("\n");
                                    stR.append(dir).append("\nB/ ");
                                    stR.append(rs.getString("barrio"));
                                    stR.append("\nFECHA: ").append(rs.getString("fecha")).append("\nUA: ").append(rs.getString("ultimaActualizacion"));
                                    stR.append("\n\n");
                                }while(rs.next());
                                reply.setBody(stR.toString());
                            }
                            else
                                reply.setBody("NO HAY REACCIONES PENDIENTES PARA USTED.");
                        }//</editor-fold>
                        
                        else if (mensaje.equals("E"))
                        //<editor-fold defaultstate="collapsed" desc="Consulta de reacciones ERROR">
                        {
                            String sSQL = "SELECT nAlarma.nombreEscrito, nAlarma.idAlarma, nAlarma.direccion, nAlarma.barrio, reaccion.* FROM reaccion, "
                                + "(SELECT alarma.*, cliente.nombre, cliente.nombreEscrito FROM alarma, cliente WHERE alarma.idCliente = cliente.idCliente) as nAlarma "
                                + "WHERE reaccion.idAlarma = nAlarma.idAlarma AND (reaccion.estado = 'ERROR')";
                                
                            rs = st.executeQuery(sSQL);
                            if (rs.next())
                            {
                                StringBuilder stR = new StringBuilder();
                                do
                                {
                                    String dir = rs.getString("direccion").replaceAll("Número", "#").replaceAll("Carrera", "Cra.").replaceAll("Diagonal", "Dig.");
                                    stR.append("[ R").append(rs.getString("idReaccion")).append(" ]\n"); 
                                    stR.append(rs.getString("nombreEscrito")).append("\n");
                                    stR.append(dir).append("\nB/ ");
                                    stR.append(rs.getString("barrio"));
                                    stR.append("\nFECHA: ").append(rs.getString("fecha")).append("\nUA: ").append(rs.getString("ultimaActualizacion"));
                                    stR.append("\n\n");
                                }while(rs.next());
                                reply.setBody(stR.toString());
                            }
                            else
                                reply.setBody("NO HAY REACCIONES PENDIENTES MARCADAS COMO ERROR.");
                        }//</editor-fold>

                        else if (mensaje.length() == 2 && mensaje.substring(0, 1).equals("C"))
                        //<editor-fold defaultstate="collapsed" desc="CONFIRMACIÓN POR GTALK DE REACCION">
                        {
                            String idReaccion = mensaje.replaceAll("C", "");
                            rs = st.executeQuery("SELECT * FROM reaccion WHERE idReaccion = '"+ idReaccion +"'");
                            
                            if (rs.first() && !rs.getString("estado").equals("ATENDIDA") && !rs.getString("estado").contains("NUEVO EVENTO") )
                            {
                                st.executeUpdate("UPDATE reaccion SET estado = 'EN TRAMITE', idSupervisor = '"+ idSupervisor +"' "
                                        + "WHERE idReaccion= '"+ rs.getString("idReaccion") +"'");
                                reply.setBody("La reacción [ R" + idReaccion + " ] Ha sido asignada a usted existosamente.");
                            }
                            else if (rs.first() && (rs.getString("estado").equals("ATENDIDA") || rs.getString("estado").equals("EN TRAMITE") ))
                                reply.setBody("Esta reacción ya se encuentra atendida.");
                            
                            else
                                reply.setBody("No existe reaccion con ese ID");
                        }//</editor-fold>
                        
                        else if (mensaje.contains(":") && mensaje.substring(0, 1).equals("R") && rs.getString("tipo").equals("1")) // Solo el MASTER
                        //<editor-fold defaultstate="collapsed" desc="REASIGNAR REACCIÓN > R:#:zona">
                        {
                            String[] datos = mensaje.split(":");
                            if (datos.length == 3){
                                String idReaccion = datos[1];
                                rs = st.executeQuery("SELECT * FROM reaccion WHERE idReaccion = '"+ idReaccion +"'");

                                if (rs.first() && (rs.getString("estado").equals("PENDIENTE") || rs.getString("estado").equals("EN TRAMITE") || rs.getString("estado").equals("ERROR")) )
                                {
                                    // Ubicar al supervisor de zona datos[2]
                                    Statement st1 = (Statement) conn.createStatement();
                                    ResultSet nRes = st1.executeQuery("SELECT * FROM supervisor WHERE zona LIKE '"+ datos[2].toUpperCase() +"' AND status = 'A'");
                                    if (nRes.first())
                                    {
                                        // Si existe, asignarle la reacción a éste y estado EN TRAMITE
                                        String nombreSupervisor = nRes.getString("nombre");
                                        st1.executeUpdate("UPDATE reaccion SET idSupervisor='"+ nRes.getString("idSupervisor") +"', estado = 'EN TRAMITE' "
                                                + "WHERE idReaccion = '"+ idReaccion +"'");
                                        reply.setBody("La reacción [ R"+ idReaccion +"] fue asignada Exitosamente a " + nombreSupervisor);
                                    }                                    
                                    else
                                        reply.setBody("No existe supervisor de la zona " + datos[2].toUpperCase() +" disponible");
                                    st1.close();
                                }
                                else if (rs.first() && rs.getString("estado").equals("ATENDIDA"))
                                    reply.setBody("Esta reacción ya se encuentra atendida, no es posible reasignarla.");

                                else
                                    reply.setBody("No existe reaccion con ese ID");
                            }
                        }
                        //</editor-fold>
                        
                        else if (mensaje.toUpperCase().replaceAll(" ", "").equals("L500"))                        
                        //<editor-fold defaultstate="collapsed" desc="Tramite para generar una llamada tipo L500">
                        {
                            reply.setBody("En progreso..\n");
                            for (int i=0; i < supervisor.size(); i++)
                            {
                                Supervisor patrullero = supervisor.get(i);
                                if (patrullero.gTalk.equals(fromName))
                                {
                                    if (!patrullero.l500(null))
                                        reply.setBody("En este momento no es posible procesar L500\n");
                                    break;
                                }
                            }
                        }
                        //</editor-fold>
                        
                        else if (mensaje.toUpperCase().contains("/MAPS"))
                        //<editor-fold defaultstate="collapsed" desc="Reporte de GPS de supervisor">
                        {
                            if (mensaje.contains(" "))
                            {
                                String[] contenido = mensaje.split(" ");
                                // Ubicar la reacción si existe
                                rs = st.executeQuery("SELECT * FROM reaccion WHERE idReaccion = '"+ contenido[0] +"'");
                                if (rs.first())
                                {
                                    switch (rs.getString("estado")) {
                                        case "PENDIENTE":
                                        case "EN TRAMITE":
                                            // Agregar posición al reporte
                                            SimpleDateFormat f = new SimpleDateFormat("EEE, dd 'de' MMMMM yyyy 'a la(s)' HH:mm", new Locale("ES"));
                                            idOperacion = rs.getString("idOperacion");
                                            String latLon = contenido[1].substring(contenido[1].indexOf("=") + 1);
                                            // Actualización de reporte
                                            informarCierre(rs.getString("idAlarma"), "El supervisor ya se encuentra en el domicilio de su sistema de alarma, puede ver su posición ingresando al siguiente link\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=" + latLon, "REPORTE DE REACCIÓN " + f.format(Calendar.getInstance().getTime()));
                                            reply.setBody("Reporte recibido.");
                                            break;
                                        case "ATENDIDA":
                                            reply.setBody("Esta reacción ya se encuentra atendida.");
                                            break;
                                    }
                                } else reply.setBody("El código de reacción no existe, intente nuevamente.");
                            } else reply.setBody("El formato para reportar en sitio es COD.Reacción [espacio] ubicación; primero inserte la ubicación y en el mismo mensaje envie el cod. de la rección.");
                        }
                        //</editor-fold>
                        
                        else
                            reply.setBody("Ingrese una opción válida..\n");
                        
                        if (!reply.getBody().equals(""))
                            xmppConnection.sendPacket(reply);
                    }
                    else
                    {
                        ResultSet rss = st.executeQuery("SELECT * FROM empleado WHERE gTalk = '"+ fromName +"'");
                        Message reply = new Message(fromName, Message.Type.chat);
                        if (rss.first()) // Para Empleados.
                        {
                            //<editor-fold defaultstate="collapsed" desc="Eventos de alarma">
                            if (mensaje.toUpperCase().replaceAll(" ", "").contains("AHA"))
                            {
                                String[] cod = mensaje.split(":");
                                String cad = "";
                                rss = st.executeQuery("SELECT alarma.estado, alarma.direccion, alarma.ciudad, evento.fecha, evento.protocolo, evento.categoriaEvento, evento.evento, evento.tipoEvento, "
                                        + "evento.particion, evento.zona, evento.clid FROM evento, alarma WHERE evento.idAlarma ='"+ cod[1] +"' AND evento.idAlarma = alarma.idAlarma ORDER BY fecha DESC LIMIT 16");
                                rss.last();
                                
                                cad = "";
                                while (rss.previous())
                                {
                                    if (rss.getString("protocolo").equals("GPS"))
                                    {
                                        rss.first();
                                        String[] pos = rss.getString("clid").split("/");
                                        cad = "ESTADO: " + rss.getString("estado") + "\n";
                                        cad += "UBICACIÓN: " + rss.getString("direccion") + "\nCIUDAD: " + rss.getString("ciudad");
                                        cad += "\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q="+ pos[0] +"," + pos[1];
                                        break;
                                    }
                                    else
                                    {
                                        cad += rss.getString("fecha").replace("-", ".") + "\n" + rss.getString("tipoEvento") + ": "+ rss.getString("categoriaEvento")+ "\nE:"
                                                + rss.getString("evento")
                                                + " P:" + rss.getString("particion") + " Z:" + rss.getString("zona") + "\n\n";
                                    }
                                }
                                rss.close();
                                reply.setTo(fromName);
                                reply.setBody(cad);

                            }
                            //</editor-fold>

                            //<editor-fold defaultstate="collapsed" desc="DEMO">
                            else if (mensaje.toUpperCase().replaceAll(" ", "").contains("DEMO"))
                            {
                                String[] cod = mensaje.split(":");
                                String evt = "";
                                switch(cod[1].toUpperCase())
                                {
                                    case "APERTURA":
                                        evt = "14581";
                                        break;
                                    case "CIERRE":
                                        evt = "15263";
                                        break;
                                    case "TARDE":
                                        evt = "29353";
                                        break;
                                    case "ALARMA":
                                        evt = "15264";
                                }
                                if (!evt.equals(""))
                                {
                                    st.executeUpdate("UPDATE evento SET estado = 'PENDIENTE' WHERE idEvento = '"+ evt +"'");
                                    reply.setTo(fromName);
                                    reply.setBody("Enviando");
                                } else
                                {
                                    reply.setTo(fromName);
                                    reply.setBody("El evento no fue comprendido.");
                                }
                            }
                             //</editor-fold>

                            //<editor-fold defaultstate="collapsed" desc="Listado de Clientes">
                            else if (mensaje.toUpperCase().replaceAll(" ", "").contains("CASID"))
                            {
                                String cad = "";
                                rss = st.executeQuery("SELECT alarma.direccion AS d, alarma.idAlarma, alarma.telefonoContacto AS t, cliente.* FROM alarma, cliente WHERE alarma.idCliente = cliente.idCliente ORDER BY nombreEscrito");
                                while (rss.next())
                                {
                                    cad += rss.getString("idAlarma") + " " + rss.getString("nombreEscrito") + "\n" + rss.getString("d") + "\n" + rss.getString("t") + "\n";
                                }
                                rss.close();
                                reply.setTo(fromName);
                                reply.setBody(cad);
                            }
                            //</editor-fold>
                            
                            //<editor-fold defaultstate="collapsed" desc="Revisión de Clientes">
                            else if (mensaje.toUpperCase().replaceAll(" ", "").contains(":R") || mensaje.toUpperCase().replaceAll(" ", "").contains(":A"))
                            {
                                String[] dat = mensaje.split(":");
                                String sSQL = "UPDATE alarma SET estadoActual = '"+ dat[1].toUpperCase() +"' WHERE idAlarma = '"+ dat[0] +"'";                                 
                                st.executeUpdate(sSQL);
                                st.close();
                                reply.setTo(fromName);
                                switch (dat[1].toUpperCase()) {
                                    case "R":
                                        reply.setBody(dat[0] + " EN REVISIÓN, Recuerde una vez haya concluído el servicio, enviar el CASID:A.");
                                        break;
                                    case "A":
                                        reply.setBody(dat[0] + " ACTIVADO.");
                                        break;
                                }
                            }
                            //</editor-fold>
                        }
                        else if (mensaje.toUpperCase().replaceAll(" ", "").contains("GPS"))
                        //<editor-fold defaultstate="collapsed" desc="Opciones para clientes GPS">
                        {
                            
                            String[] contenido = mensaje.split(" ");
                            if ( contenido.length == 1 && contenido[0].equals("GPS"))
                            //<editor-fold defaultstate="collapsed" desc="Posición de un solo GPS o si hay varios notificar.">
                            {
                                rss = st.executeQuery("SELECT usuario.*, alarma.descripcion FROM usuario, alarma WHERE gtalk = '"+ fromName +"' AND usuario.idAlarma = alarma.idAlarma AND alarma.tipoAlarma = 'GPS' ORDER BY idAlarma");
                                if (rss.last() && rss.getRow() > 1)
                                {
                                    // Hay varios
                                    StringBuilder sb = new StringBuilder();
                                    rss.first();
                                    do
                                    {
                                        sb.append("GPS ").append(rss.getRow()).append(": ").append(rss.getString("descripcion")).append("\n");
                                    } while (rss.next());
                                    reply.setTo(fromName);
                                    reply.setBody("Actualmente tiene varios vehículos. \"envíe GPS 1\" Para el vehículo 1, o 2 para el vehículo 2.\n\n" + sb.toString());
                                    
                                }
                                else if (rss.first())
                                {
                                    String ida = rss.getString("idAlarma");
                                    rss = st.executeQuery("SELECT alarma.estado AS estado, alarma.direccion, alarma.ciudad, clid FROM evento, alarma WHERE evento.idAlarma = '"+ ida +"' AND clid NOT like '0.0/0.0/0.0' AND evento.idAlarma = alarma.idAlarma ORDER BY FECHA desc LIMIT 1");
                                    if (rss.first())
                                    {
                                        String[] pos = rss.getString("clid").split("/");
                                        if (pos.length > 2)
                                        {
                                            String cad = "ESTADO: "+ rss.getString("estado") +"\n";
                                            cad += "UBICACIÓN: " + rss.getString("direccion") + "\nCIUDAD: " + rss.getString("ciudad");
                                            cad += "\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q="+ pos[0] +"," + pos[1];
                                            reply.setTo(fromName);
                                            reply.setBody(cad);
                                        }
                                    }
                                    else
                                    {
                                        reply.setTo(fromName);
                                        reply.setBody("No hay datos de GPS");
                                    }
                                    rss.close();
                                }
                            }
//</editor-fold>
                            
                            else if (contenido.length > 1 && esNumero(contenido[1]))
                            //<editor-fold defaultstate="collapsed" desc="Multiples GPS">
                            {
                                int solicita = Integer.parseInt(contenido[1]);
                                rss = st.executeQuery("SELECT usuario.* FROM usuario, alarma WHERE gtalk = '"+ fromName +"' AND usuario.idAlarma = alarma.idAlarma AND alarma.tipoAlarma = 'GPS' ORDER BY idAlarma");
                                if (rss.last() && rss.getRow() > 1)
                                {
                                    rss.first();
                                    do
                                    {
                                        if (rss.getRow() == solicita)
                                        {
                                            rss = st.executeQuery("SELECT alarma.estado AS estado, alarma.direccion, alarma.ciudad, clid FROM evento, alarma WHERE evento.idAlarma = '"+ rss.getString("idAlarma") +"' AND clid NOT like '0.0/0.0/0.0' AND evento.idAlarma = alarma.idAlarma ORDER BY FECHA desc LIMIT 1");
                                            rss.first();
                                            String[] pos = rss.getString("clid").split("/");
                                            if (pos.length > 2)
                                            {
                                                String cad = "ESTADO: "+ rss.getString("estado") +"\n";
                                                cad += "UBICACIÓN: " + rss.getString("direccion") + "\nCIUDAD: " + rss.getString("ciudad");
                                                cad += "\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q="+ pos[0] +"," + pos[1];
                                                reply.setTo(fromName);
                                                reply.setBody(cad);
                                            }
                                            else
                                            {
                                                reply.setTo(fromName);
                                                reply.setBody("No hay datos de GPS");
                                            }
                                            break;
                                        }
                                    } while(rss.next());
                                }
                            }
                            //</editor-fold>
                            
                            else if (contenido.length > 1 && (contenido[1].toUpperCase().contains("PARQUEO") || contenido[1].toUpperCase().equals("PA") || contenido[1].toUpperCase().equals("PD")) )
                            {
                                rss = st.executeQuery("SELECT usuario.* FROM usuario, alarma WHERE gtalk = '"+ fromName +"' AND usuario.idAlarma = alarma.idAlarma AND alarma.tipoAlarma = 'GPS'");
                                String modo = "";
                                if (rss.first())
                                {
                                    switch (contenido[1].toUpperCase()) {
                                        case "PARQUEO":
                                        case "PA":
                                            modo = "RESTAURE";
                                            break;
                                        case "DESPARQUEO":
                                        case "PD":
                                            modo = "EVENTO";
                                    }
                                    st.executeUpdate("INSERT INTO evento (protocolo, trama, idAlarma, particion, evento, tipoEvento, zona, clid, inbound) VALUES "
                                            + "('GPS', 'GPS60051012', '"+ rss.getString("idAlarma") +"', '0.0', 'D', '"+ modo +"', '000', '0.0/0.0/0.0', 'gTalk')");
                                }
                            }                            
                            else if (contenido.length > 1 && contenido[1].toUpperCase().equals("B"))
                            {
                                 rss = st.executeQuery("SELECT usuario.* FROM usuario, alarma WHERE gtalk = '"+ fromName +"' AND usuario.idAlarma = alarma.idAlarma AND alarma.tipoAlarma = 'GPS'");
                                 if (rss.first())
                                 {
                                     st.executeUpdate("INSERT INTO evento (protocolo, trama, idAlarma, particion, evento, tipoEvento, zona, clid, inbound) VALUES "
                                            + "('GPS', 'GPS60051012', '"+ rss.getString("idAlarma") +"', '0.0', '5', 'EVENTO', '000', '0.0/0.0/0.0', 'gTalk')");
                                     reply.setBody("Desbloqueo remoto en progreso...");
                                 }
                            }
                            else if (contenido.length > 1 && (contenido[1].toUpperCase().equals("PWR") ||contenido[1].toUpperCase().equals("OK") ||contenido[1].toUpperCase().equals("UNLOCK") ))
                            {
                                switch(contenido[1].toUpperCase())
                                {
                                     case "UNLOCK":
                                        st.executeUpdate("UPDATE alarma SET modo = 'S3' WHERE idAlarma = '6031'");
                                        break;
                                    case "PWR":
                                        st.executeUpdate("UPDATE alarma SET modo = 'S1' WHERE idAlarma = '6031'");
                                        break;
                                    case "OK":
                                        st.executeUpdate("UPDATE alarma SET modo = 'N' WHERE idAlarma = '6031'");
                                        break;
                                }
                            }
                        }
                        //</editor-fold>
                        
                        else
                        {
                            reply.setTo(fromName);
                            reply.setBody("Seguridad Mongtomery a su servicio: " + message.getBody());
                        }
                        
                        if (!reply.getBody().equals(""))
                            xmppConnection.sendPacket(reply);
                    }
                    st.close();
                } catch (SQLException | NumberFormatException ex) {
                    Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }    
}
