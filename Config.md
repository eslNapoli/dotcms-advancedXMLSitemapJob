        <Async name="sitemap">
           <AppenderRef ref="SITEMAP-FILE" />
        </Async>

        <RollingFile name="SITEMAP-FILE"
                     fileName="${sys:DOTCMS_LOGGING_HOME}/dotcms-sitemap.log"
                     filePattern="${sys:DOTCMS_LOGGING_HOME}/archive/dotcms-sitemap%i.log.gz">
             <PatternLayout pattern="${MESSAGE_PATTERN}" />
             <Policies>
                     <SizeBasedTriggeringPolicy size="20MB" />
             </Policies>
             <DefaultRolloverStrategy max="10" />
        </RollingFile>

     <Logger name="com.dotcms.xmlsitemap" level="debug" additivity="false">
                <AppenderRef ref="sitemap" />
        </Logger>

