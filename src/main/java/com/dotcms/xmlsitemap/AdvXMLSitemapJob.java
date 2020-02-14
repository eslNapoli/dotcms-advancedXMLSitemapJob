package com.dotcms.xmlsitemap;

import com.dotcms.business.CloseDBIfOpened;
import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.contenttype.transform.contenttype.StructureTransformer;
import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.business.IdentifierAPI;
import com.dotmarketing.business.PermissionAPI;
import com.dotmarketing.business.Permissionable;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.filters.CMSFilter;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.business.HostAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.fileassets.business.FileAssetAPI;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.portlets.htmlpageasset.business.HTMLPageAssetAPI;
import com.dotmarketing.portlets.htmlpageasset.model.IHTMLPage;
import com.dotmarketing.portlets.languagesmanager.business.LanguageAPI;
import com.dotmarketing.portlets.languagesmanager.model.Language;
import com.dotmarketing.portlets.links.model.Link;
import com.dotmarketing.portlets.structure.model.Structure;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.InodeUtils;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.RegEX;
import com.dotmarketing.util.RegExMatch;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.XMLUtils;
import com.liferay.portal.model.User;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.lang.StringUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.StatefulJob;

/**
 * @author Giacomo Petillo giacomo.petillo@eng.it
 */
public class AdvXMLSitemapJob implements Job, StatefulJob {

    private Host currentHost = null;
    private User systemUser = null;
    private File temporaryFile = null;
    private File compressedFile = null;
    private OutputStreamWriter out = null;

    private int sitemapCounter = 1;
    private int processedRegistries = 0;
    private Map<String, Integer> hostFilesCounter = null;

    private String XML_SITEMAPS_FOLDER;
    private ContentletAPI conAPI = APILocator.getContentletAPI();
    private FolderAPI folderAPI = APILocator.getFolderAPI();
    private IdentifierAPI identAPI = APILocator.getIdentifierAPI();
    private UserAPI userAPI = APILocator.getUserAPI();
    private HostAPI hostAPI = APILocator.getHostAPI();
    private HTMLPageAssetAPI htmlPageAssetAPI = APILocator.getHTMLPageAssetAPI();
    private LanguageAPI languageAPI = APILocator.getLanguageAPI();

    private String modifiedDateStringValue = UtilMethods.dateToHTMLDate(new java.util.Date(), "yyyy-MM-dd");

    private String hostsToIgnoreConfig;
    private String headLog = "";
    private Map<String, String> hostnameMap;
    private String foldersToIgnoreConfig;

    private final String[] DEFAULT_IGNORED_FOLDERS = {"application", "home", "resources", "search", XMLSitemapServlet.DEFAULT_DIR_XMLSITEMAPS};

    private List<String> detailPageListToSkip;

    /**
     * Instantiates a new Enginnering xml sitemap job.
     */
    public AdvXMLSitemapJob() {

        try {
            systemUser = userAPI.getSystemUser();

            hostFilesCounter = new HashMap<>();

            XML_SITEMAPS_FOLDER = Config.getStringProperty("org.dotcms.XMLSitemap.XML_SITEMAPS_FOLDER", XMLSitemapServlet.DEFAULT_DIR_XMLSITEMAPS);
            modifiedDateStringValue = UtilMethods.dateToHTMLDate(new java.util.Date(System.currentTimeMillis()), "yyyy-MM-dd");
            hostsToIgnoreConfig = Config.getStringProperty("org.dotcms.XMLSitemap.IGNORE_hostname", "");

            hostnameMap = new HashMap<>();
            detailPageListToSkip = new ArrayList<>();

        } catch (Exception e) {
            Logger.error(this, e.getMessage(), e);
        }
    }

    @CloseDBIfOpened
    public void execute(JobExecutionContext arg0) throws JobExecutionException {
        try {

            systemUser = userAPI.getSystemUser();

            Logger.info(this,
                "INIZIO GENERAZIONE SITEMAP " + UtilMethods.dateToHTMLDate(new java.util.Date(System.currentTimeMillis()), "yyyy-MM-dd HH:mm:ss"));

            generateSitemapPerHost();

            Logger.info(this,
                "FINE GENERAZIONE SITEMAP " + UtilMethods.dateToHTMLDate(new java.util.Date(System.currentTimeMillis()), "yyyy-MM-dd HH:mm:ss"));

        } catch (Exception e) {
            Logger.error(this, e.getMessage(), e);
        }
    }

    private List<String> getIgnorableStrcutureIdsForHost(Host currentHost) {

        List<String> ignorableStructureIds = new ArrayList<>();

        final String structuresToIgnoreConfig = Config.getStringProperty("org.dotcms.XMLSitemap.IGNORE_Structure_Ids", "");
        final String structuresToIgnorePerHostConfig = Config
            .getStringProperty("org.dotcms.adv.XMLSitemap.IGNORE_Structure_Ids_" + currentHost.getHostname(), "");

        if (UtilMethods.isSet(structuresToIgnoreConfig)) {

            String[] ignorableStructureNames = structuresToIgnoreConfig.split(",");
            Logger.debug(this, headLog + "Strutture GLOBALI IGNORATE " + ignorableStructureNames);

            for (String ignorableStructureName : ignorableStructureNames) {
                ignorableStructureIds.add(ignorableStructureName.toLowerCase());
            }

        }

        if (UtilMethods.isSet(structuresToIgnorePerHostConfig)) {

            String[] ignorableStructureNames = structuresToIgnorePerHostConfig.split(",");
            Logger.debug(this, headLog + " Strutture per " + currentHost.getHostname() + " IGNORATE " + ignorableStructureNames);

            for (String ignorableStructureName : ignorableStructureNames) {
                ignorableStructureIds.add(ignorableStructureName.toLowerCase());
            }

        }

        return ignorableStructureIds;
    }

    private String getUrlPatternReplacementText(Host currentHost,
        String structureName) {
        String replacingText = "";

        try {
            String replacementTextFromConfig = Config.getStringProperty("org.dotcms.XMLSitemap." + structureName + ".IGNORE_UrlText", "");

            if (UtilMethods.isSet(replacementTextFromConfig)) {
                replacingText = replacementTextFromConfig;
            }

        } catch (Exception exception) {
            // ignore this error and return the default text.

        }
        return replacingText;
    }

    /**
     * Generate the sitemap xml based on the show on menu pages, files, link and folder
     *
     * @throws DotDataException the dot data exception
     * @throws DotSecurityException the dot security exception
     */
    @SuppressWarnings("unchecked")
    @CloseDBIfOpened
    public void generateSitemapPerHost() throws DotDataException, DotSecurityException {

        List<Host> hostsList = hostAPI.findAll(systemUser, false);
        List<String> hostToIgnoreList = generateIgnorableHostname(hostsToIgnoreConfig);

        List<ContentType> contentTypesList = APILocator.getContentTypeAPI(APILocator.systemUser()).findAll();

        for (Host host : hostsList) {

            int totalContentlet = 0;

            String baseURL = host.getHostname();
            String configuredHostname = Config.getStringProperty("org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL_" + host.getHostname(), host.getHostname());

            if (!baseURL.equalsIgnoreCase(configuredHostname)) {
                baseURL = configuredHostname;
            }

            hostnameMap.put(host.getHostname(), baseURL);

            headLog = "[" + getHostNameForSitemap(host.getHostname()) + "]  ";

            Logger.debug(this, "**************** **************** ****************");
            Logger.debug(this, "**************** [" + getHostNameForSitemap(host.getHostname()) + "] ****************");
            Logger.debug(this, "**************** **************** ****************");

            if (isHostToIgnore(host, hostToIgnoreList)) {
                Logger.debug(this, headLog + " IGNORATO ");
                continue;
            }

            processedRegistries = 0;
            currentHost = host;
            sitemapCounter = 1;

            StringBuffer stringbuf = new StringBuffer();

            try {

                hostFilesCounter.put(baseURL, sitemapCounter);

                /* adding host url */
                stringbuf.append("<url><loc>")
                    .append(XMLUtils.xmlEscape(XMLSitemapServlet.DEFAULT_PROTOCOL + baseURL + "/"))
                    .append("</loc><lastmod>")
                    .append(modifiedDateStringValue)
                    .append("</lastmod><changefreq>daily</changefreq></url>\n");

                writeFile(stringbuf.toString());

                addRegistryProcessed();

                List<String> ignorableStructureIds = getIgnorableStrcutureIdsForHost(host);

                /*
                 * This part generate the detail pages sitemap links per
                 * structure
                 */
                for (ContentType type : contentTypesList) {

                    String stVelocityVarName = type.variable();
                    if (ignorableStructureIds.contains(stVelocityVarName.toLowerCase())) {
                        Logger.debug(this, headLog + stVelocityVarName + " IGNORATO ");
                        continue;
                    }

                    Structure structure = new StructureTransformer(type).asStructure();

                    //Continue only if have a detail
                    if (!InodeUtils.isSet(structure.getPagedetail())) {
                        continue;
                    }

                    //Getting the detail page, that detail page could be a HTMLPageAsset or a legacy page
                    IHTMLPage page = null;
                    final List<Contentlet> detailPageList = conAPI.search("+identifier:" + structure.getPagedetail() + " +live:true", 0, 0, "moddate", systemUser, false);

                    if (detailPageList != null && !detailPageList.isEmpty()) {

                        //First lets asume it is a HTMLPageAsset
                        final Contentlet contentlet = detailPageList.get(0);
                        if (contentlet != null) {

                            page = htmlPageAssetAPI.fromContentlet(contentlet);

                            detailPageListToSkip.add(page.getURI());
                        }

                    }

                    if (!UtilMethods.isSet(page) || !UtilMethods.isSet(page.getIdentifier())) {
                        Logger.error(this, headLog + "Unable to find detail page for structure [" + stVelocityVarName + "].");
                        continue;
                    }

                    Identifier pageIdentifier = identAPI.find(page.getIdentifier());
                    if (!UtilMethods.isSet(pageIdentifier) || !UtilMethods.isSet(pageIdentifier.getId())) {
                        Logger.error(this, headLog + "Unable to find detail page for structure [" + stVelocityVarName + "].");
                        continue;
                    }

                    Logger.debug(this, headLog + "Creating Site Map for Structure " + stVelocityVarName);

                    String hostQuery = "+(conhost:" + host.getIdentifier() + ")";
                    String query = hostQuery + " +structureName:" + structure.getVelocityVarName() + " +deleted:false +live:true";

                    final List<Contentlet> contentletList = conAPI.search(query, -1, 0, "moddate", systemUser, true);
                    String structureURLMap = structure.getUrlMapPattern();

                    List<RegExMatch> matches = null;

                    if (UtilMethods.isSet(structureURLMap)) {
                        matches = RegEX.find(structure.getUrlMapPattern(), "({[^{}]+})");
                    }

                    int i = 0;
                    for (Contentlet contenlet : contentletList) {

                        Logger.info(this,
                            headLog + structure.getVelocityVarName() + " [" + (++i) + "/" + contentletList.size() + "] " + ++totalContentlet);

                        stringbuf = new StringBuffer();

                        try {

                            if (UtilMethods.isSet(structureURLMap) && (matches != null)) {

                                String uri = structureURLMap;

                                for (RegExMatch match : matches) {

                                    String urlMapField = match.getMatch();
                                    String urlMapFieldValue = contenlet.getStringProperty(urlMapField.substring(1, (urlMapField.length() - 1)));

                                    urlMapField = sanitizeUrlMapField(urlMapField);

                                    if (urlMapFieldValue != null) {
                                        uri = uri.replaceAll(urlMapField, urlMapFieldValue);
                                    }

                                }

                                Language contentletLanguage = languageAPI.getLanguage(contenlet.getLanguageId());

                                if (!contentletLanguage.getCountryCode().equalsIgnoreCase("it")) {
                                    uri = "/" + contentletLanguage.getLanguageCode().toLowerCase() + uri;
                                }

                                String urlReplacementText = getUrlPatternReplacementText(host, stVelocityVarName);

                                uri = uri.replaceAll(urlReplacementText, "");

                                if (StringUtils.isNotEmpty(uri)) {
                                    stringbuf.append("<url><loc>")
                                        .append(XMLUtils.xmlEscape(XMLSitemapServlet.DEFAULT_PROTOCOL + baseURL + uri))
                                        .append("</loc><lastmod>")
                                        .append(UtilMethods.dateToHTMLDate(contenlet.getModDate(), "yyyy-MM-dd"))
                                        .append("</lastmod><changefreq>daily</changefreq></url>\n");
                                }

                            } else {

                                Logger.debug(this, headLog + "Scrittura GREZZA");

                                stringbuf.append("<url><loc>")
                                    .append(XMLUtils.xmlEscape(
                                        XMLSitemapServlet.DEFAULT_PROTOCOL + baseURL + pageIdentifier.getURI() + "?id=" + contenlet
                                            .getIdentifier()))
                                    .append("</loc><lastmod>")
                                    .append(modifiedDateStringValue)
                                    .append("</lastmod><changefreq>daily</changefreq></url>\n");
                            }

                            writeFile(stringbuf.toString());
                            addRegistryProcessed();

                        } catch (Exception e) {
                            Logger.error(this, e.getMessage(), e);
                        }

                    } // FINE LISTA CONTENUTI
                } // FINE LISTA TIPI DI CONTENUTO

                List<Folder> folderList = folderAPI.findSubFolders(host, false);
                List<String> ignorableFolders = getIgnorableFolderNames(host);

                Logger.info(this, headLog + "FOLDER TROVATE [" + folderList.size() + "] DA IGNORARE [" + ignorableFolders.size() + "]" + ignorableFolders );

                for (Folder folder : folderList) {

                    if (!ignorableFolders.contains(folder.getName())) {

                        Logger.debug(this,
                            headLog + "Folder Iteration in progress Name [" + folder.getName() + "], show on Menu Indicator [" + folder
                                .isShowOnMenu() + "]");

                        buildSubFolderSiteMap(host, folder, 100, 1, 1);

                    } else {
                        Logger.info(this, headLog + folder.getName() + " IGNORATA");
                    }

                }

            } catch (Exception e) {
                Logger.error(this, e.getMessage(), e);
            }

            if (UtilMethods.isSet(temporaryFile)) {
                closeFileWriter();
            }

            Logger.debug(this, headLog + "FINITO");
        }
    }


    private List<String> getIgnorableFolderNames(Host host) {

        List<String> ignorableFolders = new ArrayList<>();

        for (String ignorableFolderName : Arrays.asList(DEFAULT_IGNORED_FOLDERS)) {
            ignorableFolders.add(ignorableFolderName.trim().toLowerCase().replace("/", ""));
        }

        final String foldersToIgnoreConfig = Config.getStringProperty("org.dotcms.XMLSitemap.IGNORE_folders", "");

        if (UtilMethods.isSet(foldersToIgnoreConfig)) {

            String[] ignorableFolderNames = foldersToIgnoreConfig.split(",");

            for (String ignorableFolderName : ignorableFolderNames) {

                ignorableFolders.add(ignorableFolderName.trim().toLowerCase());
            }

        }

        final String foldersPerHostToIgnoreConfig = Config.getStringProperty("org.dotcms.XMLSitemap.IGNORE_folders_" + host.getHostname(), "");

        if (UtilMethods.isSet(foldersPerHostToIgnoreConfig)) {

            String[] ignorableFolderNames = foldersToIgnoreConfig.split(",");

            for (String ignorableFolderName : ignorableFolderNames) {

                ignorableFolders.add(ignorableFolderName.trim().toLowerCase());
            }
        }

        return ignorableFolders;
    }


    private List<String> generateIgnorableHostname(String hostsToIgnoreConfig) {

        List<String> ignorableHosts = new ArrayList<>();

        if (UtilMethods.isSet(hostsToIgnoreConfig)) {

            String[] ignorableHostNames = hostsToIgnoreConfig.split(",");

            ignorableHosts = Arrays.asList(ignorableHostNames);
        }

        return ignorableHosts;
    }

    private boolean isHostToIgnore(Host host, List<String> hostToIgnore) {

        if (host.isSystemHost()) {
            return true;
        }

        if (hostToIgnore == null) {
            return false;
        } else {
            return hostToIgnore.contains(host.getHostname());
        }

    }

    private String sanitizeUrlMapField(String urlMapField) {
        return urlMapField.replaceFirst("\\{", "\\\\{").replaceFirst("\\}", "\\\\}");
    }

    /*
     * Add the subfolder site map code to the xml site map file
     */
    private void buildSubFolderSiteMap(Host host, Folder thisFolder, int numberOfLevels, int currentLevel, int orderDirection) throws DotDataException, DotSecurityException {

        StringBuilder stringBuilder = new StringBuilder();

        // gets menu items for this folder
        List<Folder> subFolders = folderAPI.findSubFoldersTitleSort(thisFolder, systemUser, false);
        List<IHTMLPage> liveHTMLPages = htmlPageAssetAPI.getLiveHTMLPages(thisFolder, systemUser, false);

        Identifier folderIdentifier = identAPI.find(thisFolder.getIdentifier());

        //The folder have a index page?, if don't we don' need to add it to the site map
        Identifier indexPageId = identAPI.loadFromCache(host, folderIdentifier.getURI() + "/" + CMSFilter.CMS_INDEX_PAGE);

        Logger.debug(this,
            headLog
                + "Performing check for folders [" + (folderIdentifier.getURI() + "/" + CMSFilter.CMS_INDEX_PAGE) + "], "
                + "Identifier Check [" + (indexPageId != null) + "], "
                + "Children Count [" + (subFolders.size() + liveHTMLPages.size()) + "], "
                + "Host Identifier [" + host.getIdentifier() + "], "
                + "Identifier " + ((indexPageId != null) ? indexPageId.getId() : "") + "]");

        boolean isIndexPageAlreadyConfigured = false;

        if ((indexPageId != null) && InodeUtils.isSet(indexPageId.getId())) {

            stringBuilder.append("<url><loc>")
                .append( XMLUtils.xmlEscape(XMLSitemapServlet.DEFAULT_PROTOCOL + getHostNameForSitemap(host.getHostname()) + folderIdentifier.getURI()) )
                .append("</loc><lastmod>" + modifiedDateStringValue)
                .append("</lastmod><changefreq>daily</changefreq></url>\n");

            Logger.debug(this, "Writing the XMLConfiguration for Folder[" + XMLUtils
                .xmlEscape(XMLSitemapServlet.DEFAULT_PROTOCOL + getHostNameForSitemap(host.getHostname()) + folderIdentifier.getURI()) + "]");

            isIndexPageAlreadyConfigured = true;

            writeFile(stringBuilder.toString());
            addRegistryProcessed();
        }

        if (currentLevel < numberOfLevels) {

            String hostName = getHostNameForSitemap(host.getHostname());

            for (IHTMLPage page : liveHTMLPages) {

                if( !detailPageListToSkip.contains(page.getURI())) {
                    writeHTMLPage(host, page, isIndexPageAlreadyConfigured);
                } else {
                    Logger.debug(this, page.getURI() + " DA NON INCLUDERE");
                }
            }

            for (Permissionable childElement : subFolders) {

                if (childElement instanceof Folder) {

                    if (currentLevel <= numberOfLevels) {

                        Folder childFolder = (Folder) childElement;

                        buildSubFolderSiteMap(host, childFolder, numberOfLevels, currentLevel + 1, orderDirection);

                    } else {

                        Logger.debug(this, "NON DOVRESTI ESSERE QUI!!!");

//                        stringbuf.append("<url><loc>")
//                            .append(XMLUtils.xmlEscape(XMLSitemapServlet.DEF_PROTOCOL + hostName + subFolderIdentifier.getURI()))
//                            .append("</loc><lastmod>")
//                            .append(modifiedDateStringValue)
//                            .append("</lastmod><changefreq>daily</changefreq></url>\n");
//
//                        Logger.debug(this, "Writing the XMLConfiguration Second Level Check for [" + XMLUtils.xmlEscape(XMLSitemapServlet.DEF_PROTOCOL+ hostName + subFolderIdentifier.getURI()) + "]");
//
//                        writeFile(stringbuf.toString());
//                        addRegistryProcessed();
                    }

                } else if (childElement instanceof Link) {

                    writeLink(host, (Link) childElement);

                } else if (childElement instanceof IHTMLPage) {

                    writeHTMLPage(host, (IHTMLPage) childElement, isIndexPageAlreadyConfigured);

                } else if (childElement instanceof Contentlet) {

                    writeContentlet(host, (Contentlet) childElement);

                }
            }

        }

    }

    private String getHostNameForSitemap(String hostname) {
        return hostnameMap.get(hostname);
    }

    /**
     * Create a new instance of the temporary file to save the index data
     */
    private void openFileWriter() {
        try {
            temporaryFile = new File(
                Config.getStringProperty("org.dotcms.XMLSitemap.SITEMAP_XML_FILENAME", XMLSitemapServlet.DEFAULT_SITEMAP_XML_FILENAME)
                    + ".xml");
            out = new OutputStreamWriter(Files.newOutputStream(temporaryFile.toPath()), StandardCharsets.UTF_8);

            out.write("<?xml version='1.0' encoding='UTF-8'?>\n");
            out
                .write(
                    "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\">\n");
            out.flush();

        } catch (Exception e) {
            Logger.error(this, e.getMessage(), e);
        }
    }

    /**
     * Save in backend the new XMLSitemapGenerated.xml file and delete the temporary file
     */
    private void closeFileWriter() {

        Logger.debug(this, "closeFileWriter hostname         [" + getHostNameForSitemap(currentHost.getHostname()) + "]");
        Logger.debug(this, "closeFileWriter hostFilesCounter [" + hostFilesCounter.get(getHostNameForSitemap(currentHost.getHostname())) + "]");

        int counter = hostFilesCounter.get(getHostNameForSitemap(currentHost.getHostname()));

        Logger.debug(this, "closeFileWriter contatore        [" + counter + "]");

        try {
            out.write("</urlset>");
            out.flush();
            out.close();

            /******* Begin Generate compressed file *****************************/
            String dateCounter = Calendar.getInstance().get(Calendar.MONTH)
                + "" + Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                + "" + Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                + "" + Calendar.getInstance().get(Calendar.MINUTE);

            String sitemapName = Config.getStringProperty("org.dotcms.XMLSitemap.SITEMAP_XML_GZ_FILENAME", "XMLSitemapGenerated")
                + dateCounter + counter + ".xml.gz";

            compressedFile = new File(sitemapName);
            GZIPOutputStream gout = new GZIPOutputStream(Files.newOutputStream(compressedFile.toPath()));

            // Open the input file
            InputStream in = Files.newInputStream(temporaryFile.toPath());

            // Transfer bytes from the input file to the GZIP output stream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                gout.write(buf, 0, len);
            }
            in.close();

            // Complete the GZIP file
            gout.finish();
            gout.close();
            /************ End Generate compressed file **********************/

            /* Saving file in dotCMS */

            Folder folder = folderAPI.findFolderByPath(XML_SITEMAPS_FOLDER,
                currentHost, systemUser, true);

            if (!InodeUtils.isSet(folder.getIdentifier())) {
                folder = folderAPI.createFolders(XML_SITEMAPS_FOLDER,
                    currentHost, systemUser, true);
            }

            File uploadedFile = new File(sitemapName);
            // Create the new file
            Contentlet file = new Contentlet();
            file.setStructureInode(folder.getDefaultFileType());
            file.setStringProperty(FileAssetAPI.TITLE_FIELD, UtilMethods.getFileName(sitemapName));
            file.setFolder(folder.getInode());
            file.setHost(currentHost.getIdentifier());
            file.setBinary(FileAssetAPI.BINARY_FIELD, uploadedFile);
            if (CacheLocator.getContentTypeCache().getStructureByInode(file.getStructureInode()).getStructureType()
                == Structure.STRUCTURE_TYPE_FILEASSET) {
                file.setStringProperty("fileName", sitemapName);
            }
            file = APILocator.getContentletAPI().checkin(file, systemUser, false);
            if (APILocator.getPermissionAPI().doesUserHavePermission(file, PermissionAPI.PERMISSION_PUBLISH, systemUser)) {
                APILocator.getVersionableAPI().setLive(file);
            }
            APILocator.getVersionableAPI().setWorking(file);


        } catch (Exception e) {
            Logger.error(this, e.getMessage(), e);
        } finally {
            hostFilesCounter.put(currentHost.getHostname(), counter + 1);
            temporaryFile.delete();
            compressedFile.delete();
            temporaryFile = null;
            compressedFile = null;
        }
    }

    /**
     * Write inside temporary file index pages
     */
    private void writeFile(String data) {

        try {
            if (temporaryFile == null) {
                openFileWriter();
            }

            out.write(data);
            out.flush();

            if (temporaryFile.length() > 9437184 || processedRegistries > 49999) {
                closeFileWriter();
                sitemapCounter = sitemapCounter + 1;
                processedRegistries = 0;
                openFileWriter();
            }

        } catch (Exception e) {
            Logger.error(this, e.getMessage(), e);
        }

    }

    /**
     * Add one to the the number of pages processed counter
     */
    private void addRegistryProcessed() {
        processedRegistries = processedRegistries + 1;
    }

    private void writeContentlet(Host host, Contentlet contentlet) throws DotDataException, DotSecurityException {

        if (contentlet.isLive() && !contentlet.isArchived()) {

            Identifier identifier = APILocator.getIdentifierAPI().find(contentlet);
            String url = identifier.getParentPath() + contentlet.getStringProperty(FileAssetAPI.FILE_NAME_FIELD);

            String stringbuf = "<url><loc>"
                + XMLUtils.xmlEscape(XMLSitemapServlet.DEFAULT_PROTOCOL
                + getHostNameForSitemap(host.getHostname())
                + UtilMethods.encodeURIComponent(url))
                + "</loc><lastmod>"
                + UtilMethods.dateToHTMLDate(contentlet.getModDate(), "yyyy-MM-dd")
                + "</lastmod><changefreq>daily</changefreq></url>\n";

            writeFile(stringbuf);
            addRegistryProcessed();
        }
    }

    private void writeHTMLPage(Host host, IHTMLPage page, Boolean isIndexPageAlreadyConfigured) throws DotDataException, DotSecurityException {

        Identifier paginaFiglia = identAPI.find(page.getIdentifier());

        if (page.isLive() && !page.isArchived()) {

            String pathToPageUrl = paginaFiglia.getURI();

            Language contentletLanguage = APILocator.getLanguageAPI().getLanguage(page.getLanguageId());

            if (!contentletLanguage.getCountryCode().equalsIgnoreCase("it")) {
                pathToPageUrl = "/" + contentletLanguage.getLanguageCode().toLowerCase() + pathToPageUrl;
            }

            String indexPageConfiguration = "/" + CMSFilter.CMS_INDEX_PAGE;

            pathToPageUrl = XMLUtils.xmlEscape(XMLSitemapServlet.DEFAULT_PROTOCOL + getHostNameForSitemap(host.getHostname()) + pathToPageUrl);

            if (pathToPageUrl.endsWith(indexPageConfiguration) && isIndexPageAlreadyConfigured) {
                Logger.debug(this, "Index Page is already configured, skipping the process [" + pathToPageUrl + "]");
                return;
            }

            pathToPageUrl = pathToPageUrl.replace(indexPageConfiguration, "");

            String stringbuf = "<url><loc>"
                + pathToPageUrl
                + "</loc><lastmod>"
                + UtilMethods.dateToHTMLDate(page.getModDate(), "yyyy-MM-dd")
                + "</lastmod><changefreq>daily</changefreq></url>\n";

            writeFile(stringbuf);
            addRegistryProcessed();
        }
    }

    private void writeLink(Host host, Link link) throws DotSecurityException, DotDataException {

        if (link.isLive() && !link.isDeleted()) {
            if (link.getUrl().startsWith(getHostNameForSitemap(host.getHostname()))) {

                String stringbuf = "<url><loc>"
                    + XMLUtils.xmlEscape(link.getProtocal()
                    + link.getUrl())
                    + "</loc><lastmod>"
                    + UtilMethods.dateToHTMLDate(link.getModDate(), "yyyy-MM-dd")
                    + "</lastmod><changefreq>daily</changefreq></url>\n";

                writeFile(stringbuf);
                addRegistryProcessed();
            }
        }
    }

}