/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import com.gtranslate.*;
import java.io.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import sun.audio.AudioStreamSequence;
/**
 *
 * @author Kailu Mario
 */
public class TTS extends BaseAgiScript{
    
    private Connection conn;
    
    public TTS() {} // Constructor
    
    public void service(AgiRequest request, AgiChannel channel) throws AgiException
    {
        try {
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver); 
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
             
            String datos;
            String varFuente = channel.getVariable("ALARMA");            
            String[] strTemp    = varFuente.split("/");
            String guii         = strTemp[0];
            String categoria    = strTemp[1];
            String extension    = strTemp[2];
            boolean response    = Boolean.valueOf(strTemp[3]);
            String estado       = "SIN AUTENTICACIÓN";
                        
            if (categoria.contains("ALARMA"))
            {
                // <editor-fold defaultstate="collapsed" desc="AGI para llamada al cliente E informarle">
                Statement s = (Statement)conn.createStatement();
                String sSQL = "SELECT * FROM vitacora WHERE GUID = '"+ guii +"'";
                ResultSet res = s.executeQuery(sSQL);
                answer();
                
                if (res.first()) // Reproducción de mensaje
                {
                    int intentos            = 0;
                    int intentosGlobales    = 0;
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                        
                    sSQL = "SELECT * FROM alarma WHERE idAlarma = '"+res.getString("idAlarma")+"'";
                    Statement st1 = (Statement) conn.createStatement();
                    Statement st = (Statement) conn.createStatement();
                    ResultSet identidad = st1.executeQuery(sSQL);
                    
                    if (!extension.equals("") && !extension.equals("0000"))
                    {
                        // Esperar a que haya silencio
                        exec("Wait","2");
                        exec("SendDTMF", extension);
                        exec("WaitForSilence", "1000,3"); // 2 silencios para detectar que esta timbrando.
                        // Detectar cuando hablan                        
                    }
                    
                    while (true){
                        intentosGlobales++;
                        
                        if (response){ // Se necesita que el usuario ingrese DATOS
                            String fullMjs = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            fullMjs = fullMjs.replaceAll(" am.", "AM.").replaceAll(" pm.", " PM.");
                            datos = playTTS(fullMjs, response, 4);
                            
                            if (datos.equals("")) {
                                
                                intentos++;
                                estado="SIN CONFIRMAR";
                                if (intentos > 4)
                                {
                                    st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                    playTTS("Lo sentimos, no hemos podido verificar su identidad.", false, 1);
                                    break;
                                } else
                                    playTTS("No ha digitado nada aún, por favor intentelo nuevamente.", false, 1);
                            }
                            else if (!datos.equals("") && !datos.equals("-1"))
                            {
                                // verificar identidad.                                
                                if (identidad.first()){
                                    if (identidad.getString("claveConfirmacion").equals(datos)) 
                                    {
                                        Calendar c = Calendar.getInstance();                                        
                                        estado = "IDENTIDAD CONFIRMADA";
                                        st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii + "'");
                                        playTTS("Hemos confirmado su identidad. Hasta pronto.", false, 1);
                                        break;
                                    }
                                    else if (identidad.getString("claveCoaccion").equals(datos))
                                    {
                                        Calendar c = Calendar.getInstance();
                                        estado = "IDENTIDAD CONFIRMADA CON COACCIÓN";
                                        st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii + "'");
                                        playTTS("Hemos confirmado su identidad. Hasta pronto.", false, 1);  
                                        break;
                                        // Enviar Reacción
                                    }
                                    else{
                                        playTTS("Su contraseña es incorrecta, intentelo nuevamente.", false, 1);
                                        estado = "CONTRASEÑA EQUIVOCADA";
                                    }
                                }
                            }
                            else if (datos.equals("-1")){
                                Calendar c = Calendar.getInstance();
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii + "'");
                                break;
                            }
                        }
                        else // SOLO INFORMAR
                        {
                            String fullMsj = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            fullMsj = fullMsj.substring(0, 1).toUpperCase() + fullMsj.substring(1);
                            String dato = playTTS(fullMsj, response, 2);
                            Calendar c = Calendar.getInstance();
                            
                            if (dato.equals("OK"))
                                st.executeUpdate("UPDATE vitacora SET estado = 'ATENDIDO POR USUARIO', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii + "'");
                            else
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO DURANTE MENSAJE', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii + "'");
                            System.out.println(dato);
                            break;
                        }
                        
                        if (intentosGlobales > 3){
                            Calendar c = Calendar.getInstance();
                            st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"', fechaActualizacion = '"+ f.format(c.getTime()) +"' WHERE GUID = '"+ guii + "'");
                            //Enviar Reacción <causa> NO SE AUTENTICARON
                            break;
                        }
                    }
                    st1.close();
                    st.close();
                }
                res.close();
                s.close();
                //</editor-fold>
            }
            else if (categoria.contains("xCERRAR"))
            {
                // <editor-fold defaultstate="collapsed" desc="AGI para llamada al cliente E informarle">
                Statement s = (Statement)conn.createStatement();
                String sSQL = "SELECT * FROM vitacora WHERE GUID = '"+ guii +"'";
                ResultSet res = s.executeQuery(sSQL);
                answer();
                
                if (res.first()) // Reproducción de mensaje
                {
                    int intentos            = 0;
                    int intentosGlobales    = 0;
                    boolean breakGlobal     = false;
                    sSQL = "SELECT * FROM alarma WHERE idAlarma = '"+res.getString("idAlarma")+"'";
                    Statement st = (Statement) conn.createStatement();
                    ResultSet identidad = st.executeQuery(sSQL);
                    
                    if (!extension.equals("") && !extension.equals("0000"))
                    {
                        // Esperar a que haya silencio
                        exec("Wait","2");
                        exec("SendDTMF", extension);
                        exec("WaitForSilence", "1000,3"); // 2 silencios para detectar que esta timbrando.
                        // Detectar cuando hablan                        
                    }
                    
                    while (true){
                        intentosGlobales++;
                        System.out.println(res.getString("mensaje"));
                        
                        if (response){ // Se necesita que el usuario ingrese DATOS
                            String fullMjs = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            datos = playTTS(fullMjs, response, 4);
                            
                            if (datos.equals("")) {
                                
                                intentos++;
                                estado="SIN CONFIRMAR";
                                if (intentos > 4)
                                {
                                    st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                    playTTS("Lo sentimos, no hemos podido verificar su identidad.", false, 1);
                                    break;
                                } else
                                    playTTS("No ha digitado nada aún, por favor intentelo nuevamente.", false, 1);
                            }
                            else if (!datos.equals("") && !datos.equals("-1"))
                            {
                                // verificar identidad.                                
                                if (identidad.first()){
                                    if (identidad.getString("claveConfirmacion").equals(datos)) 
                                    {
                                        String min = playTTS("Hemos confirmado su identidad. por favor digite en minutos en cuanto tiempo realizará la activación de su sistema de alarma.", true, 2);
                                       
                                        // Cliclo para solicitar los minutos
                                        while (true) {
                                            if (min.equals(""))
                                                min = playTTS("Por favor digite en minutos en cuanto tiempo activará su sistema de alarma", true, 2);
                                            else if (!min.equals("") && !min.equals("-1"))
                                            {
                                                int minutos = Integer.parseInt(min);
                                                estado = "CIERRE CONFIRMADO EN: " + minutos + " minutos";
                                                st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                                playTTS("Gracias. si no recibimos la activación de su sistema de alarma en " + minutos + " minutos, le volvere a llamar. Hasta pronto.", false, 1);
                                                breakGlobal = true;
                                                break;
                                            }
                                            else{ 
                                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO' WHERE GUID = '"+ guii + "'");
                                                breakGlobal = true;
                                                break;
                                            }
                                        }
                                        // Break global
                                        if (breakGlobal) break;
                                    }
                                    else if (identidad.getString("claveCoaccion").equals(datos))
                                    {
                                        estado = "IDENTIDAD CONFIRMADA CON COACCIÓN";
                                        st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                        playTTS("Hemos confirmado su identidad. Hasta pronto. CCC", false, 1);  
                                        break;
                                        // Enviar Reacción
                                    }
                                    else{
                                        playTTS("Su contraseña es incorrecta, intentelo nuevamente.", false, 1);
                                        estado = "CONTRASEÑA EQUIVOCADA";
                                    }
                                }
                            }
                            else if (datos.equals("-1")){
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO' WHERE GUID = '"+ guii + "'");
                                break;
                            }
                        }
                        else // SOLO INFORMAR
                        {
                            String fullMsj = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            String dato = playTTS(fullMsj, response, 2);
                            if (dato.equals("OK"))
                                st.executeUpdate("UPDATE vitacora SET estado = 'ATENDIDO POR USUARIO' WHERE GUID = '"+ guii + "'");
                            else
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO DURANTE MENSAJE' WHERE GUID = '"+ guii + "'");
                            System.out.println(dato);
                            break;
                        }
                        
                        if (intentosGlobales > 3){
                            st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                            //Enviar Reacción <causa> NO SE AUTENTICARON
                            break;
                        }
                    }
                    st.close();
                }
                res.close();
                s.close();
                //</editor-fold>
            }            
            else if (categoria.contains("VGENERAL"))
            {
                // <editor-fold defaultstate="collapsed" desc="AGI para llamada al cliente E informarle">
                Statement s = (Statement)conn.createStatement();
                String sSQL = "SELECT * FROM vitacora WHERE GUID = '"+ guii +"'";
                ResultSet res = s.executeQuery(sSQL);
                answer();
                
                if (res.first()) // Reproducción de mensaje
                {
                    int intentos            = 0;
                    int intentosGlobales    = 0;
                    boolean breakGlobal     = false;
                    sSQL = "SELECT * FROM alarma WHERE idAlarma = '"+res.getString("idAlarma")+"'";
                    Statement st = (Statement) conn.createStatement();
                    ResultSet identidad = st.executeQuery(sSQL);
                    
                    if (!extension.equals("") && !extension.equals("0000"))
                    {
                        // Esperar a que haya silencio
                        exec("Wait","2");
                        exec("SendDTMF", extension);
                        exec("WaitForSilence", "1000,3"); // 2 silencios para detectar que esta timbrando.
                        // Detectar cuando hablan                        
                    }
                    
                    while (true){
                        intentosGlobales++;
                        System.out.println(res.getString("mensaje"));
                        
                        if (response){ // Se necesita que el usuario ingrese DATOS
                            String fullMjs = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            datos = playTTS(fullMjs, response, 4);
                            
                            if (datos.equals("")) {
                                
                                intentos++;
                                estado="SIN CONFIRMAR";
                                if (intentos > 4)
                                {
                                    st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                    playTTS("Lo sentimos, no hemos podido verificar su identidad.", false, 1);
                                    break;
                                } else
                                    playTTS("No ha digitado nada aún, por favor intentelo nuevamente.", false, 1);
                            }
                            else if (!datos.equals("") && !datos.equals("-1"))
                            {
                                // verificar identidad.                                
                                if (identidad.first()){
                                    if (identidad.getString("claveConfirmacion").equals(datos)) 
                                    {
                                        String min = playTTS("Hemos confirmado su identidad. por favor digite en minutos en cuanto tiempo desea reactivar las notificaciones.", true, 2);
                                       
                                        // Cliclo para solicitar los minutos
                                        while (true) {
                                            if (min.equals(""))
                                                min = playTTS("Por favor digite en minutos en cuanto tiempo activará las notificicaciones.", true, 2);
                                            else if (!min.equals("") && !min.equals("-1"))
                                            {
                                                int minutos = Integer.parseInt(min);
                                                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                                estado = "NOTIFICACIONES EN: " + minutos + " minutos";
                                                st.executeUpdate("UPDATE vitacora SET fechaActualizacion = '"+ f.format(Calendar.getInstance().getTime()) +"', estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                                playTTS("Gracias. pasados " + minutos + " minutos, se reactivarán las notificaciones. Hasta pronto.", false, 1);
                                                breakGlobal = true;
                                                break;
                                            }
                                            else{ 
                                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO' WHERE GUID = '"+ guii + "'");
                                                breakGlobal = true;
                                                break;
                                            }
                                        }
                                        // Break global
                                        if (breakGlobal) break;
                                    }
                                    else if (identidad.getString("claveCoaccion").equals(datos))
                                    {
                                        estado = "IDENTIDAD CONFIRMADA CON COACCIÓN";
                                        st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                        playTTS("Hemos confirmado su identidad. Hasta pronto.", false, 1);  
                                        break;
                                        // Enviar Reacción
                                    }
                                    else{
                                        playTTS("Su contraseña es incorrecta, intentelo nuevamente.", false, 1);
                                        estado = "CONTRASEÑA EQUIVOCADA";
                                    }
                                }
                            }
                            else if (datos.equals("-1")){
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO' WHERE GUID = '"+ guii + "'");
                                break;
                            }
                        }
                        
                        if (intentosGlobales > 3){
                            st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                            //Enviar Reacción <causa> NO SE AUTENTICARON
                            break;
                        }
                    }
                    st.close();
                }
                res.close();
                s.close();
                //</editor-fold>
            }
            else if (categoria.contains("APERTURA"))
            {
                // <editor-fold defaultstate="collapsed" desc="AGI para llamada al cliente E informarle">
                Statement s = (Statement)conn.createStatement();
                String sSQL = "SELECT * FROM vitacora WHERE GUID = '"+ guii +"'";
                ResultSet res = s.executeQuery(sSQL);
                answer();
                
                if (res.first()) // Reproducción de mensaje
                {
                    int intentos            = 0;
                    int intentosGlobales    = 0;
                    boolean breakGlobal     = false;
                    sSQL = "SELECT * FROM alarma WHERE idAlarma = '"+res.getString("idAlarma")+"'";
                    Statement st = (Statement) conn.createStatement();
                    ResultSet identidad = st.executeQuery(sSQL);
                    
                    while (true){
                        intentosGlobales++;
                        System.out.println(res.getString("mensaje"));
                        
                        if (response){ // Se necesita que el usuario ingrese DATOS
                            String fullMsj = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            fullMsj = fullMsj.substring(0, 1).toUpperCase() + fullMsj.substring(1);
                            System.out.println(fullMsj);
                            datos = playTTS(fullMsj, response, 4);
                            
                            if (datos.equals("")) {
                                
                                intentos++;
                                estado="SIN CONFIRMAR";
                                if (intentos > 4)
                                {
                                    st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                    playTTS("Lo sentimos, no hemos podido verificar su identidad.", false, 1);
                                    break;
                                } else
                                    playTTS("No ha digitado nada aún, por favor intentelo nuevamente.", false, 1);
                            }
                            else if (!datos.equals("") && !datos.equals("-1"))
                            {
                                // verificar identidad.                                
                                if (identidad.first()){
                                    if (identidad.getString("claveConfirmacion").equals(datos)) 
                                    {
                                        String min = playTTS("Hemos confirmado su identidad. por favor presione 1 para confirmar la apertura o 2 para enviar reacción.", true, 1);
                                       
                                        // Cliclo para solicitar los minutos
                                        while (true) {
                                            if (min.equals(""))
                                                min = playTTS("por favor presione 1 para confirmar la desactivación ó 2 para enviar reacción.", true, 2);
                                            else if (!min.equals("") && !min.equals("-1"))
                                            {
                                                int respuesta = Integer.parseInt(min);
                                                if (respuesta == 1)
                                                {
                                                    estado = "INDENTIDAD CONFIRMADA";
                                                    st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                                    playTTS("Gracias. Recuerde que realizar aperturas fuera de horario puede generar visita de la Policía Nacional. Hasta pronto", false, 1);
                                                }
                                                else 
                                                {
                                                    estado = "APERTURA NO AUTORIZADA";
                                                    st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                                    playTTS("Gracias. En estos momentos estamos enviando la reacción. Le estaré informando pronto.", false, 1);
                                                }
                                                breakGlobal = true;
                                                break;
                                            }
                                            else{ 
                                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO' WHERE GUID = '"+ guii + "'");
                                                breakGlobal = true;
                                                break;
                                            }
                                        }
                                        // Break global
                                        if (breakGlobal) break;
                                    }
                                    else if (identidad.getString("claveCoaccion").equals(datos))
                                    {
                                        estado = "IDENTIDAD CONFIRMADA CON COACCIÓN";
                                        st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                                        playTTS("Hemos confirmado su identidad. Hasta pronto. CCC", false, 1);  
                                        break;
                                        // Enviar Reacción
                                    }
                                    else{
                                        playTTS("Su contraseña es incorrecta, intentelo nuevamente.", false, 1);
                                        estado = "CONTRASEÑA EQUIVOCADA";
                                    }
                                }
                            }
                            else if (datos.equals("-1")){
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO POR USUARIO' WHERE GUID = '"+ guii + "'");
                                break;
                            }
                        }
                        else // SOLO INFORMAR
                        {
                            String fullMsj = res.getString("mensaje").toLowerCase().replaceAll(" am ", " AM ").replaceAll(" pm ", " PM ");
                            String dato = playTTS(fullMsj, response, 2);
                            if (dato.equals("OK"))
                                st.executeUpdate("UPDATE vitacora SET estado = 'ATENDIDO POR USUARIO' WHERE GUID = '"+ guii + "'");
                            else
                                st.executeUpdate("UPDATE vitacora SET estado = 'COLGADO DURANTE MENSAJE' WHERE GUID = '"+ guii + "'");
                            System.out.println(dato);
                            break;
                        }
                        
                        if (intentosGlobales > 3){
                            st.executeUpdate("UPDATE vitacora SET estado = '"+ estado +"' WHERE GUID = '"+ guii + "'");
                            //Enviar Reacción <causa> NO SE AUTENTICARON
                            break;
                        }
                    }
                    st.close();
                }
                res.close();
                s.close();
                //</editor-fold>
            }
            
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(TTS.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally
        {
            try {
                if (conn != null) conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(TTS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
    
    // Función para Reproducir audios ya sea en modo Playback o en modo Backgroud    
    public String playTTS(String texto, boolean Backgroud, int cantidadDigitos)
    // <editor-fold defaultstate="collapsed" desc="Play Texto To Speech">
    {
        int inicio = 0;
        ArrayList nTexto = new ArrayList();
        
        while (true)
        {
            String strTemp ;
            int fin;
            if ( (inicio + 100) <= texto.length() )
            {
                strTemp = texto.substring(inicio, (inicio + 100) );
                if ( (strTemp.lastIndexOf(".") > strTemp.lastIndexOf(",")) & strTemp.lastIndexOf(".") > 0 ) 
                    fin = strTemp.lastIndexOf(".") + 1;
                
                else if ( (strTemp.lastIndexOf(".") < strTemp.lastIndexOf(",")) & strTemp.lastIndexOf(",") > 0)
                    fin = strTemp.lastIndexOf(",") + 1;
                
                else
                    fin = strTemp.lastIndexOf(" ") + 1;
                
                if (!strTemp.substring(0, fin).trim().equals(""))
                    nTexto.add(strTemp.substring(0, fin).trim());
                
                inicio = inicio + fin;
            }
            else
            {
                if (!texto.substring(inicio).trim().equals(""))
                    nTexto.add(texto.substring(inicio).trim());            
                break;
            }
       }
        
        // Grabar audios.
        InputStream sonido          = null;
        AudioStreamSequence sonidos = null;
        ArrayList sonidoIndividual     = new ArrayList();
        String audioId = UUID.randomUUID().toString();
        
        try {
            
            Audio audio = Audio.getInstance();
            for (int i=0; i < nTexto.size(); i++)
            {
               sonido = audio.getAudio(nTexto.get(i).toString() ,Language.SPANISH);
               sonidoIndividual.add(sonido);
            }
            sonidos = new AudioStreamSequence(Collections.enumeration(sonidoIndividual));
            
            OutputStream out = new FileOutputStream(new File("/var/spool/axar/" + audioId + ".mp3"));
            int read;
            byte[] bytes = new byte[1024];
            while ((read = sonidos.read(bytes)) != -1) {
                    out.write(bytes, 0, read);
            }
            sonidos.close();
            out.flush();
            out.close();
            
            // Conversion con SOX
            Runtime runner = Runtime.getRuntime();
            runner.exec("sox -t mp3 /var/spool/axar/"+ audioId +".mp3 -r 8000 -b 16 /var/lib/asterisk/sounds/"+ audioId +".wav tempo 1.26");
            Thread.sleep(200);
            
            // Reproducción del archivo en WAV
            if (Backgroud)
            {
                if (getChannelStatus() > 2){
                    String data = getData(audioId, 6000, cantidadDigitos);
                    if ( data.contains("timeout"))
                        return "";
                    else
                        return data;
                }
                return "-1";
            }
            else {
                String val = "";
                for (int i = 0 ; i < cantidadDigitos; i++){                    
                    streamFile(audioId);            // Reproducción
                    if (getChannelStatus() < 3)     // Fin Reproduccion, aun esta vivo?
                        break;                      // No
                    val = "OK";                     // Si
                    if (cantidadDigitos > 1)        // Solo esperar si hay que repetir el audio
                        exec("wait", "1");
                }
                return val; // Retorna el valor
            }
            
        } catch (IOException | InterruptedException | AgiException ex) {            
            Logger.getLogger(TTS.class.getName()).log(Level.SEVERE, null, ex);
            try {
                Thread.sleep(2000); // Tiempo de espera de 2 segundos antes de devolver el mensaje de error
            } catch (InterruptedException ex1) {
                Logger.getLogger(TTS.class.getName()).log(Level.SEVERE, null, ex1);
            }
        } finally {
            
            // Sin importar, eliminar el último audio
            (new File("/var/spool/axar/"+ audioId +".mp3")).delete();
            (new File("/var/lib/asterisk/sounds/"+ audioId +".wav")).delete();
            
            try {
                if (sonidos != null) sonidos.close();
                if (sonido != null) sonido.close();
            } catch (IOException ex) {
                Logger.getLogger(TTS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return "";
    }// </editor-fold>
}
