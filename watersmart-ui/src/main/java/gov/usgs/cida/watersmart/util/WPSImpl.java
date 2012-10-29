package gov.usgs.cida.watersmart.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.netcdf.dsg.Station;
import gov.usgs.cida.watersmart.common.JNDISingleton;
import gov.usgs.cida.watersmart.common.RunMetadata;
import gov.usgs.cida.watersmart.communication.EmailHandler;
import gov.usgs.cida.watersmart.communication.EmailMessage;
import gov.usgs.cida.watersmart.communication.HTTPUtils;
import gov.usgs.cida.watersmart.csw.CSWTransactionHelper;
import gov.usgs.cida.watersmart.parse.CreateDSGFromZip;
import gov.usgs.cida.watersmart.parse.CreateDSGFromZip.ReturnInfo;
import gov.usgs.cida.watersmart.wps.completion.CheckProcessCompletion;
import gov.usgs.cida.watersmart.wps.completion.ProcessStatus;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
class WPSImpl implements WPSInterface {

    static final String stats_csv_obs_test_wps = "org.n52.wps.server.r.stats_csv_obs_test_wps";
    static final String stats_csv_nahat_test_wps = "org.n52.wps.server.r.stats_csv_nahat_test_wps";
    static final String stats_compare = "org.n52.wps.server.r.stats_compare_wps";
    
    @Override
    public String executeProcess(File zipLocation, RunMetadata metadata) {
        return executeProcess(zipLocation, metadata.toKeyValueMap());
    }

    @Override
    public String executeProcess(File zipLocation, Map<String, String> metadata) {
        WPSTask task = new WPSTask(zipLocation, metadata);
        task.start();
        if (task.isAlive()) {
            return "ok";
        }
        else {
            return "not started";
        }
    }
    
    static String createCompareStatsRequest(String sosEndpoint, Collection<Station> sites, List<String> properties) {
        List<String> siteList = Lists.newLinkedList();
        for (Station station : sites) {
            siteList.add("\\\"" + station.station_id + "\\\"");                    
        }
        
        return new String(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<wps:Execute service=\"WPS\" version=\"1.0.0\" " +
                    "xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" " +
                    "xmlns:ows=\"http://www.opengis.net/ows/1.1\" " +
                    "xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                    "xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 " +
                    "http://schemas.opengis.net/wps/1.0.0/wpsExecute_request.xsd\">" +
                "<ows:Identifier>" + stats_compare + "</ows:Identifier>" +
                "<wps:DataInputs>" +
                    "<wps:Input>" +
                        "<ows:Identifier>sos_url</ows:Identifier>" +
                        "<wps:Data>" +
                            //"<wps:LiteralData>http://nwisvaws02.er.usgs.gov/ogc-swie/wml2/dv/sos?request=GetObservation&amp;featureID=</wps:LiteralData>" +
                            "<wps:LiteralData>http://waterservices.usgs.gov/nwis/dv/?format=waterml,1.1&amp;sites=</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>sites</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                StringEscapeUtils.escapeXml(StringUtils.join(siteList, ",")) +
                                //"deprecated" +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>offering</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                "Mean" +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>property</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                "Discharge" +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>model_url</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                StringEscapeUtils.escapeXml(sosEndpoint + 
                                    "?request=GetObservation&service=SOS&version=1.0.0&offering") +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>modsites</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                //"deprecated" +
                                StringEscapeUtils.escapeXml(StringUtils.join(siteList, ",")) +
                                //"\\\"02177000\\\",\\\"02178400\\\",\\\"02184500\\\",\\\"02186000\\\"" +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                    "<wps:Input>" +
                        "<ows:Identifier>modprop</ows:Identifier>" +
                        "<wps:Data>" +
                            "<wps:LiteralData>" +
                                StringEscapeUtils.escapeXml(properties.get(0)) +
                            "</wps:LiteralData>" +
                        "</wps:Data>" +
                    "</wps:Input>" +
                "</wps:DataInputs>" +
                "<wps:ResponseForm>" +
                    "<wps:ResponseDocument storeExecuteResponse=\"true\" status=\"true\">" +
                        "<wps:Output asReference=\"true\">" +
                            "<ows:Identifier>output</ows:Identifier>" +
                        "</wps:Output>" +
                    "</wps:ResponseDocument>" +
                "</wps:ResponseForm>" +
            "</wps:Execute>");
    }
}


class WPSTask extends Thread {
    static org.slf4j.Logger log = LoggerFactory.getLogger(WPSTask.class);
    private static final DynamicReadOnlyProperties props = JNDISingleton.getInstance();
    
    public static final int CHECKS_UNTIL_NOTIFY = 28;
    public static final int CHECKS_UNTIL_FAIL = 4 * 60 * 24; // currently 24 hours
    public static final int CHECK_WAIT = 15000;
    
    public static int SLEEP_FOR_THREDDS;
    static {
        try {
            SLEEP_FOR_THREDDS = Integer.parseInt(props.getProperty("watersmart.wps.delay.ms"));
        }
        catch (NumberFormatException nfe) {
            SLEEP_FOR_THREDDS = 300000;
        }
    }
    
    private File zipLocation;
    private Map<String, String> metadata;
    
    private boolean uploadSuccessful = true;
    private boolean netcdfSuccessful = false;
    private boolean rStatsSuccessful = false;
    private boolean cswTransSuccessful = false;

    public WPSTask(File zipLocation, Map<String, String> metadata) {
        this.zipLocation = zipLocation;
        this.metadata = metadata;
    }

    String postToWPS(String url, String wpsRequest) throws IOException {
        HttpPost post;
        HttpClient httpClient = new DefaultHttpClient();

        post = new HttpPost(url);

        AbstractHttpEntity entity = new InputStreamEntity(new ByteArrayInputStream(wpsRequest.getBytes()), wpsRequest.length());
        post.setEntity(entity);
        HttpResponse response = httpClient.execute(post);

        return EntityUtils.toString(response.getEntity());

    }
    
    public boolean checkWPSProcess(Document document) throws
            XPathExpressionException, IOException {

        ProcessStatus procStat = new ProcessStatus(document);

        if (procStat.isSuccess()) {
            return true;
        }
//        else if (procStat.isStarted()) {
//            // keep it going
//        }
        else if (procStat.isFailed()){
            throw new IOException("Process failed");
        }
        return false;
    }
    
    private void sendMaybeEmail(String to) {
        String subject = "Processing is taking a long time";
        StringBuilder content = new StringBuilder();
        content.append("Your process is taking longer than expected.");
        content.append("  It might finish in a bit, but here is the status so far");
        content.append("\n\tUpload: ").append((uploadSuccessful) ? "success" : "waiting");
        content.append("\n\tParse: ").append((netcdfSuccessful) ? "success" : "waiting");
        content.append("\n\tStatistics: ").append((rStatsSuccessful) ? "success" : "waiting");
        content.append("\n\tMetadata: ").append((cswTransSuccessful) ? "success" : "waiting");
        content.append("\n\nYou will receive another email if there is a success, but may not receive a failure notification.");
        List<String> bcc = new ArrayList<String>();
        String from = props.getProperty("watersmart.email.from");
        String bccAddr = props.getProperty("watersmart.email.tracker");
        if (!"".equals(bccAddr)) {
            bcc.add(bccAddr);
        }
        EmailMessage message = new EmailMessage(from, to, null, bcc, subject,
                                                content.toString());
        try {
            EmailHandler.sendMessage(message);
        } catch (AddressException ex) {
            log.error("Unable to send maybe e-mail:\n" + message, ex);
        } catch (MessagingException ex) {
            log.error("Unable to send maybe e-mail:\n" + message, ex);
        }
    }

    private void sendCompleteEmail(Map<String, String> outputs, String to) {
        String subject = "Processing Complete";
        StringBuilder content = new StringBuilder();
        content.append("Your upload has finished conversion and processing,")
            .append(" you may view the results of the processing by going to:\n");

        for (String alg : outputs.keySet()) {
            content.append("\t").append(alg).append(": ").append(outputs.get(alg)).append("\n");
        }

        content.append("\nor return to the application to view your upload.");
        
        content.append("\nJust to double check, here is what happened");
        content.append("\n\tUpload: ").append((uploadSuccessful) ? "success" : "failure");
        content.append("\n\tParse: ").append((netcdfSuccessful) ? "success" : "failure");
        content.append("\n\tStatistics: ").append((rStatsSuccessful) ? "success" : "failure");
        content.append("\n\tMetadata: ").append((cswTransSuccessful) ? "success" : "failure");
        
        RunMetadata metaObj = RunMetadata.getInstance(metadata);
        content.append("\n\n\tFile: ").append(metaObj.getFileName());
        content.append("\n\tModeler: ").append(metaObj.getName());
        content.append("\n\tComments: ").append(metaObj.getComments());
        content.append("\n\tDate: ").append(metaObj.getCreationDate());
        
        content.append("\n\nHave a nice day!");
        List<String> bcc = new ArrayList<String>();
        String from = props.getProperty("watersmart.email.from");
        String bccAddr = props.getProperty("watersmart.email.tracker");
        if (!"".equals(bccAddr)) {
            bcc.add(bccAddr);
        }

        EmailMessage message = new EmailMessage(from, to, null, bcc, subject,
                                                content.toString());
        try {
            EmailHandler.sendMessage(message);
        } catch (AddressException ex) {
            log.error("Unable to send completed e-mail:\n" + message, ex);
        } catch (MessagingException ex) {
            log.error("Unable to send completed e-mail:\n" + message, ex);
        }
    }
    
    private void sendFailedEmail(Exception ex, String to) {
        String subject = "WaterSMART processing failed";
        StringBuilder content = new StringBuilder();
        content.append("Your request unfortunately failed, we are looking into it.");
        content.append("\n\tUpload: ").append((uploadSuccessful) ? "success" : "failure");
        content.append("\n\tParse: ").append((netcdfSuccessful) ? "success" : "failure");
        content.append("\n\tStatistics: ").append((rStatsSuccessful) ? "success" : "failure");
        content.append("\n\tMetadata: ").append((cswTransSuccessful) ? "success" : "failure");
        
        RunMetadata metaObj = RunMetadata.getInstance(metadata);
        content.append("\n\n\tFile: ").append(metaObj.getFileName());
        content.append("\n\tModeler: ").append(metaObj.getName());
        content.append("\n\tComments: ").append(metaObj.getComments());
        content.append("\n\tDate: ").append(metaObj.getCreationDate());
        
        content.append("\n\nhere is the stack trace for troubleshooting:\n\n");
        for (StackTraceElement el : ex.getStackTrace()) {
            content.append(el.toString()).append("\n");
        }
        List<String> bcc = new ArrayList<String>();
        String from = props.getProperty("watersmart.email.from");
        String bccAddr = props.getProperty("watersmart.email.tracker");
        if (!"".equals(bccAddr)) {
            bcc.add(bccAddr);
        }
        EmailMessage message = new EmailMessage(from, to, null, bcc, subject,
                                                content.toString());
        try {
            EmailHandler.sendMessage(message);
        } catch (AddressException ex1) {
            log.error("Unable to send failed e-mail:\n" + message + "\n\nOriginal Exception:\n" + ex.getMessage(), ex1);
        } catch (MessagingException ex1) {
            log.error("Unable to send failed e-mail:\n" + message + "\n\nOriginal Exception:\n" + ex.getMessage(), ex1);
        }
    }
    
    @Override
    public void run() {
        CSWTransactionHelper helper;
        Map<String, String> wpsOutputMap = Maps.newHashMap();
        ReturnInfo info;
        RunMetadata metaObj = RunMetadata.getInstance(metadata);
        String compReq;
        String repo = props.getProperty("watersmart.sos.model.repo");
        String netCDFFailMessage = "NetCDF failed unexpectedly ";
        String cswResponse;
        String sosEndpoint;
        String email = metaObj.getEmail();
        UUID uuid = UUID.randomUUID();

        // 1. Create NetCDF file
        // 2. Add results from NetCDF creation to CSW record
        // 3. Wait for THREDDS
        // 4. Run the compare stats WPS process
        // 5. Add results from WPS process to CSW record

        // 1. Create NetCDF file
        try {
            // CreateDSGFromZip.create() seems to cause a lot of grief. We keep getting:
            // java.lang.UnsatisfiedLinkError: Native Library ${application_path}/loader/com/sun/jna/linux-amd64/libnetcdf.so already loaded in another classloader
            // When developing and I see this, I have to restart the server and redeploy the project
            // The fault happens at gov.usgs.cida.jna.NetCDFJNAInitializer.contextDestroyed(NetCDFJNAInitializer.java:21)
            info = CreateDSGFromZip.create(zipLocation, metaObj);
            if (info != null && info.properties != null) {
                netcdfSuccessful = true;
            } else {
                throw new IOException("Output from NetCDF creation process was null");
            }
        } catch (IOException ex) {
            log.error("Failed to create NetCDF file: " + netCDFFailMessage, ex);
            sendFailedEmail(new RuntimeException(netCDFFailMessage), email);
            return;
        } catch (XMLStreamException ex) {
            log.error("Failed to create NetCDF file: " + netCDFFailMessage, ex);
            sendFailedEmail(new RuntimeException(netCDFFailMessage), email);
            return;
        }

        // The NetCDF process has passed so create a CSW record for the run and 
        // insert what we have so far.
        // The WPS output will be updated once the process succeeds/fails.  The UI
        // will show "Process not yet completed" in the meantime.
        sosEndpoint = repo + metaObj.getTypeString() + "/" + info.filename;
        wpsOutputMap.put(WPSImpl.stats_compare, "");
        helper = new CSWTransactionHelper(metaObj, sosEndpoint, wpsOutputMap);
        // 2. Add results from NetCDF creation to CSW record
        try {
            cswResponse = helper.addServiceIdentification();
            if (cswResponse != null) {
                cswTransSuccessful = true;
            } else {
                cswTransSuccessful = false;
                throw new IOException("Unable to update CSW Record");
            }
        } catch (Exception ex) {
            log.error("Failed to perform CSW insert", ex);
            sendFailedEmail(ex, email);
            return;
        }

        // 3. Wait for THREDDS
        try {
            Thread.sleep(SLEEP_FOR_THREDDS);
        } catch (InterruptedException ex) {
            // Typically we don't care about this, but we can log and move on.
            log.warn("THREDDS wait period was interrupted.");
            // If anything needs to be handled on an interruption, handle it here
        }

        // 4. Run the compare stats using the R-WPS package
        try {
            compReq = WPSImpl.createCompareStatsRequest(sosEndpoint, info.stations, info.properties);
            String algorithmOutput = runNamedAlgorithm("compare", compReq, uuid, metaObj);
            wpsOutputMap.put(WPSImpl.stats_compare, algorithmOutput);
        } catch (Exception ex) {
            log.error("Failed to run WPS algorithm", ex);
            sendFailedEmail(ex, email);
            return;
        }

        // 5. Add results from WPS process to CSW record
        if (wpsOutputMap.get(WPSImpl.stats_compare) != null) {
            rStatsSuccessful = true;
            helper = new CSWTransactionHelper(metaObj, sosEndpoint, wpsOutputMap);
            try {
                cswResponse = helper.updateRunMetadata(metaObj);
                cswTransSuccessful = cswResponse != null;
                sendCompleteEmail(wpsOutputMap, email);
            } catch (IOException ex) {
                log.error("Failed to perform CSW update", ex);
                sendFailedEmail(ex, email);
            } catch (URISyntaxException ex) {
                log.error("Failed to perform CSW update,", ex);
                sendFailedEmail(ex, email);
            }
        } else {
            log.error("Failed to run WPS algorithm");
            sendFailedEmail(new Exception("Failed to run WPS algorithm"), email);
        }
    }
    
    /**
     * 
     * @param alg Algorithm to run
     * @param wpsRequest XML representing WPS request
     * @param uuid UUID for output
     * @param metaObj RunMetadata associated with user
     * @return Web Accessible File with process results
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws XPathExpressionException
     * @throws InterruptedException 
     */
    private String runNamedAlgorithm(String alg, String wpsRequest, UUID uuid, RunMetadata metaObj) 
            throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, InterruptedException {
        
            //String wpsRequest = WPSImpl.createNahatStatsRequest(sosEndpoint, info.stations, info.properties);
            String wpsResponse = postToWPS(props.getProperty("watersmart.wps.url"), wpsRequest);
            log.debug(wpsResponse);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document wpsResponseDoc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(wpsResponse.getBytes()));
            ProcessStatus procStat = new ProcessStatus(wpsResponseDoc);
            String wpsCheckPoint = procStat.getStatusLocation();
            log.debug(wpsCheckPoint);
            String contextPath = props.getProperty("watersmart.external.mapping.url");
            
            InputStream is = null;
            InputStream resultIs = null;

            try {
                boolean completed = false;
                Document document = null;
                int checks = 0;
                while (!completed) {
                    checks++;
                    Thread.sleep(CHECK_WAIT);
                    log.debug("Checking: " + checks);
                    is = HTTPUtils.sendPacket(new URL(wpsCheckPoint), "GET");
                    document = CheckProcessCompletion.parseDocument(is);
                    completed = checkWPSProcess(document);
                    if (checks == CHECKS_UNTIL_NOTIFY) {
                        sendMaybeEmail(metaObj.getEmail());
                    }
                    if (checks > CHECKS_UNTIL_FAIL) {
                        throw new IOException("R Statistics never returned");
                    }
                }

                ProcessStatus resultStatus = new ProcessStatus(document);
                String outputReference = resultStatus.getOutputReference();
                resultIs = HTTPUtils.sendPacket(new URL(outputReference), "GET");
                String resultStr = IOUtils.toString(resultIs, "UTF-8");
                // copy results to persistant location // switch to completed document above

                log.debug(resultStr);

                File destinationDir = new File(props.getProperty("watersmart.file.location")
                        + props.getProperty("watersmart.file.location.wps.repository") + File.separatorChar
                        + uuid);
                if (!destinationDir.exists()) {
                    FileUtils.forceMkdir(destinationDir);
                }
                String filename = metaObj.getTypeString() + "-" + metaObj.getScenario()
                        + "-" + metaObj.getModelVersion() + "." + metaObj.getRunIdent()
                        + "-" + alg + ".txt";
                File destinationFile = new File(destinationDir.getCanonicalPath()
                        + File.separatorChar + filename);
                FileUtils.write(destinationFile, resultStr, "UTF-8");
                String destinationFileName = destinationFile.getName();
                String webAccessibleFile = contextPath + props.getProperty("watersmart.file.location.wps.repository")
                        + "/" + uuid + "/" + destinationFileName;

                return webAccessibleFile;
            } finally {
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(resultIs);
            }
    }
}
