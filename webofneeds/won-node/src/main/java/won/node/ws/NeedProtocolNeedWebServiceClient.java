
package won.node.ws;

import won.protocol.ws.NeedProtocolNeedWebServiceEndpoint;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import java.net.URL;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.7-b01 
 * Generated source version: 2.2
 *
 */
@WebServiceClient(name = "needProtocol", targetNamespace = "http://www.webofneeds.org/protocol/need/soap/1.0/")
public class NeedProtocolNeedWebServiceClient
    extends Service
{

  private final static QName NEEDPROTOCOL_QNAME = new QName("http://www.webofneeds.org/protocol/need/soap/1.0/", "needProtocol");

  /**
   * TODO: We want to be able to pass the web service URI directly here... We already know the content of the wsdl file... right?
   * @param wsdlLocation
   */
  public NeedProtocolNeedWebServiceClient(URL wsdlLocation) {
    super(wsdlLocation, NEEDPROTOCOL_QNAME);
  }

  /**
   *
   * @return
   *     returns NeedProtocolNeedWebServiceEndpoint
   */
  @WebEndpoint(name = "NeedProtocolNeedWebServiceEndpointPort")
  public NeedProtocolNeedWebServiceEndpoint getNeedProtocolNeedWebServiceEndpointPort() {
    return super.getPort(new QName("http://www.webofneeds.org/protocol/need/soap/1.0/", "NeedProtocolNeedWebServiceEndpointPort"), NeedProtocolNeedWebServiceEndpoint.class);
  }

  /**
   *
   * @param features
   *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
   * @return
   *     returns NeedProtocolNeedWebServiceEndpoint
   */
  @WebEndpoint(name = "NeedProtocolNeedWebServiceEndpointPort")
  public NeedProtocolNeedWebServiceEndpoint getNeedProtocolNeedWebServiceEndpointPort(WebServiceFeature... features) {
    return super.getPort(new QName("http://www.webofneeds.org/protocol/need/soap/1.0/", "NeedProtocolNeedWebServiceEndpointPort"), NeedProtocolNeedWebServiceEndpoint.class, features);
  }


}
