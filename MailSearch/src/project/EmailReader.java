/*based on http://www.codejava.net/java-ee/javamail/using-javamail-for-searching-e-mail-messages*/
package project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;
import javax.mail.search.AndTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.ComparisonTerm;


public class EmailReader {
    private String saveDirectory;
    private Connection conn = null;
    PreparedStatement stmt = null;
    /**
     * Sets the directory where attached files will be stored.
     * 
     * @param dir
     *            absolute path of the directory
     */
    public void setSaveDirectory(String dir) {
        this.saveDirectory = dir;
    }

    public EmailReader() throws ClassNotFoundException, SQLException {
		Class.forName("org.h2.Driver");
        conn = DriverManager.getConnection("jdbc:h2:mem:test-database-name");
        //conn.prepareStatement("DROP TABLE  mail_backup").execute();
        conn.prepareStatement("CREATE TABLE IF NOT EXISTS mail_backup (id INTEGER AUTO_INCREMENT NOT NULL, fromSender VARCHAR(100),"
        		+ "subject VARCHAR(1000), sentDate VARCHAR(30),savedUrl VARCHAR(100), attachmentYN int);").execute();
        
	}

	/**
     * Downloads new messages and saves attachments to disk if any.
     * 
     * @param host
     * @param port
     * @param userName
     * @param password
     * @throws SQLException 
     * @throws ClassNotFoundException 
     * @throws IOException
     */
    public void downloadEmailAttachments(String host, String port,
            String userName, String password, Date startDate, Date endDate) throws SQLException, ClassNotFoundException {
        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        try {
        	String saveDirectory = "D:\\Backup";
        	EmailReader receiver = new EmailReader();
            receiver.setSaveDirectory(saveDirectory);
            Session session = Session.getDefaultInstance(props, null);
            Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", userName, password);
            // ...
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            SearchTerm olderThan = new ReceivedDateTerm (ComparisonTerm.LT, startDate);
            SearchTerm newerThan = new ReceivedDateTerm (ComparisonTerm.GT, endDate);
            SearchTerm andTerm = new AndTerm(olderThan, newerThan);
            //Message[] arrayMessages = inbox.getMessages(); <--get all messages
            Message[] arrayMessages = inbox.search(andTerm);
            for (int i = arrayMessages.length; i > 0; i--) { //from newer to older
                Message msg = arrayMessages[i-1];
                Address[] fromAddress = msg.getFrom();
                String from = fromAddress[0].toString();
                String subject = msg.getSubject();
                String sentDate = msg.getSentDate().toString();
                String receivedDate = msg.getReceivedDate().toString();

                String contentType = msg.getContentType();
                String messageContent = "";
                String fileNames = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date());
                String file= saveDirectory+"/"+fileNames+"mail.eml";
                msg.writeTo(new FileOutputStream(new File(file)));
                // store attachment file name, separated by comma
                String attachFiles = "";

                if (contentType.contains("multipart")) {
                    // content may contain attachments
                    Multipart multiPart = (Multipart) msg.getContent();
                    int numberOfParts = multiPart.getCount();
                    for (int partCount = 0; partCount < numberOfParts; partCount++) {
                        MimeBodyPart part = (MimeBodyPart) multiPart
                                .getBodyPart(partCount);
                        if (Part.ATTACHMENT.equalsIgnoreCase(part
                                .getDisposition())) {
                            // this part is attachment
                            String fileName = part.getFileName();
                            attachFiles += fileName + ", ";
                            part.saveFile(saveDirectory + File.separator + fileName);
                        } else {
                            // this part may be the message content
                            messageContent = part.getContent().toString();
                        }
                    }
                    if (attachFiles.length() > 1) {
                        attachFiles = attachFiles.substring(0,
                                attachFiles.length() - 2);
                    }
                } else if (contentType.contains("text/plain")
                        || contentType.contains("text/html")) {
                    Object content = msg.getContent();
                    if (content != null) {
                        messageContent = content.toString();
                    }
                }
                
                //conn.prepareStatement("INSERT INTO mail_backup values (null, '//"+from+"//','//"+subject+"//',"+sentDate+",'//"+file+"//',0);").execute();
                
                stmt = conn.prepareStatement("INSERT INTO mail_backup (fromSender, subject,sentDate,savedUrl,attachmentYN) values (?, ?,?,?,?)");
                //stmt.setString(1, "null");
                stmt.setString(1, from);
                stmt.setString(2, subject);
                stmt.setString(3, sentDate);
                stmt.setString(4, file);
                stmt.setInt(5, 0);
                stmt.executeUpdate();
                
                
                // print out details of each message
                System.out.println("Message #" + (i + 1) + ":");
                System.out.println("\t From: " + from);
                System.out.println("\t Subject: " + subject);
                System.out.println("\t Received: " + sentDate);
                System.out.println("\t Message: " + messageContent);
                System.out.println("\t Attachments: " + attachFiles);
            }

            // disconnect
            inbox.close(false);
            store.close();
            
            ResultSet rs = conn.prepareStatement("SELECT * FROM mail_backup").executeQuery();
            while (rs.next()) {
                System.out.println(rs.getInt(1) + " | " + rs.getString(2));
            }

        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (MessagingException e) {
            e.printStackTrace();
            System.exit(2);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


}
