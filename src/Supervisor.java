import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.action.OriginateAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;

/**
 *
 * @author Kailu Mario
 * @version 0.10
 * @since 2013-06-02 15:20
 */
public class Supervisor extends BaseAgiScript implements Runnable{
    
    private Thread reacciones = new Thread(this);
    private ManagerConnection managerConnection;
    private Connection conn = null;
    private XMPPConnection gTalkService, openfire;
    private final int PRESENCIA = 2; 
    
    public String idSupervisor, idGPS, nombre, zona, GPS, gTalk, status;
    public String[] telefono;
        
    public Supervisor (){}
    
    public Supervisor(String idSupervisor, XMPPConnection google, XMPPConnection openfire)
    //<editor-fold defaultstate="collapsed" desc="Constructor de AsistetedeSupervisor">
    {
        try {
            
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver);
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
            
            this.idSupervisor   = idSupervisor;
            this.openfire       = openfire;
            
            if (actualizar())
                this.gTalkService = google;
            else
                throw new Exception("No existe supervisor con ese ID");
            
            reacciones.start();
            // Iniciando el servidor de notificaciones.
            //srv = new ServidorNotificaciones(notifSocket, idGPS);
            //srv.iniciar();
            
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    private boolean actualizar()
    //<editor-fold defaultstate="collapsed" desc="Actualizar la Info de Contacto del Supervisor">
    {
        try {
            String sSQL = "SELECT * FROM supervisor WHERE idSupervisor = '"+ idSupervisor +"'";
            Statement st = (Statement) conn.createStatement();
            ResultSet res = st.executeQuery(sSQL);
            if (res.first())
            {
                this.idGPS = res.getString("idGPS");
                this.nombre = res.getString("nombre");
                this.zona = res.getString("zona");
                this.GPS = res.getString("gps");
                this.telefono = new String[] {res.getString("tel_1"), res.getString("tel_2")};
                this.gTalk = res.getString("gTalk");
                this.status = res.getString("status");
                
                res.close();
                st.close();
                
                return true;
            }
            
            if (!st.isClosed())
                st.close();            
        } catch (SQLException ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    //</editor-fold>
    
    public void solicitarActualizacion(int idReaccion, String idAlarma, String nombreAlarma, String idOperacion, String tipo)
    //<editor-fold defaultstate="collapsed" desc="Solicitud de actualización pasados 5 min">
    {
        try {
            int intento = 2;
            Calendar c;
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Statement st = (Statement) conn.createStatement();
            
            if (nombre != null && !nombre.equals("null") && !nombre.equals(""))
            {
                StringBuilder mensaje = new StringBuilder();                
                switch (tipo) {
                    case "actualizar":
                        mensaje.append(nombre);
                        mensaje.append(", Por favor actualice la reacción de ");
                        mensaje.append(nombreAlarma).append(". ");
                        break;
                    case "reinformar":
                        intento = 3;
                        ResultSet r = st.executeQuery("SELECT vitacora.mensaje FROM vitacora, reaccion WHERE "
                                + "reaccion.idReaccion = '"+ idReaccion +"' AND "
                                + "vitacora.GUID = reaccion.GUID");
                        if (r.first())
                        {
                            mensaje.append(r.getString("mensaje"));
                        }
                        r.close();
                        break;
                }

                int i;
                for (i=0; i < intento; i++)
                {
                    // En cada intento debe arrancar como INFORMANDO
                    String estadoRespuesta = "INFORMANDO";
                    String guii = UUID.randomUUID().toString();
                    st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, idOperacion, mensaje, destinatario, GUID) VALUES ("
                            + "'"+ idAlarma +"', "
                            + "'Telefónico', "
                            + "'"+ idOperacion +"', "
                            + "'"+ mensaje.toString().replaceAll("\n\n", " ").replaceAll("\n", ". ").toLowerCase() +"', "
                            + "'Supervisor "+ nombre +"', "
                            + "'"+ guii +"')");

                    try {
                        String respuesta = llamarAPI(telefono[0], guii, idReaccion, true);
                        
                        if (respuesta.contains("Error"))
                        {
                            c = Calendar.getInstance();
                            st.executeUpdate("UPDATE vitacora set estado = 'MARCACIÓN ERRADA', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii +"'");
                            estadoRespuesta = "MARCACIÓN ERRADA";
                            Thread.sleep(1400);
                        }
                        else
                        {
                            int contadorNominal = 0;
                            while (estadoRespuesta.equals("INFORMANDO"))
                            {
                                ResultSet res = st.executeQuery("SELECT * FROM vitacora WHERE GUID = '"+ guii +"'");
                                if (res.first()){
                                    estadoRespuesta = res.getString("estado");
                                }
                                contadorNominal++;
                                if (contadorNominal > 200)
                                    estadoRespuesta = "MARCACIÓN ERRADA";
                                Thread.sleep(2000);
                            }
                        }
                        
                        if (estadoRespuesta.equals("ACTUALIZADO"))
                            break;

                    } catch (IOException | InterruptedException ex) {
                        Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                try {
                    // Darle 5 segundos si no confirma o no actualiza.
                    if (i == intento) Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
                }
                st.close();
            } else
            {
                // No existe supervisor.
                st.executeUpdate("UPDATE reaccion SET estado = 'ERROR' WHERE idReaccion = '"+ idReaccion +"'");
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
        }        
    }
//</editor-fold> 
    
    public void actualizarMinuta(String idReaccion, String idAlarma, String texto, String tipo, int recibido)
    //<editor-fold defaultstate="collapsed" desc="Actualizar minuta">
    {
        try {
            String reporteMinuta = "";
            Statement st = (Statement) conn.createStatement();
            
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            SimpleDateFormat g = new SimpleDateFormat("EEE, dd 'de' MMMMM 'a la(s)' HH:mm", new Locale("ES"));
            Calendar c = Calendar.getInstance();
            
            switch (recibido)
            {
                case 1:
                    reporteMinuta = "REACCIÓN EN TRAMITE, AUDIO: " + texto;
                    st.executeUpdate("UPDATE reaccion SET estado = 'EN TRAMITE', ultimaActualizacion = '"+ f.format(c.getTime()) +"' WHERE idReaccion = '"+ idReaccion +"'");
                    break;
                case 2:
                    // TODO REPORTAR COMO FALSA ALARMA
                    reporteMinuta = "REACCIÓN CERRADA CON REPORTE DE FALSA ALARMA, AUDIO: " + texto;
                    st.executeUpdate("UPDATE reaccion SET estado = 'ATENDIDA', ultimaActualizacion = '"+ f.format(c.getTime()) +"' WHERE idReaccion = '"+ idReaccion +"'");
                    if (Integer.parseInt(idAlarma) >= 6000)
                        st.executeUpdate("UPDATE alarma SET modo = 'N' WHERE idAlarma = '"+ idAlarma +"'");
                    break;
                    
                case 3:
                    reporteMinuta = "REACCIÓN CERRADA CON REPORTE DE SERVICIO TÉCNICO, AUDIO: " + texto;
                    st.executeUpdate("UPDATE reaccion SET estado = 'ATENDIDA', ultimaActualizacion = '"+ f.format(c.getTime()) +"' WHERE idReaccion = '"+ idReaccion +"'");
                    
                    // SOLICITAR UNA OT.
                    ResultSet rs = st.executeQuery("SELECT supervisor.* FROM supervisor, reaccion WHERE reaccion.idSupervisor = supervisor.idSupervisor AND idReaccion = '"+ idReaccion +"'");
                    if (rs.first())
                    {
                        String supervisor = rs.getString("nombre");
                        rs = st.executeQuery("SELECT MAX(idservicio) AS servicios FROM servicio s, alarma a "
                            + "WHERE s.idAlarma = a.idAlarma AND s.idAlarma = '"+ idAlarma +"' AND s.estado = 'PENDIENTE' AND a.estadoActual = 'A'");                
                   
                        if (rs.next() && rs.getString("servicios") != null)
                            st.executeUpdate("UPDATE servicio SET motivo = CONCAT('"+ g.format(c.getTime()) +" Se solicita OT durante reacción a cargo de "+ supervisor +" con Cod: "+ idReaccion +" AUDIO: "+ texto +"<br>', motivo) WHERE idservicio = '"+ rs.getString("servicios") +"'");
                        else
                            st.executeUpdate("INSERT INTO servicio (idAlarma, tipo, solicitante, motivo) VALUES ('"+ idAlarma +"', 'REVISIÓN', 'Supervisor a cargo: "+ supervisor +"', 'Se solicita OT durante reacción con Cod: "+ idReaccion +" AUDIO: "+ texto +"<br>')");
                    }                    
                    break;
                    
                case 4:
                    reporteMinuta = "REACCIÓN SIN NOVEDAD CERRADA POR SUPERVISOR, AUDIO: " + texto;
                    st.executeUpdate("UPDATE reaccion SET estado = 'ATENDIDA', ultimaActualizacion = '"+ f.format(c.getTime()) +"' WHERE idReaccion = '"+ idReaccion +"'");
                    if (Integer.parseInt(idAlarma) >= 6000)
                        st.executeUpdate("UPDATE alarma SET modo = 'N' WHERE idAlarma = '"+ idAlarma +"'");
                    break;
                default:
                    reporteMinuta = "SIN INFORMACIÓN, ENTRADA: " + recibido;
                    break;
                    
            }
            st.executeUpdate("INSERT INTO minuta (idReaccion, nota, tipoMinuta) VALUES "
                    + "('"+ idReaccion +"', '"+ reporteMinuta +"', '"+ tipo +"')");
            
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    public void actualizarVitacora(String guii, String estado, String uniqueid)
    //<editor-fold defaultstate="collapsed" desc="Actualiza la vitacora">
    {
        try {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Statement st = (Statement) conn.createStatement();
            st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"', uniqueid = '"+ uniqueid +"', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID='"+ guii +"'");
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//</editor-fold>
    
    public boolean l500(String idReaccion )
    //<editor-fold defaultstate="collapsed" desc="SOLICITAR LLAMADA">  
    {
        //<editor-fold defaultstate="collapsed" desc="OLD CODE">
        /*String response = "";
         * try {
         * // Llamar al movil del patrullero
         * OriginateAction originateAction = new OriginateAction();
         * ManagerConnectionFactory factory = new ManagerConnectionFactory("localhost", "kerberus", "kerberus");
         * managerConnection = factory.createManagerConnection();
         * ManagerResponse originateResponse;
         * long timeout = 20000;
         * originateAction.setCallerId("Reaccion Montgomery  <0323730840>");
         * Statement st = (Statement) conn.createStatement();
         * ResultSet rs;
         * 
         * if (telefono[0].length() == 7)
         * rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'local' AND estado = 'A' ORDER BY idproveedor");
         * 
         * else
         * {
         * rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'celular' AND estado = 'A' ORDER BY idproveedor");
         * timeout = 30000;
         * }
         * 
         * while (rs.next())
         * {
         * if (estadoTroncal(rs.getString("nombre"), rs.getString("tech")))
         * {
         * String tech = rs.getString("tech");
         * String nombre = rs.getString("nombre");
         * String prefijo = rs.getString("prefijo");
         * originateAction.setChannel( tech + "/" + nombre + "/" + prefijo + telefono[0] );
         * break;
         * }
         * }
         * rs.close();
         * st.close();
         * 
         * //originateAction.setChannel("SIP/4006");
         * originateAction.setContext("MOD_AXAR");
         * originateAction.setExten("update");
         * 
         * originateAction.setTimeout(timeout);
         * originateAction.setPriority(new Integer(1));
         * 
         * // connect to Asterisk and log in
         * if (managerConnection.getState() != ManagerConnectionState.CONNECTED)
         * managerConnection.login();
         * 
         * // send the originate action and wait for a maximum of 30 seconds for Asterisk
         * // to send a reply
         * originateResponse = managerConnection.sendAction(originateAction, timeout);
         * 
         * // print out whether the originate succeeded or not
         * response = originateResponse.getResponse();
         * System.out.println(originateResponse.getResponse());
         * 
         * // and finally log off and disconnect
         * managerConnection.logoff();
         * 
         * if (!response.equals("ERROR"))
         * return true;
         * else
         * return false;
         * 
         * } catch (SQLException ex) {
         * Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
         * } catch (IllegalStateException ex) {
         * Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
         * } catch (IOException ex) {
         * Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
         * } catch (AuthenticationFailedException ex) {
         * Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
         * } catch (TimeoutException ex) {
         * Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
         * return false;
         * }
         * return false;*/
        //</editor-fold>
        
        Writer writer = null;
        StringBuilder sb = new StringBuilder();
        try {
            int timeout = 20;
            Statement st = (Statement) conn.createStatement();
            ResultSet rs;
            
            if (telefono[0].length() == 7)
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'local' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: Seguridad Montgomery <23110610>\n");
            }
            else
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'celular' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: Seguridad Montgomery <0323110610>\n");
                timeout = 30;
            }
            while (rs.next())
            {
                if (estadoTroncal(rs.getString("nombre"), rs.getString("tech")))
                {
                    String tech = rs.getString("tech");
                    String nombreTroncal = rs.getString("nombre");
                    String prefijo = rs.getString("prefijo");
                    sb.append("Channel: ").append(tech).append("/").append(nombreTroncal).append("/").append(prefijo).append(telefono[0]).append("\n");
                    break;
                }
            }
            rs.close();
            st.close();
            
            sb.append("MaxRetries: 0\n");
            sb.append("RetryTime: 60\n");
            sb.append("WaitTime: ").append(timeout).append("\n");
            sb.append("Context: MOD_AXAR\n");
            sb.append("Extension: update\n");
            sb.append("Priority: 1\n");
            if (idReaccion != null)
                sb.append("SetVar: IDRE=0/").append(idReaccion).append("\n");
            
        } catch (SQLException ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                String guii  = UUID.randomUUID().toString();
                writer = new BufferedWriter(new OutputStreamWriter( new FileOutputStream("/var/spool/asterisk/outgoing/" + guii + ".call"), "utf-8") );
                writer.write(sb.toString());                
                writer.close();
                return true;
            } catch (Exception ex) {
                System.out.println("" + ex.getMessage());
                return false;
            }
        }
    }//</editor-fold>
    
    public String llamarAPI (String numero, String guii, int idReaccion, boolean actualizar) throws FileNotFoundException, IOException
    //<editor-fold defaultstate="collapsed" desc="LLamada usando .call">
    {
        Writer writer = null;
        StringBuilder sb = new StringBuilder();
        try {
            int timeout = 20;
            Statement st = (Statement) conn.createStatement();
            ResultSet rs;
            
            if (numero.length() == 7)
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'local' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: Seguridad Montgomery <23730840>\n");
            }
            else
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'celular' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: Seguridad Montgomery <0323730840>\n");
                timeout = 30;
            }
            while (rs.next())
            {
                if (estadoTroncal(rs.getString("nombre"), rs.getString("tech")))
                {
                    String tech = rs.getString("tech");
                    String nombreTroncal = rs.getString("nombre");
                    String prefijo = rs.getString("prefijo");
                    if (numero.equals("4006"))
                        sb.append("Channel: SIP/4006\n");
                    else
                        sb.append("Channel: ").append(tech).append("/").append(nombreTroncal).append("/").append(prefijo).append(numero).append("\n");
                    break;
                }
            }
            rs.close();
            st.close();
            
            sb.append("MaxRetries: 0\n");
            sb.append("RetryTime: 60\n");
            sb.append("WaitTime: ").append(timeout).append("\n");
            sb.append("Context: MOD_AXAR\n");
            sb.append("Extension: update\n");
            sb.append("Priority: 1\n");            
            if (actualizar)
                sb.append("SetVar: IDRE=").append(guii).append("/").append(idReaccion).append("\n");
            else
                sb.append("SetVar: REAC=").append(guii).append("/true").append("\n");            
            
        } catch (SQLException ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                writer = new BufferedWriter(new OutputStreamWriter( new FileOutputStream("/var/spool/asterisk/outgoing/" + guii + ".call"), "utf-8") );
                writer.write(sb.toString());                
                writer.close();
                return "Ok";
            } catch (Exception ex) {
                System.out.println("" + ex.getMessage());
                return "Error";
            }
        }
    }
    //</editor-fold>
    
    public String llamar(String numero, String guii, int idReaccion, boolean actualizar) throws IOException, AuthenticationFailedException, TimeoutException
    //<editor-fold defaultstate="collapsed" desc="INFORMAR AL PATRULLERO">
    {
        try 
        {
            OriginateAction originateAction = new OriginateAction();
            ManagerConnectionFactory factory = new ManagerConnectionFactory("localhost", "kerberus", "kerberus");
            managerConnection = factory.createManagerConnection();
            ManagerResponse originateResponse;
            long timeout = 20000;
            originateAction.setCallerId("Reaccion Montgomery  <0323730840>");
            Statement st = (Statement) conn.createStatement();
            ResultSet rs;

            if (numero.length() == 7)
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'local' AND estado = 'A' ORDER BY idproveedor");
            
            else 
            {
              rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'celular' AND estado = 'A' ORDER BY idproveedor");
              timeout = 35000;
            }

            while (rs.next())
            {                
                if (estadoTroncal(rs.getString("nombre"), rs.getString("tech")))
                {
                    String tech = rs.getString("tech");
                    String nombre = rs.getString("nombre");
                    String prefijo = rs.getString("prefijo");
                    if (numero.equals("4006"))
                        originateAction.setChannel( "SIP/4006" );
                    else
                        originateAction.setChannel( tech + "/" + nombre + "/" + prefijo + numero );
                    break;
                }
            }
            rs.close();
            st.close();
            
            //originateAction.setChannel("SIP/4006");
            originateAction.setContext("MOD_AXAR");
            originateAction.setExten("update");
            
            if (actualizar)
                originateAction.setVariable("IDRE", guii + "/" + idReaccion);
            else
                originateAction.setVariable("REAC", guii + "/true");
            
            // Este timeout es para la contestación de la llamada
            originateAction.setTimeout(timeout);
            originateAction.setPriority(new Integer(1));

            // connect to Asterisk and log in
            if (managerConnection.getState() != ManagerConnectionState.CONNECTED)
                managerConnection.login();

            // send the originate action and wait for a maximum of 30 seconds for Asterisk
            // to send a reply
            originateResponse = managerConnection.sendAction(originateAction, timeout);
            
            // print out whether the originate succeeded or not
            String res = originateResponse.getResponse();        
            System.out.println("\n" + originateResponse.getResponse() + "\n");

            // and finally log off and disconnect
            /*try {     
                managerConnection.logoff();   
            } 
            catch (Exception h) { System.out.println("\nEXCEPCIÓN AL DESLOGUEO EN LLAMADA() "+ h.getMessage() +"\n" ); }   
            */
            return res;
        }
        catch (SQLException ex) {
            Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }//</editor-fold>
    
    public boolean enviarNotificacion(String mensaje, int idReaccion)
    //<editor-fold defaultstate="collapsed" desc="Notificaciones XMPP App">
    {
        if (openfire != null && openfire.isConnected())
        {
            Roster r = openfire.getRoster();
            if (r.getPresence(idGPS).getType() == Presence.Type.available)
            {
                Message msg = new Message(idGPS, Message.Type.chat);
                msg.setBody(Notificacion.APP_NOTIFICACION + idReaccion + "::" + mensaje);
                openfire.sendPacket(msg);
                return true;
            }
        }
        return false;
    }
//</editor-fold>
    
    public boolean enviarMensaje(String destino, String mensaje, String idAlarma, String idOperacion)
    //<editor-fold defaultstate="collapsed" desc="Envio de mensaje via gTalk">
    {
        try {
            Statement st = (Statement) conn.createStatement();
            
            if (!destino.equals("") && destino.contains("@") && gTalkService != null){

                Presence presence = gTalkService.getRoster().getPresence(destino);
                if (presence.getType() == Presence.Type.available)
                {
                    Message msg = new Message(destino, Message.Type.chat);                            
                    msg.setBody(mensaje.toString());
                    gTalkService.sendPacket(msg);
                    st = (Statement) conn.createStatement();
                    st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, idOperacion) Values "
                            + "('"+ idAlarma +"', 'gTalk', '"+ mensaje +"', '"+ destino +"', '"+ idOperacion +"')");
                    System.out.println("Enviado " + destino);                    
                    st.close();
                    return true;
                }
                else
                {
                    System.out.println("Usuario desconectado " + destino);
                    st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, estado, idOperacion) Values "
                            + "('"+ idAlarma +"', 'gTalk', 'Usuario desconectado', '"+ destino +"', 'DESCONECTADO', '"+ idOperacion +"')");
                    enviarEmail(idAlarma, mensaje, nombre, destino);
                    st.close();
                }
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }//</editor-fold>
    
    public void enviarEmail(String idAlarma, String mensaje, String nombre, String destino)
    //<editor-fold defaultstate="collapsed" desc="Envio de notificación via eMail">
    {
        try 
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
            message.setSubject("REACCIÓN POR ALARMA");
            message.setSentDate(Calendar.getInstance().getTime());
            message.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(destino, nombre));
            
            StringBuilder body = new StringBuilder();
            
            body.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\" /></head>");
            body.append("<body>");
            body.append("<div style=\"font-size:12px; text-align:left; border:1px solid white; font-family:Verdana, Arial, Helvetica, sans-serif; \">");
            body.append("<center><strong>");
            body.append(nombre.toUpperCase());
            body.append("<br>ESTE MENSAJE TE HA LLEGADO PORQUE TU GTALK SE ENCUENTRA FUERA DE LÍNEA</strong></center><br><br>");
            body.append(mensaje.replaceAll("\n", "<br>"));
            
            body.append("<br><br><b>&copy;2013 Seguridad Montgomery Ltda.</b><br>Website: <a href=\"http://www.montgomery.com.co\">www.montgomery.com.co</a><br>");
            body.append("PBX: +57(2) 311 0610<br>Santiago de Cali.<br>Colombia.");
            body.append("</div></body></html>");
            
            message.setContent(body.toString(), "text/html");
            Transport.send(message);
            
            // Actualiar Vitacora SUCESS
            Statement st = (Statement) conn.createStatement();
            st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario) Values "
                    + "('"+ idAlarma +"', 'eMail', '"+ mensaje +"', '"+ destino +"')");
            st.close();
            
        } catch (MessagingException ex) {
            try {
                // Actualizar vitacora FALLIDO
                Statement st = (Statement) conn.createStatement();
                st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario) Values "
                        + "('"+ idAlarma +"', 'eMail', 'Falló la entrega de la notificación via email', '"+ destino +"')");
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
    
    private boolean estadoTroncal(String nombre, String tech)
    //<editor-fold defaultstate="collapsed" desc="Verificador de estado">
    {
        try {
            
            String line;
            boolean respuesta = false;
            Process p = Runtime.getRuntime().exec(new String[]{"asterisk", "-rx", tech.toLowerCase() + " show peer " + nombre});
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = in.readLine()) != null) {
                if (line.contains("Status") && line.contains("OK"))
                {
                    respuesta = true;
                    break;
                }
            }
            in.close();
            p.destroy();
            return respuesta;
            
        } catch (IOException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    //</editor-fold> 
    
    public boolean cerrarConexiones()
    //<editor-fold defaultstate="collapsed" desc="Procedimiento para detener el hilo y cerrar la conexión a la DB">
    {
        try {
            reacciones = null;
            if (!conn.isClosed())
                conn.close();
            return true;
        } catch (SQLException ex) {
            Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    //</editor-fold>
    
    // FUNCIONES VITALES PERIODICAS
    
    @Override
    public void run()
    {
        while (Thread.currentThread() == reacciones)
        //<editor-fold defaultstate="collapsed" desc="Hilos automaticos de consulta: 3 SECS">
        {
            try {
                // Actualiza la info del supervisor
                actualizar();
                Calendar c;
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                
                // Obtener información de las reacciones PENDIENTES
                //<editor-fold defaultstate="collapsed" desc="Informar reacciones PENDIENTES">
                String sSQL = "SELECT nAlarma.nombre, nAlarma.nombreEscrito, nAlarma.direccion, nAlarma.barrio, nAlarma.descripcion, nAlarma.ciudad, reaccion.* FROM reaccion, "
                        + "(SELECT alarma.*, cliente.nombre, cliente.nombreEscrito FROM alarma, cliente WHERE alarma.idCliente = cliente.idCliente) as nAlarma "
                        + "WHERE reaccion.idAlarma = nAlarma.idAlarma AND sector = '"+ zona +"' AND reaccion.estado = 'PENDIENTE' ORDER BY ultimaActualizacion";
                
                //String sSQL = "SELECT * FROM reaccion WHERE sector = '" + GPS + "' AND estado = 'PENDIENTE' ORDER BY fechaActualizacion";
                Statement st    = (Statement) conn.createStatement();
                Statement stGlb = (Statement) conn.createStatement();
                ResultSet rs = stGlb.executeQuery(sSQL);                
                
                while(rs.next())
                {
                    // Preparar mensaje TTS
                    String idOperacion      = UUID.randomUUID().toString();
                    int idReaccion          = rs.getShort("idReaccion");
                    StringBuilder mensaje   = new StringBuilder();
                    mensaje.append(rs.getString("tipoReaccion"));
                    if (rs.getString("tipoReaccion").startsWith("9"))
                        mensaje.append(" para <nombre>, ");
                    else if (rs.getString("tipoReaccion").startsWith("8"))
                        mensaje.append(" para ").append(rs.getString("descripcion")).append(", ");
                    mensaje.append(rs.getString("direccion")).append(", Barrio ").append(rs.getString("barrio"));
                    
                    if (rs.getString("tipoReaccion").startsWith("8"))
                    {
                        String ciudad = rs.getString("ciudad").substring(rs.getString("ciudad").indexOf("-")+2);
                        mensaje.append(", ").append(ciudad);
                    }    
                    mensaje.append(", \n\n");
                    mensaje.append(rs.getString("mensaje"));

                    // Notificar gTalk
                    StringBuilder strgTalk = new StringBuilder();
                    strgTalk.append(mensaje.toString().replace("<nombre>", rs.getString("nombreEscrito")));
                    strgTalk.append("\n\nCOD. REACCIÓN: ");
                    strgTalk.append(idReaccion);
                    enviarMensaje(gTalk, strgTalk.toString() , rs.getString("idAlarma"), idOperacion);                    
                    enviarNotificacion(strgTalk.toString(), idReaccion);
                    
                    // Tiempo para que llegue la alerta
                    Thread.sleep(5000);
                    
                    // Actualizar la reacción A ASIGNANDO
                    c = Calendar.getInstance();
                    st.executeUpdate("UPDATE reaccion SET ultimaActualizacion = '"+ f.format(c.getTime()) +"', "
                            + "estado = 'ASIGNANDO', idSupervisor = '"+ idSupervisor + "', "
                            + "idOperacion = '" + idOperacion + "' "
                            + "WHERE idReaccion = '"+ idReaccion +"' ");

                    // Informar al patrullero de Sector
                    int i, intentos = 3;
                    for (i=0; i < intentos; i++)
                    {
                        String guii  = UUID.randomUUID().toString();
                        try {
                            // VERIFICAR QUE NO HAYA EN VITACORA UNA LLAMADA EN CURSO PARA idAlarma

                            // Ingresar en Vitacora
                            st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, idOperacion, mensaje, destinatario, GUID) VALUES ("
                                    + "'"+ rs.getString("idAlarma") +"', "
                                    + "'Telefónico', "
                                    + "'"+ idOperacion +"', "
                                    + "'"+ mensaje.toString().replaceAll("\n\n", " ").replaceAll("\n", ". ").replaceAll("<nombre>", rs.getString("nombre")).toLowerCase() +"', "
                                    + "'Supervisor "+ nombre +"', "
                                    + "'"+ guii +"')");

                            // Notificación vía telefónica.
                            String estadoRespuesta = "INFORMANDO";
                            System.out.println("LANZANDO LLAMADA "  + guii + " idReaccion " + idReaccion);
                            String resp;
                            
                            resp = llamarAPI(telefono[0], guii, idReaccion, false);

                            if (resp.contains("Error"))
                            {
                                c = Calendar.getInstance();
                                st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA', fechaActualizacion = '"+ f.format(c.getTime()) +"' "
                                        + "WHERE GUID = '"+ guii +"'");
                                estadoRespuesta = "MARCACIÓN ERRADA";
                            }
                            else 
                            {
                                int contadorNominal = 0;
                                while ( estadoRespuesta.equals("INFORMANDO") )
                                {
                                    ResultSet res = st.executeQuery("SELECT * FROM vitacora WHERE GUID = '"+ guii +"'");
                                    if (res.first())
                                        estadoRespuesta = res.getString("estado");

                                    contadorNominal++;
                                    if (contadorNominal > 90)
                                        estadoRespuesta = "MARCACIÓN ERRADA";
                                    Thread.sleep(2000);
                                }
                            }
                            //Analizar la vitacora.
                            System.out.println(estadoRespuesta);
                            if (estadoRespuesta.equals("CONFIRMADO"))
                                break;

                        } catch (IOException | InterruptedException ex) {
                            Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
                        }                         
                    }

                    c = Calendar.getInstance();
                    if (i < intentos)
                    {
                        st.executeUpdate("UPDATE reaccion SET ultimaActualizacion = '"+ f.format(c.getTime()) +"', "
                                + "estado = 'EN TRAMITE', idSupervisor = '"+ idSupervisor + "' "
                                + "WHERE idReaccion = '"+ idReaccion +"' ");
                    }
                    else if (i == intentos)
                    {
                        st.executeUpdate("UPDATE reaccion SET estado = 'NO ASIGNADA' WHERE idReaccion = '"+ idReaccion +"' ");
                        enviarMensaje("op.cali@montgomery.com.co", "El Supervisor " + nombre + " de la zona " + zona + ", no ha confirmado la reaccion " + idReaccion, rs.getString("idAlarma"), idOperacion);
                        // INFORMAR AL MASTER SPERVISOR
                        // UBICAR OTRO PATRULLERO TODO:
                    }
                }
                //</editor-fold>

                // Obtener información de las reacciones EN TRAMITE
                //<editor-fold defaultstate="collapsed" desc="Solicita actualización de reacciones en tramite de ANTIRROBO">
                sSQL = "SELECT nAlarma.nombreEscrito, nAlarma.nombre, nAlarma.idAlarma, reaccion.* FROM reaccion, "
                        + "(SELECT alarma.*, cliente.nombre, cliente.nombreEscrito FROM alarma, cliente WHERE alarma.idCliente = cliente.idCliente) as nAlarma "
                        + "WHERE reaccion.idAlarma = nAlarma.idAlarma AND reaccion.estado = 'EN TRAMITE' AND reaccion.tipoReaccion NOT LIKE '8%' "
                        + "AND idSupervisor = '"+ idSupervisor +"'";
                rs = stGlb.executeQuery(sSQL);
                
                while(!rs.isClosed() && rs.next())
                {
                    // Verificar que su horario actual ya haya cumplido 10 minutos.
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Calendar fActual = Calendar.getInstance();
                    Calendar fUltimo = Calendar.getInstance();
                    fActual.add(Calendar.MINUTE, -15);
                    fUltimo.setTime(format.parse(rs.getString("ultimaActualizacion")));
                    
                    if ( fUltimo.getTimeInMillis() < fActual.getTimeInMillis())
                    {
                        System.out.println("Solicitar actualización... " + rs.getString("idReaccion"));
                        solicitarActualizacion(rs.getShort("idReaccion"), rs.getString("idAlarma"), rs.getString("nombre"), rs.getString("idOperacion"), "actualizar");
                    }
                }
                //</editor-fold>
                
                // Informar de nuevos evetos a una misma Reacción
                //<editor-fold defaultstate="collapsed" desc="Informa soolo via gTalk del suceso">
                sSQL = "SELECT nAlarma.nombreEscrito, nAlarma.idAlarma, nAlarma.direccion, nAlarma.barrio, nAlarma.descripcion, nAlarma.ciudad, reaccion.* FROM reaccion, "
                        + "(SELECT alarma.*, cliente.nombre, cliente.nombreEscrito FROM alarma, cliente WHERE alarma.idCliente = cliente.idCliente) as nAlarma "
                        + "WHERE reaccion.idAlarma = nAlarma.idAlarma AND reaccion.estado = 'NUEVO EVENTO' AND sector = '"+ zona +"'";
                rs = stGlb.executeQuery(sSQL);
                while(rs.next())
                {
                    // Preparar mensaje TTS
                    int idReaccion      = rs.getShort("idReaccion");
                    //String guii       = rs.getString("GUID");
                    StringBuilder mensaje = new StringBuilder();
                    mensaje.append("NUEVO EVENTO ");
                    mensaje.append(rs.getString("tipoReaccion"));
                    if (rs.getString("tipoReaccion").startsWith("9"))
                        mensaje.append(" para <nombre>, ");
                    else if (rs.getString("tipoReaccion").startsWith("8"))
                    {
                        mensaje.append(" para ").append(rs.getString("descripcion")).append(", ");
                        mensaje.append(rs.getString("direccion")).append(", ");
                        mensaje.append(" Barrio ");
                        mensaje.append(rs.getString("barrio")).append(". ");
                    }
                    mensaje.append(rs.getString("mensaje"));
                    
                    // Notificar gTalk
                    StringBuilder strgTalk = new StringBuilder();
                    strgTalk.append(mensaje.toString().replace("<nombre>", rs.getString("nombreEscrito")));
                    
                    String[] n = rs.getString("mensaje").split("COD:");
                    enviarNotificacion( strgTalk.toString(), Integer.parseInt(n[1].replace(" ", "")) );
                    enviarMensaje(gTalk, strgTalk.toString() , rs.getString("idAlarma"), rs.getString("idOperacion"));
                    st.executeUpdate("UPDATE reaccion SET estado = 'NUEVO EVENTO INFORMADO' WHERE idReaccion='"+ idReaccion +"'");
                }
                //</editor-fold>
                
                stGlb.close();
                st.close();
                
                Thread.sleep(3000);
            } catch (InterruptedException | SQLException | ParseException ex) {
                Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //</editor-fold>       
    }
    
    @Override
    public void service(AgiRequest request, AgiChannel channel) throws AgiException 
    //<editor-fold defaultstate="collapsed" desc="Servicio para llamadas entrantes">
    {
        TTS digitador   = new TTS();
        try {
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver); 
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
            
            Calendar c;
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            digitador.answer();
            String varFuente = channel.getVariable("REAC");
            Statement st = (Statement) conn.createStatement();
            
            if (varFuente == null)
            {
                //<editor-fold defaultstate="collapsed" desc="Información Solicitada">
                int intento = 0;
                String tipo = "PERIÓDICO";
                OUTER:
                while (true)
                {
                    String[] t = null;
                    String idReaccion = "";                    
                    String dat = channel.getVariable("IDRE");
                    
                    if (dat != null)
                    {                        
                        t = dat.split("/");
                        System.out.println("SELECT * FROM vitacora WHERE GUID = '"+ t[0] +"'");
                        ResultSet rs = st.executeQuery("SELECT * FROM vitacora WHERE GUID = '"+ t[0] +"'");
                        idReaccion = t[1];
                        if (rs.first())
                            digitador.playTTS(rs.getString("mensaje").trim(), false, 1);
                        else
                            tipo = "L500";
                    }
                    else
                    {
                        tipo = "L500";
                        idReaccion   = digitador.playTTS("Por favor ingrese el código de la reacción.", true, 4);                        
                    }
                    
                    if (!idReaccion.equals("") && idReaccion.length() > 0)
                    {
                        // Verificar que el código exista                    
                        String sSQL = "SELECT * FROM reaccion WHERE idReaccion = '"+ idReaccion +"'";                        
                        ResultSet res = st.executeQuery(sSQL);
                        if (res.first() && !res.getString("estado").equals("ATENDIDA"))
                        { // Reacción existe ... pero debe estar pendiente
                            while(true)
                            {
                                String opcion = digitador.playTTS("Reacción: " + idReaccion + ". Para reportar novedad márque 1. Para falsa alarma 2. Para Servicio técnico 3. "
                                        + "Para cerrar la reacción 4. Para reportar novedad al cliente marque 5.", true, 1);
                                if (!opcion.equals("") && !opcion.equals("5"))
                                {
                                    digitador.playTTS("Por favor después del tono informe la novedad y presione cero para terminar. Gracias.", false, 1);
                                    digitador.recordFile("/var/spool/asterisk/monitor/" + channel.getUniqueId(), "gsm", "0", 20000, 0, true, 2000);
                                    
                                    System.out.println(opcion);
                                    int op = Integer.parseInt(opcion);
                                    channel.setVariable("CALLERID(num)", opcion);
                                    actualizarMinuta(idReaccion, res.getString("idAlarma"), channel.getUniqueId() + ".gsm", tipo, op);
                                    
                                    if ( dat!= null && getChannelStatus() > 3) // Si es una actualización, esta variable debe existir
                                        actualizarVitacora(t[0], "ACTUALIZADO", channel.getUniqueId() + ".gsm");
                                    
                                    else if (dat != null)
                                        actualizarVitacora(t[0], "COLGADO POR SUPERVISOR", "SIN REGISTRO");
                                    
                                    digitador.playTTS("Su reporte ha sido recibido. Hasta pronto.", false, 1);                                
                                    break OUTER;
                                }
                                else if (opcion.equals("5"))
                                {
                                    //Generar llamada al cliente que contestó la llamada
                                    
                                    /*ResultSet resAlarma = st.executeQuery("SELECT * FROM usuario WHERE idAlarma = '"+ res.getString("idAlarma") +"' AND usuario NOT LIKE '099'");
                                    while(resAlarma.next())
                                    {
                                        String movil = res.getString("tel_1");
                                        movil = movil.substring(0,1) + " " + movil.substring(1, 3) + " " + movil.substring(3, 6) + " " + movil.substring(6, 8) + " " + movil.substring(8);
                                        String mensaje = resAlarma.getString("titulo") + " " + resAlarma.getString("nombre") + ", Teléfono " + movil + ", " + movil;
                                        digitador.playTTS(mensaje, false, 1);
                                        if (digitador.getChannelStatus() < 3)
                                            break OUTER;
                                    }*/
                                }
                                else
                                    digitador.playTTS("No ha digitado nada aún.", false, 1);
                                
                                if (digitador.getChannelStatus() < 3)
                                    break OUTER;
                            }
                        }
                        else if (res.first() && res.getString("estado").equals("ATENDIDA"))
                            digitador.playTTS("Esta reacción ya fué atendida, intente de nuevo.", false, 1);
                        else 
                            digitador.playTTS("El código ingresado no existe, intente de nuevo.", false, 1);                
                    }
                    else
                        digitador.playTTS("La reacción debe poseer al menos 4 dígitos.", false, 1);
                    
                    intento++;
                    if (getChannelStatus() < 3 || intento == 3)
                    {
                        if (t != null)
                            actualizarVitacora(t[0], "COLGADO POR SUPERVISOR", "SIN REGISTRO");
                        break;
                    }
                }//</editor-fold>
            } else
            {
                //<editor-fold defaultstate="collapsed" desc="ATENCIÓN A LANZAMIENTO DE REACCIONES">
                String[] varRecibidas = varFuente.split("/");
                String guii = varRecibidas[0];
                boolean reporte = Boolean.parseBoolean(varRecibidas[1]);
                
                int intentos = 0;
                ResultSet res = st.executeQuery("SELECT * FROM vitacora WHERE GUID = '"+ guii +"'");                
                if (res.first())
                {
                    String mensaje = res.getString("mensaje");
                    OUTER1:
                    while (true) {
                        String respuesta = digitador.playTTS(mensaje, reporte, 1);
                        switch (respuesta) {
                            case "1":
                                c = Calendar.getInstance();
                                st.executeUpdate("UPDATE vitacora SET estado='CONFIRMADO', uniqueid='"+ channel.getUniqueId() +"', fechaActualizacion='"+ f.format(c.getTime()) +"' "
                                        + "WHERE GUID='"+ guii +"'");
                                digitador.playTTS("Reacción confirmada.", false, 1);
                                break OUTER1;
                            case "":
                            case "-1":
                                respuesta = digitador.playTTS("Por favor confirme la reacción presionando 1.", reporte, 1);
                                if ( respuesta.equals("1") )
                                {
                                    // Actualizar la reaccion
                                    c = Calendar.getInstance();
                                    st.executeUpdate("UPDATE vitacora SET estado = 'CONFIRMADO', uniqueid='"+ channel.getUniqueId() +"', fechaActualizacion='"+ f.format(c.getTime()) +"' "
                                            + "WHERE GUID='"+ guii +"'");
                                    digitador.playTTS("Reacción confirmada.", false, 1);
                                    break OUTER1;
                                }
                                break;
                        }
                        intentos++;
                        if (intentos == 3)
                        {
                            c = Calendar.getInstance();
                            st.executeUpdate("UPDATE vitacora SET estado = 'NO CONFIRMA', uniqueid = '"+ channel.getUniqueId() +"', fechaActualizacion = '"+ f.format(c.getTime()) +"' "
                                    + "WHERE GUID='"+ guii +"'");
                            break;
                        }
                    }
                }
                //</editor-fold>
            }
            st.close();
            
        } catch (SQLException | ClassNotFoundException ex) {
            Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
        } finally
        {
            try {
                if(conn != null) conn.close();
                digitador.hangup();
            } catch (SQLException ex) {
                Logger.getLogger(Supervisor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    //</editor-fold>
}
