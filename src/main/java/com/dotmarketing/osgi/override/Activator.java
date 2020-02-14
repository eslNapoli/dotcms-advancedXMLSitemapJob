package com.dotmarketing.osgi.override;

import com.dotmarketing.loggers.Log4jUtil;
import com.dotmarketing.osgi.GenericBundleActivator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.osgi.framework.BundleContext;

/**
 * Created by Jonathan Gamba Date: 6/18/12
 */
public class Activator extends GenericBundleActivator {

    private LoggerContext pluginLoggerContext;

    private Logger log = LogManager.getLogger(this.getClass());

    @SuppressWarnings("unchecked")
    public void start(BundleContext context) throws Exception {

        //Initializing log4j...
        LoggerContext dotcmsLoggerContext = Log4jUtil.getLoggerContext();
        //Initialing the log4j context of this plugin based on the dotCMS logger context
        pluginLoggerContext = (LoggerContext) LogManager
            .getContext(this.getClass().getClassLoader(),
                false,
                dotcmsLoggerContext,
                dotcmsLoggerContext.getConfigLocation());

        log.info("Plugin di override in caricamento");

        //Initializing services...
        initializeServices(context);

        log.info("Plugin di override caricato");

    }

    public void stop(BundleContext context) throws Exception {

        //Unpublish bundle services
        unpublishBundleServices();

        log.info("Plugin di override rimosso");

        //Shutting down log4j in order to avoid memory leaks
        Log4jUtil.shutdown(pluginLoggerContext);
    }

}