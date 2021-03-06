/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.DefaultRepositoryCacheManager;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.plugins.repository.TransferListener;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.artifacts.ResolverContainer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsConverter implements SettingsConverter {
    private static Logger logger = Logging.getLogger(DefaultSettingsConverter.class);

    private RepositoryCacheManager repositoryCacheManager;
    private IvySettings ivySettings;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final TransferListener transferListener = new ProgressLoggingTransferListener();

    public DefaultSettingsConverter(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    private static String getLengthText(TransferEvent evt) {
        return getLengthText(evt.isTotalLengthSet() ? evt.getTotalLength() : null);
    }

    private static String getLengthText(Long bytes) {
        if (bytes == null) {
            return "unknown size";
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1048576) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format("%.2f MB", bytes / 1048576.0);
        }
    }

    public IvySettings convertForPublish(List<DependencyResolver> publishResolvers, File gradleUserHome, DependencyResolver internalRepository) {
        if (ivySettings != null) {
            return ivySettings;
        }
        Clock clock = new Clock();
        ChainResolver userResolverChain = createUserResolverChain(Collections.<DependencyResolver>emptyList(), internalRepository);
        ClientModuleResolver clientModuleResolver = createClientModuleResolver(new HashMap(), userResolverChain);
        ChainResolver outerChain = createOuterChain(userResolverChain, clientModuleResolver);

        IvySettings ivySettings = createIvySettings(gradleUserHome);
        initializeResolvers(ivySettings, getAllResolvers(Collections.<DependencyResolver>emptyList(), publishResolvers, internalRepository, userResolverChain, clientModuleResolver, outerChain));
        ivySettings.setDefaultResolver(CLIENT_MODULE_CHAIN_NAME);
        logger.debug("Timing: Ivy convert for publish took {}", clock.getTime());
        return ivySettings;
    }

    public IvySettings convertForResolve(List<DependencyResolver> dependencyResolvers,
                               File gradleUserHome, DependencyResolver internalRepository, Map clientModuleRegistry) {
        if (ivySettings != null) {
            return ivySettings;
        }
        Clock clock = new Clock();
        ChainResolver userResolverChain = createUserResolverChain(dependencyResolvers, internalRepository);
        ClientModuleResolver clientModuleResolver = createClientModuleResolver(clientModuleRegistry, userResolverChain);
        ChainResolver outerChain = createOuterChain(userResolverChain, clientModuleResolver);

        IvySettings ivySettings = createIvySettings(gradleUserHome);
        initializeResolvers(ivySettings, getAllResolvers(dependencyResolvers, Collections.<DependencyResolver>emptyList(), internalRepository, userResolverChain, clientModuleResolver, outerChain));
        ivySettings.setDefaultResolver(CLIENT_MODULE_CHAIN_NAME);
        logger.debug("Timing: Ivy convert for resolve took {}", clock.getTime());
        return ivySettings;
    }

    private List<DependencyResolver> getAllResolvers(List<DependencyResolver> classpathResolvers,
                                                     List<DependencyResolver> otherResolvers, DependencyResolver internalRepository, 
                                                     ChainResolver userResolverChain, ClientModuleResolver clientModuleResolver,
                                                     ChainResolver outerChain) {
        List<DependencyResolver> allResolvers = new ArrayList<DependencyResolver>(otherResolvers);
        allResolvers.addAll(classpathResolvers);
        allResolvers.addAll(WrapUtil.toList(internalRepository, outerChain, clientModuleResolver, userResolverChain));
        return allResolvers;
    }

    private ChainResolver createOuterChain(ChainResolver userResolverChain, ClientModuleResolver clientModuleResolver) {
        ChainResolver clientModuleChain = new ChainResolver();
        clientModuleChain.setName(CLIENT_MODULE_CHAIN_NAME);
        clientModuleChain.setReturnFirst(true);
        clientModuleChain.add(clientModuleResolver);
        clientModuleChain.add(userResolverChain);
        return clientModuleChain;
    }

    private ClientModuleResolver createClientModuleResolver(Map clientModuleRegistry, ChainResolver userResolverChain) {
        return new ClientModuleResolver(CLIENT_MODULE_NAME, clientModuleRegistry, userResolverChain);
    }

    private ChainResolver createUserResolverChain(List<DependencyResolver> classpathResolvers, DependencyResolver internalRepository) {
        ChainResolver chainResolver = new ChainResolver();
        chainResolver.setName(CHAIN_RESOLVER_NAME);
        chainResolver.add(internalRepository);
        // todo Figure out why Ivy thinks this is necessary. The IBiblio resolver has already this pattern which should be good enough. By doing this we let Maven semantics seep into our whole system.
        chainResolver.setChangingPattern(".*-SNAPSHOT");
        chainResolver.setChangingMatcher(PatternMatcher.REGEXP);
        chainResolver.setReturnFirst(true);
        for (Object classpathResolver : classpathResolvers) {
            chainResolver.add((DependencyResolver) classpathResolver);
        }
        return chainResolver;
    }

    private IvySettings createIvySettings(File gradleUserHome) {
        IvySettings ivySettings = new IvySettings();
        ivySettings.setDefaultCache(new File(gradleUserHome, ResolverContainer.DEFAULT_CACHE_DIR_NAME));
        ivySettings.setDefaultCacheIvyPattern(ResolverContainer.DEFAULT_CACHE_IVY_PATTERN);
        ivySettings.setDefaultCacheArtifactPattern(ResolverContainer.DEFAULT_CACHE_ARTIFACT_PATTERN);
        ivySettings.setVariable("ivy.log.modules.in.use", "false");
        setRepositoryCacheManager(ivySettings);
        return ivySettings;
    }

    private void setRepositoryCacheManager(IvySettings ivySettings) {
        if (repositoryCacheManager == null) {
            repositoryCacheManager = ivySettings.getDefaultRepositoryCacheManager();
        } else {
            ivySettings.setDefaultRepositoryCacheManager(repositoryCacheManager);
        }
    }

    private void initializeResolvers(IvySettings ivySettings, List<DependencyResolver> allResolvers) {
        for (DependencyResolver dependencyResolver : allResolvers) {
            ivySettings.addResolver(dependencyResolver);
            ((DefaultRepositoryCacheManager) dependencyResolver.getRepositoryCacheManager()).setSettings(ivySettings);
            if (dependencyResolver instanceof RepositoryResolver) {
                Repository repository = ((RepositoryResolver) dependencyResolver).getRepository();
                if (!repository.hasTransferListener(transferListener)) {
                    repository.addTransferListener(transferListener);
                }
            }
        }
    }

    public IvySettings getIvySettings() {
        return ivySettings;
    }

    public void setIvySettings(IvySettings ivySettings) {
        this.ivySettings = ivySettings;
    }

    private class ProgressLoggingTransferListener implements TransferListener {
        private ProgressLogger logger;
        private long total;

        public void transferProgress(TransferEvent evt) {
            if (evt.getResource().isLocal()) {
                return;
            }
            if (evt.getEventType() == TransferEvent.TRANSFER_STARTED) {
                total = 0;
                DefaultSettingsConverter.logger.lifecycle("Download " + evt.getResource().getName());
                logger = progressLoggerFactory.start(DefaultSettingsConverter.class.getName());
            }
            if (evt.getEventType() == TransferEvent.TRANSFER_PROGRESS) {
                total += evt.getLength();
                logger.progress(String.format("%s/%s downloaded", getLengthText(total), getLengthText(evt)));
            }
            if (evt.getEventType() == TransferEvent.TRANSFER_COMPLETED
                    || evt.getEventType() == TransferEvent.TRANSFER_ERROR) {
                logger.completed();
            }
        }
    }
}
