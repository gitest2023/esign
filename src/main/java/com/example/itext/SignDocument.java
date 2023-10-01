package com.example.itext;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfDate;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfSignature;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

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
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.HashMap;

public class SignDocument {

    private static final int ESTIMATED_SIGNATURE_SIZE = 8192;

    private byte[] certificateChain;
    private Certificate[] certificates;
    private PrivateKey privateKey;

    public SignDocument() throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        try (InputStream inputStream = SignDocument.class.getResourceAsStream("/security/myKeystore.pkcs12")) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(inputStream, "".toCharArray());

            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("person1");

            this.privateKey = (PrivateKey) keyStore.getKey("person1", "".toCharArray());
            this.certificateChain = certificate.getEncoded();
            this.certificates = new Certificate[]{certificate};
        }
    }

    public static void main(String[] args) throws IOException, DocumentException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, CertificateException, NoSuchProviderException, InvalidKeyException, SignatureException {
        SignDocument signDocument = new SignDocument();

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream inputStream = SignDocument.class.getResourceAsStream("/document/example.pdf")) {
            // 1-6 - Create e-signature on PDF document
            signDocument.sign(IOUtils.toByteArray(inputStream), output);
        }
        // 7. Generate a file to view the signed document
        File result = new File("src/main/resources/output/signed.pdf");
        FileUtils.writeByteArrayToFile(result, output.toByteArray());
    }

    public void sign(byte[] document, ByteArrayOutputStream output) throws IOException, DocumentException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException, DocumentException {
        // 1. Receive and read the PDF Document
        PdfReader pdfReader = new PdfReader(document);
        // 2. Create the signature data
        PdfStamper signer = PdfStamper.createSignature(pdfReader, output, '\0');
        Calendar signDate = Calendar.getInstance();
        int page = 1;
        PdfSignature pdfSignature = new PdfSignature(PdfName.ADOBE_PPKLITE, PdfName.ADBE_PKCS7_DETACHED);
        pdfSignature.setReason("Reason to sign");
        pdfSignature.setLocation("Location of signature");
        pdfSignature.setContact("Person Name");
        pdfSignature.setDate(new PdfDate(signDate));
        pdfSignature.setCert(certificateChain);
        // 3. Create the appearance of the visible Signature
        PdfSignatureAppearance appearance = createAppearance(signer, page, pdfSignature);
        // 4. Generate the hash to sign
        PdfPKCS7 pdfPKCS7 = new PdfPKCS7(null, certificates, null, "SHA-256", null, false);
        InputStream data = appearance.getRangeStream();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(IOUtils.toByteArray(data));
        byte[] appeareanceHash = digest.digest();
        byte[] hashToSign = pdfPKCS7.getAuthenticatedAttributeBytes(appeareanceHash, appearance.getSignDate(), null);
        // 5. Sign the hash
        byte[] signedHash = addDigitalSignatureToHash(hashToSign);
        // 6. Put the signature on the PDF
        pdfPKCS7.setExternalDigest(signedHash, null, "RSA");
        byte[] encodedPKCS7 = pdfPKCS7.getEncodedPKCS7(appeareanceHash, appearance.getSignDate());
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
    private PdfSignatureAppearance createAppearance(PdfStamper signer, int page, PdfSignature pdfSignature) throws IOException, DocumentException {
        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setRender(PdfSignatureAppearance.SignatureRenderDescription);
        appearance.setAcro6Layers(true);

        //int lowerLeftX = 570;
        //int lowerLeftY = 70;
        int lowerLeftX = 324;
        int lowerLeftY = 253;
        int width = 370;
        int height = 150;
        appearance.setVisibleSignature(new Rectangle(lowerLeftX, lowerLeftY, width, height), page, null);

        appearance.setCryptoDictionary(pdfSignature);
        //appearance.setCrypto(null, certificates, null, PdfName.FILTER);
        appearance.setCrypto(null, certificates, null, PdfSignatureAppearance.WINCER_SIGNED);

        HashMap<Object, Object> exclusions = new HashMap<>();
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

}
