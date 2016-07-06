/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Kailu Mario
 */
public class PopupAuthenticator extends javax.mail.Authenticator {    
    String username;
    String password;

    public PopupAuthenticator(String username,String password){
        this.username=username;
        this.password=password;
    }

    @Override
    public javax.mail.PasswordAuthentication getPasswordAuthentication() {
        return new javax.mail.PasswordAuthentication(username,password);
    }
}
