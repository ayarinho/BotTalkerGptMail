package de.bsi.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.bsi.openai.chatgpt.ChatCompletionResponse;
import de.bsi.openai.chatgpt.CompletionRequest;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.ImageHelper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.*;
import java.sql.*;
import java.util.List;
import java.util.Properties;


@Service
@EnableAsync
public class EmailConf {

    @Autowired
    private ObjectMapper jsonMapper;

    @Autowired
    private OpenAiApiClient client;

    @Autowired
    private ResourceLoader resourceLoader;


    private static final String EMAIL = "bottalkermail@gmail.com";
    private static final String PASSWORD = "bouq utxn aefu ispa";
    private static final String GPT_MODEL = "gpt-4-1106-preview";

    private Session session;


    @Scheduled(fixedDelay = 2000)
    public void checkForNewMessages() throws InterruptedException {

        Properties properties = new Properties();
        properties.setProperty("mail.store.protocol", "imaps");
        properties.setProperty("mail.imaps.host", "imap.gmail.com");
        properties.setProperty("mail.imaps.port", "993");
        properties.setProperty("mail.imaps.connectiontimeout", "5000");
        properties.setProperty("mail.imaps.timeout", "5000");
        properties.setProperty("mail.imaps.ssl.enable", "true");
        properties.setProperty("mail.imaps.ssl.trust", "*");
        session = Session.getInstance(properties, null);

        try (Store store = session.getStore("imaps")) {
            int maxAttempts = Integer.MAX_VALUE;
            int attempts = 0;
            boolean connected = false;

            while (!connected && attempts < maxAttempts) {
                try {
                    store.connect("imap.gmail.com", EMAIL, PASSWORD);
                    connected = true;
                } catch (MessagingException e) {
                    e.printStackTrace();
                    Thread.sleep(5000); // Attendre 5 secondes avant de réessayer
                    attempts++;
                }
            }

            if (connected) {
                try (Folder inbox = store.getFolder("inbox")) {
                    inbox.open(Folder.READ_ONLY);

                    inbox.addMessageCountListener(new MessageCountAdapter() {
                        @Override
                        public void messagesAdded(final MessageCountEvent ev) {
                            Message[] messages = ev.getMessages();

                            for (Message message : messages) {
                                // Traitement du message
                                processMessage(message);
                            }
                        }
                    });

                    while (true) {
                        // Vérifier périodiquement les nouveaux messages
                        inbox.getMessageCount();
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Rétablir le statut d'interruption du thread
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Échec de la connexion après plusieurs tentatives.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Async
    public void processMessage(final Message message) {
        try {
            final String from = InternetAddress.toString(message.getFrom());
            final String subject = message.getSubject();
            final Object content = message.getContent();
            if (content instanceof Multipart) {
                final Multipart multipart = (Multipart) content;

                for (int i = 0; i < multipart.getCount(); i++) {
                    final BodyPart bodyPart = multipart.getBodyPart(i);

                    if (isImageAttachment(bodyPart)) {
                        processImageAttachment(message, bodyPart, from);
                    } else if (isPdfAttachment(bodyPart)) {
                        processPdfAttachment(bodyPart, from, subject, message);
                    } else if (isExcelAttachment(bodyPart)) {
                        processExcelAttachment(bodyPart, from, subject, message);
                    } else if (bodyPart.getContent() instanceof String && bodyPart.getContent() != null &&
                            !bodyPart.getContentType().toLowerCase().startsWith("text/html")) {
                        processTextMessage((String) bodyPart.getContent(), from, subject, message);
                    }
                }
            }
        } catch (final Exception e) {
            handleException("Erreur lors du traitement du message.", e);
        }
    }

    private void processExcelAttachment(final BodyPart bodyPart, final String from, final String subject, final Message originalMessage) {
        try {
            final String excelContent = extractExcelContent(bodyPart);
            final String response = generateChatGPTResponse(excelContent, from);
            sendEmail(originalMessage, subject, response);
        } catch (Exception e) {
            handleException("Erreur lors du traitement de la pièce jointe Excel.", e);
        }
    }


    private boolean isExcelAttachment(BodyPart bodyPart) throws MessagingException {
        final String fileName = bodyPart.getFileName();

        return fileName != null && (fileName.toLowerCase().endsWith(".xls") || fileName.toLowerCase().endsWith(".xlsx"));
    }


    private String extractExcelContent(final BodyPart bodyPart) {
        try (final InputStream inputStream = bodyPart.getInputStream()) {

            HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
            HSSFSheet sheet = workbook.getSheetAt(0);
            StringBuilder content = new StringBuilder();

            for (Row row : sheet) {
                for (Cell cell : row) {
                    switch (cell.getCellType()) {
                        case STRING -> content.append(cell.getStringCellValue()).append(" ");
                        case NUMERIC -> content.append(cell.getNumericCellValue()).append(" ");
                        case BOOLEAN -> content.append(cell.getBooleanCellValue()).append(" ");

                        // Vous pouvez gérer d'autres types de cellules selon vos besoins
                        default -> content.append(cell.toString()).append(" ");
                    }
                }
                content.append("\n");
            }

            workbook.close();
            return content.toString().trim();
        } catch (final Exception e) {
            handleException("Erreur lors de l'extraction du contenu Excel.", e);
            return "";
        }
    }

    private void processImageAttachment(final Message message, final BodyPart bodyPart, final String from) throws MessagingException {
        try {
            final InputStream imageStream = bodyPart.getInputStream();
            BufferedImage bufferedImage = ImageIO.read(imageStream);

            bufferedImage = preprocessImage(bufferedImage);

            final ITesseract tesseract = new Tesseract();

            tesseract.setDatapath("/app/tessdata");
            tesseract.setLanguage("eng"); // Définir la langue si nécessaire

            try {
                String response = tesseract.doOCR(bufferedImage);
                sendEmail(message, bodyPart.getFileName(), response);
                System.out.println("Texte extrait : " + response);
            } catch (TesseractException e) {
                e.printStackTrace();
            }

            // Additional Tesseract setup and usage
        } catch (MessagingException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }


    // TODO
    public static BufferedImage preprocessImage(BufferedImage image) {
        // Améliorer le contraste de l'image
        image = ImageHelper.convertImageToGrayscale(image);

        image = ImageHelper.invertImageColor(image);

        // Réduire le bruit de l'image
        image = denoiseImage(image);

        // Redimensionner l'image (facultatif)
        int targetWidth = 800;
        int targetHeight = 600;
        image = resizeImage(image, targetWidth, targetHeight);

        return image;
    }

    // TODO
    public static BufferedImage denoiseImage(BufferedImage image) {
        // Créer un noyau de filtre de lissage (par exemple, un filtre de moyenne)
        float[] matrix = {
                1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f
        };
        Kernel kernel = new Kernel(3, 3, matrix);

        // Appliquer le filtre de lissage à l'image
        ConvolveOp op = new ConvolveOp(kernel);
        BufferedImage denoisedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        op.filter(image, denoisedImage);

        return denoisedImage;
    }

    // TODO
    public static BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return resizedImage;
    }


    private void processPdfAttachment(final BodyPart bodyPart, final String from, final String subject, final Message originalMessage) {
        try {
            final String pdfContent = extractPdfContent(bodyPart);
            final String response = generateChatGPTResponse(pdfContent, from);
            sendEmail(originalMessage, subject, response);
        } catch (Exception e) {
            handleException("Erreur lors du traitement de la pièce jointe PDF.", e);
        }
    }

    private void processTextMessage(final String textMessage, final String from, final String subject, final Message originalMessage) {
        try {
            final String response = generateChatGPTResponse(textMessage, from);
            sendEmail(originalMessage, subject, response);
        } catch (final Exception e) {
            handleException("Erreur lors du traitement du message texte.", e);
        }
    }

    private boolean isImageAttachment(final BodyPart bodyPart) throws MessagingException {
        final String fileName = bodyPart.getFileName();
        final String contentType = bodyPart.getContentType();

        return fileName != null && fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp|tiff)$") &&
                contentType != null && contentType.toLowerCase().startsWith("image/");
    }

    private boolean isPdfAttachment(BodyPart bodyPart) throws MessagingException {
        final String fileName = bodyPart.getFileName();
        final String contentType = bodyPart.getContentType();

        return fileName != null && fileName.toLowerCase().endsWith(".pdf") &&
                contentType != null && contentType.toLowerCase().startsWith("application/pdf");
    }

    private String extractPdfContent(final BodyPart bodyPart) {
        try (final InputStream inputStream = bodyPart.getInputStream();
             final PDDocument document = PDDocument.load(inputStream)) {
             final PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document);
        } catch (final Exception e) {
            handleException("Erreur lors de l'extraction du contenu PDF.", e);
            return "";
        }
    }

    private String generateChatGPTResponse(final String message, final String from) {
        try {
            final CompletionRequest.Message userMessage = new CompletionRequest.Message("user", message);
            final List<CompletionRequest.Message> messages = List.of(userMessage);
            final CompletionRequest request = new CompletionRequest(GPT_MODEL, messages, 0.7);

            final String postBodyJson = jsonMapper.writeValueAsString(request);
            final String responseBody = client.postToOpenAiApi(postBodyJson, OpenAiApiClient.OpenAiService.GPT_4);

            ObjectMapper mapper = new ObjectMapper();
            final ChatCompletionResponse response = mapper.readValue(responseBody, ChatCompletionResponse.class);

            final String assistantResponse = response.getChoices()[0].getMessage().getContent();
            System.out.println("Assistant: " + assistantResponse);
            return assistantResponse;
        } catch (Exception e) {
            handleException("Erreur lors de la génération de la réponse de ChatGPT.", e);
            return "";
        }
    }

    public void sendEmail(Message originalMessage, String subject, String response) {
        String email = "bottalkermail@gmail.com";
        String password = "bouq utxn aefu ispa";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.trust", "*");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(email, password);
            }
        });

        try {
            MimeMessage replyMessage = new MimeMessage(session);

            // Utiliser le même "From" que le message original
            replyMessage.setFrom(new InternetAddress(email));

            // Répondre au "Reply-To" ou "From" si "Reply-To" n'est pas défini
            Address[] replyToAddresses = originalMessage.getReplyTo();
            if (replyToAddresses != null && replyToAddresses.length > 0) {
                replyMessage.setRecipient(Message.RecipientType.TO, replyToAddresses[0]);
            } else {
                replyMessage.setRecipient(Message.RecipientType.TO, originalMessage.getFrom()[0]);
            }

            // Mettre en place le "Subject" et le "Content"
            replyMessage.setSubject(subject);
            replyMessage.setText(response);

            // Envoyer le message
            Transport.send(replyMessage);
            System.out.println("Réponse au message envoyée avec succès.");
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private void handleException(String message, Exception e) {
        System.err.println(message);
        e.printStackTrace();
    }




    public void configureDataBase() throws SQLException {

        // Informations de connexion à la base de données PostgreSQL
        String url = "jdbc:postgresql://dpg-clcemgjmot1c73dfmjm0-a.oregon-postgres.render.com/ayarinho";
        String utilisateur = "youssef";
        String motDePasse = "I0yyHDMiLENpCfqsbSiyanQjFMbBt422";

        // Établir une connexion à la base de données PostgreSQL
        Connection connexion = DriverManager.getConnection(url, utilisateur, motDePasse);

        // Requête SQL pour récupérer le fichier inséré (vous pouvez remplacer 1 par l'ID approprié)
        String requeteSelect = "SELECT fichier FROM trainsetdata WHERE id = (SELECT MAX(id) FROM trainsetdata);";
        PreparedStatement preparedStatementSelect = connexion.prepareStatement(requeteSelect);

        ResultSet resultSet = preparedStatementSelect.executeQuery();
    }
}
