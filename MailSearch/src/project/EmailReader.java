/*based on http://www.codejava.net/java-ee/javamail/using-javamail-for-searching-e-mail-messages*/
package project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

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
import javax.mail.internet.MimeMessage;
import javax.mail.search.AndTerm;
import javax.mail.search.SearchTerm;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
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
		// conn.prepareStatement("DROP TABLE mail_backup").execute();
		conn.prepareStatement(
				"CREATE TABLE IF NOT EXISTS mail_backup (id INTEGER AUTO_INCREMENT NOT NULL, fromSender VARCHAR(100),"
						+ "subject VARCHAR(1000), sentDate VARCHAR(30),savedUrl VARCHAR(100), attachmentYN int);")
				.execute();

	}

	/**
	 * Downloads new messages and saves attachments to disk if any.
	 * 
	 * @param host
	 * @param port
	 * @param userName
	 * @param password
	 * @param selDrive
	 * @throws SQLException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public void downloadEmailAttachments(String host, String port, String userName, String password, String selDrive,
			Date startDate, Date endDate) throws SQLException, ClassNotFoundException {
		Properties props = System.getProperties();
		props.setProperty("mail.store.protocol", "imaps");
		try {
			String saveDirectory = selDrive + "\\MailBackup";
			EmailReader receiver = new EmailReader();
			receiver.setSaveDirectory(saveDirectory);
			Session session = Session.getDefaultInstance(props, null);
			Store store = session.getStore("imaps");
			store.connect("imap.gmail.com", userName, password);
			// ...
			Folder inbox = store.getFolder("INBOX");
			inbox.open(Folder.READ_ONLY);
			SearchTerm olderThan = new ReceivedDateTerm(ComparisonTerm.LT, startDate);
			SearchTerm newerThan = new ReceivedDateTerm(ComparisonTerm.GT, endDate);
			SearchTerm andTerm = new AndTerm(olderThan, newerThan);
			// Message[] arrayMessages = inbox.getMessages(); <--get all
			// messages
			Message[] arrayMessages = inbox.search(andTerm);
			for (int i = arrayMessages.length; i > 0; i--) { // from newer to
																// older
				Message msg = arrayMessages[i - 1];
				Address[] fromAddress = msg.getFrom();
				String from = fromAddress[0].toString();
				String subject = msg.getSubject();
				String sentDate = msg.getSentDate().toString();
				String receivedDate = msg.getReceivedDate().toString();

				String contentType = msg.getContentType();
				String messageContent = "";
				String fileNames = new SimpleDateFormat("yyyyMMddhhmmss").format(new Date());
				String year = new SimpleDateFormat("yyyy").format(msg.getSentDate());
				String month = new SimpleDateFormat("MM").format(msg.getSentDate());
				String date = new SimpleDateFormat("dd").format(msg.getSentDate());
				String finalFilePath = saveDirectory + "\\" + year + "\\" + month + "\\" + date + "\\";
				String file = finalFilePath + "/" + fileNames + "mail.eml";

				File theDir = new File(finalFilePath);
				// if the directory does not exist, create it
				File fileDir = new File(finalFilePath);

				boolean b = false;

				if (!fileDir.exists()) {
					b = fileDir.mkdirs();
				}
				if (b) {
					System.out.println("Directory successfully created");
				} else {
					System.out.println("Directory exists");
				}

				msg.writeTo(new FileOutputStream(new File(file)));
				// store attachment file name, separated by comma
				String attachFiles = "";

				if (contentType.contains("multipart")) {
					// content may contain attachments
					Multipart multiPart = (Multipart) msg.getContent();
					int numberOfParts = multiPart.getCount();
					for (int partCount = 0; partCount < numberOfParts; partCount++) {
						MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
						if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
							// this part is attachment
							String fileName = part.getFileName();
							attachFiles += fileName + ", ";
							// part.saveFile(saveDirectory + File.separator +
							// fileName);
						} else {
							// this part may be the message content
							messageContent = part.getContent().toString();
						}
					}
					if (attachFiles.length() > 1) {
						attachFiles = attachFiles.substring(0, attachFiles.length() - 2);
					}
				} else if (contentType.contains("text/plain") || contentType.contains("text/html")) {
					Object content = msg.getContent();
					if (content != null) {
						messageContent = content.toString();
					}
				}

				// conn.prepareStatement("INSERT INTO mail_backup values (null,
				// '//"+from+"//','//"+subject+"//',"+sentDate+",'//"+file+"//',0);").execute();

				stmt = conn.prepareStatement(
						"INSERT INTO mail_backup (fromSender, subject,sentDate,savedUrl,attachmentYN) values (?, ?,?,?,?)");
				// stmt.setString(1, "null");
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
	
	
	public static Set<File> listf(String directoryName, ArrayList<File> files,JTable mails) throws Exception {
	    File directory = new File(directoryName);
	    Set<File> hs = new HashSet<>();
	    // get all the files from a directory
	    File[] fList = directory.listFiles();
	    for (File file : fList) {
	        if (file.isFile()) {
	            //files.add(file);
	            hs.add(file);
	        } else if (file.isDirectory()) {
	            listf(file.getAbsolutePath(), files,mails);
	        }
	    }
	    for (File file : hs) {
	    	System.out.println(file.getName());
	    	display(file,mails);
		}
	    return hs;
	}
	
	   public static void display(File emlFile,JTable mails) throws Exception{
		   Properties props = System.getProperties();
	        props.put("mail.host", "smtp.gmail.com");
	        props.put("mail.transport.protocol", "smtp");
	        Session mailSession = Session.getDefaultInstance(props, null);
	        InputStream source = new FileInputStream(emlFile);
	        MimeMessage message = new MimeMessage(mailSession, source);
	        System.out.println("Subject : " + message.getSubject());
	        System.out.println("From : " + message.getFrom()[0]);
	        //System.out.println("Body : " +  message.getContent());
	        System.out.println("filename :" + emlFile.getName());
	        System.out.println("Path :" +  emlFile.getPath());
	        System.out.println("--------------");
	        DefaultTableModel model = (DefaultTableModel) mails.getModel();
	        Object[] row={message.getFrom()[0],message.getSubject()};
	        model.addRow( row);
	    }

}
