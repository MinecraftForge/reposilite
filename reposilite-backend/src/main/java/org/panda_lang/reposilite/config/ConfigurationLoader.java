/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.config;

import net.dzikoysk.cdn.Cdn;
import net.dzikoysk.cdn.CdnDeserializer;
import net.dzikoysk.cdn.CdnSerializer;
import net.dzikoysk.cdn.CdnSettings;
import net.dzikoysk.cdn.features.DefaultFeature;
import net.dzikoysk.cdn.model.Element;
import net.dzikoysk.cdn.model.Section;
import net.dzikoysk.cdn.serialization.Composer;

import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteConstants;
import org.panda_lang.reposilite.config.Configuration.Repository;
import org.panda_lang.reposilite.utils.FilesUtils;
import org.panda_lang.utilities.commons.ClassUtils;
import org.panda_lang.utilities.commons.FileUtils;
import org.panda_lang.utilities.commons.StringUtils;

import java.io.File;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;

public final class ConfigurationLoader {

    public static Cdn createCdn() {
        Predicate<Class<?>> test = cls -> {
            try {
                cls.getConstructor(); // Has default constructor, should we add anymore checks?
                return true;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        };
        Composer<?> comp = new Composer<Object>() {
            @Override
            public Element<?> serialize(CdnSettings settings, List<String> description, String key, AnnotatedType type, Object entry) throws Exception {
                Element<?> ret = new CdnSerializer(settings).serialize(entry);
                if (key == null || key.isEmpty())
                    return ret;
                Section sec = new Section(description, key);
                sec.append(ret);
                return sec;
            }

            @Override
            public Object deserialize(CdnSettings settings, Element<?> source, AnnotatedType type, Object defaultValue, boolean entryAsRecord) throws Exception {
                if (!(source instanceof Section)) {
                    throw new UnsupportedOperationException("Unsupported object source: " + source.getClass());
                }
                Class<?> clz = toClass(type.getType());
                @SuppressWarnings("unchecked")
                Object ret = new CdnDeserializer<Object>(settings).deserialize((Class<Object>)clz, (Section)source);
                return ret;
            }
        };
        return Cdn.configure()
            .withDynamicComposer(test, comp)
            .installFeature(new DefaultFeature())
            .build();
    }

    public static Class<?> toClass(Type type) {
        if (type instanceof ParameterizedType) {
            return toClass(((ParameterizedType)type).getRawType());
        }

        try {
            return Class.forName(type.getTypeName());
        }
        catch (ClassNotFoundException classNotFoundException) {
            throw new IllegalArgumentException("Cannot find generic type " + type);
        }
    }

    public static Configuration tryLoad(String customConfigurationFile, String workingDirectory) {
        try {
            return load(customConfigurationFile, workingDirectory);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception exception) {
            throw new RuntimeException("Cannot load configuration", exception);
        }
    }

    public static Configuration load(String customConfigurationFile, String workingDirectory) throws Exception {
        File configurationFile = StringUtils.isEmpty(customConfigurationFile)
            ? new File(workingDirectory, ReposiliteConstants.CONFIGURATION_FILE_NAME)
            : new File(customConfigurationFile);

        if (!FilesUtils.getExtension(configurationFile.getName()).equals("cdn")) {
            throw new IllegalArgumentException("Custom configuration file does not have '.cdn' extension");
        }

        Cdn cdn = createCdn();

        Configuration configuration = configurationFile.exists()
            ? cdn.load(configurationFile, Configuration.class)
            : new Configuration();


        sanitize(configuration);
        FileUtils.overrideFile(configurationFile, cdn.render(configuration));
        loadProperties("reposilite", configuration);
        sanitize(configuration);

        return configuration;
    }

    private static void sanitize(Configuration config) {
        String basePath = config.basePath;

        if (!StringUtils.isEmpty(basePath)) {
            if (!basePath.startsWith("/")) {
                basePath = "/" + basePath;
            }

            if (!basePath.endsWith("/")) {
                basePath += "/";
            }

            config.basePath = basePath;
        }

        for (Entry<String, Repository> entry : config.repositories.entrySet()) {
            String name = entry.getKey();
            Repository repo = entry.getValue();
            for (int index = 0; index < repo.proxies.size(); index++) {
                String proxy = repo.proxies.get(index);

                if (!proxy.endsWith("/")) {
                    repo.proxies.set(index, proxy + '/');
                }
            }

            if (repo.delegate != null) {
                repo.delegate = repo.delegate.trim();
                if (repo.delegate.isEmpty())
                    repo.delegate = null;
                else if (!config.repositories.containsKey(repo.delegate))
                    throw new IllegalStateException("Repository " + name + " specifies delegate \"" + repo.delegate + "\" that does not exist.");
                else if (!repo.proxies.isEmpty())
                    throw new IllegalStateException("Repository " + name + " specified a delegate, and proxies. Only one is allowed");
            }

            for (int index = 0; index < repo.prefixes.size(); index++) {
                String prefix = FilesUtils.trim(repo.prefixes.get(index), '/');
                repo.prefixes.set(index, prefix + '/');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadProperties(String prefix, Object inst) {
        for (Field declaredField : inst.getClass().getDeclaredFields()) {
            String custom = System.getProperty(prefix + '.' + declaredField.getName());
            Class<?> type = ClassUtils.getNonPrimitiveClass(declaredField.getType());

            if (Map.class.isAssignableFrom(type)) {
                Map<Object, Object> map = null;
                try {
                    map = (Map<Object, Object>)declaredField.get(inst);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new RuntimeException("Cannot modify configuration value", e);
                }
                if (!StringUtils.isEmpty(custom)) {
                    map.clear();
                    if (!"{}".equals(custom)) { // System.setProperty doesn't like setting to a empty value, so allow '{}' to define a empty map
                        if (!(declaredField.getGenericType() instanceof ParameterizedType)) {
                            throw new RuntimeException("Cannot modify configuration value, could not find map type " + declaredField.getDeclaringClass() + '.' + declaredField.getName());
                        }
                        Type valueType = ((ParameterizedType)declaredField.getGenericType()).getActualTypeArguments()[1];
                        Class<?> valueClz = toClass(valueType);

                        for (Object name : custom.split(",")) {
                            try {
                                map.put(name, (Object)valueClz.getConstructor().newInstance());
                            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                                throw new RuntimeException("Cannot modify configuration value, could not instantiate type", e);
                            }
                        }
                    }
                }
                for (Object key : map.keySet()) {
                    loadProperties(prefix + '.' + declaredField.getName() + '.' + key, map.get(key));
                }
                continue;
            }

            if (StringUtils.isEmpty(custom)) {
                continue;
            }

            Object customValue;

            if (String.class == type) {
                customValue = custom;
            }
            else if (Integer.class == type) {
                customValue = Integer.parseInt(custom);
            }
            else if (Boolean.class == type) {
                customValue = Boolean.parseBoolean(custom);
            }
            else if (Collection.class.isAssignableFrom(type)) {
                customValue = Arrays.asList(custom.split(","));
            }
            else {
                Reposilite.getLogger().info("Unsupported type: " + type + " for " + custom);
                continue;
            }

            try {
                declaredField.set(inst, customValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot modify configuration value", e);
            }
        }
    }

}
