package org.panda_lang.reposilite.log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import org.panda_lang.reposilite.utils.FilesUtils;
import org.tinylog.configuration.PropertiesConfigurationLoader;
import org.tinylog.runtime.RuntimeProvider;

public class ExtractingConfigurationLoader extends PropertiesConfigurationLoader {
    private static final String CONFIGURATION_FILE = "/tinylog.properties";
    private static final String CONFIGURATION_PROPERTY = "tinylog.configuration";
    private static final Pattern URL_DETECTION_PATTERN = Pattern.compile("^[a-zA-Z]{2,}:/.*");

    @Override
    public Properties load() {
        String file = System.getProperty(CONFIGURATION_PROPERTY);
        if (file != null && !URL_DETECTION_PATTERN.matcher(file).matches()) {
            InputStream stream = RuntimeProvider.getClassLoader().getResourceAsStream(file);
            if (stream == null) {
                File target = new File(file);
                if (!target.exists()) {
                    try {
                        FilesUtils.copyResource(CONFIGURATION_FILE, target);
                    } catch (IOException e) {
                        System.out.println("Failed to extract default log config to " + target);
                        e.printStackTrace();
                    }
                }
            } else {
                try {
                    stream.close();
                } catch (IOException e) {}
            }
        }
        return super.load();
    }
}
