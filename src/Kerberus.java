
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.Statement;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
 */
public class Kerberus extends BaseAgiScript{

    public void service(AgiRequest request, AgiChannel channel) throws AgiException
    {
        Connection conn = null;
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        try {
            // Declaración de variables
            String driver = "com.mysql.jdbc.Driver";
            Class.forName(driver); 
            String url = "jdbc:mysql://localhost/axar";
            conn = (Connection)(DriverManager.getConnection(url, "kerberus", "aster1sk"));
            
            boolean emitidoenTono;
            int contadorGeneral = 0;
            boolean tipoHandshake = true;
            boolean emitirHandshake = true;
            StringBuilder sSQL = new StringBuilder();
            StringBuilder trama;
            String inbound;
            
            // INICIO
            answer();               // Contestar
            setAutoHangup(2*60);    // Colgar en 2 minutos.
            exec("Wait","1");       // Esperar 1 segundo.
            if (this.getVariable("INGRESO") != null && this.getVariable("INGRESO").equals("SIP")) inbound = "SIP";
            else inbound = "ANALOG";
            
            while (true){
                
                if (tipoHandshake && emitirHandshake)
                {
                    tipoHandshake = !tipoHandshake;
                    emitidoenTono = true;
                    exec("Playtones", "!0/600,!1400/100,!0/100,!2300/100");
                }
                else if (!tipoHandshake && emitirHandshake)
                {
                    tipoHandshake = !tipoHandshake;
                    emitidoenTono = false;
                    streamFile("handshake");
                }

                // Creación de la trama vacia.
                trama = new StringBuilder();
                int checksumTrama = 0;
                int checkSum = 0;
                int contadorValidador = 0;
                
                while (trama.length() < 16)
                {
                    if (getChannelStatus() < 3) break;  // Verificar que el canal este activo
                    if (contadorValidador > 3) break;   // Ya ha hecho 3 intentos y no transmite.
                    
                    char digito = waitForDigit(1500);                
                    //System.out.println("Digito: \"" + digito + "\" \"" + (int)digito + "\"");
                    
                    if ( (int)digito > 0 )
                    {
                         trama.append(digito);
                         if (trama.length() < 16)
                            checksumTrama += checkSum(digito, false);
                         else
                            checkSum = checkSum(digito, true);
                    }
                    else
                        contadorValidador++;
                }
                
                // Verificar El CHECKSUM
                if (trama.length() == 16)
                //<editor-fold defaultstate="collapsed" desc="Validador de Trama">
                {
                    emitirHandshake = false;                            // Trama de 16 digitos, no emitir Handshake, siguiente Evento.
                    
                    int hMultiplo;
                    int mod = checksumTrama % 15;
                    if (mod != 0)
                        hMultiplo = (checksumTrama/15) + 1;
                    else
                        hMultiplo = checksumTrama / 15;
                    
                    if ((15*hMultiplo - checksumTrama) == checkSum && trama.toString().substring(4, 6).equals("18")){    // TRAMA DE CHECKSUM CORRECTA
                        
                        String evento       = trama.toString();
                        String idAlarma     = evento.substring(0, 4);
                        int codevento       = Integer.parseInt(evento.substring(7, 10));
                        String particion    = evento.substring(10, 12);
                        String zona         = evento.substring(12, 15);
                        String tipoEvento   = "";
                        
                        switch ( Integer.parseInt(evento.substring(6, 7)) )
                        {
                            case 1:
                                if (codevento >= 400 && codevento < 410)
                                    tipoEvento = "APERTURA";
                                else
                                    tipoEvento = "EVENTO";
                                break;
                            case 3:
                                if (codevento >= 400 && codevento < 410)
                                    tipoEvento = "CIERRE";
                                else
                                    tipoEvento = "RESTAURE";
                                break;
                        }
                        
                        System.out.println("CheckSum Correcto, TRAMA: " + evento);
                        sSQL.append("('"+ dateFormat.format(new Date()) +"', '"+ evento +"', '"+ idAlarma +"', '"+ particion +"', '"+ codevento +"', '"+ zona +"', '"+ tipoEvento +"', '"+ channel.getVariable("CALLERID(num)") +"', '"+ inbound +"'), ");
                        
                        // Verificar emitidoenTono (verificar KISSOFF)
                        exec("Playtones", "!0/100,!1400/800");
                        exec("Wait", "1");
                        tipoHandshake = !tipoHandshake;
                    }
                    else
                    {
                        System.out.println("CheckSum INCORRECTO, TRAMA:" + trama.toString());
                        //sSQL.append("('"+ dateFormat.format(new Date()) +"', '"+ trama.toString() +"', '9999', '99', '9999', '99', 'DESCONOCIDO', '"+ channel.getVariable("CALLERID(num)") +"'), ");
                    }
                }
                else if (trama.length() == 0)
                    contadorGeneral++; // No esta transmitiendo nada trama.lenght = 0;
                //</editor-fold>
                
                if (getChannelStatus()< 3 || contadorGeneral > 3) 
                    break;
            }
            
            // Fin del while verificar si hay algo que insertar
            if (sSQL.length() > 0){
                String nSQL = sSQL.toString().substring(0, sSQL.length()-2);
                Statement s = (Statement) conn.createStatement();   // Crear la conexión.
                s.executeUpdate("INSERT INTO evento (fecha, trama, idAlarma, particion, evento, zona, tipoEvento, clid, inbound) VALUES " + nSQL);
            }
            conn.close();
            
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(Kerberus.class.getName()).log(Level.SEVERE, null, ex);
            
        }
        finally
        {
            try {
                if(conn != null && !conn.isClosed()) conn.close();
            } catch (SQLException ex1) {
                Logger.getLogger(Kerberus.class.getName()).log(Level.SEVERE, null, ex1);
            }
            hangup();
        }
    }
    
    private int checkSum(char digito, boolean end)
    {
        if (!end)
        {
            switch (digito){
                case (char)48:  // 0
                    return 10;
                case (char)42:  // *
                    return 11;
                case (char)35:  // #
                    return 12;
                case (char)68:  // D
                    return 13;
                case (char)69:  // E
                    return 14;
                case (char)70:  // F
                    return 15;
                default:
                    return ((int)digito)-48;
            }       
        } else
        {
            switch (digito){
                case (char)42:  // *
                    return 11;
                case (char)48:  // 0
                    return 10;
                case (char)35:  // #
                    return 12;
                case (char)65:  // D
                    return 13;
                case (char)66:  // E
                    return 14;
                case (char)67:  // F
                    return 0;
                default:
                    return ((int)digito)-48;
            }
        }
    }
}
