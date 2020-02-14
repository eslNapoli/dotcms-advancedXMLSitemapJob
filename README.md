# README

Advanced XMLSitemapJob 

## How to build this example

To install all you need to do is build the JAR. to do this run
`./gradlew jar`

This will build two jars in the `build/libs` directory: a bundle fragment (in order to expose needed 3rd party libraries from dotCMS) and the plugin jar 

## Config parameter


- org.dotcms.adv.XMLSitemap.XML_SITEMAPS_FOLDER, default "/sitemaps/"
- org.dotcms.adv.XMLSitemap.SITEMAP_XML_FILENAME, default "sitemap"
- org.dotcms.adv.XMLSitemap.SITEMAP_PROTOCOL, default "https://"
- org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL, default host.getHostname
- org.dotcms.adv.XMLSitemap.SITEMAP_BASE_URL_<HOSTNAME>, default host.getHostname
- org.dotcms.adv.XMLSitemap.IGNORE_hostname, default ""
- org.dotcms.adv.XMLSitemap.IGNORE_Structure_Ids, defualt ""
- org.dotcms.adv.XMLSitemap.IGNORE_Structure_Ids_<HOSTNAME>, defualt ""
adv.
