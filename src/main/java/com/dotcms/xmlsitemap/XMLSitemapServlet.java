package com.dotcms.xmlsitemap;

import com.dotmarketing.business.IdentifierAPI;
import java.io.IOException;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dotmarketing.beans.Host;
import com.dotmarketing.beans.Identifier;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.UserAPI;
import com.dotmarketing.business.web.HostWebAPI;
import com.dotmarketing.business.web.HostWebAPIImpl;
import com.dotmarketing.portlets.contentlet.business.ContentletAPI;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.fileassets.business.FileAssetAPI;
import com.dotmarketing.portlets.folders.business.FolderAPI;
import com.dotmarketing.portlets.folders.model.Folder;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.XMLUtils;
import org.jetbrains.annotations.NotNull;

/**
 * The type Xml sitemap servlet.
 */
public class XMLSitemapServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_DIR_XMLSITEMAPS = "/sitemaps/";
    public static final String DEFAULT_SITEMAP_XML_FILENAME = "sitemap";
    public static final String DEFAULT_PROTOCOL = "https://";


    private FolderAPI folderAPI = APILocator.getFolderAPI();
    private UserAPI userAPI = APILocator.getUserAPI();
    private ContentletAPI conAPI = APILocator.getContentletAPI();

    public void init(ServletConfig config) throws ServletException {
        Logger.debug(this, "Init " + this.getClass().getCanonicalName() );
    }

    protected void service(HttpServletRequest request, @NotNull HttpServletResponse response) throws ServletException, IOException {

        Logger.debug( this, "*********** RECUPERO sitemap_index.xml");

        ServletOutputStream out = response.getOutputStream();

        try {

            if (Config.CONTEXT == null) {
                Logger.debug(this, "Link not Found");
                response.sendError(404, "Link not Found");
                return;
            }

            HostWebAPI hostWebAPI = new HostWebAPIImpl();
            Host host = hostWebAPI.getCurrentHost(request);
            String hostId = host.getIdentifier();
            List<Contentlet> sitemapsGeneratedList;

            Folder sitemapsFolder = folderAPI.findFolderByPath(Config.getStringProperty("org.dotcms.adv.XMLSitemap.XML_SITEMAPS_FOLDER", DEFAULT_DIR_XMLSITEMAPS), hostId, userAPI.getSystemUser(), true);
            String protocol = Config.getStringProperty("org.dotcms.adv.XMLSitemap.SITEMAP_PROTOCOL", DEFAULT_PROTOCOL);

            String baseURL = Config.getStringProperty("org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL", host.getHostname());

            // Aggiungere "org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL_<nomehost>" in dotmarketing-config.properties
            if( !baseURL.equalsIgnoreCase(Config.getStringProperty("org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL_"+host.getHostname(), host.getHostname())) ) {

                Logger.debug(this, Config.getStringProperty("org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL_"+host.getHostname(), host.getHostname()));

                baseURL = Config.getStringProperty("org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL_"+host.getHostname(), host.getHostname());
            }

            Logger.info(this,  "Richiesta sitemap_index.xml");
            Logger.debug(this, "XMLSitemap PARAMETER");
            Logger.debug(this, "           protocol " + protocol);
            Logger.debug(this, "           baseURL  " + baseURL);

            sitemapsGeneratedList = conAPI.findContentletsByFolder(sitemapsFolder, userAPI.getSystemUser(), false);

            if(sitemapsGeneratedList.size() > 0){

                StringBuilder sitemapIndex =  new StringBuilder();
                sitemapIndex.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                sitemapIndex.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/siteindex.xsd\">");

                IdentifierAPI identifierAPI = APILocator.getIdentifierAPI();

                for (Contentlet sitemap : sitemapsGeneratedList) {

                    if (sitemap.isLive() && !sitemap.isArchived()) {

                        Identifier identifier = identifierAPI.find(sitemap);
                        sitemapIndex.append("<sitemap>")
                                    .append("<loc>"+ XMLUtils.xmlEscape(protocol  + baseURL
                            + UtilMethods.encodeURIComponent(identifier.getParentPath()+sitemap.getStringProperty(FileAssetAPI.FILE_NAME_FIELD)))
                            + "</loc>")
                                    .append("<lastmod>"+UtilMethods.dateToHTMLDate(sitemap.getModDate(), "yyyy-MM-dd")+"</lastmod>")
                                    .append("</sitemap>");

                    }

                }

                sitemapIndex.append("</sitemapindex>");
                out.print(sitemapIndex.toString());

            }else {
                Logger.debug(this, "No Index found");
                response.sendError(404, "Link not Found");
//                return;
            }

        }catch(Exception e){
            Logger.error(this, "Error getting XML SiteMap Index file. "+e.getMessage());
        }finally{
            out.close();
        }
    }
}