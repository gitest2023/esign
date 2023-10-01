package com.example.itext;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfDate;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSignature;
import com.itextpdf.text.pdf.PdfSignatureAppearance;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfString;
import com.itextpdf.text.pdf.parser.ContentByteUtils;
import com.itextpdf.text.pdf.parser.PdfContentStreamProcessor;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextMarginFinder;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.security.CertificateUtil;
import com.itextpdf.text.pdf.security.MakeSignature;
import com.itextpdf.text.pdf.security.PdfPKCS7;
import com.itextpdf.text.pdf.security.TSAClient;
import com.itextpdf.text.pdf.security.TSAClientBouncyCastle;
import com.spire.pdf.FileFormat;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import com.spire.pdf.general.find.PdfTextFind;
import com.spire.pdf.general.find.PdfTextFindCollection;
import com.spire.pdf.graphics.PdfBrushes;
import com.spire.pdf.graphics.PdfTrueTypeFont;
import com.spire.pdf.widget.PdfPageCollection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SignDocumentNew {

    private static final int ESTIMATED_SIGNATURE_SIZE = 8192;

    private X509Certificate _certificate;
    private byte[] certificateChain;
    private Certificate[] certificates;
    private PrivateKey privateKey;

    public SignDocumentNew() throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        try (InputStream inputStream = SignDocumentNew.class.getResourceAsStream("/security/myKeystore.pkcs12")) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, "".toCharArray());

            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("person1");
            this._certificate = certificate;

            this.privateKey = (PrivateKey) keyStore.getKey("person1", "".toCharArray());
            this.certificateChain = certificate.getEncoded();
            this.certificates = new Certificate[]{certificate};
        }
    }

    public static void main(String[] args) throws IOException, DocumentException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, NoSuchProviderException, InvalidKeyException, SignatureException {
        SignDocumentNew signDocument = new SignDocumentNew();

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream inputStream = SignDocumentNew.class.getResourceAsStream("/document/test.pdf")) {
            // 1-6 - Create e-signature on PDF document
            signDocument.sign(IOUtils.toByteArray(inputStream), output);
        }
        // 7. Generate a file to view the signed document
        File result = new File("src/main/resources/output/signed.pdf");
        FileUtils.writeByteArrayToFile(result, output.toByteArray());
    }

    public void sign(byte[] document, ByteArrayOutputStream output) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException, DocumentException {
        // 1. Receive and read the PDF Document
        PdfReader pdfReader = new PdfReader(document);

        //Map<String, Integer> position = findTextPosition("src/main/resources/document/example.pdf", "PDF");
        Map<String, Integer> position = findTextPosition(document, "vien");
        System.out.println("position => " + position);

        // 2. Create the signature data
        PdfStamper signer = PdfStamper.createSignature(pdfReader, output, '\0');
        Calendar signDate = Calendar.getInstance();
        int page = 1;
        PdfSignature pdfSignature = new PdfSignature(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
        pdfSignature.setReason("Reason to sign");
//        pdfSignature.setLocation("Location of signature");
        pdfSignature.setLocation("PDF");
        pdfSignature.setContact("Person Name");
        pdfSignature.setDate(new PdfDate(signDate));
        pdfSignature.setCert(certificateChain);
        // 3. Create the appearance of the visible Signature
        //PdfSignatureAppearance appearance = createAppearance(signer, page, pdfSignature);
        PdfSignatureAppearance appearance = createAppearance(signer, position, pdfSignature);
        // 4. Generate the hash to sign
        PdfPKCS7 pdfPKCS7 = new PdfPKCS7(null, certificates, "SHA-256", null, null, false);
        InputStream data = appearance.getRangeStream();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(IOUtils.toByteArray(data));
        byte[] appeareanceHash = digest.digest();
        byte[] hashToSign = pdfPKCS7.getAuthenticatedAttributeBytes(appeareanceHash, null, null, MakeSignature.CryptoStandard.CMS);
        // 5. Sign the hash
        byte[] signedHash = addDigitalSignatureToHash(hashToSign);
        // 6. Put the signature on the PDF
        pdfPKCS7.setExternalDigest(signedHash, null, "RSA");
        byte[] encodedPKCS7 = pdfPKCS7.getEncodedPKCS7(appeareanceHash, null, null, null, MakeSignature.CryptoStandard.CMS);

        byte[] paddedSig = new byte[ESTIMATED_SIGNATURE_SIZE];
        System.arraycopy(encodedPKCS7, 0, paddedSig, 0, encodedPKCS7.length);
        PdfDictionary dictionary = new PdfDictionary();
        dictionary.put(PdfName.CONTENTS, new PdfString(paddedSig).setHexWriting(true));
        appearance.close(dictionary);
    }

    /**
     * Create the appearance of the visible Signature
     *
     * @param signer Pdf Stamper
     * @param page Page Number
     * @param pdfSignature Pdf Signature
     * @return PdfSignatureAppearance
     * @throws IOException
     * @throws DocumentException
     */
    //private PdfSignatureAppearance createAppearance(PdfStamper signer, Integer page, PdfSignature pdfSignature) throws IOException, DocumentException {
    private PdfSignatureAppearance createAppearance(PdfStamper signer, Map<String, Integer> position, PdfSignature pdfSignature) throws IOException, DocumentException {
        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setRenderingMode(PdfSignatureAppearance.RenderingMode.DESCRIPTION);
        appearance.setAcro6Layers(true);

        int lowerLeftX = 570;
        int lowerLeftY = 70;
        int width = 370;
        int height = 150;
        int page = 1;
        if (position != null) {
            int hActualPage = position.get("hActualPage");
            int wActualPage = position.get("wActualPage");
            int hPage = position.get("hPage");
            int wPage = position.get("wPage");
            int xText = position.get("x");
            int yText = position.get("y");
            int hText = position.get("h");
            int wText = position.get("w");

            lowerLeftX = xText;
            lowerLeftY = hPage - yText;
            width = (xText + hActualPage - hPage) + 90;
            height = (hActualPage - yText) - hText;
            page = position.get("currentPage");
        }

        // (llx, lly) as lower-left corner, (urx, ury) as upper-right corner
        //Rectangle rectangle = new Rectangle(xText, hPage - yText,(xText + hActualPage - hPage) + 90, (hActualPage - yText) - hText);
        //appearance.setVisibleSignature(rectangle, position.get("currentPage"), null);
        appearance.setVisibleSignature(new Rectangle(lowerLeftX, lowerLeftY, width, height), page, null);

        appearance.setCryptoDictionary(pdfSignature);
//        appearance.setCrypto(null, certificates, null, PdfName.FILTER);

        HashMap<PdfName, Integer> exclusions = new HashMap<>();
        exclusions.put(PdfName.CONTENTS, ESTIMATED_SIGNATURE_SIZE * 2 + 2);
        appearance.preClose(exclusions);

        return appearance;
    }

    /**
     * Sign the hash
     *
     * @param hashToSign Hash string
     * @return signature
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws InvalidKeyException
     */
    public byte[] addDigitalSignatureToHash(byte[] hashToSign) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(hashToSign);

        return signature.sign();
    }

    public void findText(String src, String text) throws IOException {
        PdfReader reader = new PdfReader(src);

        RenderListener listener = new MyTextRenderListener();
        PdfContentStreamProcessor processor = new PdfContentStreamProcessor(listener);
        PdfDictionary pageDic = reader.getPageN(1);
        PdfDictionary resourcesDic = pageDic.getAsDict(PdfName.RESOURCES);
        processor.processContent(ContentByteUtils.getContentBytesForPage(reader, 1), resourcesDic);

    }

    public void findText(PdfReader reader, String text) throws IOException {
        System.out.println("text => " + text);
        List<String> keywords = new ArrayList<>();
        keywords.add("lorem");
        keywords.add("hendrerit");
//        MyTextRenderListener myTextRenderListener = new MyTextRenderListener(keywords);
//        PdfContentStreamProcessor processor = new PdfContentStreamProcessor(myTextRenderListener);
//        PdfDictionary pageDic = reader.getPageN(1);
//        PdfDictionary resourcesDic = pageDic.getAsDict(PdfName.RESOURCES);
//        processor.processContent(ContentByteUtils.getContentBytesForPage(reader, 1), resourcesDic);
//        System.out.println("texts => " + myTextRenderListener.getTexts());
//        System.out.println("positions => " + myTextRenderListener.getPositions());

        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        MyTextRenderListener myTextRenderListener = parser.processContent(1, new MyTextRenderListener(keywords));
        System.out.println("texts => " + myTextRenderListener.getTexts());
        System.out.println("positions => " + myTextRenderListener.getPositions());
    }

    public void test() {
        //Create a PdfDocument instance
        PdfDocument doc = new PdfDocument();

        //Load a PDF sample document
        doc.loadFromFile("src/main/resources/document/example.pdf");

        //Get the first page of the PDF
        PdfPageBase page = doc.getPages().get(0);

        //Find the specific string from the page
        PdfTextFindCollection collection = page.findText("Example",false);
        System.out.println("page => " + page.getClientSize());
        System.out.println("page => " + page.getActualBounds(true));
        System.out.println("page => " + page.getActualSize());

        //Define new text for replacing
        String newText = "Oscar Wilde--playwright, novelist, poet, critic";

        //Create a PdfTrueTypeFont object based on a specific used font
        PdfTrueTypeFont font = new PdfTrueTypeFont(new Font("Times New Roman",  Font.BOLD, 24));
        Dimension2D dimension2D = font.measureString(newText);
        double fontWidth = dimension2D.getWidth();
        double height = dimension2D.getHeight();

        for (PdfTextFind find : collection.getFinds()) {
            List<Rectangle2D> textBounds = find.getTextBounds();
            System.out.println(textBounds.get(0));
            System.out.println("position => " + find.getPosition());

            //Draw a white rectangle to cover the old text
            Rectangle2D rectangle2D = textBounds.get(0);
            System.out.println("rectangle2D => " + rectangle2D);
            System.out.println("box => " + rectangle2D.getX() + " x " + rectangle2D.getY());
            System.out.println("box => " + rectangle2D.getWidth() + " x " + rectangle2D.getHeight());
            System.out.println("box => " + rectangle2D.getCenterX() + " x " + rectangle2D.getCenterY());
            //new Rectangle((int)rectangle2D.getX(),(int)rectangle2D.getY(),(int)fontWidth,(int)height);
            //page.getCanvas().drawRectangle(PdfBrushes.getWhite(), rectangle2D);

            //Draw new text at the position of the old text
            //page.getCanvas().drawString(newText, font, PdfBrushes.getBlack(), rectangle2D.getX(), rectangle2D.getY() );
        }

        //Save the document to file
        //String result = "FindandReplace.pdf";
        //doc.saveToFile(result, FileFormat.PDF);
    }

    //public Map<String, Integer> findTextPosition(String src, String text) {
    public Map<String, Integer> findTextPosition(byte[] src, String text) {
        // Create a PdfDocument instance
        PdfDocument doc = new PdfDocument();
        // Load a PDF sample document
        //doc.loadFromFile(src);
        doc.loadFromBytes(src);
        List<List<Integer>> boxes = new ArrayList<>();
        for (int i=0; i < doc.getPages().getCount(); i++) {
            PdfPageBase page = doc.getPages().get(i);
            // Find the specific string from the page
            PdfTextFindCollection collection = page.findText(text, false, true);
            for (PdfTextFind find : collection.getFinds()) {
                List<Rectangle2D> textBounds = find.getTextBounds();
                Rectangle2D rectangle2D = textBounds.get(0);
                List<Integer> box = new ArrayList<>();
                box.add((int) rectangle2D.getX());
                box.add((int) rectangle2D.getY());
                box.add((int) rectangle2D.getWidth());
                box.add((int) rectangle2D.getHeight());
                box.add((int) page.getClientSize().getWidth());
                box.add((int) page.getClientSize().getHeight());
                box.add((int) page.getActualSize().getWidth());
                box.add((int) page.getActualSize().getHeight());
                box.add(i + 1);
                boxes.add(box);
            }
        }
        System.out.println("boxes => " + boxes);
        if (!boxes.isEmpty()) {
            List<Integer> b = boxes.get(boxes.size() - 1);
            return Map.of(
                    "x", b.get(0),
                    "y", b.get(1),
                    "w", b.get(2),
                    "h", b.get(3),
                    "wPage", b.get(4),
                    "hPage", b.get(5),
                    "wActualPage", b.get(6),
                    "hActualPage", b.get(7),
                    "currentPage", b.get(8)
            );
        }
        return null;
    }

    public void findAndHighlight () {
        //Create a PdfDocument instance
        PdfDocument pdf = new PdfDocument();

        //Load a PDF sample document
        pdf.loadFromFile("src/main/resources/document/example.pdf");

        PdfTextFind[] result = null;
        for (Object pageObj : pdf.getPages()) {
            PdfPageBase page =(PdfPageBase)pageObj;
            //Find text
            result = page.findText("hendrerit", false).getFinds();
            for (PdfTextFind find : result) {
                //Highlight searched text
                find.highLight(Color.green);
            }
        }

        //Save the result file
        pdf.saveToFile("src/main/resources/output/HighlightText.pdf");
    }

    public void test2(PdfReader reader) throws IOException {
        int numberOfPages = reader.getNumberOfPages();
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);
        parser.processContent(numberOfPages, new TextMarginFinder() {
            @Override
            public void renderText(TextRenderInfo renderInfo) {
                super.renderText(renderInfo);
                System.out.println(renderInfo.getText() + ", x: " + renderInfo.getBaseline().getBoundingRectange().x + ", y: " + renderInfo.getBaseline().getBoundingRectange().y);
            }
        });
    }

    public void FindandReplace() {

        //Create a PdfDocument instance
        PdfDocument doc = new PdfDocument();

        //Load a PDF sample document
        doc.loadFromFile("Test00.pdf");

        //Get the first page of the PDF
        PdfPageBase page = doc.getPages().get(0);

        //Find the specific string from the page
        PdfTextFindCollection collection = page.findText("Oscar Wilde",false);

        //Define new text for replacing
        String newText = "Oscar Wilde--playwright, novelist, poet, critic";

        //Create a PdfTrueTypeFont object based on a specific used font
        PdfTrueTypeFont font = new PdfTrueTypeFont(new Font("Times New Roman",  Font.BOLD, 24));
        Dimension2D dimension2D = font.measureString(newText);
        double fontWidth = dimension2D.getWidth();
        double height = dimension2D.getHeight();

        for (Object findObj : collection.getFinds()) {
            PdfTextFind find=(PdfTextFind)findObj;
            List<Rectangle2D> textBounds = find.getTextBounds();

            //Draw a white rectangle to cover the old text
            Rectangle2D rectangle2D = textBounds.get(0);
            new Rectangle((int)rectangle2D.getX(),(int)rectangle2D.getY(),(int)fontWidth,(int)height);
            page.getCanvas().drawRectangle(PdfBrushes.getWhite(), rectangle2D);

            //Draw new text at the position of the old text
            page.getCanvas().drawString(newText, font, PdfBrushes.getBlack(), rectangle2D.getX(), rectangle2D.getY() );
        }

        //Save the document to file
        String result = "FindandReplace.pdf";
        doc.saveToFile(result, FileFormat.PDF);
    }

    public static X509Certificate getX509Certificate(String encodedString) {
        if (encodedString == null) {
            return null;
        }

        java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
        byte[] decodedData = decoder.decode(encodedString);

        try (InputStream inputStream = new ByteArrayInputStream(decodedData)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            java.security.cert.Certificate certificate = cf.generateCertificate(inputStream);

            if (certificate instanceof X509Certificate) {
                return (X509Certificate) certificate;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }

    public static X509Certificate getX509Certificate(byte[] encodedBytes) {
        if (encodedBytes == null) {
            return null;
        }

        java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();
        byte[] decodedData = decoder.decode(encodedBytes);

        try (InputStream inputStream = new ByteArrayInputStream(decodedData)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");

            java.security.cert.Certificate certificate = cf.generateCertificate(inputStream);

            if (certificate instanceof X509Certificate) {
                return (X509Certificate) certificate;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;

    }
}
