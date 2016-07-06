import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

import org.asteriskjava.manager.AuthenticationFailedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.json.JSONArray;
import org.json.JSONObject;
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Kailu Mario
 * @version 0.10
 * @since 2013-06-02 15:20
 */
public class Operador implements Runnable {
    
    private String idAlarma;
    private Alarma alarmaCliente;
    private long intervalo = 600; 
    private Connection conn;          
    private XMPPConnection gtalk;
    private Thread tramitar;
    private ArrayList<Usuario> usuario;
    private ArrayList<Evento> alarma, apertura, otros, tardeCerrar, evtMtto;
   
    private ArrayList<EventoGPS> alarmaGPS  = new ArrayList<>();
    private ArrayList<EventoGPS> parqueoGPS = new ArrayList<>();
    private ArrayList<EventoGPS> desparqueo = new ArrayList<>();
    private ArrayList<EventoGPS> otrosGPS   = new ArrayList<>();
    private ArrayList<EventoGPS> actParqueo = new ArrayList<>();
    private ArrayList<EventoGPS> estadoGPS = new ArrayList<>();
    private ArrayList<EventoGPS> mttoGPS = new ArrayList<>();
    
    private boolean finTramitar = false, finEnTramite = false;
    
    public Operador(long intervalo, String idAlarma, XMPPConnection gtalk)
    //<editor-fold defaultstate="collapsed" desc="Constructor de creacion para operador x cliente">
    {
        this.gtalk  = gtalk;        
        try {
            
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver);
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
            this.intervalo = intervalo;
            this.idAlarma = idAlarma;
            
            try (Statement st = (Statement) conn.createStatement()) 
            {
                ResultSet res = st.executeQuery("SELECT * FROM usuario WHERE idAlarma = '"+ idAlarma +"' AND nombre NOT LIKE 'COACCION' ORDER BY usuario");
                usuario = new ArrayList<>();
                
                while(res.next()) {
                    usuario.add(new Usuario(res.getString("titulo"), res.getString("nombre"), res.getString("usuario"), res.getString("email"), res.getString("tel_1"), res.getString("tel_2"), res.getString("gtalk")));
                }
                res.close();
            }
            
            tramitar = new Thread(this);
            tramitar.start();
            
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    @Override
    public void run() {
        Thread hilo = Thread.currentThread();        
        while (hilo == tramitar)
        //<editor-fold defaultstate="collapsed" desc="Eventos a Tramitar">
        {
            Statement s = null;
            try {
                // Estado del cliente
                // Ubicar todos Los eventos en tramite de idAlarma
                String sSQL = "SELECT * FROM evento WHERE (evento.estado = 'TRAMITAR') "
                        + "AND evento.idAlarma = '"+ idAlarma +"' "
                        + "ORDER BY categoriaEvento, fecha";
                
                s = (Statement) conn.createStatement();
                ResultSet res = s.executeQuery(sSQL);
                
                StringBuilder eventosUpdate = new StringBuilder();
                StringBuilder eventosRestaure = new StringBuilder();
                
                boolean panico = false;
                alarma      = new ArrayList<>();
                apertura    = new ArrayList<>();
                otros       = new ArrayList<>();
                evtMtto     = new ArrayList<>();
                tardeCerrar = new ArrayList<>();
                
                if (res.first()) { // SI hay eventos, asignarlos
                    
                    // Creación de los datos de la Alarma
                    alarmaCliente = new Alarma(idAlarma);
                    
                    //<editor-fold defaultstate="collapsed" desc="Sistema Antirrobo">
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    if (!res.getString("trama").contains("GPS"))
                    {
                        do {
                            Evento evt = new Evento(
                                    res.getString("idEvento"),
                                    new Date(res.getTimestamp("fecha").getTime()),
                                    res.getString("evento"),
                                    res.getString("zona"),
                                    clasificarZona(res.getString("categoriaEvento"), res.getString("zona")),
                                    res.getString("particion"),
                                    "EN TRAMITE",
                                    res.getString("tipoEvento"),
                                    res.getString("clid"));
                            
                            if (res.getString("categoriaEvento").contains("ALARMA") && res.getString("tipoEvento").equals("EVENTO")) {
                                alarma.add(evt);
                                eventosUpdate.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                            }
                            
                            else if (res.getString("categoriaEvento").contains("FALLA DE ") && !res.getString("categoriaEvento").contains("FALLA DE SONIDO") && res.getString("tipoEvento").equals("EVENTO"))
                                evtMtto.add(evt);
                            
                            else if (res.getString("categoriaEvento").contains("ALARMA") && res.getString("tipoEvento").equals("RESTAURE"))
                                eventosRestaure.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                            
                            else if (res.getString("categoriaEvento").contains("APERTURA")) {
                                
                                apertura.add(evt);
                                
                                // Verificar que no hayan eventos en apertura en tramite
                                String nsql = "SELECT COUNT(idevento) AS nEvento FROM evento WHERE idAlarma = '"+ idAlarma +"' "
                                        + "AND estado = 'EN TRAMITE' "
                                        + "AND categoriaEvento LIKE '%APERTURA%'";
                                Statement nSt = (Statement) conn.createStatement();
                                ResultSet nrs = nSt.executeQuery(nsql);
                                
                                if (nrs.first() && Integer.parseInt(nrs.getString("nEvento")) > 0)
                                {
                                    // Consultar el id con el que se va a evacuar el evento.
                                    liberarEventos(apertura, "");
                                } else
                                    eventosUpdate.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                                
                                nrs.close();
                                nSt.close();
                            }
                            else if (res.getString("categoriaEvento").contains("TARDE")) {
                                tardeCerrar.add(evt);
                                eventosUpdate.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                            }
                            else {
                                //if (!evt.evento.contains("COMUNICACIÓN IP"))
                                otros.add(evt);
                                eventosUpdate.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                            }
                            
                        } while( res.next() );
                    } //</editor-fold>
                    
                    //<editor-fold defaultstate="collapsed" desc="Sistemas de GPS">
                    else if (res.getString("trama").contains("GPS"))
                    {
                        do {
                            EventoGPS evt = new EventoGPS(
                                    res.getString("idEvento"), //1
                                    new Date(res.getTimestamp("fecha").getTime()), // 2                                    
                                    res.getString("evento"), // 3
                                    res.getString("zona"), // 4
                                    clasificarZona(res.getString("categoriaEvento"), res.getString("zona")), // 5
                                    res.getString("particion"), // 6
                                    "EN TRAMITE", // 7
                                    res.getString("tipoEvento"), // 8
                                    res.getString("clid")); // 9
                            
                            if (res.getString("categoriaEvento").contains("GENERAL") && res.getString("tipoEvento").equals("EVENTO"))
                            {
                                if (evt.evento.contains("PÁNICO")) panico = true;
                                alarmaGPS.add(evt);
                            }
                            
                            else if (res.getString("categoriaEvento").contains("ESTADO")) // En estadoGPS.tipoEvento
                                estadoGPS.add(evt);
                            
                            else if (res.getString("categoriaEvento").contains("ALARMA DE PARQUEO") && (evt.tipoEvento.equals("ENCENDIDO") || evt.tipoEvento.equals("EVENTO") ))
                                parqueoGPS.add(evt);
                            
                            // Un solo evento
                            else if (res.getString("categoriaEvento").equals("PARQUEO") && res.getString("tipoEvento").equals("EVENTO"))
                                desparqueo.add(evt);                            
                            else if (res.getString("categoriaEvento").equals("PARQUEO") && res.getString("tipoEvento").equals("RESTAURE"))
                                actParqueo.add(evt);
                            // Un solo evento
                            
                            else if (res.getString("categoriaEvento").equals("MANTENIMIENTO") && evt.tipoEvento.contains("SIN SE")) // SIn señal de GPS
                                mttoGPS.add(evt);
                            
                            else
                                otrosGPS.add(evt);
                            
                            eventosUpdate.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                            
                        } while (res.next());
                    }
                    //</editor-fold>
                    
                    if (alarmaCliente.nombre != null && !alarmaCliente.nombre.equals(""))
                    {
                        // Fin Creación
                        if (eventosUpdate.length() > 0 )
                        {
                            if (s.isClosed()) s = (Statement) conn.createStatement();
                            sSQL = eventosUpdate.toString();
                            sSQL = "UPDATE evento SET estado = 'EN TRAMITE' WHERE " + sSQL.substring(0, (sSQL.length()-3));
                            s.executeUpdate(sSQL);
                        }
                        
                        // Limpia los eventos de tipo RESTAURE  de la categoría ALARMA
                        if (eventosRestaure.length() > 0)
                        {
                            if (s.isClosed()) s = (Statement) conn.createStatement();
                            sSQL = eventosRestaure.toString();
                            sSQL = "UPDATE evento SET estado = 'ATENDIDO' WHERE " + sSQL.substring(0, (sSQL.length()-3));
                            s.executeUpdate(sSQL);
                        }
                        
                        alarmaCliente.iniciar();
                        // LOS EVENTOS HAN SIDO AGRUPADOS
                        // Dar Prioridad a los eventos de apertura
                        
                        switch (alarmaCliente.tipoAlarma) {
                            //<editor-fold defaultstate="collapsed" desc="ANTIROBO">
                            case "ANTIROBO":
                                if (!apertura.isEmpty())
                                {
                                    System.out.println("Verificado Apertura " + idAlarma + " Eventos " + apertura.size());
                                    verificarApertura();
                                }
                                if (!alarma.isEmpty()) {
                                    System.out.println("Verificado Alarmas " + idAlarma + " Eventos " + alarma.size());
                                    verificarEventoAlarma();
                                }
                                if (!tardeCerrar.isEmpty() && apertura.isEmpty()) {
                                    System.out.println("Verificado TardeXCerrar " + idAlarma + " Eventos " + tardeCerrar.size());
                                    verificarTardeXCerrar();
                                }
                                if(!otros.isEmpty()) {
                                    System.out.println("Verificado OTROS " + idAlarma + " Eventos " + otros.size());
                                    verificarOtrosEventos();
                                }
                                if (!evtMtto.isEmpty())
                                {
                                    System.out.println("Verificado Evt. Mtto " + idAlarma + " Eventos " + evtMtto.size());
                                    verificarEventoMtto();
                                }
                                break;
                            //</editor-fold>
                            
                            //<editor-fold defaultstate="collapsed" desc="GPS">
                            case "GPS":                                
                                if (!otrosGPS.isEmpty()) {
                                    //System.out.println("Verificado Otros " + idAlarma + " Eventos " + otrosGPS.size());
                                    verificarOtrosGPS();                                    
                                }
                                
                                if(!alarmaGPS.isEmpty()) {
                                    System.out.println("Verificado Alarmas Normales " + idAlarma + " Eventos " + alarmaGPS.size());
                                    verificarAlarmaGPS(panico);
                                }
                                
                                if (!parqueoGPS.isEmpty()) {
                                    System.out.println("Verificado Alarmas de Parqueo " + idAlarma + " Eventos " + parqueoGPS.size());
                                    verificarAlarmaParqueo();
                                }
                                
                                if (!desparqueo.isEmpty()) {
                                    System.out.println("Verificado DESARMADO " + idAlarma + " Eventos " + desparqueo.size());
                                    verificarAperturaGPS();
                                }
                                
                                if (!mttoGPS.isEmpty()) {
                                    verificarGPS();
                                    System.out.println("Verificado señal de GPS " + idAlarma + " Eventos " + mttoGPS.size());
                                }
                                
                                if (!actParqueo.isEmpty()) {
                                    System.out.println("Activar Parqueo " + idAlarma + " Eventos " + actParqueo.size());
                                    activarParqueo();                                    
                                }
                                
                                if (!estadoGPS.isEmpty()) {
                                    System.out.println("Verificando Estado " + idAlarma + " Eventos " + estadoGPS.size());
                                    verificarEstado();
                                }
                                break;
                            //</editor-fold>
                        }
                    }
                    else // Cliente idAlarma no existe
                        s.executeUpdate("UPDATE evento SET estado = 'SIN ASIGNACIÓN' WHERE idAlarma = '"+ idAlarma +"'");
                }
                
                else {
                    finEnTramite = false;
                    finTramitar = true;
                    eventosEnTramite();
                }
                
                // No hay más eventos que procesar
                res.close();
                s.close();
                
                Thread.sleep(intervalo);
                
            } catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException p) {
                try {
                    if (s != null && !s.isClosed()) s.close();
                    if (conn != null && !conn.isClosed())
                        conn.close();
                    tramitar = null;
                } catch (SQLException ex) {
                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        //</editor-fold>
    }
    
    public String getAlarmaAsignada()
    {
        return idAlarma;
    }
    
    public boolean getEstado()
    //<editor-fold defaultstate="collapsed" desc="Obtención del estado para Eliminar el operador virtual desde Centinela">
    {
        return finTramitar && finEnTramite;
    }
    //</editor-fold>
    
    public void cerrarConexion()
    //<editor-fold defaultstate="collapsed" desc="Cerrar las conexiones a DB">
    {
        if (alarmaCliente != null) alarmaCliente.finallizar();
        tramitar.interrupt();
    }
    //</editor-fold>
    
    private String clasificarZona(String evento, String zona)
    //<editor-fold defaultstate="collapsed" desc="Califica zona de acuerdo al evento">
    {
        try {
            ResultSet res;
            Statement s = (Statement) conn.createStatement();
            
            if ( evento.contains("APERTURA") )
                res = s.executeQuery("SELECT nombre AS rs FROM usuario WHERE idUsuario = '"+ zona +"' AND idAlarma = '"+ idAlarma +"'");
            else
                res = s.executeQuery("SELECT descripcion AS rs FROM zona WHERE zona = '"+ zona +"' AND idAlarma = '"+ idAlarma +"'");
            
            String strEvento = "";
            while(res.next())
            {
                strEvento = res.getString("rs").toUpperCase();
            }
            res.close();
            s.close();
            return strEvento;
            
        } catch (SQLException ex) {
            Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }
    //</editor-fold>
    
    public void eventosEnTramite() 
    //<editor-fold defaultstate="collapsed" desc="Verificación de eventos en Tramite despues de haber sido analizados por el hilo 'tramitar'">
    {
        finEnTramite = false;
        try {
             String sSQL = "SELECT * FROM evento WHERE (evento.estado = 'EN TRAMITE') "
                    + "AND evento.idAlarma = '"+ idAlarma +"' "
                    //+ "AND evento NOT LIKE '700' "
                    + "ORDER BY categoriaEvento, fecha";

            Statement s  = (Statement) conn.createStatement();
            Statement st = (Statement) conn.createStatement();
            ResultSet res = s.executeQuery(sSQL);
            StringBuilder eventoPendiente = new StringBuilder();
            StringBuilder eventoAtendido  = new StringBuilder();

            while (res.next())
            {
                sSQL = "SELECT COUNT(idReaccion) AS cantidad FROM reaccion WHERE eventos LIKE '%"+ res.getString("idEvento") +"%' AND estado != 'ATENDIDA'";
                ResultSet r = st.executeQuery(sSQL);

                if (r.first() && Integer.parseInt(r.getString("cantidad")) > 0) // EXISTE UNA REACCIÓN PERO EL EVENTO AUN SIGUE EN TRAMITE
                {
                    eventoAtendido.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");                    
                    System.out.println("Evento con reaccion");
                }
                else
                {
                    System.out.println("Evento sin reaccion");
                    eventoPendiente.append("idEvento = '").append(res.getString("idEvento")).append("' OR ");
                }
            }
            if (eventoPendiente.length() > 0)
                st.executeUpdate("UPDATE evento SET estado = 'TRAMITAR' WHERE " + eventoPendiente.toString().substring(0, eventoPendiente.length()-4));
            if (eventoAtendido.length() > 0)
                st.executeUpdate("UPDATE evento SET estado = 'ATENDIDO' WHERE " + eventoAtendido.toString().substring(0, eventoAtendido.length()-4));

            s.close();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } finally
        {
            finEnTramite = true;
        }
    }//</editor-fold>
    
    private boolean troncalEnlinea(String nombre, String tech)
    //<editor-fold defaultstate="collapsed" desc="Verificador de estado de Troncal para hacer llamada">
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
    
    // VERIFICACIÓN DE EVENTOS
    
    public void verificarEventoAlarma()
    //<editor-fold defaultstate="collapsed">
    {
        String idOperacion = UUID.randomUUID().toString();
        // Identificar estado de la alarma
        DateFormat dateFormat;
        boolean reaccionInmediata = false;
        try {
            // Preparar mensaje
            Statement st = (Statement) conn.createStatement();
            StringBuilder mensaje = new StringBuilder();
            mensaje.append("Su sistema de alarma se ha activado y nos reporta ");
            mensaje.append(alarma.size());
            if (alarma.size() > 1) mensaje.append(" eventos de Alarma");
            else mensaje.append(" evento de Alarma");
            
            for (int i=0; i < alarma.size(); i++)
            {
                Evento evt = (Evento)alarma.get(i);
                dateFormat = new SimpleDateFormat("h:m aaa");
                
                mensaje.append("\nActivación a las ");
                mensaje.append(dateFormat.format(evt.fecha));
                if (evt.tipoEvento.equals("RESTAURE"))
                    mensaje.append(" ").append(evt.tipoEvento);
                else if (evt.evento.contains("PANICO SILENCIOSO"))
                    reaccionInmediata = true;
                mensaje.append(" de ");
                mensaje.append(evt.evento.toUpperCase());
                
                if (evt.evento.toUpperCase().contains("COACCIÓN") && !evt.zona.equals("000"))
                {
                    for (int j=0; j < usuario.size(); j++)
                    {
                        Usuario usr = (Usuario)usuario.get(j);
                        if (usr.idUsuario.equals(evt.zona))
                        {
                            evt.descZona = usr.nombre;
                            break;
                        }
                    }
                    mensaje.append(" con la clave del usuario ");
                    mensaje.append(evt.zona);
                    mensaje.append(" que corresponde a ").append(evt.descZona);
                }
                else if (!evt.zona.equals("000"))
                    mensaje.append(". La activación es en la Zona ").append(Integer.parseInt(evt.zona)).append(" que corresponde a ").append(evt.descZona);
            }
            
            //<editor-fold defaultstate="collapsed" desc="Notificaciones via gTalk">
            StringBuilder msjGTalk = new StringBuilder();
            msjGTalk.append(mensaje.toString());
            
            if (alarmaCliente.estado.equals("CIERRE")) {
                msjGTalk.append("\n\nEn estos momentos un supervisor se esta dirigiendo hacia el domicilio de su sistema de alarma ubicado en: ");
                msjGTalk.append(alarmaCliente.direccion);
                msjGTalk.append(", Barrio ");
                msjGTalk.append(alarmaCliente.barrio.replaceAll("Número", "#"));
                msjGTalk.append(", este es un mensaje preventivo. le estaré informando cualquier otra novedad.");
            }//else
               // msjGTalk.append("\n\nPor favor ingrese su SEGUNDA CLAVE de seguridad.");
            
            enviarMensaje(msjGTalk.toString(), usuario, "REPORTE DE ALARMA EN PROGRESO", idOperacion);
            //</editor-fold>
            
            // <editor-fold defaultstate="collapsed" desc="Notificacion vía Telefónica">
            
            String mensajeFinal = "Le llamo de Seguridad Montgomery. " + mensaje.toString().replaceAll(":", " y ");
            mensajeFinal = mensajeFinal.replaceAll("\n\n", "");
            mensajeFinal = mensajeFinal.replaceAll("\n", ". ");

            if (alarmaCliente.estado.equals("CIERRE")) {
                // <editor-fold defaultstate="collapsed" desc="INFORMAR A LOS USUARIOS">

                // Genera reacción || establecimiento armado
                insertarReaccion(alarma, "9 47", alarmaCliente, "Alarma en Establecimiento cerrado", false);
                //lanzarReaccion(alarma, "Alarma en Establecimiento cerrado", "9 47");                    
                // FIN

                mensajeFinal += ". En estos momentos un supervisor se esta dirigiendo hacia el domicilio de su sistema de alarma ubicado en, ";
                mensajeFinal += alarmaCliente.direccion + ", Barrio ";
                mensajeFinal += alarmaCliente.barrio;
                mensajeFinal += ", este es un mensaje preventivo. le estaré informando cualquier otra novedad.";

                // ENVIAR REACCIÓN
                Outer:
                for (int i=0; i < usuario.size(); i++)
                {
                    // Informar al empleado
                    String guii;
                    String[] telInformar;
                    Usuario usr = (Usuario)usuario.get(i);
                    String mensajeFinalPub = usr.titulo + " " + usr.nombre + ", " + mensajeFinal;

                    // Los intentos van asociados a los teléfonos de conatacto
                    if ((usr.tel1 != null && usr.tel1.length() >= 7) || (usr.tel2 != null && usr.tel2.length() >= 7))
                    {
                        if (usr.tel2.equals(""))
                            telInformar = new String[] {usr.tel1, usr.tel1};
                        else
                            telInformar = new String[] {usr.tel1, usr.tel1, usr.tel2, usr.tel2};
                        //

                        for (int j=0; j < telInformar.length; j++) {
                            try {
                                guii = UUID.randomUUID().toString();

                                //st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, GUID) Values "
                                insertarBitacora(idAlarma, "Telefónico", mensajeFinalPub, usr.nombre +" <"+ telInformar[j] +">", guii, false, idOperacion);

                                String statusVitacora = "INFORMANDO";
                                String response = informar(telInformar[j], guii, "ALARMA", "0000", false);

                                if (response.contains("Error"))
                                {
                                    st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                                    statusVitacora = "MARCACIÓN ERRADA";
                                }
                                else
                                {   // La llamada fue efectiva, contestaron.
                                    // Verificar el estado de la contestación si ya se ha actualizado
                                    String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                                    ResultSet resReaccion;
                                    int contadorNominal = 0;

                                    while(statusVitacora.equals("INFORMANDO"))
                                    {
                                        try {
                                            resReaccion = st.executeQuery(sSQL);
                                            if (resReaccion.first())
                                                statusVitacora = resReaccion.getString("estado");

                                            contadorNominal++;
                                            if (contadorNominal > 90)
                                                statusVitacora = "MARCACIÓN ERRADA";

                                            Thread.sleep(4000);
                                        } catch (InterruptedException ex) {
                                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }
                                }

                                // Analisis de la vitacora
                                System.out.println(statusVitacora);
                                if (!statusVitacora.equals("MARCACIÓN ERRADA")){
                                    // Evaluar si el usuario atendio o no la llamada
                                    if (statusVitacora.contains("ATENDIDO POR USUARIO")) // I(nformarle al supervisor 
                                    {
                                        ArrayList e = new ArrayList();
                                        Evento eTemp = new Evento("0000", Calendar.getInstance().getTime(), "NOTIFICACIÓN", "000", "NOTIFICACIÓN", "", "01", "NA", "000-000");
                                        e.add(eTemp);
                                        insertarReaccion(e, "9 10", alarmaCliente, "USUARIO INFORMADO: " + usr.titulo + " " + usr.nombre, false);
                                    }

                                    if (statusVitacora.contains("ATENDIDO POR USUARIO") || statusVitacora.contains("COLGADO POR USUARIO"))
                                        break Outer;
                                }
                                else // Hubo marcación errada
                                    Thread.sleep(2000);

                            } catch (IOException ex) {
                                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (AuthenticationFailedException ex) {
                                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                            } 
                        }
                    }
                    else
                        insertarBitacora(idAlarma, "Telefónico", "El usuario no posee números de contácto.", usr.nombre, UUID.randomUUID().toString(), false, idOperacion);
                }
                
                // Al menos uno de los encargados contestó
                liberarEventos(alarma, idOperacion);
                //</editor-fold>
            } 
            else {  // Alarmas con establecimiento abierto.... PÁNICOS O ZONAS 24 HORAS
                if ( !alarmaCliente.telContacto.equals("") && (alarmaCliente.telContacto.length() >= 7 && alarmaCliente.telContacto.length() <= 10) )
                {
                    // Establecimiento abierto, llamar al establecimiento.
                    //<editor-fold defaultstate="collapsed" desc="Procedimiento para llamar al establecimiento">
                    mensajeFinal = "Apreciado cliente, " + mensajeFinal;
                    mensajeFinal += ". Por favor digite su segunda clave de seguridad.";

                    String guii;
                    int intentosCom = 3, i;
                    for (i=0; i < intentosCom; i++)
                    {
                        //<editor-fold desc="Codigo para informar" defaultstate="collapsed">
                        try {
                            guii = UUID.randomUUID().toString();
                            
                            insertarBitacora(idAlarma, "Telefónico", mensajeFinal, "ESTABLECIMIENTO <"+ alarmaCliente.telContacto + ">", guii, false, idOperacion);
                            
                            String response = informar(alarmaCliente.telContacto, guii, "ALARMA", alarmaCliente.extension, true);

                            String statusVitacora = "INFORMANDO";
                            if (response.contains("Error"))
                            {
                                st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                                statusVitacora = "MARCACIÓN ERRADA";
                            }
                            else
                            {   // La llamada fue efectiva, contestaron.
                                // Verificar el estado de la contestación si ya se ha actualizado
                                String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                                ResultSet resReaccion;
                                int contadorNominal = 0;
                                
                                while(statusVitacora.equals("INFORMANDO"))
                                {
                                    try {
                                        resReaccion = st.executeQuery(sSQL);
                                        if (resReaccion.first())
                                            statusVitacora = resReaccion.getString("estado");
                                        
                                        contadorNominal++;
                                        if (contadorNominal > 50)
                                            statusVitacora = "MARCACIÓN ERRADA";
                                    
                                        Thread.sleep(5000);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                            System.out.println(statusVitacora);
                            if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("NO CONTESTA")){

                                if (requiereReaccion(statusVitacora, "ALARMA")) // TRUE usuario necesita reacción
                                {
                                    // Enviar reacción
                                    System.out.println("Enviando reaccion...");
                                    insertarReaccion(alarma, "9 47", alarmaCliente, "ALARMA: " + statusVitacora, false);
                                }
                                
                                liberarEventos(alarma, idOperacion);
                                break;
                            }

                        } catch (IOException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (AuthenticationFailedException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        //</editor-fold>
                    }
                    
                    if (i == intentosCom) { 
                        System.out.println("No contesto || Marcarcion errada > Se envia reacción");

                        insertarReaccion(alarma, "9 47", alarmaCliente, "ALARMA, NO CONTESTAN", false);
                        //lanzarReaccion(alarma, "ALARMA, No contestan", "9 47");
                        liberarEventos(alarma, idOperacion);
                    }
                //</editor-fold>
                }
                else // El cliente no tiene donde confirmar los eventos.... dismiss o enviar reaccion
                {
                    insertarReaccion(alarma, "9 47", alarmaCliente, "EL USUARIO NO POSEE TELÉFONO DE CONTACTO, NO ES POSIBLE CONFIRMAR. ESTADO: " + alarmaCliente.estado, false);
                    liberarEventos(alarma, idOperacion);
                }
            }          
            //</editor-fold>
            
            st.close();
            
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    public void verificarTardeXCerrar()
    //<editor-fold defaultstate="collapsed">
    {
        String idOperacion = UUID.randomUUID().toString();
        // Informar al cliente [ESTABLECIMIENTO]
        if ( alarmaCliente.estado.equals("APERTURA") )
        {
            try {
                String guii;
                StringBuilder mensaje = new StringBuilder();
                mensaje.append("Apreciado Cliente, aun no hemos recibido la señal de armado de su sistema de alarma, por favor ingrese su segunda clave de seguridad.");
                int intentosCom = 3, i;
                Statement st = (Statement) conn.createStatement();
                
                if (!alarmaCliente.telContacto.equals(""))
                {
                    for (i=1; i <= intentosCom; i++)
                    {
                    //<editor-fold desc="Codigo para informar" defaultstate="collapsed">
                    try {
                        guii = UUID.randomUUID().toString();
                        
                        // Verificar si alarmaCliente.estado = 'CIERRE'
                        if (alarmaCliente.estado.equals("CIERRE")) // NO llamar mas, el cliente ya realizó el cierre
                        {
                            liberarEventos(tardeCerrar, idOperacion);
                            insertarBitacora(idAlarma, "NOVEDAD", "YA SE REALIZÓ EL ARMADO, SE CANCELA EL PROCEDIMIENTO", "SISTEMA", "-1", false, idOperacion); 
                            break;
                        }
                        
                        insertarBitacora(idAlarma, "Telefónico", mensaje.toString(), "ESTABLECIMIENTO <"+ alarmaCliente.telContacto +">", guii, false, idOperacion);                       
                        String response = informar(alarmaCliente.telContacto, guii, "xCERRAR", alarmaCliente.extension, true);

                        String statusVitacora = "INFORMANDO";
                        if (response.contains("Error"))
                        {
                            st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                            statusVitacora = "MARCACIÓN ERRADA";
                        }
                        else
                        {   // La llamada fue efectiva, contestaron.
                            // Verificar el estado de la contestación si ya se ha actualizado
                            String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                            ResultSet resReaccion;
                            int contadorNominal = 0;
                            while(statusVitacora.equals("INFORMANDO"))
                            {
                                try {
                                    resReaccion = st.executeQuery(sSQL);
                                    if (resReaccion.first())
                                        statusVitacora = resReaccion.getString("estado");
                                    
                                    contadorNominal++;
                                    if (contadorNominal > 60)
                                        statusVitacora = "MARCACIÓN ERRADA";
                                        
                                    Thread.sleep(3000);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }

                        System.out.println(statusVitacora);
                        if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("COLGADO POR USUARIO")){

                            if (requiereReaccion(statusVitacora, "TARDE")) // TRUE usuario necesita reacción
                            {
                                // Enviar reacción
                                System.out.println("Enviando reacción..");
                                insertarReaccion(tardeCerrar, "9 47", alarmaCliente, statusVitacora, false);
                                // TODO: Informar a lo usuarios
                                String nMensaje = "Se ha generado una reacción para su sisteama de alarma, MOTIVO: "+ statusVitacora +" Durante la confirmación de Armado; en estos momentos estamos enviando "
                                        + "a un supervisor hacia su dirección para verificar, le estare informado cualquier otra novedad.";
                                enviarMensaje(nMensaje, usuario, "REPORTE DE NOVEDAD DURANTE EL ARMADO", idOperacion);

                                //TODO: INFORMAR VIA CELL

                                Calendar c = Calendar.getInstance();
                                st.executeUpdate("UPDATE horario SET PM = '23:59' WHERE idAlarma = '"+ alarmaCliente.idAlarma +"'"
                                        + " AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'");
                                liberarEventos(tardeCerrar, idOperacion);
                                break;
                                // Liberar evento; y postergar su horario de cierre
                            }
                            else if (statusVitacora.contains("CIERRE CONFIRMADO"))
                            {
                                // Actualizar el nuevo horario de cierre
                                int nextTime = 0;
                                String[] tiempo = statusVitacora.split(":");
                                if (tiempo.length > 1)
                                    nextTime = Integer.parseInt(tiempo[1].replaceAll("minutos", "").replaceAll(" ", ""));
                                System.out.println("Cierre en " + nextTime);

                                DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                                Calendar c = Calendar.getInstance();
                                c.add(Calendar.MINUTE, (nextTime + 2)); // Se le agregan 2 minutos de RATA
                                st.executeUpdate("UPDATE horario SET PM = '"+ dateFormat.format(c.getTime()) 
                                        +"' WHERE idAlarma = '"+ alarmaCliente.idAlarma +"'"
                                        + " AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'");
                                liberarEventos(tardeCerrar, idOperacion);
                                break;
                            }
                        }

                        } catch ( IOException | AuthenticationFailedException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        } 
                    //</editor-fold>

                        if (i == intentosCom)
                        { // Fin de los intentos
                            System.out.println("No contesto || Marcarcion errada || Llamar a los encargados");
                            
                            // Actualizar el horario a las 23:59:59 para no generar mas tarde x cerrar
                            Calendar c = Calendar.getInstance();
                            st.executeUpdate("UPDATE horario SET PM = '23:59:59' WHERE idAlarma = '"+ alarmaCliente.idAlarma +"'"
                                    + " AND dia like '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'");
                            liberarEventos(tardeCerrar, idOperacion);
                            
                            StringBuilder mensajeEncargado = new StringBuilder();
                            mensajeEncargado.append("Su Sistema de Alarma de ").append(alarmaCliente.nombre);
                            mensajeEncargado.append(" aun no ha reportado el armado, estamos llamando al establecimiento ");
                            mensajeEncargado.append("pero no contestan. De no confirmarse el armado, la alarma no reportará eventos de intrusión.");

                            // Notificación gTalk
                            enviarMensaje(mensajeEncargado.toString(), usuario, "REPORTE DE ALARMA SIN ARMADO", idOperacion);

                            //Notificar a los encargados.
                            USUARIOS:
                            for (int h=0; h < usuario.size(); h++)
                            { //<editor-fold defaultstate="collapsed" desc="Informa a los encargados que no hay cierre">
                                
                                String[] telInformar;
                                Usuario usr = (Usuario)usuario.get(h);
                                if ((usr.tel1 != null && usr.tel1.length() >= 7) || (usr.tel2 != null && usr.tel2.length() >= 7))
                                {
                                    if (usr.tel2.equals(""))
                                        telInformar = new String[] {usr.tel1, usr.tel1};
                                    else
                                        telInformar = new String[] {usr.tel1, usr.tel1, usr.tel2, usr.tel2};

                                    for (String movil : telInformar) 
                                    {
                                        // Verificar si alarmaCliente.estado = 'CIERRE'
                                        if (alarmaCliente.estado.equals("CIERRE")) // NO llamar mas, el cliente ya realizó el cierre
                                        {
                                            liberarEventos(tardeCerrar, idOperacion);
                                            insertarBitacora(idAlarma, "NOVEDAD", "YA SE REALIZÓ EL ARMADO, SE CANCELA EL PROCEDIMIENTO", "SISTEMA", "-1", false, idOperacion); 
                                            break USUARIOS;
                                        }

                                        guii = UUID.randomUUID().toString();
                                        try {
                                            String statusVitacora = "INFORMANDO";
                                            String nMensaje = usr.titulo + " " + usr.nombre +". " +  mensajeEncargado.toString();
                                            insertarBitacora(idAlarma, "Telefónico", nMensaje, usr.nombre +" <" + movil + ">", guii, false, idOperacion);
                                            String response = informar(movil, guii, "ALARMA", "0000", false);
                                            if (response.contains("Error"))
                                            {
                                                st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                                                statusVitacora = "MARCACIÓN ERRADA";
                                            }
                                            else
                                            {   // La llamada fue efectiva, contestaron.
                                                // Verificar el estado de la contestación si ya se ha actualizado
                                                String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                                                ResultSet resReaccion;
                                                int contadorNominal = 0;

                                                while(statusVitacora.equals("INFORMANDO"))
                                                {
                                                    try {
                                                        resReaccion = st.executeQuery(sSQL);
                                                        if (resReaccion.first())
                                                            statusVitacora = resReaccion.getString("estado");

                                                        contadorNominal++;
                                                        if (contadorNominal > 36)
                                                            statusVitacora = "MARCACIÓN ERRADA";

                                                        Thread.sleep(5000);
                                                    } catch (InterruptedException ex) {
                                                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                                    }
                                                }
                                            }
                                            if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("NO CONTESTA")) // COLGADO POR USUARIO NO CUENTA
                                                break;
                                        }catch (IOException | AuthenticationFailedException ex) {
                                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    }
                                }
                                else
                                    insertarBitacora(idAlarma, "Telefónico", "El usuario no posee números de contácto.", usr.nombre, UUID.randomUUID().toString(), false, idOperacion);
                            //</editor-fold>
                            }
                        }
                    }
                }
                else 
                {
                    // No hay donde confirmar
                    Calendar c = Calendar.getInstance();
                    st.executeUpdate("UPDATE horario SET PM = '23:59:59' WHERE idAlarma = '"+ alarmaCliente.idAlarma +"'"
                    + " AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'");
                    liberarEventos(tardeCerrar, idOperacion);
                }
                st.close();
            } catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else
            liberarEventos(tardeCerrar, idOperacion);
        
    }//</editor-fold>
    
    public void verificarApertura()
    //<editor-fold defaultstate="collapsed">
    {
        String idOperacion = UUID.randomUUID().toString();
        try {
            // Categorizar horario
            String msjFueraUsuario = "";
            String usuarioApertura = "";
            boolean fueradeHorario = false;
            Statement st = (Statement) conn.createStatement();
            StringBuilder mensaje = new StringBuilder();
            Calendar c  = Calendar.getInstance();                       // Fecha actual en la que se produce la apertura
            Calendar am = Calendar.getInstance();
            Calendar pm = Calendar.getInstance();
            
            mensaje.append("Hemos registrado ");
            mensaje.append(apertura.size());
            if (apertura.size() > 1) mensaje.append(" Desactivaciones ");
            else mensaje.append(" Desactivación ");
            
            String sSQL = "SELECT * FROM horario WHERE idAlarma = '"+ alarmaCliente.idAlarma +"' AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'";
            ResultSet res = st.executeQuery(sSQL);
            if (res.first())
            {
                String[] timeAM = res.getString("AM").split(":");
                String[] timePM = res.getString("PM").split(":");
                
                am.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeAM[0]));
                am.set(Calendar.MINUTE, Integer.parseInt(timeAM[1]));
                am.set(Calendar.SECOND, Integer.parseInt(timeAM[2]));
                
                pm.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timePM[0]));
                pm.set(Calendar.MINUTE, Integer.parseInt(timePM[1]));
                pm.set(Calendar.SECOND, Integer.parseInt(timePM[2]));
                
                if ( c.getTimeInMillis() < am.getTimeInMillis() || c.getTimeInMillis() > pm.getTimeInMillis() )
                {
                    fueradeHorario = true;
                    mensaje.append("FUERA DE HORARIO");
                    
                    if (c.getTimeInMillis() > pm.getTimeInMillis()) // Si la apertura es en la tarde o mayor al horario actual,
                    {
                        pm = c;
                        pm.add(Calendar.MINUTE, 10); // Se le dán 10 min al cierre
                        st.executeUpdate("UPDATE horario SET PM = '"+ pm.get(Calendar.HOUR_OF_DAY) +":"+ pm.get(Calendar.MINUTE) +":"+ pm.get(Calendar.SECOND) +"' WHERE "
                                + "idAlarma = '"+ alarmaCliente.idAlarma +"' AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'");
                    }
                }
                else
                    mensaje.append("en Horario Habitual");
            }
            // TODO: ELSE si no hay horario, es una apertura no autorizada.
            
            // Verificar si hay alarmas = Apertura con Alarma
            if (!alarma.isEmpty())
            {
                mensaje.append(" con ALARMA");
                liberarEventos(alarma, idOperacion);
                alarma.clear();
            }
            
            for (int i=0; i < apertura.size(); i++)
            {
                Evento evt = (Evento)apertura.get(i);
                DateFormat dateFormat = new SimpleDateFormat("h:m aaa");
                
                if (!evt.evento.toLowerCase().contains("cancelación"))
                {
                    mensaje.append("\nDesactivación ");
                    mensaje.append(evt.evento);
                }
                else
                    mensaje.append("\n").append(evt.evento);
                
                mensaje.append(" a las ");
                mensaje.append(dateFormat.format(evt.fecha));
                msjFueraUsuario = mensaje.toString();       // Variable para mensaje a usuario
                
                if (!evt.zona.equals("000")){                    
                    for (int j=0; j < usuario.size(); j++)
                    {
                        Usuario usr = (Usuario)usuario.get(j);
                        if (usr.idUsuario.equals(evt.zona))
                        {
                            evt.descZona = usr.nombre; usuarioApertura = evt.zona; 
                            break;
                        }
                    }
                    mensaje.append(" con la clave del usuario ");
                    mensaje.append(evt.zona);
                    mensaje.append(" que corresponde a ").append(evt.descZona);
                }
            }
            
            // Actualización de datos.
            alarmaCliente = new Alarma(idAlarma);
            if (alarmaCliente.estado.equals("CIERRE")) // Ya esta cerrado
                liberarEventos(apertura, idOperacion);
            else 
            {
                //<editor-fold defaultstate="collapsed" desc="Notificación gTalk">
                enviarMensaje(mensaje.toString(), usuario, "NOTIFICACIÓN DE DESARMADO", idOperacion);
                //</editor-fold>

                //Notificación Telefónica
                //<editor-fold defaultstate="collapsed" desc="Procedimiento para llamar al establecimiento y/o Encargado">
                String mensajeFinal = "Apreciado cliente, Le llamo de Seguridad Montgomery. " + mensaje.toString().replaceAll(":", " y ");
                mensajeFinal = mensajeFinal.replaceAll("\n\n", "");
                mensajeFinal = mensajeFinal.replaceAll("\n", ". ");
                mensajeFinal += ". Por favor digite su segunda clave de seguridad.";

                String guii;
                final int INTENTOS = 3;
                int i = 0;
                
                if (alarmaCliente.telContacto != null && !alarmaCliente.telContacto.equals(""))
                {   // Verificar si el cliente tiene o no donde informarle                
                    //<editor-fold desc="Codigo para informar al establecimiento" defaultstate="collapsed"> 
                    for (i=0; i < INTENTOS; i++)
                    {
                        try {
                            guii = UUID.randomUUID().toString();
                            insertarBitacora(idAlarma, "Telefónico", mensajeFinal, "ESTABLECIMIENTO <"+ alarmaCliente.telContacto +">", guii, false, idOperacion);
                            
                            String response = informar(alarmaCliente.telContacto, guii, "ALARMA", alarmaCliente.extension, true);

                            String statusVitacora = "INFORMANDO";
                            if (response.contains("Error"))
                            {
                                st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                                statusVitacora = "MARCACIÓN ERRADA";
                            }
                            else
                            {   // La llamada fue efectiva, contestaron.
                                // Verificar el estado de la contestación si ya se ha actualizado
                                sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                                ResultSet resReaccion;
                                int contadorNominal = 0;
                                
                                while(statusVitacora.equals("INFORMANDO"))
                                {
                                    try {
                                        resReaccion = st.executeQuery(sSQL);
                                        if (resReaccion.first())
                                            statusVitacora = resReaccion.getString("estado");
                                        
                                        contadorNominal++;
                                        if (contadorNominal > 36)
                                            statusVitacora = "MARCACIÓN ERRADA";
                                        
                                        Thread.sleep(5000);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }

                            System.out.println(statusVitacora);
                            if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("NO CONTESTA")){ // COLGADO POR USUARIO NO CUENTA

                                if (!statusVitacora.equals("SIN CONFIRMAR") && requiereReaccion(statusVitacora, "APERTURA") && !statusVitacora.equals("COLGADO POR USUARIO")) // TRUE usuario necesita reacción
                                {
                                    // Enviar reacción
                                    System.out.println("Enviando reaccion...");
                                    insertarReaccion(apertura, "9 47", alarmaCliente, statusVitacora, false);
                                    liberarEventos(apertura, idOperacion);
                                    break;
                                }
                                else if (i == (INTENTOS-1) && (statusVitacora.equals("SIN CONFIRMAR") || statusVitacora.equals("COLGADO POR USUARIO") ) )
                                {
                                    System.out.println("Enviando reaccion... 3 Intentos...");
                                    insertarReaccion(apertura, "9 47", alarmaCliente, statusVitacora, false);
                                    liberarEventos(apertura, idOperacion);
                                    
                                    // No se necesita evualar los intentos.
                                    i = 0;
                                    break;
                                } else if (statusVitacora.equals("IDENTIDAD CONFIRMADA"))
                                {
                                    liberarEventos(apertura, idOperacion);
                                    break;
                                }
                                //else // Los Eventos asociados a este procedimiento debe ser descartados o ATENDIDOS
                            }                            
                            Thread.sleep(1000);

                        } catch (IOException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (AuthenticationFailedException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }//</editor-fold>
                } 
                else
                {// NO tiene a donde notificarle.
                    System.out.println("Debe eliminarse los eventos...");
                    liberarEventos(apertura, idOperacion);
                }

                if (i == INTENTOS){
                    //<editor-fold defaultstate="collapsed" desc="Código para informar a el encargado con el que se desactivo la alarma">
                    System.out.println("No contesto || Marcarcion errada");
                    if (!fueradeHorario) // Apertura Normal
                    {
                        System.out.println("Eventos liberados HORARIO NORMAL");
                        liberarEventos(apertura, idOperacion);
                    }

                    else if (fueradeHorario)
                    { // Contactar a los encargados.
                        for (i=0; i < usuario.size(); i++)
                        //<editor-fold defaultstate="collapsed" desc="Fuera de Horario">
                        {
                            // Informar al empleado
                            String[] telInformar;
                            boolean breakUsuario = false;
                            Usuario usr = (Usuario)usuario.get(i);
                            
                            if (usuarioApertura.equals(usr.idUsuario))
                            {
                                String mensajeFinalPub = usr.titulo + " " + usr.nombre + ", " + msjFueraUsuario.replaceAll(":", " y ");
                                mensajeFinalPub = mensajeFinalPub.replaceAll("\n\n", "");
                                mensajeFinalPub = mensajeFinalPub.replaceAll("\n", ". ");
                                mensajeFinalPub += ". La desactivación se realizó con su contraseña, por favor ingrese su segunda clave.";
                                
                                // Los intentos van asociados a los teléfonos de conatacto
                                if (usr.tel2.equals(""))
                                    telInformar = new String[] {usr.tel1, usr.tel1};
                                else
                                    telInformar = new String[] {usr.tel1, usr.tel1, usr.tel2, usr.tel2};
                                //
                                
                                int j;
                                for (j=0; j < telInformar.length; j++) {
                                    try {
                                        guii = UUID.randomUUID().toString();
                                        insertarBitacora(idAlarma, "Telefónico", mensajeFinalPub, usr.nombre +" <"+ telInformar[j] +">", guii, false, idOperacion);
                                        
                                        String statusVitacora = "INFORMANDO";
                                        String response = informar(telInformar[j], guii, "APERTURA", "0000", true);
                                        
                                        if (response.contains("Error"))
                                        {
                                            st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                                            statusVitacora = "MARCACIÓN ERRADA";
                                        }
                                        else
                                        {   // La llamada fue efectiva, contestaron.
                                            // Verificar el estado de la contestación si ya se ha actualizado
                                            sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                                            ResultSet resReaccion;
                                            int contadorNominal = 0;
                                            while(statusVitacora.equals("INFORMANDO"))
                                            {
                                                try {
                                                    resReaccion = st.executeQuery(sSQL);
                                                    if (resReaccion.first())
                                                        statusVitacora = resReaccion.getString("estado");
                                                    
                                                    contadorNominal++;
                                                    if (contadorNominal > 45)
                                                        statusVitacora = "MARCACIÓN ERRADA";
                                                    
                                                    Thread.sleep(4000);
                                                } catch (InterruptedException ex) {
                                                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                                }
                                            }
                                        }
                                        
                                        System.out.println(statusVitacora);
                                        if (!statusVitacora.equals("MARCACIÓN ERRADA")){
                                            
                                            if (requiereReaccion(statusVitacora, "APERTURA")) // TRUE usuario necesita reacción
                                            {
                                                // Enviar reacción
                                                System.out.println("Enviando reaccion...");
                                                insertarReaccion(apertura, "9 47", alarmaCliente, statusVitacora + ". APERTURA FUERA DE HORARIO", false);
                                                //lanzarReaccion(apertura, statusVitacora, "9 47");
                                            }
                                            //else // Los Eventos asociados a este procedimiento debe ser descartados o ATENDIDOS
                                            liberarEventos(apertura, idOperacion);
                                            break;
                                        }
                                        
                                        Thread.sleep(1000);
                                        
                                    } catch (IOException | AuthenticationFailedException | InterruptedException ex) {
                                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                                
                                System.out.println(j + " " + telInformar.length );
                                if (j == telInformar.length)
                                {
                                    System.out.println("No contestó... se envia reacción");
                                    insertarReaccion(tardeCerrar, "9 47", alarmaCliente, "No contestó en apertura fuera de Horario, se envia reacción", false);
                                    //lanzarReaccion(tardeCerrar, "No contestó, se envia reacción", "9 47");
                                }
                                if(breakUsuario)
                                    break;
                            }
                        }
                        //</editor-fold>
                    }
                    //</editor-fold>
                }
                //</editor-fold>
            }
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    //</editor-fold>
    
    public void verificarEventoMtto()
    //<editor-fold defaultstate="collapsed">
    {
        String idOperacion = UUID.randomUUID().toString();  
        StringBuilder contenido = new StringBuilder();        
        StringBuilder msjCliente = new StringBuilder();
        SimpleDateFormat f = new SimpleDateFormat("EEEE, dd 'de' MMM yyyy 'a la(s)' HH:mm", new Locale("ES"));
        String nf = f.format(Calendar.getInstance().getTime());
        
        for (Evento eMtto: evtMtto)
        {
            if (!eMtto.evento.contains("BATERÍA"))
                contenido.append(eMtto.evento).append(": Recibido el ").append(nf).append("<br>");
            
            else if (eMtto.evento.contains("BATERÍA"))
            {
                String stb = "";
                if (!eMtto.zona.equals("000"))
                    stb = " en la zona " + eMtto.zona + " ["+ eMtto.descZona +"]";                
                contenido.append("Cambio de Batería por falla").append(stb).append(", Recibido el ").append(nf).append("<br>");
                msjCliente.append(contenido.toString());
            }
        }
        
        /*for (int i=0; i < evtMtto.size(); i++)
        {
            if (!evtMtto.get(i).evento.contains("BATERÍA"))
                contenido.append(evtMtto.get(i).evento).append(": Recibido el ").append(nf).append("<br>");
            
            else if (evtMtto.get(i).evento.contains("BATERÍA"))
            {
                String stb = "";
                if (!evtMtto.get(i).zona.equals("000"))
                    stb = " en la zona " + evtMtto.get(i).zona + " ["+ evtMtto.get(i).descZona +"]";                
                contenido.append("Cambio de Batería por falla").append(stb).append(", Recibido el ").append(nf).append("<br>");
                msjCliente.append(contenido.toString());
            }
        }*/
        
        if (contenido.toString().length() > 0)
        {
            try (Statement st = (Statement) conn.createStatement()) 
            {
                ResultSet rs = st.executeQuery("SELECT MAX(idservicio) AS servicios FROM servicio s, alarma a "
                        + "WHERE s.idAlarma = a.idAlarma AND s.idAlarma = '"+ alarmaCliente.idAlarma +"' AND s.estado = 'PENDIENTE' AND (a.estadoActual = 'A' OR a.estadoActual = 'R')");
                if (rs.next() && rs.getString("servicios") != null)
                    st.executeUpdate("UPDATE servicio SET motivo = CONCAT('"+ contenido.toString() +"', motivo) WHERE idservicio = '"+ rs.getString("servicios") +"'");
                else
                    st.executeUpdate("INSERT INTO servicio (idAlarma, tipo, solicitante, motivo) VALUES ('"+ alarmaCliente.idAlarma +"', 'REVISIÓN', 'SISTEMA', '"+ contenido.toString() +"')");
                
                // Informar a los clientes
                if (msjCliente.length() > 0)
                {
                    String fullMsj = "Hemos recibido eventos de mantenimiento de su sistema de alarma que deben ser atendidos con la menor brevedad prosible, los eventos son:<br><br>";
                    fullMsj += msjCliente.toString() + "<br>";
                    enviarMensaje(fullMsj, usuario, "[URGENTE] Informe de revisión técnica", idOperacion);                
                }
                st.close();
            }
            catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        liberarEventos(evtMtto, idOperacion);
    }
    //</editor-fold>
    
    public void verificarOtrosEventos()
    //<editor-fold defaultstate="collapsed">
    {
        for (Evento evt : otros)
        {
            if (evt.evento.equals("COMUNICACIÓN GPRS"))
            {
                switch (evt.tipoEvento) {
                    case "EVENTO":
                        try {
                            Statement nst = (Statement) conn.createStatement();
                            nst.executeUpdate("UPDATE alarma SET modo = 'OFF' WHERE idAlarma = '"+ idAlarma +"'");
                            nst.close();
                        } catch (SQLException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        }   break;
                    case "RESTAURE":
                        try {
                            Statement nst = (Statement) conn.createStatement();
                            nst.executeUpdate("UPDATE alarma SET modo = 'ON' WHERE idAlarma = '"+ idAlarma +"'");
                            nst.close();
                        } catch (SQLException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        }   break;
                }
            }
        }
        String idOperacion = UUID.randomUUID().toString();
        liberarEventos(otros, idOperacion);
    }
    //</editor-fold>
    
    public void posicionarGPS(EventoGPS gps)
    //<editor-fold defaultstate="collapsed" desc="Geolocalizacion con dirección">
    {
        String idOperacion = UUID.randomUUID().toString();
        try {
            String[] addFormatted = new String[2];
            String dirActual = "", barrio = "", ciudad = "";
            Statement st = (Statement) conn.createStatement();
            String url = "http://maps.googleapis.com/maps/api/geocode/json?latlng=";
            String latlng = gps.lat + "," + gps.lon;
            
            HttpClient cliente = new HttpClient();
            HttpMethod metodo = new GetMethod(url + latlng + "&sensor=true");
            cliente.executeMethod(metodo);
            
            JSONObject obj = new JSONObject(metodo.getResponseBodyAsString());
            if (obj.getString("status").equals("OK"))
            {
                JSONObject res = obj.getJSONArray("results").getJSONObject(0);
                if (res.getString("formatted_address").contains(","))
                    dirActual = res.getString("formatted_address").substring(0, res.getString("formatted_address").indexOf(","));
                else
                    dirActual = res.getString("formatted_address");
                
                JSONArray jTipo = res.getJSONArray("types");
                if (jTipo.length() > 0 && (jTipo.get(0).toString().equals("street_address") || jTipo.get(0).toString().equals("route")))
                {
                    if (dirActual.contains("-"))
                        dirActual = dirActual.substring(0, dirActual.indexOf("-"));
                    else if (!dirActual.contains("-"))
                        dirActual = dirActual.replace(" a ", " hasta ");
                    else  
                    {
                        StringBuilder sb = new StringBuilder(dirActual);
                        if (sb.lastIndexOf(" ") > -1)
                            dirActual = sb.replace(sb.lastIndexOf(" "), sb.lastIndexOf(" ")+1, " # ").toString();
                    }

                    if (dirActual.contains("#")) // Contiene la estructura necesaria
                        addFormatted = dirActual.split("#");
                    else 
                        addFormatted = new String[] {dirActual, ""};
                                       
                    
                    // Complementar direcciones
                    JSONArray addComp = obj.getJSONArray("results").getJSONObject(1).getJSONArray("address_components");
                    for (int i=0; i < addComp.length(); i++)
                    {
                        String tipo = addComp.getJSONObject(i).getJSONArray("types").getString(0);
                        if (tipo.contains("sublocality_level_1") || tipo.contains("neighborhood")) // Barrio
                            barrio = addComp.getJSONObject(i).getString("short_name");
                        
                        else if (tipo.contains("administrative_area_level_2"))
                            ciudad = addComp.getJSONObject(i).getString("short_name");
                        
                        else if (tipo.contains("administrative_area_level_1"))
                            ciudad = addComp.getJSONObject(i).getString("short_name") + " - " + ciudad;
                        
                        else if (tipo.contains("route"))
                        {
                            String shortFormatted = addComp.getJSONObject(i).getString("short_name");
                            if (!shortFormatted.contains(" "))
                                shortFormatted = shortFormatted.replace("C", "Calle ").replace("K", "Carrera ");

                            if (!shortFormatted.equals(addFormatted[0].trim()) && !tipo.equals("route"))
                                addFormatted[0] = addFormatted[0].trim() + " " + shortFormatted;

                            // Complemeneto de la 2da parte de la dirección
                            if (addFormatted[0].contains("Calle") && !addFormatted[1].equals(""))
                                addFormatted[1] = "Carrera " + addFormatted[1].trim();
                            else if (addFormatted[0].contains("Carrera") && !addFormatted[1].equals(""))
                                addFormatted[1] = "Calle " + addFormatted[1].trim();
                        }                        
                    }                    
                }
                
                if (addFormatted[1] != null && !addFormatted[1].equals(""))
                    dirActual = addFormatted[0].trim() + " con " + addFormatted[1].trim();
                else
                    dirActual = addFormatted[0];
                if (dirActual != null && !dirActual.equals(""))
                {
                    dirActual = dirActual.replace("'a", "ñ").replace("'", "").replace("Ã­", "í");
                    barrio = barrio.replace("'a", "ñ").replace("'", "");
                    ciudad = ciudad.replace("'a", "ñ").replace("'", "").toUpperCase(new Locale("ES"));
                    st.executeUpdate("UPDATE alarma SET direccion = '"+ dirActual +"', barrio = '"+ barrio +"', ciudad = '"+ ciudad +"' WHERE idAlarma = '"+ idAlarma +"'");
                } else
                    System.out.println(idAlarma + " " + gps.lat + "," + gps.lon);
            }
                    
            //<editor-fold defaultstate="collapsed" desc="Deprecated">
            /*
            if (false)
            {
            InputStream resXML = jsonResponse;
            
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setIgnoringComments(false);
            dbf.setIgnoringElementContentWhitespace(true);
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            
            Document doc = (Document) db.parse(resXML);
            doc.getDocumentElement().normalize();
            
            //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
            NodeList estado = doc.getElementsByTagName("status");
            NodeList nList = doc.getElementsByTagName("result");
            
            if ( estado.item( estado.getLength()-1 ).getTextContent().equals("OK") )
            {
            for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
            
            Element eElement = (Element) nNode;
            String tipo = eElement.getElementsByTagName("type").item(0).getTextContent();
            if (tipo.equals("street_address") || tipo.equals("route"))
            {
            dirActual = eElement.getElementsByTagName("formatted_address").item(0).getTextContent();
            dirActual = dirActual.substring(0, dirActual.indexOf(",")); //.replaceAll("#", "Con");
            
            if (tipo.equals("street_address"))
            {
            if (dirActual.indexOf("-") > -1)
            dirActual = dirActual.substring(0, dirActual.indexOf("-"));
            else if (dirActual.indexOf("-") == -1)
            dirActual = dirActual.replace(" a ", " hasta ");
            }
            else
            {
            StringBuilder sb = new StringBuilder(dirActual);
            if (sb.lastIndexOf(" ") > -1)
            dirActual = sb.replace(sb.lastIndexOf(" "), sb.lastIndexOf(" ")+1, " # ").toString();
            }
            
            if (dirActual.indexOf("#") > -1) // Contiene la estructura necesaria
            addFormatted = dirActual.split("#");
            
            else
            addFormatted = new String[] {dirActual, ""};
            
            //System.out.println("Dirección : " + dirActual);
            
            NodeList subList = doc.getElementsByTagName("address_component");
            OUTER:
            for (int i = 0; i < subList.getLength(); i++) {
            Node subNodo = subList.item(i);
            Element n = (Element) subNodo;
            switch (n.getElementsByTagName("type").item(0).getTextContent())
            {
            case "route":
            // Complemento de la dirección
            String shortFormatted = n.getElementsByTagName("short_name").item(0).getTextContent();
            if (shortFormatted.indexOf(" ") == -1)
            shortFormatted = shortFormatted.replace("C", "Calle ").replace("K", "Carrera ");
            
            if (!shortFormatted.equals(addFormatted[0].trim()) && !tipo.equals("route"))
            addFormatted[0] = addFormatted[0].trim() + " " + shortFormatted;
            
            // Complemeneto de la 2da parte de la dirección
            if (addFormatted[0].contains("Calle") && !addFormatted[1].equals(""))
            addFormatted[1] = "Carrera " + addFormatted[1].trim();
            else if (addFormatted[0].contains("Carrera") && !addFormatted[1].equals(""))
            addFormatted[1] = "Calle " + addFormatted[1].trim();
            
            break;
            
            case "sublocality":
            //System.out.println("Barrio : " + n.getElementsByTagName("long_name").item(0).getTextContent());
            barrio = n.getElementsByTagName("long_name").item(0).getTextContent();
            break;
            
            case "locality":
            ciudad = n.getElementsByTagName("long_name").item(0).getTextContent();
            break;
            case "administrative_area_level_1":
            ciudad = n.getElementsByTagName("long_name").item(0).getTextContent() + " - " + ciudad;
            break OUTER;
            }
            }
            break;
            }
            }
            }
            }
            
            
            if (nList.getLength() > 0)
            {
            if (addFormatted[1] != null && !addFormatted[1].equals(""))
            dirActual = addFormatted[0].trim() + " con " + addFormatted[1].trim();
            else
            dirActual = addFormatted[0];
            
            if (dirActual != null && !dirActual.equals(""))
            {
            dirActual = dirActual.replace("'a", "ñ").replace("'", "");
            barrio = barrio.replace("'a", "ñ").replace("'", "");
            ciudad = ciudad.replace("'a", "ñ").replace("'", "").toUpperCase(new Locale("ES"));
            st.executeUpdate("UPDATE alarma SET direccion = '"+ dirActual +"', barrio = '"+ barrio +"', ciudad = '"+ ciudad +"' WHERE idAlarma = '"+ idAlarma +"'");
            } else
            System.out.println(gps.lat + "," + gps.lon);
            }}*/
//</editor-fold>
            st.close();
            
        } catch (HttpException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
    //</editor-fold>
    
    private String fromStream(BufferedReader in) throws IOException
    {
        String line;
        StringBuilder out = new StringBuilder();
        while ((line = in.readLine()) != null)
        {
            out.append(line);
        }
        return out.toString();
    }
    
    public static String fromStream(InputStream in) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
        StringBuilder out = new StringBuilder();
        String newLine = System.getProperty("line.separator");
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append(newLine);
        }
        return out.toString();
    }
    
    // Funciones PARA GPS
    private void verificarAlarmaGPS(Boolean panico)   
    //<editor-fold defaultstate="collapsed" desc="....">
    {
        String idOperacion = UUID.randomUUID().toString();
        if (!alarmaGPS.isEmpty())
            posicionarGPS(alarmaGPS.get(alarmaGPS.size()-1));
        alarmaCliente.actualizar();
        String reply;
        
        StringBuilder mensaje = new StringBuilder();
        if (panico)
        {
            mensaje.append("El sistema de monitoreo del vehículo ").append(alarmaCliente.descripcion)
                    .append(" se ha activado en modo de asistencia urgente.");
        } else
        {
            mensaje.append("Su sistema de monitoreo vehicular se ha activado y nos reporta ").append(alarmaGPS.size());
            
            if (alarmaGPS.size() > 1)
                mensaje.append(" eventos de alarma");
            else
                mensaje.append(" evento de alarma");
        }
        
        for(EventoGPS evt : alarmaGPS)
        {
            DateFormat dateFormat = new SimpleDateFormat("h:m aaa");
            mensaje.append("\nActivación a las ").append(dateFormat.format(evt.fecha)).append(" de ");
            mensaje.append(evt.evento.toUpperCase());
        }
        
        //<editor-fold defaultstate="collapsed" desc="Notificaciones via gTalk">
        StringBuilder msjGTalk = new StringBuilder();
        msjGTalk.append(mensaje.toString());
        msjGTalk.append(". la última posición conocida es ").append(alarmaCliente.direccion).append(", Barrio ").append(alarmaCliente.barrio).append("\n\n");
        
        if ( !alarmaGPS.get(alarmaGPS.size()-1).lat.equals("0.0") && !alarmaGPS.get(alarmaGPS.size()-1).lon.equals("0.0"))
        {
            msjGTalk.append("https://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=").
                    append(alarmaGPS.get(alarmaGPS.size()-1).lat).append(",").
                    append(alarmaGPS.get(alarmaGPS.size()-1).lon).append("\n\n");
        }
        //</editor-fold>
        
        //IDENTIFICAR SI ES UN PÁNICO.
        if (panico)
        {
            // SOLO SE NOTIFICA VÍA EMAIL O GTALK TODO:
            //msjGTalk.append("En estos momentos un supervisor se esta dirigiendo hacia él vehículo, le estaré informando cualquier otra novedad.");
            enviarMensaje(msjGTalk.toString(), usuario, "SISTEMA DE MODO DE ASISTENCIA", idOperacion);
            insertarReaccion(alarmaGPS, "8 47", alarmaCliente, "PÁNICO SILENCIOSO.");
            liberarEventosGPS(alarmaGPS, "R", idOperacion);
            
            if (alarmaCliente.estado.equals("APAGADO"))
                solicitarReporte(); // lanzar llamada de encendido
            
        } else {
            
            // VERIFICAR QUE NO ESTE EN MODO RECUPERACIÓN
            switch (alarmaCliente.modo)
            {
                case "R":
                    msjGTalk.append("Su vehículo esta en modo RECUPERACIÓN");
                    enviarMensaje(msjGTalk.toString(), usuario, "INFORME DE EVENTO EN PROGRESO", idOperacion);
                    insertarReaccion(alarmaGPS, "8 47", alarmaCliente, "NUEVO EVENTO DE ALARMA");
                    liberarEventosGPS(alarmaGPS, "", idOperacion);
                    break;
                case "O":
                case "N":
                case "P":
                    if ( !alarmaCliente.telContacto.equals("") && (alarmaCliente.telContacto.length() >= 7 && alarmaCliente.telContacto.length() <= 10) )                    
                    {
                        enviarMensaje(msjGTalk.toString(), usuario, "SISTEMA EN MODO DE ASISTENCIA", idOperacion);
                        // <editor-fold defaultstate="collapsed" desc="Notificacion vía Telefónica">
                        String mensajeFinal = "Le llamo de Seguridad Montgomery. " + mensaje.toString().replaceAll(":", " y ");
                        mensajeFinal = mensajeFinal.replaceAll("\n\n", "");
                        mensajeFinal = mensajeFinal.replaceAll("\n", ". ");
                        
                        try {
                            // Llamar al directo responsable del vehiculo
                            mensajeFinal = "Apreciado cliente, " + mensajeFinal;
                            mensajeFinal += ". Por favor digite su clave de seguridad.";
                            
                            String guii;
                            int intentosCom = 3, i;
                            Statement st = (Statement) conn.createStatement();
                            for (i=0; i < intentosCom; i++)
                            {
                                //<editor-fold desc="Codigo para informar" defaultstate="collapsed">
                                try {
                                    guii = UUID.randomUUID().toString();
                                    
                                    insertarBitacora(idAlarma, "Telefónico", mensajeFinal, "RESPONSABLE <"+ alarmaCliente.telContacto + ">", guii, false, idOperacion);
                                    //String response = informar(alarmaCliente.telContacto, guii, "VGENERAL", alarmaCliente.extension, true);
                                    String response = informar(alarmaCliente.telContacto, guii, "ALARMA", alarmaCliente.extension, true);
                                    
                                    String statusVitacora = "INFORMANDO";
                                    if (response.contains("Error"))
                                    {
                                        st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                                        statusVitacora = "MARCACIÓN ERRADA";
                                    }
                                    else
                                    {   // La llamada fue efectiva, contestaron.
                                        // Verificar el estado de la contestación si ya se ha actualizado
                                        String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                                        ResultSet resReaccion;
                                        int contadorNominal = 0;
                                        
                                        while(statusVitacora.equals("INFORMANDO"))
                                        {
                                            try {
                                                resReaccion = st.executeQuery(sSQL);
                                                if (resReaccion.first())
                                                    statusVitacora = resReaccion.getString("estado");
                                                
                                                contadorNominal++;
                                                if (contadorNominal > 50)
                                                    statusVitacora = "MARCACIÓN ERRADA";
                                                
                                                Thread.sleep(5000);
                                            } catch (InterruptedException ex) {
                                                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                            }
                                        }
                                    }
                                    
                                    System.out.println(statusVitacora);
                                    if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("NO CONTESTA")){
                                        
                                        reply = "N";
                                        if (requiereReaccion(statusVitacora, "ALARMA")) // TRUE usuario necesita reacción
                                        {
                                            // Enviar reacción
                                            reply = "R";
                                            System.out.println("Enviando reaccion...");
                                            if (usuario.size() > 0)
                                            {
                                                StringBuilder strMensaje = new StringBuilder();
                                                strMensaje.append("Hemos confirmado un evento de RECUPERACIÓN para el vehículo ");
                                                strMensaje.append(alarmaCliente.descripcion).append(", Con evento generado desde ").append(alarmaCliente.direccion);
                                                strMensaje.append(", ").append(alarmaCliente.ciudad[1].trim()).append(", ").append(alarmaCliente.ciudad[0]);
                                                strMensaje.append(". Le estaré informando de cualquier otra novedad.");
                                                enviarMensaje(strMensaje.toString(), usuario, "INFORME DE REACCIÓN EN PROGRESO.", idOperacion);
                                            }
                                            insertarReaccion(alarmaGPS, "8 47", alarmaCliente, "ALARMA: " + statusVitacora);
                                        }
                                        
                                        liberarEventosGPS(alarmaGPS, reply, idOperacion);
                                        break;
                                    }
                                    
                                } catch (IOException | AuthenticationFailedException ex) {
                                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                //</editor-fold>
                            }
                            
                            if (i == intentosCom) {
                                System.out.println("No contesto || Marcarcion errada > INFORMAR AL ACUDIENTE O ACUDIENTES.");
                                
                                // INFORMAR A LOS ACUDIENTES
                                if (usuario.size() > 0)
                                {
                                    StringBuilder strMensaje = new StringBuilder();
                                    strMensaje.append("Hemos tratado de confirmar evento(s) de ALARMA al teléfono de contacto ").
                                            append(alarmaCliente.telContacto).
                                            append(" pero ha sido imposible o no contesta, en estos momentos estamos enviando un supervisor hacia el vehículo ");
                                    strMensaje.append(alarmaCliente.descripcion).append(", Con evento generado desde ").append(alarmaCliente.direccion);
                                    strMensaje.append(", ").append(alarmaCliente.ciudad[1].trim()).append(", ").append(alarmaCliente.ciudad[0]);
                                    strMensaje.append(". Le estaré informando de cualquier otra novedad.");
                                    if (!alarmaGPS.isEmpty())
                                    {
                                        strMensaje.append("\n\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=").
                                            append(alarmaGPS.get(alarmaGPS.size()-1).lat).append(",").
                                            append(alarmaGPS.get(alarmaGPS.size()-1).lon);
                                    }
                                    enviarMensaje(strMensaje.toString(), usuario, "INFORME DE REACCIÓN EN PROGRESO.", idOperacion);
                                }
                                insertarReaccion(alarmaGPS, "8 47", alarmaCliente, "ALARMA, NO CONTESTA");
                                liberarEventosGPS(alarmaGPS, "R", idOperacion);
                            }
                            
                            st.close();
                            
                        } catch (SQLException ex) {
                            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    //</editor-fold>
                     else // no tiene teléfono, o no es válido.
                        liberarEventosGPS(alarmaGPS, alarmaCliente.modo, idOperacion);
                    break;
            }
        }
    }
    //</editor-fold>
        
    private void verificarAperturaGPS()
    //<editor-fold defaultstate="collapsed" desc="...">
    {
        // No se posiciona
        String idOperacion = UUID.randomUUID().toString();        
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Hemos registrado ");
        if (desparqueo.size() > 1)
            mensaje.append(desparqueo.size()).append(" desactivaciones ");
        else
            mensaje.append("una desactivación ");
        mensaje.append(" del sistema de Parqueo Vehicular.");
        
        DateFormat dateFormat = new SimpleDateFormat("h:m aaa");
        for (EventoGPS evt: desparqueo)
        {
            mensaje.append("\nDesactivación a las ").append(dateFormat.format(evt.fecha));
        }
        
        //<editor-fold defaultstate="collapsed" desc="Notificaciones via gTalk">
        StringBuilder msjGTalk = new StringBuilder();
        msjGTalk.append(mensaje.toString());
        msjGTalk.append(". la última posición conocida es ").append(alarmaCliente.direccion).append(", Barrio ").append(alarmaCliente.barrio);
       /* msjGTalk.append("\n\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=").
                append(desparqueo.get(desparqueo.size()-1).lat).append(",").
                append(desparqueo.get(desparqueo.size()-1).lon).append("\n\n");*/
        enviarMensaje(msjGTalk.toString(), usuario, "REPORTE DE DESACTIVACIÓN", idOperacion);
        //</editor-fold>
        
        // <editor-fold defaultstate="collapsed" desc="Notificacion vía Telefónica">
        String mensajeFinal = "Le llamo de Seguridad Montgomery. " + mensaje.toString().replaceAll(":", " y ");
        mensajeFinal = mensajeFinal.replaceAll("\n\n", "");
        mensajeFinal = mensajeFinal.replaceAll("\n", ". ");
        
        if ( !alarmaCliente.telContacto.equals("") && (alarmaCliente.telContacto.length() >= 7 && alarmaCliente.telContacto.length() <= 10) )
        {
            try {
                // Llamar al directo responsable del vehiculo
                //<editor-fold defaultstate="collapsed" desc="Procedimiento para llamar al propietario">
                mensajeFinal = "Apreciado cliente, " + mensajeFinal;
                mensajeFinal += ". Por favor digite su clave de seguridad.";
                
                String guii;
                int intentosCom = 3, i;
                Statement st = (Statement) conn.createStatement();
                for (i=0; i < intentosCom; i++)
                {
                    //<editor-fold desc="Codigo para informar" defaultstate="collapsed">
                    try {
                        
                        guii = UUID.randomUUID().toString();                        
                        insertarBitacora(idAlarma, "Telefónico", mensajeFinal, "RESPONSABLE <"+ alarmaCliente.telContacto + ">", guii, false, idOperacion);
                        String response = informar(alarmaCliente.telContacto, guii, "ALARMA", alarmaCliente.extension, true);
                        
                        String statusVitacora = "INFORMANDO";
                        if (response.contains("Error"))
                        {
                            st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                            statusVitacora = "MARCACIÓN ERRADA";
                        }
                        else
                        {   // La llamada fue efectiva, contestaron.
                            // Verificar el estado de la contestación si ya se ha actualizado
                            String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                            ResultSet resReaccion;
                            int contadorNominal = 0;
                            
                            while(statusVitacora.equals("INFORMANDO"))
                            {
                                try {
                                    resReaccion = st.executeQuery(sSQL);
                                    if (resReaccion.first())
                                        statusVitacora = resReaccion.getString("estado");
                                    
                                    contadorNominal++;
                                    if (contadorNominal > 50)
                                        statusVitacora = "MARCACIÓN ERRADA";
                                    
                                    Thread.sleep(5000);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                        
                        System.out.println(statusVitacora);
                        if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("NO CONTESTA")){
                            
                            String reply = "N"; // Suspende modo parqueo
                            if (requiereReaccion(statusVitacora, "ALARMA")) // TRUE usuario necesita reacción
                            {
                                // Enviar reacción
                                reply = "R";
                                System.out.println("Enviando reaccion...");
                                insertarReaccion(desparqueo, "8 47", alarmaCliente, "ALARMA EN DESACTIVACIÓN: " + statusVitacora);
                                if (usuario.size() > 0)
                                {
                                    StringBuilder strMensaje = new StringBuilder();
                                    strMensaje.append("Hemos confirmado un evento de RECUPERACIÓN para el vehículo ");
                                    strMensaje.append(alarmaCliente.descripcion).append(", Con evento generado desde ").append(alarmaCliente.direccion);
                                    strMensaje.append(", ").append(alarmaCliente.ciudad[1].trim()).append(", ").append(alarmaCliente.ciudad[0]);
                                    strMensaje.append(". Le estaré informando de cualquier otra novedad.");
                                    enviarMensaje(strMensaje.toString(), usuario, "INFORME DE REACCIÓN EN PROGRESO.", idOperacion);
                                }
                            }
                            
                            liberarEventosGPS(desparqueo, reply, idOperacion);
                            break;
                        }
                        
                    } catch (IOException | AuthenticationFailedException ex) {
                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    //</editor-fold>
                }
                
                //<editor-fold defaultstate="collapsed" desc="No contesto || Marcarcion errada > INFORMAR AL ACUDIENTE O ACUDIENTES.">
                if (i == intentosCom)
                {
                    System.out.println("No contesto || Marcarcion errada > INFORMAR AL ACUDIENTE O ACUDIENTES.");
                    
                    // INFORMAR A LOS ACUDIENTES
                    if (usuario.size() > 0)
                    {
                        StringBuilder strMensaje = new StringBuilder();
                        strMensaje.append("Hemos tratado de confirmar evento(s) de ALARMA al teléfono de contacto ").
                                append(alarmaCliente.telContacto).
                                append(" pero ha sido imposible o no contesta, en estos momentos estamos enviando un supervisor hacia el vehículo ");
                        strMensaje.append(alarmaCliente.descripcion).append(", Con evento generado desde ").append(alarmaCliente.direccion);
                        strMensaje.append(", ").append(alarmaCliente.ciudad[1].trim()).append(", ").append(alarmaCliente.ciudad[0]);
                        strMensaje.append(". Le estaré informando de cualquier otra novedad.");
                        enviarMensaje(strMensaje.toString(), usuario, "INFORME DE REACCIÓN EN PROGRESO.", idOperacion);
                    }
                    insertarReaccion(desparqueo, "8 47", alarmaCliente, "DESACTIVACIÓN, NO CONTESTA");
                    liberarEventosGPS(desparqueo, "R", idOperacion);
                }
                //</editor-fold>
                
                st.close();
                
                //</editor-fold>
            } catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else // El cliente no tiene donde confirmar los eventos.... dismiss o enviar reaccion
        {
            // TODO: NOTIFICAR A LOS ACUDIENTES
            insertarReaccion(desparqueo, "8 47", alarmaCliente, "EL USUARIO NO POSEE TELÉFONO DE CONTACTO, NO ES POSIBLE CONFIRMAR. ESTADO: " + alarmaCliente.estado);
            liberarEventosGPS(desparqueo, "N", idOperacion);
        }
        //</editor-fold>
    }
    //</editor-fold>
    
    private void verificarAlarmaParqueo()
    //<editor-fold defaultstate="collapsed" desc="...">
    {
        DateFormat dateFormat = new SimpleDateFormat("h:m aaa");
        String idOperacion = UUID.randomUUID().toString();
        if (!parqueoGPS.isEmpty())
            posicionarGPS(parqueoGPS.get(parqueoGPS.size()-1));
        alarmaCliente.actualizar();
        
        StringBuilder mensaje = new StringBuilder();
        mensaje.append("Su sistema de monitoreo vehicular se ha activado y nos reporta ").append(parqueoGPS.size());
        if (parqueoGPS.size() > 1)
            mensaje.append(" eventos de alarma");
        else
            mensaje.append(" evento de alarma");
                
        // Analisis de los evetos y preparación de mensaje
        for (EventoGPS gps: parqueoGPS)
        {
            mensaje.append("\nActivación a las ").append(dateFormat.format(gps.fecha)).append(" de ");
            if (gps.idGPS.equals("512"))
                mensaje.append("ENCENDIDO ").append(gps.evento.toUpperCase());
            else
                mensaje.append(gps.tipoEvento.toUpperCase()).append(" ").append(gps.evento.toUpperCase());

            if (!gps.zona.equals("000"))
                mensaje.append(". La activación es en la Zona ").append(Integer.parseInt(gps.zona)).append(" que corresponde a ").append(gps.desc);
        }

        //<editor-fold defaultstate="collapsed" desc="Notificaciones via gTalk">
        StringBuilder msjGTalk = new StringBuilder();
        msjGTalk.append(mensaje.toString());
        msjGTalk.append(". la última posición conocida es ").append(alarmaCliente.direccion).append(", Barrio ").append(alarmaCliente.barrio);
        msjGTalk.append("\n\nhttps://maps.google.com/maps?f=q&source=s_q&hl=es&geocode=&q=").
                append(parqueoGPS.get(parqueoGPS.size()-1).lat).append(",").
                append(parqueoGPS.get(parqueoGPS.size()-1).lon).append("\n\n");
        enviarMensaje(msjGTalk.toString(), usuario, "REPORTE DE ALARMA EN PROGRESO", idOperacion);
        //</editor-fold>
        
        // <editor-fold defaultstate="collapsed" desc="Notificacion vía Telefónica">            
        String mensajeFinal = "Le llamo de Seguridad Montgomery. " + mensaje.toString().replaceAll(":", " y ");
        mensajeFinal = mensajeFinal.replaceAll("\n\n", "");
        mensajeFinal = mensajeFinal.replaceAll("\n", ". ");

        if ( !alarmaCliente.telContacto.equals("") && (alarmaCliente.telContacto.length() >= 7 && alarmaCliente.telContacto.length() <= 10) )
        {
            try {
                // Llamar al directo responsable del vehiculo
                //<editor-fold defaultstate="collapsed" desc="Procedimiento para llamar al propietario">
                mensajeFinal = "Apreciado cliente, " + mensajeFinal;
                mensajeFinal += ". Por favor digite su clave de seguridad.";

                String guii;
                int intentosCom = 3, i;
                Statement st = (Statement) conn.createStatement();
                for (i=0; i < intentosCom; i++)
                {
                    //<editor-fold desc="Codigo para informar" defaultstate="collapsed">
                    try {
                        guii = UUID.randomUUID().toString();

                        insertarBitacora(idAlarma, "Telefónico", mensajeFinal, "RESPONSABLE <"+ alarmaCliente.telContacto + ">", guii, false, idOperacion);
                        String response = informar(alarmaCliente.telContacto, guii, "ALARMA", alarmaCliente.extension, true);

                        String statusVitacora = "INFORMANDO";
                        if (response.contains("Error"))
                        {
                            st.executeUpdate("UPDATE vitacora SET estado = 'MARCACIÓN ERRADA' WHERE GUID = '"+ guii +"'");
                            statusVitacora = "MARCACIÓN ERRADA";
                        }
                        else
                        {   // La llamada fue efectiva, contestaron.
                            // Verificar el estado de la contestación si ya se ha actualizado
                            String sSQL = "SELECT estado FROM vitacora WHERE GUID = '" + guii +"'";
                            ResultSet resReaccion;
                            int contadorNominal = 0;

                            while(statusVitacora.equals("INFORMANDO"))
                            {
                                try {
                                    resReaccion = st.executeQuery(sSQL);
                                    if (resReaccion.first())
                                        statusVitacora = resReaccion.getString("estado");

                                    contadorNominal++;
                                    if (contadorNominal > 50)
                                        statusVitacora = "MARCACIÓN ERRADA";

                                    Thread.sleep(5000);
                                } catch (InterruptedException ex) {
                                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }

                        System.out.println(statusVitacora);
                        if (!statusVitacora.equals("MARCACIÓN ERRADA") && !statusVitacora.equals("NO CONTESTA")){

                            String reply = "N";
                            if (requiereReaccion(statusVitacora, "ALARMA")) // TRUE usuario necesita reacción
                            {
                                // Enviar reacción
                                reply = "R";
                                System.out.println("Enviando reaccion...");
                                if (usuario.size() > 0)
                                {
                                    StringBuilder strMensaje = new StringBuilder();
                                    strMensaje.append("Hemos confirmado un evento de RECUPERACIÓN para el vehículo ");
                                    strMensaje.append(alarmaCliente.descripcion).append(", Con evento generado desde ").append(alarmaCliente.direccion);
                                    strMensaje.append(", ").append(alarmaCliente.ciudad[1].trim()).append(", ").append(alarmaCliente.ciudad[0]);
                                    strMensaje.append(". Le estaré informando de cualquier otra novedad.");
                                    enviarMensaje(strMensaje.toString(), usuario, "INFORME DE REACCIÓN EN PROGRESO.", idOperacion);
                                }
                                insertarReaccion(parqueoGPS, "8 47", alarmaCliente, "ALARMA: " + statusVitacora);
                            }
                            liberarEventosGPS(parqueoGPS, reply, idOperacion);
                            break;
                        }

                    } catch (IOException | AuthenticationFailedException ex) {
                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    //</editor-fold>
                }

                //<editor-fold defaultstate="collapsed" desc="No contesto || Marcarcion errada > INFORMAR AL ACUDIENTE O ACUDIENTES.">
                if (i == intentosCom) {
                    System.out.println("No contesto || Marcarcion errada > INFORMAR AL ACUDIENTE O ACUDIENTES.");
                    
                    // INFORMAR A LOS ACUDIENTES
                    if (usuario.size() > 0)
                    {
                        StringBuilder strMensaje = new StringBuilder();
                        strMensaje.append("Hemos tratado de confirmar evento(s) de ALARMA al teléfono de contacto ").
                                append(alarmaCliente.telContacto).
                                append(" pero ha sido imposible o no contesta, en estos momentos estamos enviando un supervisor hacia el vehículo ");
                        strMensaje.append(alarmaCliente.descripcion).append(", Con evento generado desde ").append(alarmaCliente.direccion);
                        strMensaje.append(", ").append(alarmaCliente.ciudad[1].trim()).append(", ").append(alarmaCliente.ciudad[0]);
                        strMensaje.append(". Le estaré informando de cualquier otra novedad.");
                        enviarMensaje(strMensaje.toString(), usuario, "INFORME DE REACCIÓN EN PROGRESO.", idOperacion);
                    }
                    insertarReaccion(parqueoGPS, "8 47", alarmaCliente, "ALARMA, NO CONTESTA");
                    liberarEventosGPS(parqueoGPS, "N", idOperacion);
                }
                //</editor-fold>
                
                st.close();
                
            //</editor-fold>
            } catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        else // El cliente no tiene donde confirmar los eventos.... dismiss o enviar reaccion
        {
             // TODO: NOTIFICAR A LOS ACUDIENTES
            insertarReaccion(parqueoGPS, "8 47", alarmaCliente, "EL USUARIO NO POSEE TELÉFONO DE CONTACTO, NO ES POSIBLE CONFIRMAR. ESTADO: " + alarmaCliente.estado);
            liberarEventosGPS(parqueoGPS, "R", idOperacion);
        }
        //</editor-fold>

    }//</editor-fold>
    
    private void verificarEstado()
    //<editor-fold defaultstate="collapsed" desc="...">
    {
        try {
            String modo = alarmaCliente.modo, subject = "";
            Calendar c = Calendar.getInstance();
            Calendar am = Calendar.getInstance();
            Calendar pm = Calendar.getInstance();
            StringBuilder sb = new StringBuilder();
            String idOperacion = UUID.randomUUID().toString();
            Statement st = (Statement) conn.createStatement();
            
            for (EventoGPS estado: estadoGPS)
            {
                if (estado.tipoEvento.equals("EVENTO") && estado.evento.contains("ENCENDIDO"))
                //<editor-fold defaultstate="collapsed" desc="Encendido">
                {
                    subject = "Reporte de Estado";
                    sb.append("El vehículo ").append(alarmaCliente.descripcion).append(" se ha encendido; ultima posición conocída es: ");
                    sb.append(alarmaCliente.direccion);
                    
                    // Verificar horarios de operación normal.
                    String sSQL = "SELECT * FROM horario WHERE idAlarma = '"+ alarmaCliente.idAlarma +"' AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'";
                    ResultSet res = st.executeQuery(sSQL);
                    if (res.first())
                    {
                        String[] timeAM = res.getString("AM").split(":");
                        String[] timePM = res.getString("PM").split(":");
                        
                        am.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeAM[0]));
                        am.set(Calendar.MINUTE, Integer.parseInt(timeAM[1]));
                        am.set(Calendar.SECOND, Integer.parseInt(timeAM[2]));
                        
                        pm.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timePM[0]));
                        pm.set(Calendar.MINUTE, Integer.parseInt(timePM[1]));
                        pm.set(Calendar.SECOND, Integer.parseInt(timePM[2]));
                        
                        if ( c.getTimeInMillis() < am.getTimeInMillis() || c.getTimeInMillis() > pm.getTimeInMillis() )
                        {
                            modo = "R";
                            sb.append("\n\nENCENDIDO FUERA DE HORARIO");
                            
                            if (c.getTimeInMillis() > pm.getTimeInMillis()) // Si la apertura es en la tarde o mayor al horario actual,
                            {
                                pm = c;
                                pm.add(Calendar.MINUTE, 10); // Se le dán 10 min al cierre
                                st.executeUpdate("UPDATE horario SET PM = '"+ pm.get(Calendar.HOUR_OF_DAY) +":"+ pm.get(Calendar.MINUTE) +":"+ pm.get(Calendar.SECOND) +"' WHERE "
                                        + "idAlarma = '"+ alarmaCliente.idAlarma +"' AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'");
                            }
                        }
                        else
                            sb.append("\n\n Encendido en Horario Habitual");
                    }
                }
                //</editor-fold>
                
                else if (estado.tipoEvento.equals("RESTAURE") && estado.evento.contains("ENCENDIDO"))
                //<editor-fold defaultstate="collapsed" desc="Apagado">
                {
                    subject = "Reporte de Estado";
                    sb.append("El vehículo ").append(alarmaCliente.descripcion).append(" se ha Apagado; ultima posición conocída es: ");
                    sb.append(alarmaCliente.direccion);
                }
                //</editor-fold>
                
                else if (estado.tipoEvento.equals("EVENTO") && estado.evento.contains("TARDE"))
                //<editor-fold defaultstate="collapsed" desc="Tarde para apagar">
                {
                    // Notificar.
                    sb.append("El vehículo ").append(alarmaCliente.descripcion).append(" aun se encuentra Encendido; ultima posición conocída es: ");
                    sb.append(alarmaCliente.direccion).append("\n\n");
                    sb.append("Si el vehículo no se apaga en 10 minutos, le volveré a informar.");
                    Calendar tActual = Calendar.getInstance();
                    tActual.add(Calendar.MINUTE, 10);
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    
                    st.executeUpdate("UPDATE horario SET PM = '"+ f.format(tActual.getTime()) +"' WHERE idAlarma = '"+ idAlarma +"'");
                    subject = "REPORTE DE TARDE PARA APAGAR";
                } 
                else if (estado.tipoEvento.equals("RESTAURE") && estado.evento.contains("APAGADO"))
                {
                    // Verificar si lo hizo fuera de horario
                    String sSQL = "SELECT * FROM horario WHERE idAlarma = '"+ alarmaCliente.idAlarma +"' AND dia LIKE '%"+ c.get(Calendar.DAY_OF_WEEK) +"%'";
                    ResultSet res = st.executeQuery(sSQL);
                    if (res.first())
                    {
                        String[] timePM = res.getString("PM").split(":");
                        pm.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timePM[0]));
                        pm.set(Calendar.MINUTE, Integer.parseInt(timePM[1]));
                        pm.set(Calendar.SECOND, Integer.parseInt(timePM[2]));
                        
                        if (c.getTimeInMillis() > pm.getTimeInMillis() )
                        {
                            subject = "Reporte de Estado";
                            sb.append("El vehículo ").append(alarmaCliente.descripcion).append(" se ha apagado; ultima posición conocída es: ");
                            sb.append(alarmaCliente.direccion).append("\n\nAPAGADO FUERA DE HORARIO");
                        }
                    }
                }
                //</editor-fold>
                               
            }
            if (sb.length() > 0)
                enviarMensaje(sb.toString(), usuario, subject, idOperacion);
            
            st.close();
            liberarEventosGPS(estadoGPS, modo, idOperacion);
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    private void verificarGPS()
    //<editor-fold defaultstate="collapsed" desc="...">
    {
        // Verificar el modo
        String idOperacion = UUID.randomUUID().toString();
        if (alarmaCliente.modo.equals("P"))
        {
            // Notificar al usuario
            String mensaje = "Su sistema de monitoreo vehicular nos reporta que su Vehículo se encuentra en una zona con baja cobertura de GPS Satelital durante el modo PARQUEO, "
                    + "en caso de presentarse una eventualidad el sistema puede no operar como es desado. Por favor reubique su vehículo.";            
            enviarMensaje(mensaje, usuario, "REPORTE DE SEÑAL DE GPS", idOperacion);
        } 
        
        liberarEventosGPS(mttoGPS,"", idOperacion);
    }
    //</editor-fold>
    
    private void activarParqueo()
    //<editor-fold defaultstate="collapsed" desc="Procedimiento para activar parqueo con llamada">
    {
        // No se posiciona
        String idOperacion = UUID.randomUUID().toString();
        try {
            Calendar fechaPaqueo;
            boolean activo = false;
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            sb.append("Vehículo: ").append(alarmaCliente.descripcion);
            sb.append("\nSu sistema de parqueo ha sido ACTIVADO correctamente, cualquier novedad le informaremos oportunamente.");
            
            // 1. Establecer la respuesta T2 == P
            Statement st = (Statement) conn.createStatement();
            st.executeUpdate("UPDATE alarma SET modo = 'P' WHERE idAlarma  = '"+ idAlarma +"'");
            // 2. Verificar si el vehículo en encuentra en MODO ENCEDIDO
            if (alarmaCliente.estado.equals("ENCENDIDO"))
            {
                fechaPaqueo = Calendar.getInstance();
                fechaPaqueo.add(Calendar.SECOND, 5); // Se le agrega 5 segundos para verificar eventos nuevos
                
                // Esperar 20 seg para saber si recibió el evento.
                Thread.sleep(20000);
                // Consultar en DB si hay eventos de posición superiores a la fecha en la que se estabelece el modo T2
                ResultSet res = st.executeQuery("SELECT * FROM evento WHERE idAlarma = '"+ idAlarma +"' AND "
                        + "fecha > '"+ f.format(fechaPaqueo.getTime()) +"'");
                
                if (res.first()) activo = true;// Hay eventos.                    
            }
            
            enviarMensaje(sb.toString(), usuario, "Modo Parqueo Activo", idOperacion);
            
            // 3. Lanzar llamada SI no pudo activarse
            if (!activo)
                activo = solicitarReporte();
            
            // 3. Si no queda activo notificar que no pudo se activado
            if (!activo)
                System.out.println("No es posible activar el modo parqueo de este vehiculo");
            else
                System.out.println("Modo activado correctamente");
            st.close();
            
            liberarEventosGPS(actParqueo, "P", idOperacion);
            
        } catch (SQLException | InterruptedException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
    //</editor-fold>
    
    private boolean solicitarReporte()
    //<editor-fold defaultstate="collapsed" desc="Lanzar llamada al equipo para que responda">
    {
        int intento = 0;
        boolean activo = false;
        String uii = UUID.randomUUID().toString();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        try{
            Statement st = (Statement) conn.createStatement();
            while (!activo)
            {
                Writer writer = null;
                intento++;
                StringBuilder fb = new StringBuilder();
                fb.append("Channel: SIP/Emcali/03").append(alarmaCliente.telGPS).append("\n");
                fb.append("MaxRetries: 0\n");
                fb.append("RetryTime: 60\n");
                fb.append("WaitTime: 7\n");
                fb.append("Context: MOD_AXAR\n");
                fb.append("Extension: activar\n");
                fb.append("Priority: 1\n");
                writer = new BufferedWriter(new OutputStreamWriter( new FileOutputStream("/var/spool/asterisk/outgoing/" + uii + ".call"), "utf-8") );
                writer.write(fb.toString());
                writer.close();
                
                Calendar fechaSolicitud = Calendar.getInstance();
                fechaSolicitud.add(Calendar.SECOND, 5);
                Thread.sleep(30000); // Se espera 25 Secs.
                
                ResultSet res = st.executeQuery("SELECT * FROM evento WHERE idAlarma = '"+ idAlarma +"' AND "
                        + "fecha > '"+ f.format(fechaSolicitud.getTime()) +"'");
                System.out.println("SELECT * FROM evento WHERE idAlarma = '"+ idAlarma +"' AND FECHA > '"+ f.format(fechaSolicitud.getTime()) +"'");
                
                if (res.first()) 
                {
                    activo = true; // Hay eventos.
                    break;
                }
                if (intento > 2) break;
                Thread.sleep(5000);
            }
            st.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } catch (    InterruptedException | SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
        return activo;
    }
//</editor-fold>
    
    private void verificarOtrosGPS()
    //<editor-fold defaultstate="collapsed" desc="Otros Eventos GPS">
    {
        String modo = "";
        String idOperacion = UUID.randomUUID().toString();
        if (!otrosGPS.isEmpty())
            posicionarGPS(otrosGPS.get(otrosGPS.size()-1));
        alarmaCliente.actualizar();
        
        SimpleDateFormat f = new SimpleDateFormat("hh:mm aaa");        
        // Identificar que evento se notifican
        for (EventoGPS evt: otrosGPS)
        {
            if (evt.idGPS.equals("512") || evt.idGPS.equals("2048")) // Notificación de apagado de en modo parqueo.
            {
                String mensaje = "Hemos registrado un evento de " + evt.tipoEvento + " " + evt.evento 
                        + " con el vehículo " + alarmaCliente.descripcion + " A las " + f.format(evt.fecha) 
                        + " En " + alarmaCliente.direccion;
                enviarMensaje(mensaje, usuario, "NOTIFICACIÓN DE " + evt.tipoEvento, idOperacion);
            }
            else if (evt.idGPS.equals("3") && !alarmaCliente.modo.equals("O"))
            {
                modo = "O";
                System.out.println("Solicitando " + evt.idGPS);
                if (alarmaCliente.estado.equals("APAGADO")) solicitarReporte();
            }
            else if (evt.idGPS.equals("5")) // Evento de desBloqueo remoto
            {
                try {
                    if (alarmaCliente.estado.equals("APAGADO"))
                        solicitarReporte();
                    try (Statement st = (Statement) conn.createStatement()) {
                        st.executeUpdate("UPDATE alarma SET modo = 'S3' WHERE idAlarma = '"+ idAlarma +"'");
                    }
                } catch (SQLException ex) {
                    Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }        
        liberarEventosGPS(otrosGPS, modo, idOperacion);
    }
//</editor-fold>
    // FIN Funciones PARA GPS
    
    // ANALISIS DE REACCIONES
    
    public boolean requiereReaccion(String respuesta, String tipoEvento)
    //<editor-fold defaultstate="collapsed" desc="EVUALAR ESTADOS">
    {
        if (!respuesta.equals(""))
        {
            if (respuesta.contains("COACCIÓN") || respuesta.contains("coaccion"))
                return true;

            else if (respuesta.contains("COLGADO POR USUARIO"))
                return true;

            else if (respuesta.contains("CONTRASEÑA EQUIVOCADA"))
                return true;
            
            else if (respuesta.contains("SIN AUTENTI"))
                return true;
            
            else if (respuesta.contains("APERTURA NO AUTORIZADA"))
                return true;
            
            else if (respuesta.contains("SIN CONFIRMAR") && !tipoEvento.equals("TARDE"))
                return true;            
            
        }
        return false;
    }
    //</editor-fold>
    
    private void actualizarBitacora(String guii, String estado)
    //<editor-fold defaultstate="collapsed" desc="Actualización de vitacora">
    {
        try {
            Statement st = (Statement) conn.createStatement();
            st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii +"'");
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    private void insertarBitacora(String idAlarma, String medio, String mensaje, String destinatario, String GUID, boolean multiple, String idOperacion)
    //<editor-fold defaultstate="collapsed" desc="Insersión en Bitaora">
    {
        try 
        {
            Statement st = (Statement) conn.createStatement();
            if (!multiple)
            {
                String estado = "INFORMANDO";
                if (medio.toUpperCase().equals("EMAIL") || medio.toUpperCase().equals("GTALK"))
                    estado = "INFORMADO";
                st.executeUpdate("INSERT INTO vitacora (idAlarma, estado, medio, mensaje, destinatario, GUID, idOperacion) Values "
                    + "('"+ idAlarma +"', '"+ estado +"', '"+ medio +"', '"+ mensaje +"', '"+ destinatario +"', '"+ GUID +"', '"+ idOperacion +"')");
            }
            else
            {
                st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario, idOperacion, estado) VALUES " + mensaje);
            }
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    // ArrayList
    private void insertarReaccion(ArrayList<EventoGPS> evento, String tipoReaccion, Alarma alarmaCliente, String motivo)
    //<editor-fold defaultstate="collapsed" desc="Reacciones GPS">
    {
        if (alarmaCliente.reaccionInmediata) // SI EL CLIENTE ESTA EN MODO REVISIÓN DEBE ESTAR R
        {
            Calendar c              = Calendar.getInstance();
            SimpleDateFormat f      = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuilder sEventos  = new StringBuilder();  // ID-eventos
            StringBuilder evMensaje = new StringBuilder();

            try {

                for (int i=0; i < evento.size(); i++)
                {
                    EventoGPS evt = evento.get(i);
                    sEventos.append(evt.idEvento);                
                    evMensaje.append(evt.evento);

                    if (!evt.zona.equals("000"))
                        evMensaje.append(" EN ZONA ").append(evt.zona);

                    evMensaje.append(". ");

                    if ( i < (evento.size()-1) )
                        sEventos.append("-");
                }

                // Construcción del mensaje
                StringBuilder strMensaje = new StringBuilder();
                strMensaje.append("Motivo:\n");
                strMensaje.append(motivo.toUpperCase());

                if (evento.size() > 1)
                    strMensaje.append(". Eventos de ");
                else if (evento.size() > 0 )
                    strMensaje.append(". Evento de ");

                strMensaje.append(evMensaje.toString());

                // Insersión de eventos en axar:reaccion76
                String sSQL = "";
                Statement st = (Statement) conn.createStatement();
                ResultSet res = st.executeQuery("SELECT * FROM reaccion WHERE idAlarma = '"+ idAlarma +"' AND "
                        + "(estado = 'EN TRAMITE' OR estado = 'PENDIENTE' OR estado = 'ASIGNANDO' OR estado = 'NO ASIGNADA')");

                if (res.first())
                {   // Existe reaccion en tramite para este cliente
                    sSQL = "INSERT INTO reaccion (idAlarma, tipoReaccion, eventos, ultimaActualizacion, mensaje, sector, estado) VALUES ("
                            + "'"+ idAlarma +"', "
                            + "'"+ tipoReaccion +"', "
                            + "'"+ sEventos +"', "
                            + "'"+ f.format(c.getTime()) +"', "
                            + "'"+ strMensaje.toString() +"\n\nPara la Reacción COD: "+ res.getString("idReaccion") +"', "
                            + "'"+ alarmaCliente.GPS +"', "
                            + "'NUEVO EVENTO')";
                }
                else
                {   // NO existe reaccion, insertar una nueva
                    sSQL = "INSERT INTO reaccion (idAlarma, tipoReaccion, eventos, ultimaActualizacion, mensaje, sector) VALUES ("
                            + "'"+ idAlarma +"', "
                            + "'"+ tipoReaccion +"', "
                            + "'"+ sEventos +"', "
                            + "'"+ f.format(c.getTime()) +"', "
                            + "'"+ strMensaje.toString() +"', "
                            + "'"+ alarmaCliente.GPS +"')";
                }
                st.executeUpdate(sSQL);
                st.close();
            } catch (SQLException ex) {
                Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    //</editor-fold>
    
    private void insertarReaccion(ArrayList<Evento> evento, String tipoReaccion, Alarma alarmaCliente, String motivo, boolean dummy)
    //<editor-fold defaultstate="collapsed" desc="Insertar Reacción en tabla">
    {        
        if (alarmaCliente.reaccionInmediata) // SI EL CLIENTE ESTA EN MODO REVISIÓN DEBE ESTAR R
        {
            Calendar c              = Calendar.getInstance();
            //String guii             = UUID.randomUUID().toString(); // Relaciona la DB vitacora y reaccion; de Vitacora sale la lectura del mensaje
            SimpleDateFormat f      = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuilder sEventos  = new StringBuilder();  // ID-eventos
            StringBuilder evMensaje = new StringBuilder();
            //StringBuilder zEventos  = new StringBuilder();

            try {

                for (int i=0; i < evento.size(); i++)
                {
                    Operador.Evento evt = (Operador.Evento)evento.get(i);
                    sEventos.append(evt.idEvento);

                    if (evt.tipoEvento.equals("APERTURA"))
                        evMensaje.append(" APERTURA ");
                    
                    evMensaje.append(evt.evento);
                    
                    if (evt.tipoEvento.equals("APERTURA") && !evt.zona.equals("000"))
                        evMensaje.append(" ").append(evt.zona);
                    else if (!evt.zona.equals("000"))
                        evMensaje.append(" EN ZONA ").append(evt.zona);
                    
                    evMensaje.append(". ");
                    
                    if ( i < (evento.size()-1) )
                        sEventos.append("-");
                }

                // Construcción del mensaje
                StringBuilder strMensaje = new StringBuilder();
                strMensaje.append("Motivo:\n");
                strMensaje.append(motivo.toUpperCase());

                if (evMensaje.length() > 3 ) // Si el evento es reconocido.
                {
                    if (evento.size() > 1)
                        strMensaje.append(". Eventos de ");
                    else if (evento.size() > 0 )
                        strMensaje.append(". Evento de ");

                    strMensaje.append(evMensaje.toString());
                }

                // Insersión de eventos en axar:reaccion76
                String sSQL = "";
                Statement st = (Statement) conn.createStatement();
                ResultSet res = st.executeQuery("SELECT * FROM reaccion WHERE idAlarma = '"+ idAlarma +"' AND "
                        + "(estado = 'EN TRAMITE' OR estado = 'PENDIENTE' OR estado = 'ASIGNANDO' OR estado = 'NO ASIGNADA')");

                if (res.first()) 
                {   // Existe reaccion en tramite para este cliente
                    sSQL = "INSERT INTO reaccion (idAlarma, tipoReaccion, eventos, ultimaActualizacion, mensaje, sector, idOperacion, estado) VALUES ("
                        + "'"+ idAlarma +"', "
                        + "'"+ tipoReaccion +"', "
                        + "'"+ sEventos +"', "
                        + "'"+ f.format(c.getTime()) +"', "
                        + "'"+ strMensaje.toString() +"\n\nPara la Reacción COD: "+ res.getString("idReaccion") +"', "
                        + "'"+ alarmaCliente.GPS +"', "
                        + "'"+ res.getString("idOperacion") +"', "
                        + "'NUEVO EVENTO')";
                }
                else
                {   // NO existe reaccion, insertar una nueva
                    sSQL = "INSERT INTO reaccion (idAlarma, tipoReaccion, eventos, ultimaActualizacion, mensaje, sector) VALUES ("
                        + "'"+ idAlarma +"', "
                        + "'"+ tipoReaccion +"', "
                        + "'"+ sEventos +"', "
                        + "'"+ f.format(c.getTime()) +"', "
                        + "'"+ strMensaje.toString() +"', "
                        + "'"+ alarmaCliente.GPS +"')";
                }
                st.executeUpdate(sSQL);
                st.close();
            } catch (SQLException ex) {
                Logger.getLogger(Reaccion.class.getName()).log(Level.SEVERE, null, ex);
            } 
        }
    }
    //</editor-fold>
    
    public void liberarEventos(ArrayList<Evento> eventos, String inOperacion)
    //<editor-fold defaultstate="collapsed" desc="de PENDINTE, EN TRAMITE a ATENDIDO">
    {
        try {
            boolean chkApertura = false;
            String fecha = "";
            StringBuilder cadSQL = new StringBuilder();
            for (int i=0; i < eventos.size(); i++)
            {
                Evento evt = (Evento)eventos.get(i);
                cadSQL.append("idEvento = '");
                cadSQL.append(evt.idEvento);
                cadSQL.append("' OR ");
                if (evt.tipoEvento.equals("APERTURA"))
                {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    chkApertura = true;
                    fecha = f.format(evt.fecha);
                }
            }
            
            if (cadSQL.length() > 0)
            {
                String sSQL = cadSQL.toString();
                sSQL = sSQL.substring(0, sSQL.length()-3);
                Statement st = (Statement) conn.createStatement();
                st.executeUpdate("UPDATE evento SET estado = 'ATENDIDO', idOperacion = '"+ inOperacion +"' WHERE " + sSQL);
                eventos.clear();

                // Libera eventos de APERTURA futuro a este o estos..
                if (chkApertura)
                    st.executeUpdate("UPDATE evento SET estado = 'ATENDIDO', idOperacion = '"+ inOperacion +"' WHERE idAlarma = '"+ idAlarma +"' AND tipoEvento = 'APERTURA' AND fecha > '" + fecha + "'");

                st.close();
            }
            
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>
    
    public void liberarEventosGPS(ArrayList<EventoGPS> eventos, String respuesta, String inOperacion)
    //<editor-fold defaultstate="collapsed" desc="de PENDINTE, EN TRAMITE a ATENDIDO">
    {
        String idOperacion = inOperacion;
        try {
            boolean chkEncendido = false;
            StringBuilder cadSQL = new StringBuilder();
            for (int i=0; i < eventos.size(); i++)
            {
                cadSQL.append("idEvento = '");
                cadSQL.append(eventos.get(i).idEvento);
                cadSQL.append("' OR ");
            }
            
            String sSQL = cadSQL.toString();
            sSQL = sSQL.substring(0, sSQL.length()-3);
            try (Statement st = (Statement) conn.createStatement()) 
            {
                if (!respuesta.equals("")) // modo = '" + respuesta + "'
                    st.executeUpdate("UPDATE alarma SET modo = 'N' WHERE idAlarma = '"+ idAlarma +"'");
                st.executeUpdate("UPDATE evento SET estado = 'ATENDIDO', idOperacion = '"+ idOperacion +"' WHERE " + sSQL);
                eventos.clear();
            }
            
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
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
    
    // NOTIFICACIONES
    
    public String informar(String numero, String guii, String categoria, String extension, boolean  informar) throws IOException, AuthenticationFailedException
    //<editor-fold defaultstate="collapsed" desc="Informar Usuario">
    {
        //<editor-fold defaultstate="collapsed" desc="Old code">
        /*
         * try {
         * OriginateAction originateAction = new OriginateAction();
         * ManagerResponse originateResponse;
         * long timeout = 20000;
         * originateAction.setCallerId("Seguridad Montgomery <0323730840>");
         * Statement st = (Statement) conn.createStatement();
         * ResultSet rs;
         * 
         * if (numero.length() == 7)
         * rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'local' AND estado = 'A' ORDER BY idproveedor");
         * 
         * else
         * {
         * rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'celular' AND estado = 'A' ORDER BY idproveedor");
         * timeout = 35000;
         * }
         * 
         * while (rs.next())
         * {
         * if (troncalEnlinea(rs.getString("nombre"), rs.getString("tech")))
         * {
         * String tech = rs.getString("tech");
         * String nombre = rs.getString("nombre");
         * String prefijo = rs.getString("prefijo");
         * originateAction.setChannel( tech + "/" + nombre + "/" + prefijo + numero );
         * break;
         * }
         * }
         * 
         * rs.close();
         * st.close();
         * 
         * //originateAction.setChannel("SIP/4006");
         * originateAction.setContext("MOD_AXAR");
         * originateAction.setExten("s");
         * originateAction.setVariable("ALARMA", guii + "/" + categoria + "/" + extension + "/" + informar);
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
         * String res = originateResponse.getResponse();
         * System.out.println(originateResponse.getResponse());
         * 
         * // and finally log off and disconnect
         * /*try {
         * managerConnection.logoff();
         * }
         * catch (Exception h) { System.out.println("\nEXCEPCIÓN AL DESLOGUEO EN LLAMADA "+ h.getMessage() +"\n" ); }
         * return res;
         * } catch (SQLException ex) {
         * Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
         * }*/
        //</editor-fold>
        
        Writer writer;
        StringBuilder sb = new StringBuilder();
        
        try {
            int timeout = 20;
            Statement st = (Statement) conn.createStatement();
            ResultSet rs;
            
            if (numero.length() == 7)
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'local' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: \"Seguridad Montgomery\" <23110610>\n");
            }
            else if (numero.length() == 8)
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'nacional' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: \"Seguridad Montgomery\" <0323110610>\n");
                timeout = 30;
            }
            else
            {
                rs = st.executeQuery("SELECT * FROM proveedor WHERE proxy = 'celular' AND estado = 'A' ORDER BY idproveedor");
                sb.append("CallerID: \"Seguridad Montgomery\" <0323110610>\n");
                timeout = 30;
            }
            while (rs.next())
            {
                if (troncalEnlinea(rs.getString("nombre"), rs.getString("tech")))
                {
                    String tech = rs.getString("tech");
                    String nombre = rs.getString("nombre");
                    String prefijo = rs.getString("prefijo");
                    sb.append("Channel: ").append(tech).append("/").append(nombre).append("/").append(prefijo).append(numero).append("\n");
                    break;
                }
            }
            rs.close();
            st.close();
            
            sb.append("MaxRetries: 0\n");
            sb.append("RetryTime: 60\n");
            sb.append("WaitTime: ").append(timeout).append("\n");
            sb.append("Context: MOD_AXAR\n");
            sb.append("Extension: s\n");
            sb.append("Priority: 1\n");
            sb.append("SetVar: ALARMA=").append(guii).append("/").append(categoria).append("/").append(extension).append("/").append(informar).append("\n");
            
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
    
    public boolean enviarMensaje(String mensaje, ArrayList<Usuario> usuario, String asunto, String idOperacion)
    //<editor-fold defaultstate="collapsed" desc="Mensaje gTALK e Email">
    {
        try {
            Statement st = (Statement) conn.createStatement();
            for (int i=0; i < usuario.size(); i++)
            {
                Usuario usr = (Usuario)usuario.get(i);
                if (gtalk != null && !usr.gtalk.equals("") && usr.gtalk.contains("@")){

                    Presence presence = gtalk.getRoster().getPresence(usr.gtalk);
                    if (presence.getType() == Presence.Type.available)
                    {
                        Message msg = new Message(usr.gtalk, Message.Type.chat);                            
                        msg.setBody("Apreciad@ " + usr.nombre + ". " + mensaje.toString());
                        gtalk.sendPacket(msg);
                        st = (Statement) conn.createStatement();
                        insertarBitacora(idAlarma, "gTalk", mensaje.toString(), usr.gtalk, "-1", false, idOperacion);                        
                        System.out.println("Enviado " + usr.gtalk);
                    }
                    else
                    {
                        System.out.println("Usuario desconectado " + usr.nombre);
                        insertarBitacora(idAlarma, "gTalk", "Usuario Desconectado", usr.gtalk, "-1", false, idOperacion);                        
                    }
                }
            }
            enviarEmail(idAlarma, mensaje, asunto, usuario, idOperacion);
            st.close();
            return true;
        } catch (SQLException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }//</editor-fold>
    
    public void enviarSMS(String mensaje, String destino)
    //<editor-fold defaultstate="collapsed" desc="Envio de SMS a clientes">
    {
        
    }//</editor-fold>
    
    public void enviarEmail(String idAlarma, String mensaje, String asunto, ArrayList<Usuario> usuario, String idOperacion)
    //<editor-fold defaultstate="collapsed" desc="Envio de notificación via eMail">
    {
        StringBuilder query = new StringBuilder();
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
            message.setSubject(asunto);
            message.setSentDate(Calendar.getInstance().getTime());
            
            StringBuilder body = new StringBuilder();
            
            body.append("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\" /></head>");
            body.append("<body>");
            body.append("<div style=\"font-size:12px; text-align:left; border:1px solid white; font-family:Verdana, Arial, Helvetica, sans-serif; \">");
            body.append("<strong>Apreciad@ Usuario</strong><br><br>");
            body.append(mensaje.replaceAll("\n", "<br>"));
            
            body.append("<br><br>Este es un servicio del operador inteligente. <br><br>");
            body.append("<b>&copy;2014 Seguridad Montgomery Ltda.</b><br>Website: <a href=\"http://www.montgomery.com.co\">www.montgomery.com.co</a><br>");
            body.append("PBX: +57(2) 311 0610<br>Santiago de Cali.<br>Colombia.");
            body.append("</div></body></html>");
            
            message.setContent(body.toString(), "text/html");
            
            for (int i=0; i < usuario.size(); i++)
            {
                Usuario usr = (Usuario)usuario.get(i);
                if (usr.email.contains("@") && usr.email.indexOf("@") < usr.email.lastIndexOf(".") && !usr.email.contains("@none"))
                {
                    query.append("('").append(idAlarma).append("', 'Email', '").append(mensaje).append("', '").
                            append(usr.email).append("', '").append(idOperacion).append("', 'INFORMADO'), ");
                    message.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(usr.email, usr.nombre));
                }
            }
            
            Transport.send(message);
            
            // Actualiar Vitacora SUCESS
            String strTemp = query.toString();
            insertarBitacora(idAlarma, "", strTemp.toString().substring(0, strTemp.length()-2), "", "", true, idOperacion);
            /*Statement st = (Statement) conn.createStatement();
            st.executeUpdate("INSERT INTO vitacora (idAlarma, medio, mensaje, destinatario) Values "+ strTemp.toString().substring(0, strTemp.length()-2) );
            st.close();*/
            
        } catch (MessagingException ex) {
            
            // Actualizar vitacora FALLIDO
            query = new StringBuilder();
            for (int i=0; i < usuario.size(); i++)
            {
                Usuario usr = (Usuario)usuario.get(i);
                if (usr.email.contains("@") && usr.email.indexOf("@") < usr.email.lastIndexOf(".") )
                {
                    query.append("('").append(idAlarma).append("', 'Email', '").append(mensaje).append("', '").
                            append(usr.email).append("', '").append(idOperacion).append("', 'FALLIDO'), ");
                }
            }
            String strTemp = query.toString();
            if (strTemp.length() > 0){
                insertarBitacora(idAlarma, "", strTemp.toString().substring(0, strTemp.length()-2), "", "", true, idOperacion);
            }
            
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
    //</editor-fold>
    
    
    //<editor-fold defaultstate="collapsed" desc="Clase EventoGPS">
    public class EventoGPS
    {
        public String idEvento, evento, estado, velocidad, zona, desc, tipoEvento, lat, lon, curso, idGPS;        
        public Date fecha;
        private Connection conn;
                
        public EventoGPS(String idEvento, Date fecha, String evento, String zona, String desc, String velocidad, String estado, String tipoEvento, String gPos)
        {
            try {
                String driver = "com.mysql.jdbc.Driver";
                Class.forName(driver);
                String url = "jdbc:mysql://localhost/axar";
                conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
                
                String[] v = gPos.split("/");
                this.idEvento = idEvento;
                this.evento = clasificarEvento(evento, "GPS");
                this.zona = zona;
                this.velocidad = velocidad;
                this.estado = estado;
                this.tipoEvento = tipoEvento;
                this.fecha = fecha;
                this.desc = desc;
                this.lat = v[0];
                this.lon = v[1];
                this.curso = v[2];
                this.idGPS = evento;
                
                conn.close();
            } catch (    ClassNotFoundException | SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        private String clasificarEvento(String evento, String protocolo)
        //<editor-fold defaultstate="collapsed" desc="Califica evento de acuerdo a Número de Evento">
        {
            try {
                ResultSet res;
                Statement s = (Statement) conn.createStatement();
                switch (protocolo) {
                    case "CID":
                        res = s.executeQuery("SELECT evento FROM tablaCID WHERE idCID = '"+ evento +"'");
                        break;
                    case "GPS":
                        res = s.executeQuery("SELECT evento FROM tablaGPS WHERE idGPS = '"+ evento +"'");
                        break;
                    default:
                        s.close();
                        return "";
                }
                String strEvento = "";
                while(res.next())
                {
                    strEvento = res.getString("evento").toUpperCase();
                }
                res.close();
                s.close();
                return strEvento;

            } catch (SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
            return "";
        }
        //</editor-fold>
        
    }//</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="Clase Evento">
    public class Evento
    {
        public String idEvento, evento, estado, particion, zona, descZona, tipoEvento, clid, idCID;
        public Date fecha;
        private Connection conn;
        
        public Evento(String idEvento, Date fecha, String evento, String zona, String desc, String particion, String estado, String tipoEvento, String clid)
        {
            try {
                String driver = "com.mysql.jdbc.Driver";
                Class.forName(driver);
                String url = "jdbc:mysql://localhost/axar";
                conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
                
                this.idEvento = idEvento;
                this.evento = clasificarEvento(evento, "CID");
                this.zona = zona;
                this.particion = particion;
                this.estado = estado;
                this.tipoEvento = tipoEvento;
                this.fecha = fecha;
                this.descZona = desc;
                this.clid = clid;
                this.idCID = evento;
                
                conn.close();
            } catch (    ClassNotFoundException | SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        private String clasificarEvento(String evento, String protocolo)
        //<editor-fold defaultstate="collapsed" desc="Califica evento de acuerdo a Número de Evento">
        {
            try {
                ResultSet res;
                Statement s = (Statement) conn.createStatement();
                switch (protocolo) {
                    case "CID":
                        res = s.executeQuery("SELECT evento FROM tablaCID WHERE idCID = '"+ evento +"'");
                        break;
                    case "GPS":
                        res = s.executeQuery("SELECT evento FROM tablaGPS WHERE idGPS = '"+ evento +"'");
                        break;
                    default:
                        s.close();
                        return "";
                }
                String strEvento = "";
                while(res.next())
                {
                    strEvento = res.getString("evento").toUpperCase();
                }
                res.close();
                s.close();
                return strEvento;

            } catch (SQLException ex) {
                Logger.getLogger(Centinela.class.getName()).log(Level.SEVERE, null, ex);
            }
            return "";
        }
        //</editor-fold>
    }//</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="Clase Usuario">
    public class Usuario{
        
        public String titulo, nombre, idUsuario, email, tel1, tel2, gtalk;
        
        public Usuario(String titulo, String nombre, String idUsuario, String email, String tel1, String tel2, String gtalk)
        {           
            this.idUsuario = idUsuario;
            this.titulo = titulo;
            this.nombre = nombre;
            this.email = email;
            this.tel1 = tel1;
            this.tel2 = tel2;
            this.gtalk = gtalk;
        }        
    }//</editor-fold>
    
    //<editor-fold defaultstate="collapsed" desc="Clase [Alarma]">
    public class Alarma implements Runnable{
        
        public String idAlarma, tipoAlarma, estado, telContacto, extension, direccion, barrio, GPS, confirmacion, coaccion, nombre, nombreEscrito, descripcion, telGPS, modo;
        public String[] ciudad;
        public boolean reaccionInmediata = false;
        private Thread actual;
        private Statement st;
        
        public Alarma(String idAlarma){
            try {
                this.idAlarma = idAlarma;
                actual = new Thread(this);                
                
                st = (Statement)conn.createStatement();
                ResultSet res = st.executeQuery("SELECT alarma.*, cliente.nombre, cliente.nombreEscrito FROM alarma, cliente "
                            + "WHERE alarma.idCliente = cliente.idCliente AND "
                            + "(idAlarma = '"+ idAlarma +"' AND (estadoActual = 'A' OR estadoActual = 'R') )");
                while(res.next())
                {
                    this.nombre = res.getString("nombre");
                    this.tipoAlarma = res.getString("tipoAlarma");
                    this.estado = res.getString("estado");
                    this.telContacto = res.getString("telefonoContacto");
                    this.extension = res.getString("extension");
                    this.direccion = res.getString("direccion");
                    this.barrio = res.getString("barrio");
                    this.GPS = res.getString("GPS");
                    this.confirmacion = res.getString("claveConfirmacion");
                    this.coaccion = res.getString("claveCoaccion");
                    this.nombreEscrito = res.getString("nombreEscrito");
                    this.descripcion = res.getString("descripcion");
                    this.telGPS = res.getString("teclado");
                    this.modo = res.getString("modo");
                    if (res.getString("ciudad").contains("-"))
                        this.ciudad = res.getString("ciudad").split("-");
                    else
                        this.ciudad = new String[] {"", res.getString("ciudad")};
                    this.reaccionInmediata = res.getString("estadoActual").equals("A");
                }
                res.close();
                
            } catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        public void iniciar() { actual.start(); }
        
        public void finallizar() {
            actual.interrupt();
            st = null;
        }
        
        public void actualizar()
        {
            try {
                if (st != null && !st.isClosed())
                {
                    ResultSet res = st.executeQuery("SELECT * FROM alarma WHERE "
                                + "idAlarma = '"+ idAlarma +"' AND (estadoActual = 'A' OR estadoActual = 'R')");
                    if(res.first())
                    {
                        this.direccion = res.getString("direccion");
                        this.barrio = res.getString("barrio");
                        this.GPS = res.getString("GPS");
                        this.descripcion = res.getString("descripcion");
                        if (res.getString("ciudad").contains("-"))
                            this.ciudad = res.getString("ciudad").split("-");
                        else
                            this.ciudad = new String[] {"", res.getString("ciudad")};
                        this.estado = res.getString("estado");
                        this.reaccionInmediata = res.getString("estadoActual").equals("A");
                        this.modo = res.getString("modo");
                    }
                    res.close();
                }
            } catch (SQLException ex) {
                Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex);
                finallizar();
            }
        }

        @Override
        public void run() {
            while (Thread.currentThread() == actual)
            {
                try {
                    actualizar();
                    Thread.sleep(8000);
                } catch (InterruptedException ex) {
                    try {
                        if (st != null && !st.isClosed()) st.close();
                        actual = null;
                    } catch (SQLException ex1) {
                        Logger.getLogger(Operador.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            }
        }
    }//</editor-fold>
}