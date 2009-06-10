package fi.csc.microarray.analyser.ws.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fi.csc.microarray.analyser.ws.HtmlUtil;
import fi.csc.microarray.analyser.ws.ResultTableCollector;
import fi.csc.microarray.util.XmlUtil;

public class EnfinWsUtils {

	static interface AnnotationIdentifier {
		boolean isAnnotation(Element setElement);
	}

	static interface AnnotationNameFinder {
		String findAnnotationName(Element setElement);
	}

	public static void main(String[] args) throws SAXException, ParserConfigurationException, TransformerException, SOAPException, IOException {
		String[] probes = new String[] {"204259_at", "1993_s_at"};
		ResultTableCollector intactAnnotations = queryIntact(probes);
		HtmlUtil.writeHtmlTable(intactAnnotations, new String[] {"Interaction", "Participants"}, "Enfin IntAct annotation", new File("intact.html"));
		ResultTableCollector reactomeAnnotations = queryReactome(probes);
		HtmlUtil.writeHtmlTable(reactomeAnnotations, new String[] {"Interaction", "Participants"}, "Enfin Reactome annotation", new File("reactome.html"));
	}

	private static ResultTableCollector queryIntact(String[] probes) throws SOAPException, MalformedURLException, SAXException, IOException, ParserConfigurationException, TransformerException {
		
		Document uniprotResponse = queryUniprotIds(probes);

		// query IntAct with UniProt identifiers
		SOAPMessage intactSoapMessage = initialiseSoapMessage();
		SOAPBody intactSoapBody = initialiseSoapBody(intactSoapMessage);
		
		attachEnfinXml(intactSoapBody, fetchEnfinXml(uniprotResponse), "findPartners", "http://ebi.ac.uk/enfin/core/web/services/intact");
		
		Document intactResponse = sendSoapMessage(intactSoapMessage, new URL("http://www.ebi.ac.uk/enfin-srv/encore/intact/service"));
		
		return collectAnnotations(intactResponse, new AnnotationIdentifier() {

			public boolean isAnnotation(Element setElement) {
				NodeList names = setElement.getElementsByTagName("names");
				return names.getLength() > 0 && "IntAct interaction".equals(names.item(0).getChildNodes().item(0).getTextContent());
				}
			
		}, new AnnotationNameFinder() {

			public String findAnnotationName(Element setElement) {
				Element primaryRef = (Element)setElement.getElementsByTagName("xrefs").item(0).getChildNodes().item(0);
				return primaryRef.getAttribute("id");
			}
			
		});
	}

	private static ResultTableCollector queryReactome(String[] probes) throws SOAPException, MalformedURLException, SAXException, IOException, ParserConfigurationException, TransformerException {
		
		Document uniprotResponse = queryUniprotIds(probes);

		// query IntAct with UniProt identifiers
		SOAPMessage intactSoapMessage = initialiseSoapMessage();
		SOAPBody intactSoapBody = initialiseSoapBody(intactSoapMessage);
		
		attachEnfinXml(intactSoapBody, fetchEnfinXml(uniprotResponse), "findPath", "http://ebi.ac.uk/enfin/core/web/services/reactome");
		
		Document reactomeResponse = sendSoapMessage(intactSoapMessage, new URL("http://www.ebi.ac.uk/enfin-srv/encore/reactome/service"));
		
		return collectAnnotations(reactomeResponse, new AnnotationIdentifier() {

			public boolean isAnnotation(Element setElement) {
				NodeList setTypes = setElement.getElementsByTagName("setType");
				return setTypes.getLength() > 0 && "Reactome".equals(((Element)setTypes.item(0)).getAttribute("db"));
			}
			
		}, new AnnotationNameFinder() {

			public String findAnnotationName(Element setElement) {
				Element fullName = (Element)setElement.getElementsByTagName("names").item(0).getChildNodes().item(0);
				return fullName.getTextContent();
			}
			
		});
	}

	private static Document queryUniprotIds(String[] probes) throws SOAPException, SAXException, IOException, ParserConfigurationException, MalformedURLException {
		
		// Step 1. Create ENFIN XML out of Affy probe list
		SOAPMessage probeSoapMessage = initialiseSoapMessage();
		SOAPBody probeSoapBody = initialiseSoapBody(probeSoapMessage);

		SOAPElement probeEntries = createOperation(probeSoapBody, "docFromAffyList", "http://ebi.ac.uk/enfin/core/web/services/utility");

		for (String probe : probes) {
			SOAPElement arg = probeEntries.addChildElement("parameter");
			arg.setTextContent(probe);
		}
		
		Document probeResponse = sendSoapMessage(probeSoapMessage, new URL("http://www.ebi.ac.uk/enfin-srv/encore/utility/service"));
		
		// Step 2. Convert Affy probes to UniProt identifiers
		SOAPMessage uniprotSoapMessage = initialiseSoapMessage();
		SOAPBody uniprotSoapBody = initialiseSoapBody(uniprotSoapMessage);
		
		attachEnfinXml(uniprotSoapBody, fetchEnfinXml(probeResponse), "mapAffy2UniProt", "http://ebi.ac.uk/enfin/core/web/services/affy2uniprot");

		Document uniprotResponse = sendSoapMessage(uniprotSoapMessage, new URL("http://www.ebi.ac.uk/enfin-srv/encore/affy2uniprot/service"));
		return uniprotResponse;
	}

	private static Document fetchEnfinXml(Document response) throws ParserConfigurationException {
		Document document = XmlUtil.getInstance().newDocument();
		document.appendChild(document.importNode(response.getDocumentElement().getElementsByTagNameNS("http://ebi.ac.uk/enfin/core/model", "entries").item(0), true));
		return document;
	}
	
	private static ResultTableCollector collectAnnotations(Document response, AnnotationIdentifier annotationIdentifier, AnnotationNameFinder annotationNameFinder) {
		ResultTableCollector annotationCollector = new ResultTableCollector();
		NodeList childNodes = response.getDocumentElement().getChildNodes().item(0).getChildNodes().item(0).getChildNodes().item(0).getChildNodes().item(0).getChildNodes();

		// iterate over molecules
		HashMap<String, String> moleculeMap = new HashMap<String, String>();
		for (int i = 0; i < childNodes.getLength(); i++) {
			if ("molecule".equals(childNodes.item(i).getNodeName())) {
				Element molecule = (Element)childNodes.item(i);					
				String moleculeId = molecule.getAttribute("id");
				
				Element primaryRef = (Element)molecule.getElementsByTagName("xrefs").item(0).getChildNodes().item(0);
				String moleculeName = primaryRef.getAttribute("id");
				moleculeMap.put(moleculeId, moleculeName);		
			}
		}

		// iterate over interactions
		int index = 0;
		for (int i = 0; i < childNodes.getLength(); i++) {
			if ("set".equals(childNodes.item(i).getNodeName())) {
				Element set = (Element)childNodes.item(i);
				
				if (annotationIdentifier.isAnnotation(set)) {
					
					String annotationName = annotationNameFinder.findAnnotationName(set);
					annotationCollector.addAnnotation(index, "Interaction", annotationName);
					
					String participantValue = "";
					NodeList participants = set.getElementsByTagName("participant");
					for (int p = 0; p < participants.getLength(); p++) {
						Element participant = (Element)participants.item(p);
						String moleculeName = moleculeMap.get(participant.getAttribute("moleculeRef"));
						participantValue += (" " + moleculeName);
					}
					annotationCollector.addAnnotation(index, "Participants", participantValue);
					
					index++;
				}						
			}
		}
		
		return annotationCollector;

	}

	private static Document sendSoapMessage(SOAPMessage soapMessage, URL endpoint) throws SAXException, IOException, ParserConfigurationException, SOAPException {
		soapMessage.saveChanges();
		SOAPConnectionFactory connectionFactory = SOAPConnectionFactory.newInstance();
		SOAPConnection soapConnection = connectionFactory.createConnection();

		SOAPMessage resp = soapConnection.call(soapMessage, endpoint);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		resp.writeTo(out);
		soapConnection.close();
		
		return XmlUtil.getInstance().parseReader(new InputStreamReader(new ByteArrayInputStream(out.toByteArray())));
	}

	private static SOAPBody initialiseSoapBody(SOAPMessage message) throws SOAPException {
		SOAPPart soapPart = message.getSOAPPart();
		SOAPEnvelope soapEnvelope = soapPart.getEnvelope();
		
		
		return soapEnvelope.getBody();
	}
	
	private static SOAPMessage initialiseSoapMessage() throws SOAPException {
		MessageFactory mf = MessageFactory.newInstance();
		return mf.createMessage();
	}
	
	private static void attachEnfinXml(SOAPBody soapBody, Document enfinXml, String operation, String operationNamespace) throws SOAPException, ParserConfigurationException {
		Document document = XmlUtil.getInstance().newDocument();
		document.appendChild(document.createElementNS(operationNamespace, operation));	
		document.getDocumentElement().appendChild(document.importNode(enfinXml.getDocumentElement(), true));		
		soapBody.addDocument(document);
	}

	private static SOAPElement createOperation(SOAPBody soapBody, String operation, String operationNamespace) throws SOAPException {
		soapBody.addNamespaceDeclaration("oper", operationNamespace);
		SOAPElement elementFindPartners = soapBody.addChildElement(operation, "oper");
		elementFindPartners.addNamespaceDeclaration("model", "http://ebi.ac.uk/enfin/core/model");
		return elementFindPartners;
	}
}
