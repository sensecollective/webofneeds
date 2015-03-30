package won.cryptography.rdfsign;

import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.vocabulary.RDF;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import won.cryptography.exception.KeyNotSupportedException;
import won.cryptography.key.KeyInformationExtractor;
import won.cryptography.key.KeyInformationExtractorBouncyCastle;
import won.protocol.util.RdfUtils;
import won.protocol.vocabulary.CERT;
import won.protocol.vocabulary.WONCRYPT;

import javax.transaction.NotSupportedException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;


/**
 * User: ypanchenko
 * Date: 27.03.2015
 */
public class WonKeysExtractor
{

  public static Map<String, PublicKey> getPublicKeys(Dataset dataset)
    throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
    Map<String, PublicKey> keys = new HashMap<>();
    addPublicKeys(dataset.getDefaultModel(), keys);
    for (String name : RdfUtils.getModelNames(dataset)) {
      addPublicKeys(dataset.getNamedModel(name), keys);
    }
    return keys;
  }


  public static Map<String, PublicKey> getPublicKeys(Model model)
    throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
    Map<String, PublicKey> keys = new HashMap<>();
    addPublicKeys(model, keys);
    return keys;
  }

  private static void addPublicKeys(final Model model, final Map<String, PublicKey> keys)
    throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {

    StmtIterator keyStmts = model.listStatements(null, CERT.KEY, RdfUtils.EMPTY_RDF_NODE);
    // TODO replace if with while if we allow multiple keys
    if (keyStmts.hasNext()) {
      Statement statement = keyStmts.next();
      Resource keyAgent = statement.getSubject();
      RDFNode keyObj = statement.getObject();

      // pub key statements
      NodeIterator ni = model.listObjectsOfProperty(keyObj.asResource(), CERT.PUBLIC_KEY);
      if (ni.hasNext()) {
        RDFNode eccKeyObj = ni.next();


        // ECC pub key statements
        StmtIterator eccPubKeyStmts = model.listStatements(eccKeyObj.asResource(), RDF.type, WONCRYPT.ECC_PUBLIC_KEY);
        if (eccPubKeyStmts.hasNext()) {
          // extract properties of ECC public key:
          ni = model.listObjectsOfProperty(eccKeyObj.asResource(), WONCRYPT.ECC_ALGORITHM);
          //TODO actually should be nested ifs
          String algName = null;
          String curveId = null;
          String qx = null;
          String qy = null;
          if (ni.hasNext()) {
            algName = ni.next().asLiteral().toString();
          }
          ni = model.listObjectsOfProperty(eccKeyObj.asResource(), WONCRYPT.ECC_CURVE_ID);
          if (ni.hasNext()) {
            curveId = ni.next().asLiteral().toString();
          }
          ni = model.listObjectsOfProperty(eccKeyObj.asResource(), WONCRYPT.ECC_QX);
          if (ni.hasNext()) {
            qx = ni.next().asLiteral().toString();
          }
          ni = model.listObjectsOfProperty(eccKeyObj.asResource(), WONCRYPT.ECC_QY);
          if (ni.hasNext()) {
            qy = ni.next().asLiteral().toString();
          }

          ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(curveId);
          org.bouncycastle.math.ec.ECPoint ecPoint = ecSpec.getCurve()
                                                           .createPoint(new BigInteger(qx, 16), new BigInteger(qy, 16));
          ECParameterSpec paramSpec = new ECParameterSpec(ecSpec.getCurve(), ecSpec.getG(), ecSpec.getN());
          ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(ecPoint, paramSpec);
          // TODO add provider to RDF?
          KeyFactory keyFactory = KeyFactory.getInstance(algName, "BC");
          PublicKey key = keyFactory.generatePublic(pubKeySpec);

          keys.put(keyAgent.getURI(), key);
        }
      }
    }

  }

  public static void addPublicKey(Model model, Resource keySubject, WonEccPublicKey pubKey) {

    // EC public key triples
    Resource bn = model.createResource();
    Statement stmt = model.createStatement(bn, RDF.type, WONCRYPT.ECC_PUBLIC_KEY);
    model.add(stmt);
    stmt = model.createStatement(bn, WONCRYPT.ECC_ALGORITHM, pubKey.getAlgorithm());
    model.add(stmt);
    stmt = model.createStatement(bn, WONCRYPT.ECC_CURVE_ID, pubKey.getCurveId());
    model.add(stmt);
    stmt = model.createStatement(bn, WONCRYPT.ECC_QX, model.createLiteral(pubKey.getQx()));
    model.add(stmt);
    stmt = model.createStatement(bn, WONCRYPT.ECC_QY, model.createLiteral(pubKey.getQy()));
    model.add(stmt);

    // public key triple
    Resource bn2 = model.createResource();
    stmt = model.createStatement(bn2, CERT.PUBLIC_KEY, bn);
    model.add(stmt);

    // key triple
    stmt = model.createStatement(keySubject, CERT.KEY, bn2);
    model.add(stmt);

  }

  public static Model toModel(Resource keySubject, WonEccPublicKey pubKey) {

    Model model = ModelFactory.createDefaultModel();
    addPublicKey(model, keySubject, pubKey);
    return model;

  }

  public static void addPublicKeys(Model model, Resource keySubject, PublicKey publicKey) throws NotSupportedException,
    KeyNotSupportedException {

    if (publicKey instanceof ECPublicKey) {
      KeyInformationExtractor info = new KeyInformationExtractorBouncyCastle();
      addPublicKey(model, keySubject, new WonEccPublicKey(info.getCurveID(publicKey), info.getAlgorithm(publicKey),
                                               info.getQX(publicKey),
                                                   info.getQY(publicKey)));
    } else {
      throw new NotSupportedException("Not supported key: " + publicKey.getClass().getName());
    }
  }

}
