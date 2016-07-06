
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Kailu Mario
 * @version 0.0.1
 * @since 2013-06-03
 */
public class Servicio extends BaseAgiScript {
    
    private Connection conn;
    private TTS tts;
    
    private final int INTENTOS = 3;
    
    public Servicio(){}
    
    private boolean Conexion()
    //<editor-fold defaultstate="collapsed" desc="Procedimieno de conexión a DB">
    {
        try {
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver);
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
            return true;
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(Servicio.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    //</editor-fold>
    
    private void insertarReaccion(String idAlarma, String strMensaje, String zona)
    //<editor-fold defaultstate="collapsed" desc="Método para insertar reacciones">
    {
        try {
            
            String sSQL         = "";
            Calendar c          = Calendar.getInstance();
            String guii         = UUID.randomUUID().toString(); // Relaciona la DB vitacora y reaccion; de Vitacora sale la lectura del mensaje
            SimpleDateFormat f  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Statement st = (Statement) conn.createStatement();
            ResultSet res = st.executeQuery("SELECT * FROM reaccion WHERE idAlarma = '"+ idAlarma +"' AND (estado = 'EN TRAMITE' OR estado = 'PENDIENTE')");
            
            if (res.first())
            {   // Existe reaccion en tramite para este cliente
                sSQL = "INSERT INTO reaccion (idAlarma, tipoReaccion, eventos, idOperacion, ultimaActualizacion, mensaje, sector, estado) VALUES ("
                        + "'"+ idAlarma +"', "
                        + "'9 0 9', "
                        + "'0000', "
                        + "'"+ guii +"', "
                        + "'"+ f.format(c.getTime()) +"', "
                        + "'"+ strMensaje +"\n\nPara la Reacción COD: "+ res.getString("idReaccion") +"', "
                        + "'"+ zona +"', "
                        + "'NUEVO EVENTO')";
            }
            else
            {   // NO existe reaccion, insertar una nueva
                sSQL = "INSERT INTO reaccion (idAlarma, tipoReaccion, eventos, idOperacion, ultimaActualizacion, mensaje, sector) VALUES ("
                        + "'"+ idAlarma +"', "
                        + "'9 0 9', "
                        + "'0000', "
                        + "'"+ guii +"', "
                        + "'"+ f.format(c.getTime()) +"', "
                        + "'"+ strMensaje +"', "
                        + "'"+ zona +"')";
            }
            st.executeUpdate(sSQL);
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Servicio.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    //</editor-fold>

    @Override
    public void service(AgiRequest ar, AgiChannel ac) throws AgiException 
    //<editor-fold defaultstate="collapsed" desc="Atención a llamada de Servicio técnico">
    {
        tts = new TTS();
        if (Conexion())
        {   
            try {
                // Hay conexión con la base de datos.
                String src = ac.getVariable("CALLERID(name)");
                if (src != null &&  src.equals("tecnico"))
                //<editor-fold defaultstate="collapsed" desc="Tratamiento para cuando es técnico">
                {
                    int cont=0, orden=0, opcion=0;
                    Statement st = (Statement)conn.createStatement();
                    CEDULA:
                    while (true)
                    {
                        cont++; // Incrementa el conteo de intentos
                        exec("Answer");
                        String cedula = tts.playTTS("Bienvenido al servicio de audiorespuesta Técnica, por favor ingrese su cédula.", true, 10);
                        if (cedula.length() > 7)
                        {
                            String sSQL = "SELECT * FROM empleado WHERE cargo LIKE 'TÉCNICO' AND idempleado = '"+ cedula +"'";
                            ResultSet rs = st.executeQuery(sSQL);
                            if (rs.first())
                            {
                                while (true)
                                {
                                    orden++;
                                    // Empleado existe;
                                    int ot=0;
                                    String otString = tts.playTTS("Por favor ingrese la orden de trabajo.", true, 5);
                                    if (!otString.equals("")) ot = Integer.parseInt(otString);

                                    if (ot > 0)
                                    {
                                        // Buscar la OT y verififcar que exista
                                        String nSQL = "SELECT * FROM servicio WHERE idservicio = '"+ ot +"'";
                                        rs = st.executeQuery(nSQL);
                                        if (rs.first() && rs.getString("estado").equals("ENTRAMITE"))
                                        {
                                            // OT EXISTE VERIFICAR QUE NO ESTE ATENDIDA
                                            opcion = 0;
                                            OPCION:
                                            while (true)
                                            {
                                                opcion++;
                                                int otOpcion = Integer.parseInt(tts.playTTS("Para cerrar la OT, presione uno. Para solicitar reprogramación dos.", true, 1));
                                                if (otOpcion > 0) {

                                                    tts.playTTS("Por favor después del tono realice el informe y presione numeral para terminar.", false, 1);
                                                    recordFile("/var/spool/asterisk/monitor/" + ac.getUniqueId(), "gsm", "#", 30000, 0, true, 5000);

                                                    Calendar c = Calendar.getInstance();
                                                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                                                    if (otOpcion == 1)
                                                    {
                                                        st.executeUpdate("UPDATE servicio SET estado = 'FINALIZADA', "
                                                                + "fechaVisita = '"+ f.format(c.getTime()) +"', "
                                                                + "encargado = '"+ cedula +"', "
                                                                + "fuente = '"+ ac.getVariable("CALLERID(num)") +"', "
                                                                + "notas = 'Cierre de OT por Técnico AUDIO: "+ ac.getUniqueId() +".gsm' WHERE idservicio = '"+ ot +"'");
                                                        
                                                        // Actualiza el estado del servicio a Activo
                                                        ResultSet ans = st.executeQuery("SELECT * FROM alarma WHERE idAlarma = '"+ rs.getString("idAlarma") +"'");
                                                        if (ans.first() && !ans.getString("estadoActual").equals("I"))
                                                            st.executeUpdate("UPDATE alarma SET estadoActual = 'A' WHERE idAlarma = '"+ rs.getString("idAlarma") +"'");

                                                        tts.playTTS("Su reporte ha sido recibido. Hasta pronto.", false, 1);
                                                        break OPCION; // FIN

                                                    } else if (otOpcion == 2)
                                                    {
                                                        st.executeUpdate("UPDATE servicio SET estado = 'REPROGRAMAR', "
                                                                + "fechaVisita = '"+ f.format(c.getTime()) +"', "
                                                                + "encargado = '"+ cedula +"', "
                                                                + "fuente = '"+ ac.getVariable("CALLERID(num)") +"', "
                                                                + "notas = 'Solicitud de Reprogramación de Visita AUDIO: "+ ac.getUniqueId() +".gsm' WHERE idservicio = '"+ ot +"'");
                                                        
                                                        // Actualiza el estado del servicio a Activo
                                                        // Verificar si el cliente esta en revisión o esta en instalación en cuyo caso no deberá ser considerado como A
                                                        ResultSet ans = st.executeQuery("SELECT * FROM alarma WHERE idAlarma = '"+ rs.getString("idAlarma") +"'");
                                                        if (ans.first() && ans.getString("estadoActual").equals("R"))
                                                            st.executeUpdate("UPDATE alarma SET estadoActual = 'A' WHERE idAlarma = '"+ rs.getString("idAlarma") +"'");
                                                                                                             
                                                        tts.playTTS("Su reporte ha sido recibido. Se reprogramará la visita. Hasta pronto.", false, 1);
                                                        break OPCION; // FIN

                                                    } else
                                                        tts.playTTS("Esta opción es inválida.", false, 1);
                                                }
                                                else if (ac.getChannelStatus() < 3) // Esta colgado
                                                {
                                                    if (!st.isClosed())
                                                        st.close();
                                                    break CEDULA;
                                                }
                                                else if (opcion >= INTENTOS)
                                                    break;
                                            }
                                            
                                            st.close();
                                        }
                                        else if (rs.getString("estado").equals("PENDIENTE"))
                                            tts.playTTS("Esta orden de trabajo aún no ha sido asignada, Intentelo nuevamente.", false, 1);
                                        else if (rs.getString("estado").equals("ATENDIDA"))
                                            tts.playTTS("Esta orden de trabajo ya fue cerrada. Si necesita agregar información por favor comuníquese con el Jefe de Departamento Técnico.", false, 1);
                                        else
                                            tts.playTTS("Esta orden de trabajo no existe.", false, 1);
                                    }
                                    else if (orden >= INTENTOS)
                                        break;
                                    else
                                        tts.playTTS("No ha ingresado nada aún.", false, 1);
                                }
                            }
                            else
                                tts.playTTS("Su número de documento no existe. Intenete nuevamente", false, 1);
                        }
                        else if (ac.getChannelStatus() < 3) // Esta colgado
                        {
                            System.out.println(ac.getChannelStatus());
                            if (!st.isClosed())
                                st.close();
                            break;
                        }
                        else if (cont >= INTENTOS)
                            break;
                        else
                            tts.playTTS("Su número de documento es inválido, intente nuevamente.", false, 1);

                    }
                }
                //</editor-fold>

                else if (src != null && src.contains("cliente"))
                //<editor-fold defaultstate="collapsed" desc="Tratamiento para comunicación entrante de Cliente">
                {
                    int intID = 0;
                    exec("Answer");
                    Statement st = (Statement)conn.createStatement();
                    IDENTIFICACION:
                    while (true)
                    {
                        intID++;
                        if (intID >= INTENTOS || getChannelStatus()< 3)
                        {
                            if (!st.isClosed()) st.close();
                            break;
                        }
                        
                        String dat = tts.playTTS("Bienvenido al servicio de audiorespuesta para clientes de Seguridad Montgomery. Por favor ingrese su cédula o nit con el dígito de verificación.", true, 11);
                        if (dat.length() > 6)
                        {
                            // Solo Alarma
                            String sSQL = "SELECT * FROM alarma RIGHT JOIN (SELECT * FROM cliente WHERE identificacion = '"+ dat +"') AS nCliente "
                                    + "ON nCliente.idCliente = alarma.idCliente AND tipoAlarma = 'ANTIROBO'";
                            ResultSet rs = st.executeQuery(sSQL);
                            
                            //<editor-fold defaultstate="collapsed" desc="Verificación de existencia ALARMA">
                            if (rs.last() && rs.getRow() > 0)
                            {
                                if (rs.getRow() > 1)
                                {
                                    int contEstab = 0;
                                    String[] vecEst = new String[rs.getRow()];
                                    StringBuilder est = new StringBuilder();
                                    
                                    rs.first();
                                    do {
                                        contEstab++;
                                        vecEst[(contEstab-1)] = rs.getString("idAlarma");
                                        est.append("Para el establecimiento ubicado en ").append(rs.getString("direccion")).append(" Márque: ").append(contEstab).append(". ");
                                    } while(rs.next());
                                    
                                    int intEsta = 0;
                                    tts.playTTS("Actualmente cuenta con varios establecimientos monitoreados.", false, 1);
                                    ESTAB:
                                    //<editor-fold defaultstate="collapsed" desc="Evalua que establecimiento desea consultar">
                                    while (true)
                                    {
                                        intEsta++;
                                        String selecc = tts.playTTS(est.toString().trim(), true, 1);
                                        if (!selecc.equals("")){
                                            int selected = Integer.parseInt(selecc);
                                            if ( selected > 0 && (selected-1) < vecEst.length) {// Cantidad de establecimientos
                                                rs = st.executeQuery("SELECT * FROM alarma WHERE idAlarma = '"+ vecEst[(selected-1)] +"'");
                                                rs.first();
                                                break ESTAB;
                                            }
                                            else
                                                tts.playTTS("La opción seleccionada no es válida.", false, 1);
                                        }
                                        else if (intEsta >= INTENTOS){ // Supero los intentos.
                                            st.close();
                                            break IDENTIFICACION;
                                        }
                                        else
                                            tts.playTTS("No ha seleccionado ninguno de los establecimientos registrados.", false, 1);
                                    }
                                    //</editor-fold>
                                }
                                
                                // Si el cliente no tiene segunda clave de seguridad
                                if (rs.getString("claveConfirmacion") == null  || rs.getString("claveConfirmacion").equals(""))
                                //<editor-fold defaultstate="collapsed" desc="Asignación de Segunda Clave y Coacción">
                                {
                                    int intClave = 0;
                                    tts.playTTS("Actualmente no cuenta con segunda clave. A continuación iniciaremos el proceso para asignarla.", false, 1);
                                    CLAVE:
                                    while(true)
                                    {
                                        // Cliente no tiene segunda clave de Seguridad y Coacción
                                        intClave++;
                                        String claveConfirmacion = tts.playTTS("Por favor ingrese su nueva segunda clave de seguridad de cuatro dígitos.", true, 4);

                                        if (claveConfirmacion.length() == 4)
                                        {
                                            // Solicitar la clave de coacción
                                            String claveCoaccion = tts.playTTS("Ahora ingrese su nueva clave de coacción de cuatro dígitos.", true, 4);
                                            if (claveCoaccion.length() == 4 && !claveCoaccion.equals(claveConfirmacion) )
                                            {
                                                // Ya estan ambas claves
                                                st.executeUpdate("UPDATE alarma SET claveConfirmacion = '"+ claveConfirmacion +"', "
                                                        + "claveCoaccion = '"+ claveCoaccion +"' WHERE idAlarma = '"+ rs.getString("idAlarma") +"'");
                                                tts.playTTS("Sus nuevas contraseñas han sido establecidas. "
                                                        + "La primer clave debe usarla para confirmar su identidad cuando nos comuniquemos con usted, la clave de coacción "
                                                        + "deberá usarla si se encuentra en problemas. Para más información consulte con su asesor comercial. Hasta pronto.", false, 1);
                                                st.close();
                                                break IDENTIFICACION;
                                            }
                                            else if ( claveCoaccion.equals(claveConfirmacion) )
                                                tts.playTTS("Su segunda clave no debe ser igual a su clave de coacción. Intentelo nuevamente.", false, 1);
                                            else
                                                tts.playTTS("Su contraseña de coacción no es válida. Intentelo nuevamente.", false, 1);
                                        }
                                        else if (intClave >= INTENTOS || getChannelStatus()< 3 )
                                            break CLAVE;
                                        else
                                            tts.playTTS("Su segunda clave no es válida. Intentelo nuevamente.", false, 1);
                                    }
                                }
                                //</editor-fold>
                            
                                else
                                //<editor-fold defaultstate="collapsed" desc="Audiorespuesta para solicitar servicios">
                                {
                                    // Usuario Autenticado.
                                    int intClave = 0;
                                    switch (rs.getString("tipoAlarma")) {
                                        case "ANTIROBO":
                                            //<editor-fold defaultstate="collapsed" desc="ANTIROBO">
                                            while(true)
                                            {
                                                if (getChannelStatus()< 3)
                                                    break IDENTIFICACION;
                                                intClave++;
                                                String op = tts.playTTS("Para solicitar visita del Supervisor, márque 1. Para solicitar un servicio técnico, márque 2. En cualquier momento de la llamada marque 9 para ser atendido por un Agente.", true, 1);
                                                if (!op.equals("") && op.equals("2"))
                                                {
                                                    tts.playTTS("Por favor después del tono informenos el motivo del servicio y presione numeral para terminar. Gracias.", false, 1);
                                                    recordFile("/var/spool/asterisk/monitor/" + ac.getUniqueId(), "gsm", "#", 30000, 0, true, 5000);

                                                    if (ac.getVariable("CALLERID(num)") != null)
                                                    {
                                                        st.executeUpdate("INSERT INTO servicio (idAlarma, motivo, solicitante) VALUES "
                                                                + "('"+ rs.getString("idAlarma") +"', "
                                                                + "'Solicitud telefónica por Cliente AUDIO: "+ ac.getUniqueId() +".gsm', 'Cliente <"+ ac.getVariable("CALLERID(num)") +">')");
                                                        tts.playTTS("Su solicitud ha sido incluída, nuestro departamento procederá a programar su servicio y le informaremos oportunamente. Gracias.", false, 1);
                                                    }
                                                    st.close();
                                                    break IDENTIFICACION;
                                                }
                                                else if (!op.equals("") && op.equals("1"))
                                                {
                                                    // TODO: Insertar un reacción tipo 909
                                                    insertarReaccion(rs.getString("idAlarma"), "Motivo: El cliente solicita visita de supervisión.", rs.getString("GPS"));
                                                    tts.playTTS("Su solicitud ha sido procesada, en estos momentos un supervisor se esta dirigiendo hacia su domicilio. Gracias por utilizar nuestros servicios.", false, 1);
                                                    break IDENTIFICACION;
                                                }
                                                else if (intClave >= INTENTOS) {
                                                    st.close();
                                                    break IDENTIFICACION;
                                                }
                                                else
                                                    tts.playTTS("Su opción no es válida, intentelo nuevamente", false, 1);
                                            }
                                            //</editor-fold>
                                        case "GPS":
                                            break;

                                    }
                                    //<editor-fold defaultstate="collapsed" desc="Código para la segunda clave">
                                    
                                    /*while(true)
                                     * {
                                     * intClave++;
                                     * String segClave = tts.playTTS("Por favor ingrese su segunda clave de seguridad.", true, 4);
                                     * if (segClave.length() == 4 && segClave.equals(rs.getString("claveConfirmacion")))
                                     * {
                                     * 
                                     * }
                                     * else if (intClave >= INTENTOS)
                                     * {
                                     * st.close();
                                     * break IDENTIFICACION;
                                     * } else
                                     * tts.playTTS("Su contraseña es incorrecta, intentelo nuevamente.", false, 1);
                                     * }*/
                                    //</editor-fold>
                                }
                                //</editor-fold>
                            }
                            else
                                tts.playTTS("Su número de identificación no se encuentra registrado. Por favor intentelo nuevamente.", false, 1);
                            //</editor-fold>
                        }
                        else if (dat.length() <= 6)
                            tts.playTTS("Su número de registro es demasiado corto, intentelo nuevamente.", false, 1);
                        else
                            tts.playTTS("No ha digitado nada aún. Por favor intentelo nuevamente.", false, 1);
                    }
                }
                //</editor-fold>
                else if (src != null && src.contains("gps"))
                {
                    
                }
                
            } catch (SQLException ex) {
                Logger.getLogger(Servicio.class.getName()).log(Level.SEVERE, null, ex);
            } finally
            {
                try {
                    conn.close();
                    ac.hangup();
                } catch (SQLException ex) {
                    Logger.getLogger(Servicio.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    //</editor-fold>
}